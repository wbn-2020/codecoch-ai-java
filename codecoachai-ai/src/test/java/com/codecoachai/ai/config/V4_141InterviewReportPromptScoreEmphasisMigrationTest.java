package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 锁定 v4-141 面试报告 Prompt 的解析侧对齐：
 * 解析层已能容忍 focusSkills 字符串数组与缺失 totalScore，
 * Prompt 侧必须同步强化输出要求，避免长期依赖兜底。
 */
class V4_141InterviewReportPromptScoreEmphasisMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_141__interview_report_prompt_score_emphasis.sql";
    private static final String VERSION_CODE = "v4-141-interview-score-emphasis";
    private static final String PREVIOUS_VERSION_CODE = "v4-102-interview-score-contract";

    @Test
    void migrationStrengthensInterviewReportPromptAndSwitchesActivePointer() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("'INTERVIEW_REPORT_GENERATE'"));
        assertTrue(VERSION_CODE.length() <= 32);
        assertTrue(sql.contains("'" + VERSION_CODE + "'"));
        // 基于既有 ACTIVE 版本派生，避免丢失 v4-102 已建立的评分合同
        assertTrue(sql.contains("'4. 仅在没有任何有效回答或没有任何可用评分证据时，totalScore 才能为 null"));
        assertTrue(sql.contains("顶层 totalScore 就必须输出"));
        // 规则 11：focusSkills 形态容忍（与 FocusSkill.Deserializer 对称）
        assertTrue(sql.contains("focusSkills 若无法逐项给出 code 与 name"));
        // 规则 12：rubricScores 非空（与 applyTotalScoreFallbackFromRubric 对称）
        assertTrue(sql.contains("rubricScores 至少输出一个维度"));
        assertTrue(sql.contains("不得输出空数组"));
        // 激活切换与旧版本停用
        assertTrue(sql.contains("version.status = 'ACTIVE'"));
        assertTrue(sql.contains("version.is_active = 1"));
        assertTrue(sql.contains("p.active_version_id = v.id"));
        assertTrue(sql.contains("version.version_code <> '" + VERSION_CODE + "'"));
    }

    @Test
    void derivedVersionKeepsPreviousContractVersionAsSource() throws IOException {
        String sql = migrationSql();

        // 新版本内容来自当前 ACTIVE（v4-102 合同），而不是重复一段脆弱的手写模板
        assertTrue(sql.contains("v.is_active = 1"));
        assertTrue(sql.contains("COALESCE(v.variables_json, p.variables, '')"));
        assertTrue(PREVIOUS_VERSION_CODE.length() <= 32);
    }

    private String migrationSql() throws IOException {
        return Files.readString(repositoryRoot().resolve(MIGRATION));
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve(MIGRATION))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate repository root");
        }
        return current;
    }
}
