package com.codecoachai.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试报告导出 + 知识点雷达图数据。
 */
@Slf4j
@Tag(name = "面试报告导出")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewReportExportController {

    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    @Operation(summary = "导出面试报告（Markdown 格式，可转 PDF/Word）")
    @GetMapping("/{id}/report/export")
    public void exportReport(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Long userId = SecurityAssert.requireLoginUserId();
        InterviewReport report = getReportBySession(id, userId);

        String markdown = buildReportMarkdown(report, id);

        response.setContentType("text/markdown;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode("面试报告_" + id + ".md", StandardCharsets.UTF_8));
        PrintWriter writer = response.getWriter();
        writer.write(markdown);
        writer.flush();
    }

    @Operation(summary = "导出面试报告（JSON 格式）")
    @GetMapping("/{id}/report/export/json")
    public void exportReportJson(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Long userId = SecurityAssert.requireLoginUserId();
        InterviewReport report = getReportBySession(id, userId);

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode("面试报告_" + id + ".json", StandardCharsets.UTF_8));
        objectMapper.writeValue(response.getOutputStream(), report);
    }

    @Operation(summary = "知识点雷达图数据")
    @GetMapping("/{id}/report/radar")
    public Result<RadarChartVO> radarChart(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        InterviewReport report = getReportBySession(id, userId);

        RadarChartVO vo = new RadarChartVO();
        vo.setSessionId(id);
        vo.setTotalScore(report.getTotalScore());

        // 解析 stageScores JSON
        List<RadarItem> items = new ArrayList<>();
        if (StringUtils.hasText(report.getStageScores())) {
            try {
                List<Map<String, Object>> stages = objectMapper.readValue(
                        report.getStageScores(), new TypeReference<>() {});
                for (Map<String, Object> stage : stages) {
                    RadarItem item = new RadarItem();
                    item.setLabel(String.valueOf(stage.getOrDefault("stageName",
                            stage.getOrDefault("name", "未知"))));
                    Object scoreObj = stage.getOrDefault("score", stage.getOrDefault("stageScore", 0));
                    item.setScore(scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 0);
                    item.setMaxScore(100);
                    items.add(item);
                }
            } catch (Exception e) {
                log.warn("解析 stageScores 失败 sessionId={}", id, e);
            }
        }

        // 如果没有 stageScores，尝试从 reportContent 中提取
        if (items.isEmpty() && StringUtils.hasText(report.getReportContent())) {
            try {
                Map<String, Object> content = objectMapper.readValue(
                        report.getReportContent(), new TypeReference<>() {});
                Object stageScoresObj = content.get("stageScores");
                if (stageScoresObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            RadarItem ri = new RadarItem();
                            Object label = map.get("stageName");
                            if (label == null) {
                                label = map.get("name");
                            }
                            ri.setLabel(label == null ? "未知" : String.valueOf(label));
                            Object s = map.get("score");
                            ri.setScore(s instanceof Number ? ((Number) s).intValue() : 0);
                            ri.setMaxScore(100);
                            items.add(ri);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 reportContent 失败 sessionId={}", id, e);
            }
        }

        vo.setItems(items);
        return Result.success(vo);
    }

    private InterviewReport getReportBySession(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试不存在");
        }
        InterviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, sessionId)
                        .last("limit 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告尚未生成");
        }
        return report;
    }

    private String buildReportMarkdown(InterviewReport report, Long sessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 模拟面试报告\n\n");
        sb.append("- 面试ID: ").append(sessionId).append("\n");
        sb.append("- 总分: ").append(report.getTotalScore()).append("\n");
        sb.append("- 生成时间: ").append(report.getGeneratedAt()).append("\n\n");

        if (StringUtils.hasText(report.getSummary())) {
            sb.append("## 总体评价\n\n").append(report.getSummary()).append("\n\n");
        }
        if (StringUtils.hasText(report.getStrengths())) {
            sb.append("## 亮点\n\n").append(report.getStrengths()).append("\n\n");
        }
        if (StringUtils.hasText(report.getWeaknesses())) {
            sb.append("## 不足\n\n").append(report.getWeaknesses()).append("\n\n");
        }
        if (StringUtils.hasText(report.getMainProblems())) {
            sb.append("## 主要问题\n\n").append(report.getMainProblems()).append("\n\n");
        }
        if (StringUtils.hasText(report.getProjectProblems())) {
            sb.append("## 项目表达问题\n\n").append(report.getProjectProblems()).append("\n\n");
        }
        if (StringUtils.hasText(report.getReviewSuggestions())) {
            sb.append("## 复习建议\n\n").append(report.getReviewSuggestions()).append("\n\n");
        }
        if (StringUtils.hasText(report.getSuggestions())) {
            sb.append("## 综合建议\n\n").append(report.getSuggestions()).append("\n\n");
        }
        if (StringUtils.hasText(report.getQaReview())) {
            sb.append("## 问答明细\n\n").append(report.getQaReview()).append("\n\n");
        }
        return sb.toString();
    }

    @Data
    public static class RadarChartVO {
        private Long sessionId;
        private Integer totalScore;
        private List<RadarItem> items;
    }

    @Data
    public static class RadarItem {
        private String label;
        private int score;
        private int maxScore;
    }
}
