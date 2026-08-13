package com.codecoachai.question.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.question.config.QuestionImportProperties;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionImportService;
import com.codecoachai.question.service.QuestionImportService.ImportResult;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin question import/export controller.
 * Import supports Excel(.xlsx), Markdown(.md), Word(.docx), and PDF(.pdf).
 * Export supports Excel(.xlsx) and JSON.
 */
@Tag(name = "Question Import/Export Admin")
@Slf4j
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionImportController {

    private static final String PERM_QUESTION_LIST = "admin:question:list";
    private static final String PERM_QUESTION_IMPORT = "admin:question:import";
    private static final String PERM_QUESTION_EXPORT = "admin:question:export";
    private static final int EXPORT_BATCH_SIZE = 500;

    private final QuestionImportService questionImportService;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;
    private final QuestionImportProperties questionImportProperties;

    @Operation(summary = "Import questions in batch")
    @OperationLog(module = "question", action = "IMPORT_QUESTIONS", description = "Import questions in batch", logArgs = false, logResponse = false)
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResult> importQuestions(@RequestPart("file") MultipartFile file,
                                                @RequestParam(required = false) Boolean confirm,
                                                @RequestParam(required = false, defaultValue = "false") Boolean dryRun,
                                                @RequestParam(required = false) String reason,
                                                @RequestParam(required = false) String idempotencyKey) {
        adminPermissionGuard.require(PERM_QUESTION_IMPORT);
        if (file.isEmpty()) {
            return Result.fail(400, "File cannot be empty");
        }
        if (file.getSize() > questionImportProperties.safeMaxFileBytes()) {
            return Result.fail(400, "Question import file cannot exceed "
                    + questionImportProperties.maxFileSizeLabel() + ".");
        }
        String lockKey = requireConfirmedOperation("question-import:" + file.getOriginalFilename(),
                confirm, dryRun, reason, idempotencyKey);
        try {
            ImportResult result = questionImportService.importQuestions(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    SecurityAssert.requireLoginUserId(),
                    Boolean.TRUE.equals(dryRun));
            return Result.success(result);
        } catch (Exception e) {
            operationConfirmationGuard.release(lockKey);
            log.warn("Question import failed, filename={}", file.getOriginalFilename(), e);
            return Result.fail(500, "Question import failed. Please check file format and template.");
        }
    }

    @Operation(summary = "Export questions in batch as Excel")
    @OperationLog(module = "question", action = "EXPORT_QUESTIONS_EXCEL", description = "Export question bank excel", logArgs = false, logResponse = false)
    @GetMapping({"/export/excel", "/export"})
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) Long categoryId,
                            @RequestParam(required = false) String difficulty,
                            @RequestParam(required = false) String questionType,
                            @RequestParam(required = false, defaultValue = "excel") String format,
                            @RequestParam(required = false) Boolean confirm,
                            @RequestParam(required = false, defaultValue = "false") Boolean dryRun,
                            @RequestParam(required = false) String reason,
                            @RequestParam(required = false) String idempotencyKey) throws Exception {
        if ("json".equalsIgnoreCase(format)) {
            exportJson(response, categoryId, difficulty, questionType, confirm, dryRun, reason, idempotencyKey);
            return;
        }
        adminPermissionGuard.require(PERM_QUESTION_EXPORT);
        String lockKey = requireConfirmedExport("excel", categoryId, difficulty, questionType, confirm, dryRun, reason, idempotencyKey);
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename="
                    + URLEncoder.encode("question-export.xlsx", StandardCharsets.UTF_8));
            try (ExcelWriter writer = EasyExcel.write(response.getOutputStream(), QuestionExportRow.class).build()) {
                WriteSheet sheet = EasyExcel.writerSheet("questions").build();
                exportQuestionBatches(categoryId, difficulty, questionType, batch ->
                        writer.write(batch.stream().map(this::toExportRow).toList(), sheet));
            }
        } catch (Exception ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Operation(summary = "Export questions in batch as JSON")
    @OperationLog(module = "question", action = "EXPORT_QUESTIONS_JSON", description = "Export question bank json", logArgs = false, logResponse = false)
    @GetMapping({"/export/json", "/download/json"})
    public void exportJson(HttpServletResponse response,
                           @RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) String difficulty,
                           @RequestParam(required = false) String questionType,
                           @RequestParam(required = false) Boolean confirm,
                           @RequestParam(required = false, defaultValue = "false") Boolean dryRun,
                           @RequestParam(required = false) String reason,
                           @RequestParam(required = false) String idempotencyKey) throws Exception {
        adminPermissionGuard.require(PERM_QUESTION_EXPORT);
        String lockKey = requireConfirmedExport("json", categoryId, difficulty, questionType, confirm, dryRun, reason, idempotencyKey);
        try {
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename="
                    + URLEncoder.encode("question-export.json", StandardCharsets.UTF_8));
            try (JsonGenerator generator = objectMapper.getFactory().createGenerator(response.getOutputStream())) {
                generator.writeStartArray();
                exportQuestionBatches(categoryId, difficulty, questionType, batch -> {
                    for (Question question : batch) {
                        objectMapper.writeValue(generator, toExportRow(question));
                    }
                });
                generator.writeEndArray();
            }
        } catch (Exception ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    private String requireConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                             String reason, String idempotencyKey) {
        return operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
    }

    private String requireConfirmedExport(String format, Long categoryId, String difficulty, String questionType,
                                          Boolean confirm, Boolean dryRun, String reason, String idempotencyKey) {
        return operationConfirmationGuard.requireConfirmed(
                "question-export:" + format + ":" + exportScopeKey(categoryId, difficulty, questionType),
                confirm,
                dryRun,
                reason,
                idempotencyKey);
    }

    private String exportScopeKey(Long categoryId, String difficulty, String questionType) {
        return (categoryId == null ? "all" : categoryId)
                + ":" + (StringUtils.hasText(difficulty) ? difficulty.trim() : "all")
                + ":" + (StringUtils.hasText(questionType) ? questionType.trim() : "all");
    }

    @Operation(summary = "Download question import template")
    @GetMapping({"/template", "/template/excel", "/import/template", "/download/template"})
    public void downloadTemplate(HttpServletResponse response) throws Exception {
        adminPermissionGuard.require(PERM_QUESTION_LIST);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode("question-import-template.xlsx", StandardCharsets.UTF_8));
        EasyExcel.write(response.getOutputStream(), QuestionExportRow.class)
                .sheet("questions")
                .doWrite(List.of(templateRow()));
    }

    private QuestionExportRow templateRow() {
        QuestionExportRow row = new QuestionExportRow();
        row.setTitle("Example: Java HashMap principle");
        row.setContent("Please explain HashMap put/get internals.");
        row.setReferenceAnswer("Mention hash, bucket, collision, resize, treeify.");
        row.setAnalysis("Focus on JDK 8 array + linked list + red-black tree.");
        row.setDifficulty("MEDIUM");
        row.setQuestionType("SHORT_ANSWER");
        row.setExperienceLevel("MID");
        return row;
    }

    private QuestionExportRow toExportRow(Question q) {
        QuestionExportRow row = new QuestionExportRow();
        row.setId(q.getId());
        row.setTitle(q.getTitle());
        row.setContent(q.getContent());
        row.setReferenceAnswer(q.getReferenceAnswer());
        row.setAnalysis(q.getAnalysis());
        row.setDifficulty(q.getDifficulty());
        row.setQuestionType(q.getQuestionType());
        row.setExperienceLevel(q.getExperienceLevel());
        return row;
    }

    private void exportQuestionBatches(Long categoryId, String difficulty, String questionType,
                                       QuestionBatchConsumer consumer) throws Exception {
        Long lastId = null;
        while (true) {
            List<Question> batch = queryExportBatch(categoryId, difficulty, questionType, lastId, EXPORT_BATCH_SIZE);
            if (batch.isEmpty()) {
                return;
            }
            consumer.accept(batch);
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < EXPORT_BATCH_SIZE) {
                return;
            }
        }
    }

    private List<Question> queryExportBatch(Long categoryId, String difficulty, String questionType,
                                            Long lastId, int batchSize) {
        return questionMapper.selectPage(
                        Page.of(1, batchSize, false),
                        new LambdaQueryWrapper<Question>()
                                .eq(categoryId != null, Question::getCategoryId, categoryId)
                                .eq(StringUtils.hasText(difficulty), Question::getDifficulty, difficulty)
                                .eq(StringUtils.hasText(questionType), Question::getQuestionType, questionType)
                                .eq(Question::getStatus, 1)
                                .gt(lastId != null, Question::getId, lastId)
                                .orderByAsc(Question::getId))
                .getRecords();
    }

    @FunctionalInterface
    private interface QuestionBatchConsumer {
        void accept(List<Question> questions) throws Exception;
    }

    @Data
    public static class QuestionExportRow {
        @ExcelProperty("ID")
        private Long id;
        @ExcelProperty("Title")
        private String title;
        @ExcelProperty("Content")
        private String content;
        @ExcelProperty("Reference Answer")
        private String referenceAnswer;
        @ExcelProperty("Analysis")
        private String analysis;
        @ExcelProperty("Difficulty")
        private String difficulty;
        @ExcelProperty("Question Type")
        private String questionType;
        @ExcelProperty("Experience Level")
        private String experienceLevel;
    }
}
