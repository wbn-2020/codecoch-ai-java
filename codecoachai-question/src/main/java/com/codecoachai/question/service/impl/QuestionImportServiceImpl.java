package com.codecoachai.question.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.codecoachai.question.service.QuestionImportService;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 题目批量导入服务实现。
 *
 * 支持格式：
 * - Excel(.xlsx)：每行一题，列：标题/内容/参考答案/解析/难度/题型/经验年限/分类/标签
 * - Markdown(.md)：以 ## 或 ### 为题目标题分隔，下方内容为答案
 * - Word(.docx)：同 Markdown 逻辑，按标题样式分隔
 * - PDF(.pdf)：提取文本后按 Markdown 逻辑解析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportServiceImpl implements QuestionImportService {

    private final QuestionMapper questionMapper;
    private final QuestionEmbeddingIndexService questionEmbeddingIndexService;
    private final QuestionDuplicateService questionDuplicateService;

    private static final Pattern MD_TITLE_PATTERN = Pattern.compile("^#{1,3}\\s+(.+)$");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importQuestions(String fileName, InputStream inputStream, Long importedBy) {
        String ext = getExtension(fileName);
        List<ParsedQuestion> parsed;

        switch (ext) {
            case "xlsx", "xls" -> parsed = parseExcel(inputStream);
            case "md", "txt" -> parsed = parseMarkdown(inputStream);
            case "docx" -> parsed = parseDocx(inputStream);
            case "pdf" -> parsed = parsePdf(inputStream);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的文件格式: " + ext);
        }

        return saveQuestions(parsed, importedBy);
    }

    // ==================== Excel 解析 ====================

    private List<ParsedQuestion> parseExcel(InputStream inputStream) {
        List<ParsedQuestion> results = new ArrayList<>();
        EasyExcel.read(inputStream, QuestionExcelRow.class, new ReadListener<QuestionExcelRow>() {
            @Override
            public void invoke(QuestionExcelRow row, AnalysisContext context) {
                if (!StringUtils.hasText(row.getTitle())) return;
                ParsedQuestion pq = new ParsedQuestion();
                pq.setTitle(row.getTitle());
                pq.setContent(row.getContent());
                pq.setReferenceAnswer(row.getReferenceAnswer());
                pq.setAnalysis(row.getAnalysis());
                pq.setDifficulty(row.getDifficulty());
                pq.setQuestionType(row.getQuestionType());
                pq.setExperienceLevel(row.getExperienceLevel());
                pq.setCategoryName(row.getCategoryName());
                pq.setTags(row.getTags());
                results.add(pq);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        return results;
    }

    // ==================== Markdown 解析 ====================

    private List<ParsedQuestion> parseMarkdown(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parseMarkdownLines(reader.lines().toList());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Markdown 解析失败: " + e.getMessage());
        }
    }

    private List<ParsedQuestion> parseMarkdownLines(List<String> lines) {
        List<ParsedQuestion> results = new ArrayList<>();
        ParsedQuestion current = null;
        StringBuilder contentBuilder = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = MD_TITLE_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                // 保存上一题
                if (current != null) {
                    finishMarkdownQuestion(current, contentBuilder.toString());
                    results.add(current);
                }
                current = new ParsedQuestion();
                current.setTitle(matcher.group(1).trim());
                contentBuilder = new StringBuilder();
            } else if (current != null) {
                contentBuilder.append(line).append("\n");
            }
        }
        // 最后一题
        if (current != null) {
            finishMarkdownQuestion(current, contentBuilder.toString());
            results.add(current);
        }
        return results;
    }

    private void finishMarkdownQuestion(ParsedQuestion pq, String body) {
        // 尝试从 body 中提取 **答案** / **解析** 段落
        String answer = extractSection(body, "答案", "参考答案", "answer");
        String analysis = extractSection(body, "解析", "分析", "analysis");

        if (StringUtils.hasText(answer)) {
            pq.setReferenceAnswer(answer.trim());
            // content 为去掉答案和解析后的部分
            String content = body.replace(answer, "").replace(analysis != null ? analysis : "", "").trim();
            if (StringUtils.hasText(content)) {
                pq.setContent(content);
            }
        } else {
            // 整个 body 作为参考答案
            pq.setReferenceAnswer(body.trim());
        }
        if (StringUtils.hasText(analysis)) {
            pq.setAnalysis(analysis.trim());
        }
    }

    private String extractSection(String body, String... labels) {
        for (String label : labels) {
            // 匹配 **答案**：xxx 或 答案：xxx 或 > 答案：xxx
            Pattern p = Pattern.compile("(?:>?\\s*\\*{0,2}" + label + "\\*{0,2}[：:]\\s*)(.+?)(?=\\n\\*{0,2}[\\u4e00-\\u9fa5]+\\*{0,2}[：:]|$)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(body);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    // ==================== DOCX 解析 ====================

    private List<ParsedQuestion> parseDocx(InputStream inputStream) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            List<String> lines = new ArrayList<>();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                String style = para.getStyleID();
                // 将标题样式转为 Markdown 格式
                if (style != null && (style.startsWith("Heading") || style.startsWith("heading"))) {
                    lines.add("## " + text);
                } else if (text != null && !text.isBlank()) {
                    lines.add(text);
                }
            }
            return parseMarkdownLines(lines);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "DOCX 解析失败: " + e.getMessage());
        }
    }

    // ==================== PDF 解析 ====================

    private List<ParsedQuestion> parsePdf(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                List<String> lines = List.of(text.split("\\r?\\n"));
                return parseMarkdownLines(lines);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 解析失败: " + e.getMessage());
        }
    }

    // ==================== 保存逻辑 ====================

    private ImportResult saveQuestions(List<ParsedQuestion> parsed, Long importedBy) {
        ImportResult result = new ImportResult();
        result.setTotalCount(parsed.size());
        result.setErrors(new ArrayList<>());

        int success = 0;
        int fail = 0;
        int duplicate = 0;
        Map<String, Integer> duplicateReasonCounts = new LinkedHashMap<>();
        Set<String> seenNormalizedTitleHashes = new LinkedHashSet<>();
        Set<String> seenContentHashes = new LinkedHashSet<>();
        List<Long> importedQuestionIds = new ArrayList<>();

        for (int i = 0; i < parsed.size(); i++) {
            ParsedQuestion pq = parsed.get(i);
            try {
                if (!StringUtils.hasText(pq.getTitle())) {
                    ImportError err = new ImportError();
                    err.setRowIndex(i + 1);
                    err.setTitle("");
                    err.setReason("标题为空");
                    result.getErrors().add(err);
                    fail++;
                    continue;
                }

                String normalized = QuestionTextNormalizeUtils.normalizeTitle(pq.getTitle());
                String normalizedTitleHash = QuestionTextNormalizeUtils.sha256Hex(normalized);
                String normalizedContent = QuestionTextNormalizeUtils.normalizeContent(
                        pq.getTitle(), pq.getContent(), pq.getReferenceAnswer(), pq.getAnalysis());
                String contentHash = QuestionTextNormalizeUtils.sha256Hex(normalizedContent);

                if (StringUtils.hasText(normalizedTitleHash) && seenNormalizedTitleHashes.contains(normalizedTitleHash)) {
                    duplicate++;
                    incrementDuplicateReason(duplicateReasonCounts, "FILE_TITLE_DUPLICATE");
                    ImportError err = new ImportError();
                    err.setRowIndex(i + 1);
                    err.setTitle(pq.getTitle());
                    err.setReason("FILE_TITLE_DUPLICATE");
                    result.getErrors().add(err);
                    continue;
                }
                if (StringUtils.hasText(contentHash) && seenContentHashes.contains(contentHash)) {
                    duplicate++;
                    incrementDuplicateReason(duplicateReasonCounts, "FILE_CONTENT_DUPLICATE");
                    ImportError err = new ImportError();
                    err.setRowIndex(i + 1);
                    err.setTitle(pq.getTitle());
                    err.setReason("FILE_CONTENT_DUPLICATE");
                    result.getErrors().add(err);
                    continue;
                }

                Long titleExistsCount = questionMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                                .eq(StringUtils.hasText(normalizedTitleHash), Question::getNormalizedTitleHash, normalizedTitleHash));
                if (titleExistsCount > 0) {
                    duplicate++;
                    incrementDuplicateReason(duplicateReasonCounts, "BANK_TITLE_DUPLICATE");
                    ImportError err = new ImportError();
                    err.setRowIndex(i + 1);
                    err.setTitle(pq.getTitle());
                    err.setReason("BANK_TITLE_DUPLICATE");
                    result.getErrors().add(err);
                    continue;
                }
                Long contentExistsCount = questionMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                                .eq(StringUtils.hasText(contentHash), Question::getContentHash, contentHash));
                if (contentExistsCount > 0) {
                    duplicate++;
                    incrementDuplicateReason(duplicateReasonCounts, "BANK_CONTENT_DUPLICATE");
                    ImportError err = new ImportError();
                    err.setRowIndex(i + 1);
                    err.setTitle(pq.getTitle());
                    err.setReason("BANK_CONTENT_DUPLICATE");
                    result.getErrors().add(err);
                    continue;
                }

                Question question = new Question();
                question.setTitle(pq.getTitle());
                question.setNormalizedTitle(normalized);
                question.setNormalizedTitleHash(normalizedTitleHash);
                question.setContentHash(contentHash);
                question.setContent(pq.getContent());
                question.setReferenceAnswer(pq.getReferenceAnswer());
                question.setAnalysis(pq.getAnalysis());
                question.setDifficulty(StringUtils.hasText(pq.getDifficulty()) ? pq.getDifficulty() : "MEDIUM");
                question.setQuestionType(StringUtils.hasText(pq.getQuestionType()) ? pq.getQuestionType() : "SHORT_ANSWER");
                question.setExperienceLevel(pq.getExperienceLevel());
                question.setIsHighFrequency(0);
                question.setIsRecommended(0);
                question.setStatus(1);
                question.setAuditStatus("APPROVED");
                question.setSourceType("IMPORT");
                questionMapper.insert(question);
                importedQuestionIds.add(question.getId());
                if (StringUtils.hasText(normalizedTitleHash)) {
                    seenNormalizedTitleHashes.add(normalizedTitleHash);
                }
                if (StringUtils.hasText(contentHash)) {
                    seenContentHashes.add(contentHash);
                }
                success++;
            } catch (Exception ex) {
                fail++;
                ImportError err = new ImportError();
                err.setRowIndex(i + 1);
                err.setTitle(pq.getTitle());
                err.setReason(ex.getMessage());
                result.getErrors().add(err);
            }
        }

        result.setSuccessCount(success);
        result.setFailCount(fail);
        result.setDuplicateCount(duplicate);
        result.setDuplicateReasonCounts(duplicateReasonCounts);
        syncQuestionEmbeddingAndDuplicateCheckAfterCommit(importedQuestionIds, importedBy);
        log.info("题目导入完成 total={} success={} fail={} duplicate={}", parsed.size(), success, fail, duplicate);
        return result;
    }

    private void syncQuestionEmbeddingAndDuplicateCheckAfterCommit(List<Long> questionIds, Long importedBy) {
        if (questionIds == null || questionIds.isEmpty()) {
            return;
        }
        List<Long> ids = questionIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Runnable action = () -> {
            questionEmbeddingIndexService.indexQuestions(ids);
            for (Long questionId : ids) {
                try {
                    questionDuplicateService.checkDuplicateForQuestion(questionId, importedBy);
                } catch (Exception ex) {
                    log.warn("Question duplicate check failed after import questionId={}", questionId, ex);
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

    private void incrementDuplicateReason(Map<String, Integer> counts, String reasonCode) {
        counts.put(reasonCode, counts.getOrDefault(reasonCode, 0) + 1);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    // ==================== Excel 行模型 ====================

    @lombok.Data
    @com.alibaba.excel.annotation.ExcelIgnoreUnannotated
    public static class QuestionExcelRow {
        @com.alibaba.excel.annotation.ExcelProperty(value = "标题", index = 0)
        private String title;
        @com.alibaba.excel.annotation.ExcelProperty(value = "内容", index = 1)
        private String content;
        @com.alibaba.excel.annotation.ExcelProperty(value = "参考答案", index = 2)
        private String referenceAnswer;
        @com.alibaba.excel.annotation.ExcelProperty(value = "解析", index = 3)
        private String analysis;
        @com.alibaba.excel.annotation.ExcelProperty(value = "难度", index = 4)
        private String difficulty;
        @com.alibaba.excel.annotation.ExcelProperty(value = "题型", index = 5)
        private String questionType;
        @com.alibaba.excel.annotation.ExcelProperty(value = "经验年限", index = 6)
        private String experienceLevel;
        @com.alibaba.excel.annotation.ExcelProperty(value = "分类", index = 7)
        private String categoryName;
        @com.alibaba.excel.annotation.ExcelProperty(value = "标签", index = 8)
        private String tags;
    }
}
