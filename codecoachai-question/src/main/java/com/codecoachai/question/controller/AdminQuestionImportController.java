package com.codecoachai.question.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionImportService;
import com.codecoachai.question.service.QuestionImportService.ImportResult;
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
 * 题目批量导入导出 Controller（管理端）。
 * 导入支持：Excel(.xlsx) / Markdown(.md) / Word(.docx) / PDF(.pdf)
 * 导出支持：Excel(.xlsx) / JSON
 */
@Tag(name = "题目导入导出-后台")
@Slf4j
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionImportController {

    private static final String PERM_QUESTION_LIST = "admin:question:list";
    private static final String PERM_QUESTION_IMPORT = "admin:question:import";
    private static final String PERM_QUESTION_EXPORT = "admin:question:export";

    private final QuestionImportService questionImportService;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @Operation(summary = "批量导入题目（支持 xlsx/md/docx/pdf）")
    @OperationLog(module = "question", action = "IMPORT_QUESTIONS", description = "批量导入题目", logArgs = false, logResponse = false)
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResult> importQuestions(@RequestPart("file") MultipartFile file,
                                                @RequestParam(required = false) Boolean confirm,
                                                @RequestParam(required = false, defaultValue = "false") Boolean dryRun,
                                                @RequestParam(required = false) String reason,
                                                @RequestParam(required = false) String idempotencyKey) {
        adminPermissionGuard.require(PERM_QUESTION_IMPORT);
        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
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
            return Result.fail(500, "导入失败，请检查文件格式和模板后重试");
        }
    }

    @Operation(summary = "批量导出题目（Excel）")
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
            List<Question> questions = queryForExport(categoryId, difficulty, questionType);

            List<QuestionExportRow> rows = questions.stream().map(this::toExportRow).toList();

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename="
                    + URLEncoder.encode("题目导出.xlsx", StandardCharsets.UTF_8));
            EasyExcel.write(response.getOutputStream(), QuestionExportRow.class).sheet("题目").doWrite(rows);
        } catch (Exception ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Operation(summary = "批量导出题目（JSON）")
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
            List<QuestionExportRow> questions = queryForExport(categoryId, difficulty, questionType).stream()
                    .map(this::toExportRow)
                    .toList();

            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename="
                    + URLEncoder.encode("题目导出.json", StandardCharsets.UTF_8));
            objectMapper.writeValue(response.getOutputStream(), questions);
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

    private List<Question> queryForExport(Long categoryId, String difficulty, String questionType) {
        return questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(categoryId != null, Question::getCategoryId, categoryId)
                        .eq(StringUtils.hasText(difficulty), Question::getDifficulty, difficulty)
                        .eq(StringUtils.hasText(questionType), Question::getQuestionType, questionType)
                        .eq(Question::getStatus, 1)
                        .orderByAsc(Question::getId)
                        .last("limit 5000"));
    }

    @Data
    public static class QuestionExportRow {
        @ExcelProperty("ID")
        private Long id;
        @ExcelProperty("标题")
        private String title;
        @ExcelProperty("内容")
        private String content;
        @ExcelProperty("参考答案")
        private String referenceAnswer;
        @ExcelProperty("解析")
        private String analysis;
        @ExcelProperty("难度")
        private String difficulty;
        @ExcelProperty("题型")
        private String questionType;
        @ExcelProperty("经验年限")
        private String experienceLevel;
    }
}
