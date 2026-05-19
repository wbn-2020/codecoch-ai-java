package com.codecoachai.question.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
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
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionImportController {

    private final QuestionImportService questionImportService;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    @Operation(summary = "批量导入题目（支持 xlsx/md/docx/pdf）")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResult> importQuestions(@RequestPart("file") MultipartFile file) {
        SecurityAssert.requireAdmin();
        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        try {
            ImportResult result = questionImportService.importQuestions(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    SecurityAssert.requireLoginUserId());
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(500, "导入失败: " + e.getMessage());
        }
    }

    @Operation(summary = "批量导出题目（Excel）")
    @GetMapping("/export/excel")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) Long categoryId,
                            @RequestParam(required = false) String difficulty,
                            @RequestParam(required = false) String questionType) throws Exception {
        SecurityAssert.requireAdmin();
        List<Question> questions = queryForExport(categoryId, difficulty, questionType);

        List<QuestionExportRow> rows = questions.stream().map(q -> {
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
        }).toList();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode("题目导出.xlsx", StandardCharsets.UTF_8));
        EasyExcel.write(response.getOutputStream(), QuestionExportRow.class).sheet("题目").doWrite(rows);
    }

    @Operation(summary = "批量导出题目（JSON）")
    @GetMapping("/export/json")
    public void exportJson(HttpServletResponse response,
                           @RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) String difficulty,
                           @RequestParam(required = false) String questionType) throws Exception {
        SecurityAssert.requireAdmin();
        List<Question> questions = queryForExport(categoryId, difficulty, questionType);

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode("题目导出.json", StandardCharsets.UTF_8));
        objectMapper.writeValue(response.getOutputStream(), questions);
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
