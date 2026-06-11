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
import java.util.Properties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态值只能是 0 或 1");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "系统配置不存在或已不可用");
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
        List<AdminDashboardOverviewVO.SummaryCardVO> cards = new ArrayList<>();
        cards.add(summaryCard("users", "用户总数", count("sys_user", "deleted = 0"), "sys_user"));
        cards.add(summaryCard("resumes", "简历总数", count("resume", "deleted = 0"), "resume"));
        cards.add(summaryCard("interviews", "面试记录数", count("interview_session", "deleted = 0"), "interview_session"));
        cards.add(summaryCard("studyPlans", "学习计划数", count("study_plan", "deleted = 0"), "study_plan"));
        cards.add(summaryCard("aiCalls", "AI 调用总数", count("ai_call_log", "deleted = 0"), "ai_call_log"));
        cards.add(summaryCard("todayAiCalls", "今日 AI 调用",
                count("ai_call_log", "deleted = 0 AND DATE(created_at) = CURDATE()"), "ai_call_log"));
        cards.add(summaryCard("failedAiCalls", "智能生成失败", failedAiCallCount(), "ai_call_log"));
        cards.add(summaryCard("pendingQuestionReviews", "待审核生成题",
                count("question_review", "deleted = 0 AND review_status = 'PENDING'"), "question_review"));
        cards.add(summaryCard("failedResumeParses", "简历解析失败",
                failedResumeParseCount(), "resume_analysis_record"));
        cards.add(summaryCard("failedAsyncTasks", "异步任务失败", failedAsyncTaskCount(), "async_task"));
        cards.add(summaryCard("agentSuccessRate", "计划生成成功率", agentSuccessRatePercent(), "agent_run"));
        cards.add(summaryCard("slowSqlWarnings", "慢 SQL 告警", slowSqlWarningCount(), "slow_sql_log"));
        cards.add(summaryCard("notificationFailures", "通知发送失败", notificationFailureCount(), "notification"));
        return cards;
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
        try {
            jdbcTemplate.query(sql, rs -> {
                LocalDate date = rs.getDate("stat_date").toLocalDate();
                AdminDashboardOverviewVO.TrendStatVO vo = map.get(date);
                if (vo != null) {
                    setter.set(vo, rs.getLong("total"));
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Admin dashboard trend stat skipped, table={}, dateColumn={}", tableName, dateColumn, ex);
        }
    }

    private List<AdminDashboardOverviewVO.PendingItemVO> pendingItems() {
        List<AdminDashboardOverviewVO.PendingItemVO> items = new ArrayList<>();
        items.add(pendingItem("pendingQuestionReviews", "待审核 AI 生成题目",
                count("question_review", "deleted = 0 AND review_status = 'PENDING'"), "question_review", null));
        items.add(pendingItem("duplicateQuestionReviews", "待审核重复题目",
                count("question_duplicate_review", "deleted = 0 AND review_status = 'PENDING'"),
                "question_duplicate_review", null));
        items.add(pendingItem("promptVersions", "Prompt 版本待启用",
                count("prompt_template_version", "deleted = 0 AND (is_active = 0 OR status <> 'ACTIVE')"),
                "prompt_template_version", null));
        items.add(pendingItem("failedAiCalls", "智能生成失败", failedAiCallCount(), "ai_call_log", null));
        items.add(pendingItem("failedResumeParses", "简历解析失败",
                failedResumeParseCount(), "resume_analysis_record", null));
        items.add(pendingItem("failedAsyncTasks", "异步任务失败", failedAsyncTaskCount(), "async_task", null));
        items.add(pendingItem("failedAgentRuns", "近 7 日计划生成失败", failedAgentRunCount(), "agent_run", null));
        items.add(pendingItem("slowSqlWarnings", "近 7 日慢 SQL 告警", slowSqlWarningCount(), "slow_sql_log", null));
        items.add(notificationFailurePendingItem());
        return items;
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

    private long failedAsyncTaskCount() {
        if (!tableExists("async_task") || !columnExists("async_task", "status")) {
            return 0L;
        }
        return count("async_task", "deleted = 0 AND status IN ('FAILED','DEAD','ERROR','DEAD_LETTER')");
    }

    private long failedAiCallCount() {
        if (!tableExists("ai_call_log")) {
            return 0L;
        }
        boolean hasStatus = columnExists("ai_call_log", "status");
        boolean hasSuccess = columnExists("ai_call_log", "success");
        if (!hasStatus && !hasSuccess) {
            return 0L;
        }
        List<String> conditions = new ArrayList<>();
        if (hasStatus) {
            conditions.add("status = 0");
        }
        if (hasSuccess) {
            conditions.add("success = 0");
        }
        return count("ai_call_log", "deleted = 0 AND (" + String.join(" OR ", conditions) + ")");
    }

    private long failedResumeParseCount() {
        if (!tableExists("resume_analysis_record")
                || !columnExists("resume_analysis_record", "file_id")
                || !columnExists("resume_analysis_record", "parse_status")) {
            return 0L;
        }
        String sql = """
                SELECT COUNT(1)
                FROM resume_analysis_record latest
                INNER JOIN (
                  SELECT file_id, MAX(CONCAT(DATE_FORMAT(created_at, '%Y%m%d%H%i%s'), LPAD(id, 20, '0'))) AS sort_key
                  FROM resume_analysis_record
                  WHERE deleted = 0
                    AND file_id IS NOT NULL
                  GROUP BY file_id
                ) picked ON picked.file_id = latest.file_id
                  AND picked.sort_key = CONCAT(DATE_FORMAT(latest.created_at, '%Y%m%d%H%i%s'), LPAD(latest.id, 20, '0'))
                WHERE latest.deleted = 0
                  AND latest.parse_status = 'FAILED'
                """;
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            log.warn("Admin dashboard resume parse failure count skipped", ex);
            return 0L;
        }
    }

    private long failedAgentRunCount() {
        if (!tableExists("agent_run") || !columnExists("agent_run", "status")) {
            return 0L;
        }
        String recentCondition = columnExists("agent_run", "created_at")
                ? " AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)"
                : "";
        return count("agent_run", "deleted = 0 AND status = 'FAILED'" + recentCondition);
    }

    private long agentSuccessRatePercent() {
        if (!tableExists("agent_run") || !columnExists("agent_run", "status")) {
            return 0L;
        }
        String recentCondition = columnExists("agent_run", "created_at")
                ? " AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)"
                : "";
        long success = count("agent_run", "deleted = 0 AND status = 'SUCCESS'" + recentCondition);
        long failed = count("agent_run", "deleted = 0 AND status = 'FAILED'" + recentCondition);
        long total = success + failed;
        if (total <= 0L) {
            return 0L;
        }
        return Math.round(success * 100.0D / total);
    }

    private long slowSqlWarningCount() {
        if (!tableExists("slow_sql_log")) {
            return 0L;
        }
        String condition = "deleted = 0";
        if (columnExists("slow_sql_log", "created_at")) {
            condition += " AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)";
        }
        return count("slow_sql_log", condition);
    }

    private AdminDashboardOverviewVO.PendingItemVO notificationFailurePendingItem() {
        if (!tableExists("notification")) {
            return pendingItem("notificationFailures", "通知发送失败", 0L,
                    "notification", "通知表暂不可用，当前无法统计发送失败。");
        }
        String statusColumn = firstExistingColumn("notification", List.of("send_status", "delivery_status", "deliver_status"));
        if (statusColumn == null) {
            return pendingItem("notificationFailures", "通知发送失败", 0L,
                    "notification", "通知暂未记录发送状态，当前无法统计发送失败。");
        }
        long failures = notificationFailureCount(statusColumn);
        return pendingItem("notificationFailures", "通知发送失败", failures, "notification", null);
    }

    private long notificationFailureCount() {
        if (!tableExists("notification")) {
            return 0L;
        }
        String statusColumn = firstExistingColumn("notification", List.of("send_status", "delivery_status", "deliver_status"));
        if (statusColumn == null) {
            return 0L;
        }
        return notificationFailureCount(statusColumn);
    }

    private long notificationFailureCount(String statusColumn) {
        return count("notification", "deleted = 0 AND `" + statusColumn + "` IN ('FAILED','ERROR','SEND_FAILED','DELIVERY_FAILED')");
    }

    private String firstExistingColumn(String tableName, List<String> candidates) {
        for (String candidate : candidates) {
            if (columnExists(tableName, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private AdminDashboardOverviewVO.SystemStatusVO systemStatus(LocalDateTime generatedAt) {
        AdminDashboardOverviewVO.SystemStatusVO vo = new AdminDashboardOverviewVO.SystemStatusVO();
        // 这里只做本服务可直接验证的探测；其他微服务未接入注册中心健康查询前显式标记 UNKNOWN。
        List<AdminDashboardOverviewVO.ServiceStatusVO> services = List.of(
                serviceStatus("overview", "HEALTHY", "管理概览接口聚合完成。", "local"),
                serviceStatus("database", databaseHealthy() ? "HEALTHY" : "DOWN",
                        databaseHealthy() ? "SELECT 1 执行成功。" : "SELECT 1 执行失败。", "jdbc"),
                probeGateway("codecoachai-gateway", "codecoachai-gateway", 18080),
                probeService("codecoachai-auth", "codecoachai-auth", 9201),
                probeService("codecoachai-user", "codecoachai-user", 9202),
                probeService("codecoachai-resume", "codecoachai-resume", 9204),
                probeService("codecoachai-interview", "codecoachai-interview", 9205),
                probeService("codecoachai-question", "codecoachai-question", 9203),
                probeService("codecoachai-ai", "codecoachai-ai", 9206),
                probeService("codecoachai-task", "codecoachai-task", 8090),
                probeService("codecoachai-file", "codecoachai-file", 9209)
        );
        vo.setServices(services);
        vo.setStatus(services.stream().anyMatch(service -> "DOWN".equals(service.getStatus())) ? "DEGRADED" : "HEALTHY");
        vo.setOpsMetrics(opsMetrics());
        vo.setGeneratedAt(generatedAt);
        return vo;
    }

    private AdminDashboardOverviewVO.ServiceStatusVO probeService(String name, String host, int port) {
        String url = "http://" + host + ":" + port + "/actuator/health";
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

    private AdminDashboardOverviewVO.ServiceStatusVO probeGateway(String name, String host, int port) {
        AdminDashboardOverviewVO.ServiceStatusVO actuatorStatus = probeService(name, host, port);
        if ("HEALTHY".equals(actuatorStatus.getStatus())) {
            return actuatorStatus;
        }

        String url = "http://" + host + ":" + port + "/";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(900))
                    .GET()
                    .build();
            HttpResponse<String> response = healthHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean reachable = response.statusCode() >= 200 && response.statusCode() < 500;
            return serviceStatus(name, reachable ? "HEALTHY" : "DOWN",
                    reachable ? "Gateway HTTP port reachable." : "Gateway HTTP port returned " + response.statusCode() + ".",
                    url);
        } catch (Exception ex) {
            return serviceStatus(name, actuatorStatus.getStatus(), actuatorStatus.getReason(), actuatorStatus.getSource());
        }
    }

    private AdminDashboardOverviewVO.OpsMetricsVO opsMetrics() {
        AdminDashboardOverviewVO.OpsMetricsVO vo = new AdminDashboardOverviewVO.OpsMetricsVO();
        long requestsLastMinute = recentCount("operation_log", "created_at", "deleted = 0")
                + recentCount("login_log", "created_at", "1 = 1");
        long transactionsLastMinute = recentCount("resume", "created_at", "deleted = 0")
                + recentCount("target_job", "created_at", "deleted = 0")
                + recentCount("study_plan", "created_at", "deleted = 0")
                + recentCount("study_task", "created_at", "deleted = 0")
                + recentCount("interview_session", "created_at", "deleted = 0")
                + recentCount("practice_record", "created_at", "deleted = 0")
                + recentCount("agent_task", "created_at", "deleted = 0");
        vo.setQps(round2(requestsLastMinute / 60.0));
        vo.setTps(round2(transactionsLastMinute / 60.0));
        vo.setRpm(requestsLastMinute);
        vo.setTpm(recentTokenCount());
        fillJvmMetrics(vo);
        fillRedisMetrics(vo);
        vo.setMetricsSource("runtime-db-jvm-redis");
        return vo;
    }

    private long recentCount(String tableName, String dateColumn, String condition) {
        if (!tableExists(tableName) || !columnExists(tableName, dateColumn)) {
            return 0L;
        }
        String sql = "SELECT COUNT(1) FROM `" + tableName + "` WHERE " + condition
                + " AND `" + dateColumn + "` >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)";
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            log.warn("Admin dashboard recent count skipped, table={}, dateColumn={}", tableName, dateColumn, ex);
            return 0L;
        }
    }

    private long recentTokenCount() {
        if (!tableExists("ai_call_log") || !columnExists("ai_call_log", "created_at")) {
            return 0L;
        }
        boolean hasPromptTokens = columnExists("ai_call_log", "prompt_tokens");
        boolean hasCompletionTokens = columnExists("ai_call_log", "completion_tokens");
        if (!hasPromptTokens && !hasCompletionTokens) {
            return 0L;
        }
        String promptExpr = hasPromptTokens ? "COALESCE(prompt_tokens, 0)" : "0";
        String completionExpr = hasCompletionTokens ? "COALESCE(completion_tokens, 0)" : "0";
        String sql = "SELECT COALESCE(SUM(" + promptExpr + " + " + completionExpr
                + "), 0) FROM ai_call_log WHERE deleted = 0 AND created_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)";
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            log.warn("Admin dashboard token count skipped", ex);
            return 0L;
        }
    }

    private void fillJvmMetrics(AdminDashboardOverviewVO.OpsMetricsVO vo) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        vo.setHeapUsedMb(used / 1024 / 1024);
        vo.setHeapMaxMb(max / 1024 / 1024);
        vo.setHeapUsage(max > 0 ? round2((double) used * 100 / max) : 0.0);
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            vo.setProcessCpuUsage(percent(osBean.getProcessCpuLoad()));
            vo.setSystemCpuUsage(percent(osBean.getCpuLoad()));
        }
    }

    private void fillRedisMetrics(AdminDashboardOverviewVO.OpsMetricsVO vo) {
        RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            return;
        }
        try (RedisConnection connection = factory.getConnection()) {
            Properties stats = connection.serverCommands().info("stats");
            Properties clients = connection.serverCommands().info("clients");
            long hits = parseLong(stats.getProperty("keyspace_hits"));
            long misses = parseLong(stats.getProperty("keyspace_misses"));
            vo.setRedisKeyspaceHits(hits);
            vo.setRedisKeyspaceMisses(misses);
            vo.setRedisHitRate(hits + misses > 0 ? round2((double) hits * 100 / (hits + misses)) : 0.0);
            vo.setRedisConnectedClients((int) parseLong(clients.getProperty("connected_clients")));
        } catch (RuntimeException ex) {
            vo.setRedisHitRate(0.0);
        }
    }

    private Double percent(double value) {
        return value >= 0 ? round2(value * 100) : null;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
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
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            log.warn("Admin dashboard count skipped, table={}, condition={}", tableName, condition, ex);
            return 0L;
        }
    }

    private boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, tableName);
            return count != null && count > 0;
        } catch (RuntimeException ex) {
            log.warn("Admin dashboard table existence check failed, table={}", tableName, ex);
            return false;
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        String sql = "SELECT COUNT(1) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, tableName, columnName);
            return count != null && count > 0;
        } catch (RuntimeException ex) {
            log.warn("Admin dashboard column existence check failed, table={}, column={}", tableName, columnName, ex);
            return false;
        }
    }

    @FunctionalInterface
    private interface TrendSetter {
        void set(AdminDashboardOverviewVO.TrendStatVO vo, Long value);
    }
}
