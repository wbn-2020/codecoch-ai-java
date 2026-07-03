package com.codecoachai.question.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.config.QuestionImportProperties;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.codecoachai.question.service.QuestionImportService;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Imports question data from Excel, Markdown, DOCX, and PDF files.
 *
 * <p>The service enforces a file-size guard, parses each format into a unified question model,
 * performs duplicate checks, and writes embeddings after the surrounding transaction commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportServiceImpl implements QuestionImportService {
    private static final int IMPORT_POST_COMMIT_SYNC_BATCH_SIZE = 200;

    private final QuestionMapper questionMapper;
    private final QuestionEmbeddingIndexService questionEmbeddingIndexService;
    private final QuestionDuplicateService questionDuplicateService;
    private final QuestionImportProperties questionImportProperties;

    private static final Pattern MD_TITLE_PATTERN = Pattern.compile("^#{1,3}\\s+(.+)$");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importQuestions(String fileName, InputStream inputStream, Long importedBy, boolean dryRun) {
        String ext = getExtension(fileName);
        try (InputStream limitedInputStream = boundedInputStream(inputStream)) {
            return switch (ext) {
                case "xlsx", "xls" -> importExcel(limitedInputStream, importedBy, dryRun);
                case "md", "txt" -> saveQuestions(parseMarkdownIterator(limitedInputStream), importedBy, dryRun);
                case "docx" -> saveQuestions(parseDocx(limitedInputStream), importedBy, dryRun);
                case "pdf" -> saveQuestions(parsePdf(limitedInputStream), importedBy, dryRun);
                default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "\u4e0d\u652f\u6301\u7684\u6587\u4ef6\u683c\u5f0f: " + ext);
            };
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "\u9898\u76ee\u5bfc\u5165\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002");
        }
    }

    // ==================== Excel parsing ====================

    private ImportResult importExcel(InputStream inputStream, Long importedBy, boolean dryRun) {
        ImportSaveContext saveContext = new ImportSaveContext(importedBy, dryRun);
        try {
            EasyExcel.read(inputStream, QuestionExcelRow.class, new ReadListener<QuestionExcelRow>() {
                @Override
                public void invoke(QuestionExcelRow row, AnalysisContext context) {
                    if (!StringUtils.hasText(row.getTitle())) {
                        return;
                    }
                    saveContext.accept(toParsedQuestion(row));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet().doRead();
            return saveContext.finish();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Excel \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
        }
    }

    // ==================== Markdown parsing ====================

    private List<ParsedQuestion> parseMarkdown(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parseMarkdownLines(reader.lines().iterator());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Markdown \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
        }
    }

    private Iterator<ParsedQuestion> parseMarkdownIterator(InputStream inputStream) {
        try {
            return new MarkdownQuestionIterator(
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Markdown \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
        }
    }

    private List<ParsedQuestion> parseMarkdownLines(List<String> lines) {
        return parseMarkdownLines(lines.iterator());
    }

    private List<ParsedQuestion> parseMarkdownLines(Iterator<String> lines) {
        MarkdownQuestionAccumulator accumulator = new MarkdownQuestionAccumulator();
        while (lines.hasNext()) {
            accumulator.acceptLine(lines.next());
        }
        return accumulator.finish();
    }

    private void finishMarkdownQuestion(ParsedQuestion pq, String body) {
        SectionMatch answer = extractSection(body, "\u7b54\u6848", "\u53c2\u8003\u7b54\u6848", "answer");
        SectionMatch analysis = extractSection(body, "\u89e3\u6790", "\u5206\u6790", "analysis");

        if (answer != null && StringUtils.hasText(answer.content())) {
            pq.setReferenceAnswer(answer.content().trim());
            String content = removeMatchedSections(body, answer, analysis).trim();
            if (StringUtils.hasText(content)) {
                pq.setContent(content);
            }
        } else {
            pq.setReferenceAnswer(body.trim());
        }
        if (analysis != null && StringUtils.hasText(analysis.content())) {
            pq.setAnalysis(analysis.content().trim());
        }
    }

    private SectionMatch extractSection(String body, String... labels) {
        for (String label : labels) {
            Pattern p = Pattern.compile("(?:^|\\n)\\s*>?\\s*\\*{0,2}" + Pattern.quote(label) + "\\*{0,2}[:\\uFF1A]\\s*(.+?)(?=\\n\\s*>?\\s*\\*{0,2}(?:[\\u4e00-\\u9fa5A-Za-z][\\u4e00-\\u9fa5A-Za-z\\s]*)\\*{0,2}[:\\uFF1A]|\\z)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(body);
            if (m.find()) {
                return new SectionMatch(m.group(1), m.start(), m.end());
            }
        }
        return null;
    }

    private String removeMatchedSections(String body, SectionMatch first, SectionMatch second) {
        if (first == null && second == null) {
            return body;
        }
        SectionMatch earlier = first;
        SectionMatch later = second;
        if (earlier == null || (later != null && later.start() < earlier.start())) {
            earlier = second;
            later = first;
        }
        StringBuilder content = new StringBuilder(body.length());
        int cursor = appendUnmatchedSegment(body, content, 0, earlier);
        appendUnmatchedSegment(body, content, cursor, later);
        return content.toString();
    }

    private int appendUnmatchedSegment(String body, StringBuilder content, int cursor, SectionMatch match) {
        if (match == null) {
            if (cursor < body.length()) {
                content.append(body, cursor, body.length());
            }
            return body.length();
        }
        if (cursor < match.start()) {
            content.append(body, cursor, match.start());
        }
        return Math.max(cursor, match.end());
    }

    private record SectionMatch(String content, int start, int end) {
    }

    // ==================== DOCX parsing ====================

    private Iterator<ParsedQuestion> parseDocx(InputStream inputStream) {
        try {
            XWPFDocument doc = new XWPFDocument(inputStream);
            return parseDocxParagraphIterator(docParagraphIterator(doc), doc);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "DOCX \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
        }
    }

    private Iterator<XWPFParagraph> docParagraphIterator(XWPFDocument doc) {
        return new BodyParagraphIterator(doc.getBodyElements());
    }

    private Iterator<ParsedQuestion> parseDocxParagraphIterator(
            Iterator<XWPFParagraph> paragraphs,
            AutoCloseable closeable) {
        return new DocxQuestionIterator(paragraphs, closeable);
    }

    // ==================== PDF parsing ====================

    private Iterator<ParsedQuestion> parsePdf(InputStream inputStream) {
        try {
            RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(inputStream);
            PDDocument doc = Loader.loadPDF(buffer);
            return parsePdfTextIterator(new PdfPageLineIterator(doc, buffer));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
        }
    }

    private Iterator<ParsedQuestion> parsePdfTextIterator(Iterator<String> lines) {
        return new PdfTextQuestionIterator(lines);
    }

    // ==================== Input size guard ====================

    private InputStream boundedInputStream(InputStream inputStream) {
        long maxBytes = questionImportProperties.safeMaxFileBytes();
        return new FilterInputStream(inputStream) {
            private long total;

            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value != -1) {
                    trackBytes(1L);
                }
                return value;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int read = super.read(b, off, len);
                if (read > 0) {
                    trackBytes(read);
                }
                return read;
            }

            @Override
            public long skip(long n) throws IOException {
                long skipped = super.skip(n);
                if (skipped > 0L) {
                    trackBytes(skipped);
                }
                return skipped;
            }

            private void trackBytes(long delta) {
                total += delta;
                if (total > maxBytes) {
                    throw new BusinessException(
                            ErrorCode.PARAM_ERROR,
                            "\u9898\u76ee\u5bfc\u5165\u6587\u4ef6\u4e0d\u80fd\u8d85\u8fc7 " + questionImportProperties.maxFileSizeLabel());
                }
            }
        };
    }

    private final class MarkdownQuestionAccumulator {
        private final List<ParsedQuestion> results = new ArrayList<>();
        private ParsedQuestion current;
        private StringBuilder contentBuilder = new StringBuilder();

        private void acceptLine(String line) {
            Matcher matcher = MD_TITLE_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                finishCurrentQuestion();
                current = new ParsedQuestion();
                current.setTitle(matcher.group(1).trim());
                contentBuilder = new StringBuilder();
                return;
            }
            if (current != null) {
                contentBuilder.append(line).append("\n");
            }
        }

        private List<ParsedQuestion> finish() {
            finishCurrentQuestion();
            return results;
        }

        private void finishCurrentQuestion() {
            if (current == null) {
                return;
            }
            finishMarkdownQuestion(current, contentBuilder.toString());
            results.add(current);
            current = null;
            contentBuilder = new StringBuilder();
        }
    }

    private final class MarkdownQuestionIterator implements Iterator<ParsedQuestion>, AutoCloseable {
        private final BufferedReader reader;
        private ParsedQuestion current;
        private StringBuilder contentBuilder = new StringBuilder();
        private ParsedQuestion nextQuestion;
        private boolean exhausted;
        private boolean closed;

        private MarkdownQuestionIterator(BufferedReader reader) {
            this.reader = reader;
        }

        @Override
        public boolean hasNext() {
            if (nextQuestion != null) {
                return true;
            }
            if (exhausted) {
                return false;
            }
            loadNextQuestion();
            return nextQuestion != null;
        }

        @Override
        public ParsedQuestion next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            ParsedQuestion question = nextQuestion;
            nextQuestion = null;
            return question;
        }

        @Override
        public void close() {
            closeQuietly();
        }

        private void loadNextQuestion() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = MD_TITLE_PATTERN.matcher(line.trim());
                    if (matcher.matches()) {
                        String title = matcher.group(1).trim();
                        if (current == null) {
                            startQuestion(title);
                            continue;
                        }
                        ParsedQuestion completedQuestion = finishCurrentQuestion();
                        startQuestion(title);
                        nextQuestion = completedQuestion;
                        return;
                    }
                    if (current != null) {
                        contentBuilder.append(line).append("\n");
                    }
                }

                exhausted = true;
                nextQuestion = finishCurrentQuestion();
                closeQuietly();
            } catch (BusinessException ex) {
                exhausted = true;
                closeQuietly();
                throw ex;
            } catch (Exception ex) {
                exhausted = true;
                closeQuietly();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Markdown \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
            }
        }

        private void startQuestion(String title) {
            current = new ParsedQuestion();
            current.setTitle(title);
            contentBuilder = new StringBuilder();
        }

        private ParsedQuestion finishCurrentQuestion() {
            if (current == null) {
                return null;
            }
            ParsedQuestion completedQuestion = current;
            finishMarkdownQuestion(completedQuestion, contentBuilder.toString());
            current = null;
            contentBuilder = new StringBuilder();
            return completedQuestion;
        }

        private void closeQuietly() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                reader.close();
            } catch (IOException ex) {
                log.warn("Question markdown reader close failed", ex);
            }
        }
    }

    private final class DocxQuestionIterator implements Iterator<ParsedQuestion>, AutoCloseable {
        private final Iterator<XWPFParagraph> paragraphs;
        private final AutoCloseable closeable;
        private ParsedQuestion current;
        private StringBuilder contentBuilder = new StringBuilder();
        private ParsedQuestion nextQuestion;
        private boolean exhausted;
        private boolean closed;

        private DocxQuestionIterator(Iterator<XWPFParagraph> paragraphs, AutoCloseable closeable) {
            this.paragraphs = paragraphs;
            this.closeable = closeable;
        }

        @Override
        public boolean hasNext() {
            if (nextQuestion != null) {
                return true;
            }
            if (exhausted) {
                return false;
            }
            loadNextQuestion();
            return nextQuestion != null;
        }

        @Override
        public ParsedQuestion next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            ParsedQuestion question = nextQuestion;
            nextQuestion = null;
            return question;
        }

        @Override
        public void close() {
            closeQuietly();
        }

        private void loadNextQuestion() {
            try {
                while (paragraphs.hasNext()) {
                    XWPFParagraph para = paragraphs.next();
                    String text = para.getText();
                    String style = para.getStyleID();
                    String line = null;
                    if (style != null && (style.startsWith("Heading") || style.startsWith("heading"))) {
                        line = "## " + text;
                    } else if (text != null && !text.isBlank()) {
                        line = text;
                    }
                    if (line == null) {
                        continue;
                    }

                    Matcher matcher = MD_TITLE_PATTERN.matcher(line.trim());
                    if (matcher.matches()) {
                        String title = matcher.group(1).trim();
                        if (current == null) {
                            startQuestion(title);
                            continue;
                        }
                        ParsedQuestion completedQuestion = finishCurrentQuestion();
                        startQuestion(title);
                        nextQuestion = completedQuestion;
                        return;
                    }
                    if (current != null) {
                        contentBuilder.append(line).append("\n");
                    }
                }

                exhausted = true;
                nextQuestion = finishCurrentQuestion();
                closeQuietly();
            } catch (BusinessException ex) {
                exhausted = true;
                closeQuietly();
                throw ex;
            } catch (Exception ex) {
                exhausted = true;
                closeQuietly();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "DOCX \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
            }
        }

        private void startQuestion(String title) {
            current = new ParsedQuestion();
            current.setTitle(title);
            contentBuilder = new StringBuilder();
        }

        private ParsedQuestion finishCurrentQuestion() {
            if (current == null) {
                return null;
            }
            ParsedQuestion completedQuestion = current;
            finishMarkdownQuestion(completedQuestion, contentBuilder.toString());
            current = null;
            contentBuilder = new StringBuilder();
            return completedQuestion;
        }

        private void closeQuietly() {
            if (closed) {
                return;
            }
            closed = true;
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception ex) {
                log.warn("Question DOCX iterator close failed", ex);
            }
        }
    }

    private final class PdfTextQuestionIterator implements Iterator<ParsedQuestion>, AutoCloseable {
        private final Iterator<String> lines;
        private final AutoCloseable closeable;
        private ParsedQuestion current;
        private StringBuilder contentBuilder = new StringBuilder();
        private ParsedQuestion nextQuestion;
        private boolean exhausted;
        private boolean closed;

        private PdfTextQuestionIterator(Iterator<String> lines) {
            this(lines, lines instanceof AutoCloseable closeable ? closeable : null);
        }

        private PdfTextQuestionIterator(Iterator<String> lines, AutoCloseable closeable) {
            this.lines = lines;
            this.closeable = closeable;
        }

        @Override
        public boolean hasNext() {
            if (nextQuestion != null) {
                return true;
            }
            if (exhausted) {
                return false;
            }
            loadNextQuestion();
            return nextQuestion != null;
        }

        @Override
        public ParsedQuestion next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ParsedQuestion question = nextQuestion;
            nextQuestion = null;
            return question;
        }

        @Override
        public void close() {
            closeQuietly();
        }

        private void loadNextQuestion() {
            try {
                while (lines.hasNext()) {
                    String line = lines.next();
                    Matcher matcher = MD_TITLE_PATTERN.matcher(line.trim());
                    if (matcher.matches()) {
                        String title = matcher.group(1).trim();
                        if (current == null) {
                            startQuestion(title);
                            continue;
                        }
                        ParsedQuestion completedQuestion = finishCurrentQuestion();
                        startQuestion(title);
                        nextQuestion = completedQuestion;
                        return;
                    }
                    if (current != null) {
                        contentBuilder.append(line).append("\n");
                    }
                }

                exhausted = true;
                nextQuestion = finishCurrentQuestion();
                closeQuietly();
            } catch (BusinessException ex) {
                exhausted = true;
                closeQuietly();
                throw ex;
            } catch (Exception ex) {
                exhausted = true;
                closeQuietly();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
            }
        }

        private void startQuestion(String title) {
            current = new ParsedQuestion();
            current.setTitle(title);
            contentBuilder = new StringBuilder();
        }

        private ParsedQuestion finishCurrentQuestion() {
            if (current == null) {
                return null;
            }
            ParsedQuestion completedQuestion = current;
            finishMarkdownQuestion(completedQuestion, contentBuilder.toString());
            current = null;
            contentBuilder = new StringBuilder();
            return completedQuestion;
        }

        private void closeQuietly() {
            if (closed) {
                return;
            }
            closed = true;
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception ex) {
                log.warn("Question PDF iterator close failed", ex);
            }
        }
    }

    private static final class BodyParagraphIterator implements Iterator<XWPFParagraph> {
        private final Deque<Iterator<IBodyElement>> bodyIterators = new ArrayDeque<>();
        private XWPFParagraph nextParagraph;

        private BodyParagraphIterator(List<IBodyElement> bodyElements) {
            if (bodyElements != null && !bodyElements.isEmpty()) {
                bodyIterators.push(bodyElements.iterator());
            }
        }

        @Override
        public boolean hasNext() {
            if (nextParagraph != null) {
                return true;
            }
            nextParagraph = loadNextParagraph();
            return nextParagraph != null;
        }

        @Override
        public XWPFParagraph next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            XWPFParagraph paragraph = nextParagraph;
            nextParagraph = null;
            return paragraph;
        }

        private XWPFParagraph loadNextParagraph() {
            while (!bodyIterators.isEmpty()) {
                Iterator<IBodyElement> iterator = bodyIterators.peek();
                if (!iterator.hasNext()) {
                    bodyIterators.pop();
                    continue;
                }
                IBodyElement element = iterator.next();
                if (element == null) {
                    continue;
                }
                if (element.getElementType() == BodyElementType.PARAGRAPH && element instanceof XWPFParagraph paragraph) {
                    return paragraph;
                }
                if (element.getElementType() == BodyElementType.TABLE && element instanceof XWPFTable table) {
                    Iterator<IBodyElement> tableIterator = new TableBodyElementIterator(table);
                    if (tableIterator.hasNext()) {
                        bodyIterators.push(tableIterator);
                    }
                }
            }
            return null;
        }
    }

    private static final class TableBodyElementIterator implements Iterator<IBodyElement> {
        private final List<XWPFTableRow> rows;
        private int rowIndex;
        private int cellIndex;
        private Iterator<IBodyElement> currentBodyIterator;
        private IBodyElement nextElement;

        private TableBodyElementIterator(XWPFTable table) {
            this.rows = table == null ? List.of() : table.getRows();
        }

        @Override
        public boolean hasNext() {
            if (nextElement != null) {
                return true;
            }
            nextElement = loadNextElement();
            return nextElement != null;
        }

        @Override
        public IBodyElement next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            IBodyElement element = nextElement;
            nextElement = null;
            return element;
        }

        private IBodyElement loadNextElement() {
            while (true) {
                if (currentBodyIterator != null && currentBodyIterator.hasNext()) {
                    return currentBodyIterator.next();
                }
                currentBodyIterator = null;
                XWPFTableCell cell = nextCell();
                if (cell == null) {
                    return null;
                }
                List<IBodyElement> bodyElements = cell.getBodyElements();
                if (bodyElements == null || bodyElements.isEmpty()) {
                    continue;
                }
                currentBodyIterator = bodyElements.iterator();
            }
        }

        private XWPFTableCell nextCell() {
            while (rowIndex < rows.size()) {
                XWPFTableRow row = rows.get(rowIndex);
                List<XWPFTableCell> cells = row == null ? null : row.getTableCells();
                if (cells == null || cellIndex >= cells.size()) {
                    rowIndex++;
                    cellIndex = 0;
                    continue;
                }
                XWPFTableCell cell = cells.get(cellIndex);
                cellIndex++;
                if (cell != null) {
                    return cell;
                }
            }
            return null;
        }
    }

    private static final class PdfPageLineIterator implements Iterator<String>, AutoCloseable {
        private final PDDocument document;
        private final Closeable buffer;
        private final PDFTextStripper stripper = new PDFTextStripper();
        private Iterator<String> pageLines = List.<String>of().iterator();
        private int nextPage = 1;
        private boolean closed;

        private PdfPageLineIterator(PDDocument document, Closeable buffer) {
            this.document = document;
            this.buffer = buffer;
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                return false;
            }
            try {
                while (!pageLines.hasNext()) {
                    if (document == null || nextPage > document.getNumberOfPages()) {
                        close();
                        return false;
                    }
                    stripper.setStartPage(nextPage);
                    stripper.setEndPage(nextPage);
                    nextPage++;
                    pageLines = stripper.getText(document).lines().iterator();
                }
                return true;
            } catch (BusinessException ex) {
                closeQuietly();
                throw ex;
            } catch (Exception ex) {
                closeQuietly();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF \u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u5185\u5bb9\u683c\u5f0f\u3002");
            }
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return pageLines.next();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Exception failure = null;
            try {
                document.close();
            } catch (Exception ex) {
                failure = ex;
            }
            try {
                buffer.close();
            } catch (Exception ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
            if (failure != null) {
                throw new IllegalStateException("Question PDF iterator close failed", failure);
            }
        }

        private void closeQuietly() {
            try {
                close();
            } catch (Exception ignore) {
            }
        }
    }

    private ImportResult saveQuestions(List<ParsedQuestion> parsed, Long importedBy, boolean dryRun) {
        return saveQuestions(parsed.iterator(), importedBy, dryRun);
    }

    private ImportResult saveQuestions(Iterator<ParsedQuestion> parsed, Long importedBy, boolean dryRun) {
        ImportSaveContext saveContext = new ImportSaveContext(importedBy, dryRun);
        try {
            while (parsed.hasNext()) {
                saveContext.accept(parsed.next());
            }
            return saveContext.finish();
        } finally {
            closeParsedIterator(parsed);
        }
    }

    private void closeParsedIterator(Iterator<ParsedQuestion> parsed) {
        if (!(parsed instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            log.warn("Question import iterator close failed", ex);
        }
    }

    private ParsedQuestion toParsedQuestion(QuestionExcelRow row) {
        ParsedQuestion parsedQuestion = new ParsedQuestion();
        parsedQuestion.setTitle(row.getTitle());
        parsedQuestion.setContent(row.getContent());
        parsedQuestion.setReferenceAnswer(row.getReferenceAnswer());
        parsedQuestion.setAnalysis(row.getAnalysis());
        parsedQuestion.setDifficulty(row.getDifficulty());
        parsedQuestion.setQuestionType(row.getQuestionType());
        parsedQuestion.setExperienceLevel(row.getExperienceLevel());
        parsedQuestion.setCategoryName(row.getCategoryName());
        parsedQuestion.setTags(row.getTags());
        return parsedQuestion;
    }

    private final class ImportSaveContext {
        private final ImportResult result = new ImportResult();
        private final Map<String, Integer> duplicateReasonCounts = new LinkedHashMap<>();
        private final Set<String> seenNormalizedTitleHashes = new LinkedHashSet<>();
        private final Set<String> seenContentHashes = new LinkedHashSet<>();
        private final List<List<Long>> importedQuestionIdBatches = new ArrayList<>();
        private final List<Long> pendingImportedQuestionIds = new ArrayList<>(IMPORT_POST_COMMIT_SYNC_BATCH_SIZE);
        private final Long importedBy;
        private final boolean dryRun;
        private int totalCount;
        private int successCount;
        private int failCount;
        private int duplicateCount;

        private ImportSaveContext(Long importedBy, boolean dryRun) {
            this.importedBy = importedBy;
            this.dryRun = dryRun;
            result.setErrors(new ArrayList<>());
        }

        private void accept(ParsedQuestion parsedQuestion) {
            totalCount++;
            int rowIndex = totalCount;
            try {
                if (!StringUtils.hasText(parsedQuestion.getTitle())) {
                    failCount++;
                    addError(rowIndex, "", "\u8bf7\u586b\u5199\u9898\u76ee\u6807\u9898");
                    return;
                }
                String sizeLimitViolation = questionSizeLimitViolation(parsedQuestion);
                if (StringUtils.hasText(sizeLimitViolation)) {
                    failCount++;
                    addError(rowIndex, parsedQuestion.getTitle(), sizeLimitViolation);
                    return;
                }

                String normalized = QuestionTextNormalizeUtils.normalizeTitle(parsedQuestion.getTitle());
                String normalizedTitleHash = QuestionTextNormalizeUtils.sha256Hex(normalized);
                String normalizedContent = QuestionTextNormalizeUtils.normalizeContent(
                        parsedQuestion.getTitle(),
                        parsedQuestion.getContent(),
                        parsedQuestion.getReferenceAnswer(),
                        parsedQuestion.getAnalysis());
                String contentHash = QuestionTextNormalizeUtils.sha256Hex(normalizedContent);

                if (StringUtils.hasText(normalizedTitleHash) && seenNormalizedTitleHashes.contains(normalizedTitleHash)) {
                    duplicateCount++;
                    incrementDuplicateReason(duplicateReasonCounts, "FILE_TITLE_DUPLICATE");
                    addError(rowIndex, parsedQuestion.getTitle(), "FILE_TITLE_DUPLICATE");
                    return;
                }
                if (StringUtils.hasText(contentHash) && seenContentHashes.contains(contentHash)) {
                    duplicateCount++;
                    incrementDuplicateReason(duplicateReasonCounts, "FILE_CONTENT_DUPLICATE");
                    addError(rowIndex, parsedQuestion.getTitle(), "FILE_CONTENT_DUPLICATE");
                    return;
                }

                Long titleExistsCount = questionMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                                .eq(StringUtils.hasText(normalizedTitleHash), Question::getNormalizedTitleHash, normalizedTitleHash));
                if (titleExistsCount > 0) {
                    duplicateCount++;
                    incrementDuplicateReason(duplicateReasonCounts, "BANK_TITLE_DUPLICATE");
                    addError(rowIndex, parsedQuestion.getTitle(), "BANK_TITLE_DUPLICATE");
                    return;
                }
                Long contentExistsCount = questionMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                                .eq(StringUtils.hasText(contentHash), Question::getContentHash, contentHash));
                if (contentExistsCount > 0) {
                    duplicateCount++;
                    incrementDuplicateReason(duplicateReasonCounts, "BANK_CONTENT_DUPLICATE");
                    addError(rowIndex, parsedQuestion.getTitle(), "BANK_CONTENT_DUPLICATE");
                    return;
                }

                Question question = new Question();
                question.setTitle(parsedQuestion.getTitle());
                question.setNormalizedTitle(normalized);
                question.setNormalizedTitleHash(normalizedTitleHash);
                question.setContentHash(contentHash);
                question.setContent(parsedQuestion.getContent());
                question.setReferenceAnswer(parsedQuestion.getReferenceAnswer());
                question.setAnalysis(parsedQuestion.getAnalysis());
                question.setDifficulty(StringUtils.hasText(parsedQuestion.getDifficulty()) ? parsedQuestion.getDifficulty() : "MEDIUM");
                question.setQuestionType(StringUtils.hasText(parsedQuestion.getQuestionType()) ? parsedQuestion.getQuestionType() : "SHORT_ANSWER");
                question.setExperienceLevel(parsedQuestion.getExperienceLevel());
                question.setIsHighFrequency(0);
                question.setIsRecommended(0);
                question.setStatus(1);
                question.setAuditStatus("APPROVED");
                question.setSourceType("IMPORT");
                if (!dryRun) {
                    questionMapper.insert(question);
                    queueImportedQuestionId(question.getId());
                }
                if (StringUtils.hasText(normalizedTitleHash)) {
                    seenNormalizedTitleHashes.add(normalizedTitleHash);
                }
                if (StringUtils.hasText(contentHash)) {
                    seenContentHashes.add(contentHash);
                }
                successCount++;
            } catch (Exception ex) {
                failCount++;
                String title = parsedQuestion != null ? parsedQuestion.getTitle() : null;
                log.warn("Question import row failed, rowIndex={}, title={}", rowIndex, title, ex);
                addError(rowIndex, title, "\u884c\u6570\u636e\u5bfc\u5165\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u5b57\u6bb5\u683c\u5f0f\u3001\u5206\u7c7b\u548c\u9898\u76ee\u5185\u5bb9");
            }
        }

        private ImportResult finish() {
            result.setTotalCount(totalCount);
            result.setSuccessCount(successCount);
            result.setFailCount(failCount);
            result.setDuplicateCount(duplicateCount);
            result.setDuplicateReasonCounts(duplicateReasonCounts);
            if (!dryRun) {
                flushPendingImportedQuestionIds();
                syncQuestionEmbeddingAndDuplicateCheckAfterCommit(importedQuestionIdBatches, importedBy);
            }
            log.info(
                    "\u9898\u76ee\u5bfc\u5165\u5b8c\u6210 total={} success={} fail={} duplicate={}",
                    totalCount,
                    successCount,
                    failCount,
                    duplicateCount);
            return result;
        }

        private void addError(int rowIndex, String title, String reason) {
            ImportError err = new ImportError();
            err.setRowIndex(rowIndex);
            err.setTitle(title);
            err.setReason(reason);
            result.getErrors().add(err);
        }

        private void queueImportedQuestionId(Long questionId) {
            if (questionId == null) {
                return;
            }
            pendingImportedQuestionIds.add(questionId);
            if (pendingImportedQuestionIds.size() >= IMPORT_POST_COMMIT_SYNC_BATCH_SIZE) {
                flushPendingImportedQuestionIds();
            }
        }

        private void flushPendingImportedQuestionIds() {
            if (pendingImportedQuestionIds.isEmpty()) {
                return;
            }
            importedQuestionIdBatches.add(new ArrayList<>(pendingImportedQuestionIds));
            pendingImportedQuestionIds.clear();
        }
    }

    private String questionSizeLimitViolation(ParsedQuestion parsedQuestion) {
        if (parsedQuestion == null) {
            return null;
        }
        if (charLength(parsedQuestion.getTitle()) > questionImportProperties.safeMaxTitleChars()) {
            return "\u9898\u76ee\u6807\u9898\u8fc7\u957f\uff0c\u8bf7\u7f29\u77ed\u540e\u91cd\u8bd5";
        }
        if (charLength(parsedQuestion.getContent()) > questionImportProperties.safeMaxFieldChars()
                || charLength(parsedQuestion.getReferenceAnswer()) > questionImportProperties.safeMaxFieldChars()
                || charLength(parsedQuestion.getAnalysis()) > questionImportProperties.safeMaxFieldChars()) {
            return "\u9898\u76ee\u5b57\u6bb5\u8fc7\u957f\uff0c\u8bf7\u62c6\u5206\u6216\u7f29\u77ed\u540e\u91cd\u8bd5";
        }
        if (totalQuestionChars(parsedQuestion) > questionImportProperties.safeMaxEntryChars()) {
            return "\u5355\u9898\u5185\u5bb9\u8fc7\u957f\uff0c\u8bf7\u62c6\u5206\u6216\u7f29\u77ed\u540e\u91cd\u8bd5";
        }
        return null;
    }

    private long totalQuestionChars(ParsedQuestion parsedQuestion) {
        return charLength(parsedQuestion.getTitle())
                + charLength(parsedQuestion.getContent())
                + charLength(parsedQuestion.getReferenceAnswer())
                + charLength(parsedQuestion.getAnalysis())
                + charLength(parsedQuestion.getDifficulty())
                + charLength(parsedQuestion.getQuestionType())
                + charLength(parsedQuestion.getExperienceLevel())
                + charLength(parsedQuestion.getCategoryName())
                + charLength(parsedQuestion.getTags());
    }

    private long charLength(String value) {
        return value == null ? 0L : value.length();
    }

    private void syncQuestionEmbeddingAndDuplicateCheckAfterCommit(List<List<Long>> questionIdBatches, Long importedBy) {
        if (questionIdBatches == null || questionIdBatches.isEmpty()) {
            return;
        }
        Runnable action = () -> {
            for (List<Long> batch : questionIdBatches) {
                if (batch == null || batch.isEmpty()) {
                    continue;
                }
                try {
                    questionEmbeddingIndexService.indexQuestions(batch);
                } catch (Exception ex) {
                    log.error("Question import after-commit sync failed syncType=question_embedding_sync questionIds={} op=UPSERT reason={}",
                            batch, buildAfterCommitFailureReason(ex), ex);
                }
                for (Long questionId : batch) {
                    runAfterCommitSafely("question_duplicate_check", questionId, "CHECK",
                            () -> questionDuplicateService.checkDuplicateForQuestion(questionId, importedBy));
                }
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void runAfterCommitSafely(String syncType, Long questionId, String op, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Question import after-commit sync failed syncType={} questionId={} op={} reason={}",
                    syncType, questionId, op, buildAfterCommitFailureReason(ex), ex);
        }
    }

    private String buildAfterCommitFailureReason(Exception ex) {
        if (ex == null) {
            return "unknown";
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private void incrementDuplicateReason(Map<String, Integer> counts, String reasonCode) {
        counts.put(reasonCode, counts.getOrDefault(reasonCode, 0) + 1);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    // ==================== Excel row mapping ====================

    @lombok.Data
    @com.alibaba.excel.annotation.ExcelIgnoreUnannotated
    public static class QuestionExcelRow {
        @com.alibaba.excel.annotation.ExcelProperty(value = "Title", index = 0)
        private String title;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Content", index = 1)
        private String content;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Reference Answer", index = 2)
        private String referenceAnswer;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Analysis", index = 3)
        private String analysis;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Difficulty", index = 4)
        private String difficulty;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Question Type", index = 5)
        private String questionType;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Experience Level", index = 6)
        private String experienceLevel;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Category", index = 7)
        private String categoryName;
        @com.alibaba.excel.annotation.ExcelProperty(value = "Tags", index = 8)
        private String tags;
    }
}
