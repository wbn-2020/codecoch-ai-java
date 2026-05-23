package com.codecoachai.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.system.convert.SystemConfigConvert;
import com.codecoachai.system.domain.dto.SystemConfigSaveDTO;
import com.codecoachai.system.domain.dto.SystemConfigStatusDTO;
import com.codecoachai.system.domain.entity.SystemConfig;
import com.codecoachai.system.domain.vo.AdminDashboardOverviewVO;
import com.codecoachai.system.domain.vo.AdminSystemOverviewVO;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import com.codecoachai.system.mapper.SystemConfigMapper;
import com.codecoachai.system.service.SystemConfigService;
import java.sql.Date;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final HttpClient healthHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(600))
            .build();

    @Override
    public List<SystemConfigVO> listConfigs() {
        return systemConfigMapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                        .orderByDesc(SystemConfig::getUpdatedAt))
                .stream()
                .map(SystemConfigConvert::toVO)
                .toList();
    }

    @Override
    public SystemConfigVO createConfig(SystemConfigSaveDTO dto) {
        assertConfigKeyUnique(dto.getConfigKey(), null);
        SystemConfig config = new SystemConfig();
        apply(config, dto, dto.getConfigKey());
        systemConfigMapper.insert(config);
        return SystemConfigConvert.toVO(config);
    }

    @Override
    public SystemConfigVO getConfig(String keyOrId) {
        return SystemConfigConvert.toVO(getConfigEntity(keyOrId));
    }

    @Override
    public SystemConfigVO updateConfig(String keyOrId, SystemConfigSaveDTO dto) {
        SystemConfig config = getConfigEntity(keyOrId);
        String configKey = org.springframework.util.StringUtils.hasText(dto.getConfigKey())
                ? dto.getConfigKey().trim()
                : config.getConfigKey();
        assertConfigKeyUnique(configKey, config.getId());
        apply(config, dto, configKey);
        systemConfigMapper.updateById(config);
        return SystemConfigConvert.toVO(config);
    }

    @Override
    public SystemConfigVO updateConfigStatus(String keyOrId, SystemConfigStatusDTO dto) {
        SystemConfig config = getConfigEntity(keyOrId);
        if (!CommonConstants.YES.equals(dto.getStatus()) && !CommonConstants.NO.equals(dto.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status must be 0 or 1");
        }
        config.setStatus(dto.getStatus());
        systemConfigMapper.updateById(config);
        return SystemConfigConvert.toVO(config);
    }

    @Override
    public void deleteConfig(String keyOrId) {
        systemConfigMapper.deleteById(getConfigEntity(keyOrId).getId());
    }

    @Override
    public AdminSystemOverviewVO overview() {
        AdminSystemOverviewVO vo = new AdminSystemOverviewVO();
        vo.setUserCount(count("sys_user", "deleted = 0"));
        vo.setQuestionCount(count("question", "deleted = 0"));
        vo.setResumeCount(count("resume", "deleted = 0"));
        vo.setInterviewCount(count("interview_session", "deleted = 0"));
        vo.setAiCallCount(count("ai_call_log", "deleted = 0"));
        return vo;
    }

    @Override
    public AdminDashboardOverviewVO dashboardOverview() {
        AdminDashboardOverviewVO vo = new AdminDashboardOverviewVO();
        LocalDateTime now = LocalDateTime.now();
        // 管理首页只展示运行库实时聚合结果，不使用 mock 或兜底假数据，避免上线验收误判。
        vo.setSummaryCards(summaryCards());
        vo.setTrendStats(trendStats());
        vo.setPendingItems(pendingItems());
        vo.setSystemStatus(systemStatus(now));
        vo.setDataSourceDesc("管理首页数据来自运行库实时聚合。");
        vo.setGeneratedAt(now);
        return vo;
    }

    private void apply(SystemConfig config, SystemConfigSaveDTO dto, String configKey) {
        config.setConfigKey(configKey);
        if (dto.getConfigValue() != null) {
            config.setConfigValue(dto.getConfigValue());
        }
        if (dto.getValueType() != null) {
            config.setValueType(dto.getValueType());
        } else if (config.getValueType() == null) {
            config.setValueType("STRING");
        }
        if (dto.getDescription() != null) {
            config.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            config.setStatus(dto.getStatus());
        } else if (config.getStatus() == null) {
            config.setStatus(CommonConstants.YES);
        }
    }

    private SystemConfig getConfigEntity(String keyOrId) {
        SystemConfig config = isNumeric(keyOrId)
                ? systemConfigMapper.selectById(Long.valueOf(keyOrId))
                : systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, keyOrId)
                        .last("limit 1"));
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "System config not found");
        }
        return config;
    }

    private void assertConfigKeyUnique(String configKey, Long excludeId) {
        SystemConfig existing = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey)
                .last("limit 1"));
        if (existing != null && (excludeId == null || !excludeId.equals(existing.getId()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "System config key already exists");
        }
    }

    private boolean isNumeric(String value) {
        return value != null && value.matches("\\d+");
    }

    private List<AdminDashboardOverviewVO.SummaryCardVO> summaryCards() {
        return List.of(
                summaryCard("users", "User count", count("sys_user", "deleted = 0"), "sys_user"),
                summaryCard("resumes", "Resume count", count("resume", "deleted = 0"), "resume"),
                summaryCard("interviews", "Interview count", count("interview_session", "deleted = 0"), "interview_session"),
                summaryCard("studyPlans", "Study plan count", count("study_plan", "deleted = 0"), "study_plan"),
                summaryCard("aiCalls", "AI call count", count("ai_call_log", "deleted = 0"), "ai_call_log"),
                summaryCard("todayAiCalls", "Today AI call count",
                        count("ai_call_log", "deleted = 0 AND DATE(created_at) = CURDATE()"), "ai_call_log"),
                summaryCard("pendingQuestionReviews", "Pending generated question reviews",
                        count("question_review", "deleted = 0 AND review_status = 'PENDING'"), "question_review"),
                summaryCard("failedResumeParses", "Failed resume parses",
                        count("resume_analysis_record", "deleted = 0 AND parse_status = 'FAILED'"),
                        "resume_analysis_record")
        );
    }

    private AdminDashboardOverviewVO.SummaryCardVO summaryCard(String key, String label, Long value, String sourceTable) {
        AdminDashboardOverviewVO.SummaryCardVO vo = new AdminDashboardOverviewVO.SummaryCardVO();
        vo.setKey(key);
        vo.setLabel(label);
        vo.setValue(value);
        vo.setSourceTable(sourceTable);
        return vo;
    }

    private List<AdminDashboardOverviewVO.TrendStatVO> trendStats() {
        LocalDate start = LocalDate.now().minusDays(6);
        Map<LocalDate, AdminDashboardOverviewVO.TrendStatVO> map = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            AdminDashboardOverviewVO.TrendStatVO vo = new AdminDashboardOverviewVO.TrendStatVO();
            vo.setDate(date);
            vo.setInterviewCount(0L);
            vo.setResumeUploadCount(0L);
            vo.setAiCallCount(0L);
            vo.setAiCallFailedCount(0L);
            vo.setStudyPlanGeneratedCount(0L);
            vo.setQuestionReviewGeneratedCount(0L);
            map.put(date, vo);
        }
        fillTrend(map, "interview_session", "created_at", "deleted = 0", AdminDashboardOverviewVO.TrendStatVO::setInterviewCount);
        fillTrend(map, "resume_analysis_record", "created_at", "deleted = 0", AdminDashboardOverviewVO.TrendStatVO::setResumeUploadCount);
        fillTrend(map, "ai_call_log", "created_at", "deleted = 0", AdminDashboardOverviewVO.TrendStatVO::setAiCallCount);
        fillTrend(map, "ai_call_log", "created_at", "deleted = 0 AND (status = 0 OR success = 0)",
                AdminDashboardOverviewVO.TrendStatVO::setAiCallFailedCount);
        fillTrend(map, "study_plan", "created_at", "deleted = 0", AdminDashboardOverviewVO.TrendStatVO::setStudyPlanGeneratedCount);
        fillTrend(map, "question_review", "created_at", "deleted = 0", AdminDashboardOverviewVO.TrendStatVO::setQuestionReviewGeneratedCount);
        return new ArrayList<>(map.values());
    }

    private void fillTrend(Map<LocalDate, AdminDashboardOverviewVO.TrendStatVO> map, String tableName,
                           String dateColumn, String condition, TrendSetter setter) {
        // 部分本地库可能未执行所有历史迁移；趋势统计遇到缺表时保持 0，不影响整页概览。
        if (!tableExists(tableName)) {
            return;
        }
        String sql = "SELECT DATE(" + dateColumn + ") AS stat_date, COUNT(1) AS total FROM `" + tableName
                + "` WHERE " + condition + " AND " + dateColumn
                + " >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(" + dateColumn + ")";
        jdbcTemplate.query(sql, rs -> {
            LocalDate date = rs.getDate("stat_date").toLocalDate();
            AdminDashboardOverviewVO.TrendStatVO vo = map.get(date);
            if (vo != null) {
                setter.set(vo, rs.getLong("total"));
            }
        });
    }

    private List<AdminDashboardOverviewVO.PendingItemVO> pendingItems() {
        return List.of(
                pendingItem("pendingQuestionReviews", "Pending AI generated question reviews",
                        count("question_review", "deleted = 0 AND review_status = 'PENDING'"), "question_review", null),
                pendingItem("duplicateQuestionReviews", "Pending duplicate question reviews",
                        count("question_duplicate_review", "deleted = 0 AND review_status = 'PENDING'"),
                        "question_duplicate_review", null),
                pendingItem("promptVersions", "Prompt versions not active",
                        count("prompt_template_version", "deleted = 0 AND (is_active = 0 OR status <> 'ACTIVE')"),
                        "prompt_template_version", null),
                pendingItem("failedAiCalls", "Failed AI calls",
                        count("ai_call_log", "deleted = 0 AND (status = 0 OR success = 0)"), "ai_call_log", null),
                pendingItem("failedResumeParses", "Failed resume parses",
                        count("resume_analysis_record", "deleted = 0 AND parse_status = 'FAILED'"),
                        "resume_analysis_record", null)
        );
    }

    private AdminDashboardOverviewVO.PendingItemVO pendingItem(String key, String label, Long count,
                                                              String sourceTable, String reason) {
        AdminDashboardOverviewVO.PendingItemVO vo = new AdminDashboardOverviewVO.PendingItemVO();
        vo.setKey(key);
        vo.setLabel(label);
        vo.setCount(count);
        vo.setStatus(reason == null ? "SUPPORTED" : "UNKNOWN");
        vo.setSourceTable(sourceTable);
        vo.setReason(reason);
        return vo;
    }

    private AdminDashboardOverviewVO.SystemStatusVO systemStatus(LocalDateTime generatedAt) {
        AdminDashboardOverviewVO.SystemStatusVO vo = new AdminDashboardOverviewVO.SystemStatusVO();
        // 这里只做本服务可直接验证的探测；其他微服务未接入注册中心健康查询前显式标记 UNKNOWN。
        List<AdminDashboardOverviewVO.ServiceStatusVO> services = List.of(
                serviceStatus("overview", "HEALTHY", "管理概览接口聚合完成。", "local"),
                serviceStatus("database", databaseHealthy() ? "HEALTHY" : "DOWN",
                        databaseHealthy() ? "SELECT 1 执行成功。" : "SELECT 1 执行失败。", "jdbc"),
                probeService("codecoachai-gateway", 18080),
                probeService("codecoachai-auth", 9201),
                probeService("codecoachai-user", 9202),
                probeService("codecoachai-resume", 9204),
                probeService("codecoachai-interview", 9205),
                probeService("codecoachai-question", 9203),
                probeService("codecoachai-ai", 9206),
                probeService("codecoachai-task", 8090),
                probeService("codecoachai-file", 9209)
        );
        vo.setServices(services);
        vo.setStatus(services.stream().anyMatch(service -> "DOWN".equals(service.getStatus())) ? "DEGRADED" : "HEALTHY");
        vo.setGeneratedAt(generatedAt);
        return vo;
    }

    private AdminDashboardOverviewVO.ServiceStatusVO probeService(String name, int port) {
        String url = "http://127.0.0.1:" + port + "/actuator/health";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(900))
                    .GET()
                    .build();
            HttpResponse<String> response = healthHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean healthy = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && response.body() != null
                    && response.body().contains("\"status\":\"UP\"");
            return serviceStatus(name, healthy ? "HEALTHY" : "DOWN",
                    healthy ? "Actuator 健康检查通过。" : "Actuator 返回异常状态。", url);
        } catch (Exception ex) {
            return serviceStatus(name, "UNKNOWN", "本机 Actuator 探测失败：" + ex.getClass().getSimpleName(), url);
        }
    }

    private AdminDashboardOverviewVO.ServiceStatusVO serviceStatus(String name, String status, String reason, String source) {
        AdminDashboardOverviewVO.ServiceStatusVO vo = new AdminDashboardOverviewVO.ServiceStatusVO();
        vo.setServiceName(name);
        vo.setStatus(status);
        vo.setReason(reason);
        vo.setSource(source);
        return vo;
    }

    private boolean databaseHealthy() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private long count(String tableName, String condition) {
        // 管理概览跨多个业务表，缺表按 0 处理，用于兼容开发库与灰度库迁移进度不一致。
        if (!tableExists(tableName)) {
            return 0L;
        }
        String sql = "SELECT COUNT(1) FROM `" + tableName + "` WHERE " + condition;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, tableName);
        return count != null && count > 0;
    }

    @FunctionalInterface
    private interface TrendSetter {
        void set(AdminDashboardOverviewVO.TrendStatVO vo, Long value);
    }
}
