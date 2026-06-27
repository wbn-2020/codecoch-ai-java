package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.config.QuestionImportProperties;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.codecoachai.question.service.QuestionImportService.ImportResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceImplTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionEmbeddingIndexService questionEmbeddingIndexService;
    @Mock
    private QuestionDuplicateService questionDuplicateService;

    private QuestionImportServiceImpl service;
    private QuestionImportProperties importProperties;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(Question.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
        }
    }

    @BeforeEach
    void setUp() {
        importProperties = new QuestionImportProperties();
        service = new QuestionImportServiceImpl(
                questionMapper,
                questionEmbeddingIndexService,
                questionDuplicateService,
                importProperties);
    }

    @Test
    void dryRunValidatesDuplicatesWithoutInsertingOrSchedulingSideEffects() {
        when(questionMapper.selectCount(any())).thenReturn(0L);

        ImportResult result = service.importQuestions(
                "questions.md",
                input("""
                        ## Java HashMap 原理
                        请说明 HashMap put 和 resize 流程。
                        """),
                100L,
                true);

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertEquals(0, result.getDuplicateCount());
        verify(questionMapper, never()).insert(any(Question.class));
        verify(questionEmbeddingIndexService, never()).indexQuestions(any());
        verify(questionDuplicateService, never()).checkDuplicateForQuestion(any(), any());
    }

    @Test
    void dryRunStillReportsBankDuplicates() {
        when(questionMapper.selectCount(any())).thenReturn(1L);

        ImportResult result = service.importQuestions(
                "questions.md",
                input("""
                        ## Java HashMap 原理
                        请说明 HashMap put 和 resize 流程。
                        """),
                100L,
                true);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals(1, result.getDuplicateReasonCounts().get("BANK_TITLE_DUPLICATE"));
        verify(questionMapper, never()).insert(any(Question.class));
        verify(questionEmbeddingIndexService, never()).indexQuestions(any());
        verify(questionDuplicateService, never()).checkDuplicateForQuestion(any(), any());
    }

    @Test
    void pdfImportRejectsOversizedInputBeforeParsingWholeFile() {
        importProperties.setMaxFileBytes(8L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importQuestions(
                "questions.pdf",
                new ByteArrayInputStream(new byte[9]),
                100L,
                true));

        assertTrue(ex.getMessage().contains("Question import file cannot exceed 8B."));
        verify(questionMapper, never()).selectCount(any());
        verify(questionMapper, never()).insert(any(Question.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void markdownLineIteratorParserConsumesSinglePassInput() throws Exception {
        Method method = QuestionImportServiceImpl.class.getDeclaredMethod("parseMarkdownLines", Iterator.class);
        method.setAccessible(true);

        Iterator<String> lines = List.of(
                "## Java HashMap 原理",
                "请说明 HashMap put 和 resize 流程。")
                .iterator();

        List<QuestionImportServiceImpl.ParsedQuestion> parsed =
                (List<QuestionImportServiceImpl.ParsedQuestion>) method.invoke(service, lines);

        assertEquals(1, parsed.size());
        assertEquals("Java HashMap 原理", parsed.get(0).getTitle());
        assertTrue(parsed.get(0).getReferenceAnswer().contains("HashMap put"));
    }

    @Test
    void docxImportStillParsesHeadingAndBodyAfterStreamingRefactor() throws Exception {
        when(questionMapper.selectCount(any())).thenReturn(0L);

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Java 并发原理");

            var body = document.createParagraph();
            body.createRun().setText("请说明 synchronized 和 volatile 的区别。");

            document.write(outputStream);

            ImportResult result = service.importQuestions(
                    "questions.docx",
                    new ByteArrayInputStream(outputStream.toByteArray()),
                    100L,
                    true);

            assertEquals(1, result.getTotalCount());
            assertEquals(1, result.getSuccessCount());
            assertEquals(0, result.getFailCount());
            assertEquals(0, result.getDuplicateCount());
        }
    }

    @Test
    void markdownImportProcessesCompletedQuestionBeforeLaterStreamFailure() {
        when(questionMapper.selectCount(any())).thenReturn(0L);
        when(questionMapper.insert(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(1L);
            return 1;
        });

        String markdown = """
                ## Java HashMap 鍘熺悊
                璇疯鏄?HashMap put 鍜?resize 娴佺▼銆?
                ## Java 骞跺彂鍘熺悊
                璇疯鏄?synchronized 鍜?volatile 鐨勫尯鍒€?
                """;
        String deliveredPrefix = """
                ## Java HashMap 鍘熺悊
                璇疯鏄?HashMap put 鍜?resize 娴佺▼銆?
                ## Java 骞跺彂鍘熺悊
                """;

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importQuestions(
                "questions.md",
                failingInput(markdown, deliveredPrefix.getBytes(StandardCharsets.UTF_8).length),
                100L,
                false));

        assertTrue(ex.getMessage().contains("Markdown"));
        verify(questionMapper).insert(any(Question.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void docxParagraphIteratorProcessesCompletedQuestionBeforeLaterParagraphFailure() throws Exception {
        Method parseMethod = QuestionImportServiceImpl.class.getDeclaredMethod(
                "parseDocxParagraphIterator",
                Iterator.class,
                AutoCloseable.class);
        parseMethod.setAccessible(true);

        Method saveMethod = QuestionImportServiceImpl.class.getDeclaredMethod(
                "saveQuestions",
                Iterator.class,
                Long.class,
                boolean.class);
        saveMethod.setAccessible(true);

        when(questionMapper.selectCount(any())).thenReturn(0L);
        when(questionMapper.insert(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(1L);
            return 1;
        });

        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph heading1 = document.createParagraph();
            heading1.setStyle("Heading1");
            heading1.createRun().setText("Java HashMap 鍘熺悊");

            XWPFParagraph body1 = document.createParagraph();
            body1.createRun().setText("璇疯鏄?HashMap put 鍜?resize 娴佺▼銆?");

            XWPFParagraph heading2 = document.createParagraph();
            heading2.setStyle("Heading1");
            heading2.createRun().setText("Java 骞跺彂鍘熺悊");

            XWPFParagraph body2 = document.createParagraph();
            body2.createRun().setText("璇疯鏄?synchronized 鍜?volatile 鐨勫尯鍒€?");

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            Iterator<XWPFParagraph> failingParagraphs = new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < paragraphs.size();
                }

                @Override
                public XWPFParagraph next() {
                    if (index == 3) {
                        throw new RuntimeException("paragraph stream failed after second heading");
                    }
                    return paragraphs.get(index++);
                }
            };

            Iterator<QuestionImportServiceImpl.ParsedQuestion> parsed =
                    (Iterator<QuestionImportServiceImpl.ParsedQuestion>) parseMethod.invoke(
                            service,
                            failingParagraphs,
                            (AutoCloseable) () -> {
                            });

            Exception ex = assertThrows(Exception.class, () -> saveMethod.invoke(service, parsed, 100L, false));

            assertTrue(ex.getCause().getMessage().contains("DOCX"));
            verify(questionMapper).insert(any(Question.class));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pdfTextIteratorProcessesCompletedQuestionBeforeLaterLineFailure() throws Exception {
        Method parseMethod = QuestionImportServiceImpl.class.getDeclaredMethod(
                "parsePdfTextIterator",
                Iterator.class);
        parseMethod.setAccessible(true);

        Method saveMethod = QuestionImportServiceImpl.class.getDeclaredMethod(
                "saveQuestions",
                Iterator.class,
                Long.class,
                boolean.class);
        saveMethod.setAccessible(true);

        when(questionMapper.selectCount(any())).thenReturn(0L);
        when(questionMapper.insert(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(1L);
            return 1;
        });

        Iterator<String> failingLines = new Iterator<>() {
            private int index;
            private final List<String> lines = List.of(
                    "## Java HashMap 鍘熺悊",
                    "璇疯鏄?HashMap put 鍜?resize 娴佺▼銆?",
                    "## Java 骞跺彂鍘熺悊");

            @Override
            public boolean hasNext() {
                return index < 4;
            }

            @Override
            public String next() {
                if (index == 3) {
                    throw new RuntimeException("pdf text stream failed after second heading");
                }
                return lines.get(index++);
            }
        };

        Iterator<QuestionImportServiceImpl.ParsedQuestion> parsed =
                (Iterator<QuestionImportServiceImpl.ParsedQuestion>) parseMethod.invoke(service, failingLines);

        Exception ex = assertThrows(Exception.class, () -> saveMethod.invoke(service, parsed, 100L, false));

        assertTrue(ex.getCause().getMessage().contains("PDF"));
        verify(questionMapper).insert(any(Question.class));
    }

    @Test
    void iteratorSavePathProcessesRowsBeforeFullSourceConsumption() throws Exception {
        Method method = QuestionImportServiceImpl.class.getDeclaredMethod(
                "saveQuestions",
                Iterator.class,
                Long.class,
                boolean.class);
        method.setAccessible(true);

        when(questionMapper.selectCount(any())).thenReturn(0L);
        when(questionMapper.insert(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(1L);
            return 1;
        });

        Iterator<QuestionImportServiceImpl.ParsedQuestion> iterator = new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < 2;
            }

            @Override
            public QuestionImportServiceImpl.ParsedQuestion next() {
                if (index++ == 0) {
                    return parsedQuestion("Java 锁升级", "请说明偏向锁、轻量级锁和重量级锁。");
                }
                throw new RuntimeException("stream failed after first row");
            }
        };

        Exception ex = assertThrows(Exception.class, () -> method.invoke(service, iterator, 100L, false));

        assertTrue(ex.getCause().getMessage().contains("stream failed after first row"));
        verify(questionMapper).insert(any(Question.class));
    }

    @Test
    void excelImportStillParsesRowsAfterStreamingRefactor() throws Exception {
        when(questionMapper.selectCount(any())).thenReturn(0L);

        QuestionImportServiceImpl.QuestionExcelRow row = new QuestionImportServiceImpl.QuestionExcelRow();
        row.setTitle("Java 集合原理");
        row.setReferenceAnswer("请说明 ArrayList 扩容机制。");

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            EasyExcel.write(outputStream, QuestionImportServiceImpl.QuestionExcelRow.class)
                    .sheet("questions")
                    .doWrite(List.of(row));

            ImportResult result = service.importQuestions(
                    "questions.xlsx",
                    new ByteArrayInputStream(outputStream.toByteArray()),
                    100L,
                    true);

            assertEquals(1, result.getTotalCount());
            assertEquals(1, result.getSuccessCount());
            assertEquals(0, result.getFailCount());
            assertEquals(0, result.getDuplicateCount());
        }
    }

    private ByteArrayInputStream input(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream failingInput(String content, int failAfterBytes) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            private int index;

            @Override
            public int read() throws IOException {
                if (index >= bytes.length) {
                    return -1;
                }
                if (index >= failAfterBytes) {
                    throw new IOException("stream failed after checkpoint");
                }
                return bytes[index++] & 0xFF;
            }
        };
    }

    private QuestionImportServiceImpl.ParsedQuestion parsedQuestion(String title, String answer) {
        QuestionImportServiceImpl.ParsedQuestion parsedQuestion = new QuestionImportServiceImpl.ParsedQuestion();
        parsedQuestion.setTitle(title);
        parsedQuestion.setReferenceAnswer(answer);
        return parsedQuestion;
    }
}
