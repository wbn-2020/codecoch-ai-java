package com.codecoachai.ai.agent.weekly.service;

import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportQueryDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportRefreshDTO;
import com.codecoachai.ai.agent.domain.entity.weekly.AgentWeeklyReport;
import com.codecoachai.ai.agent.feign.ResumeAgentContextFeignClient;
import com.codecoachai.ai.agent.weekly.config.WeeklyReportFeatureProperties;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.QueryContext;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportHashUtils;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportSanitizer;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportTimeProvider;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WeeklyReportRequestValidator {

    public static final String OPERATION_GENERATE = "GENERATE";
    public static final String OPERATION_REFRESH = "REFRESH";

    private static final int DEFAULT_QUERY_LIMIT = 20;
    private final WeeklyReportFeatureProperties featureProperties;
    private final WeeklyReportTimeProvider timeProvider;
    private final WeeklyReportHashUtils hashUtils;
    private final WeeklyReportSanitizer sanitizer;
    private final ResumeAgentContextFeignClient resumeAgentContextFeignClient;

    public RequestContext generate(Long userId, AgentWeeklyReportGenerateDTO dto) {
        requireUser(userId);
        AgentWeeklyReportGenerateDTO request = dto == null ? new AgentWeeklyReportGenerateDTO() : dto;
        ZoneId zoneId = zoneId(request.getTimezone());
        Instant now = timeProvider.now();
        LocalDate currentWeekStart = currentWeekStart(now, zoneId);
        LocalDate weekStart = request.getWeekStartDate() == null
                ? currentWeekStart
                : requireMonday(request.getWeekStartDate(), "周报开始日期");
        if (weekStart.isAfter(currentWeekStart)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "周报只能生成当前周或已经结束的自然周");
        }
        requireTargetOwner(userId, request.getTargetJobId());

        RequestContext context = baseContext(userId, request.getTargetJobId(), weekStart, zoneId, now);
        context.setOperation(OPERATION_GENERATE);
        context.setForceRefresh(Boolean.TRUE.equals(request.getForceRefresh()));
        context.setRequestId(safeIdentifier(request.getRequestId(), "请求 ID"));
        context.setIdempotencyKeyHash(hashUtils.idempotencyHash(
                OPERATION_GENERATE, normalizedIdentifier(request.getIdempotencyKey(), "幂等键")));
        context.setIdempotencyPayloadHash(hashUtils.hash(idempotencyPayload(
                context, Boolean.TRUE.equals(request.getForceRefresh()), null)));
        context.setTraceId(StringUtils.hasText(context.getRequestId())
                ? context.getRequestId()
                : UUID.randomUUID().toString());
        return context;
    }

    public RequestContext refresh(Long userId,
                                  AgentWeeklyReport report,
                                  AgentWeeklyReportRefreshDTO dto) {
        requireUser(userId);
        if (report == null || !Objects.equals(userId, report.getUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "周报不存在或无权访问");
        }
        requireTargetOwner(userId, report.getTargetJobId());
        ZoneId zoneId = zoneId(report.getTimezone());
        Instant now = timeProvider.now();
        LocalDate weekStart = requireMonday(report.getWeekStartDate(), "周报开始日期");
        if (weekStart.isAfter(currentWeekStart(now, zoneId))) {
            throw futureWeekError();
        }
        RequestContext context = baseContext(
                userId, report.getTargetJobId(), weekStart, zoneId, now);
        context.setOperation(OPERATION_REFRESH);
        context.setForceRefresh(true);
        AgentWeeklyReportRefreshDTO request = dto == null ? new AgentWeeklyReportRefreshDTO() : dto;
        context.setRequestId(safeIdentifier(request.getRequestId(), "请求 ID"));
        context.setIdempotencyKeyHash(hashUtils.idempotencyHash(
                OPERATION_REFRESH, normalizedIdentifier(request.getIdempotencyKey(), "幂等键")));
        context.setIdempotencyPayloadHash(hashUtils.hash(idempotencyPayload(context, true, report.getId())));
        context.setTraceId(StringUtils.hasText(context.getRequestId())
                ? context.getRequestId()
                : UUID.randomUUID().toString());
        return context;
    }

    public QueryContext query(Long userId, AgentWeeklyReportQueryDTO dto, boolean currentQuery) {
        requireUser(userId);
        AgentWeeklyReportQueryDTO request = dto == null ? new AgentWeeklyReportQueryDTO() : dto;
        ZoneId zoneId = zoneId(request.getTimezone());
        LocalDate currentWeekStart = currentWeekStart(timeProvider.now(), zoneId);
        requireTargetOwner(userId, request.getTargetJobId());

        QueryContext context = new QueryContext();
        context.setUserId(userId);
        context.setTargetJobId(request.getTargetJobId());
        context.setTargetScopeKey(targetScopeKey(request.getTargetJobId()));
        context.setTimezone(zoneId.getId());
        context.setZoneId(zoneId);
        context.setLimit(request.getLimit() == null ? DEFAULT_QUERY_LIMIT : request.getLimit());

        LocalDate requestedWeek = request.getWeekStartDate();
        if (requestedWeek != null
                && requireMonday(requestedWeek, "周报开始日期").isAfter(currentWeekStart)) {
            throw futureWeekError();
        }
        if (currentQuery) {
            LocalDate weekStart = requestedWeek == null
                    ? currentWeekStart
                    : requireMonday(requestedWeek, "周报开始日期");
            context.setWeekStartDate(weekStart);
        } else if (requestedWeek != null) {
            context.setWeekStartDate(requireMonday(requestedWeek, "周报开始日期"));
        }

        LocalDate from = request.getFromWeekStart() == null
                ? null
                : requireMonday(request.getFromWeekStart(), "查询起始周");
        LocalDate to = request.getToWeekStart() == null
                ? null
                : requireMonday(request.getToWeekStart(), "查询结束周");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "查询起始周不能晚于查询结束周");
        }
        if (from != null && from.isAfter(currentWeekStart)) {
            throw futureWeekError();
        }
        if (to != null && to.isAfter(currentWeekStart)) {
            throw futureWeekError();
        }
        context.setFromWeekStart(from);
        context.setToWeekStart(to);
        return context;
    }

    private RequestContext baseContext(Long userId,
                                       Long targetJobId,
                                       LocalDate weekStart,
                                       ZoneId zoneId,
                                       Instant now) {
        RequestContext context = new RequestContext();
        context.setUserId(userId);
        context.setTargetJobId(targetJobId);
        context.setTargetScopeKey(targetScopeKey(targetJobId));
        context.setWeekStartDate(weekStart);
        context.setWeekEndDate(weekStart.plusDays(6));
        context.setTimezone(zoneId.getId());
        context.setZoneId(zoneId);
        context.setRangeStartUtc(LocalDateTime.ofInstant(
                weekStart.atStartOfDay(zoneId).toInstant(), ZoneOffset.UTC));
        Instant rangeEnd = weekStart.plusDays(7).atStartOfDay(zoneId).toInstant();
        context.setRangeEndUtc(LocalDateTime.ofInstant(rangeEnd, ZoneOffset.UTC));
        Instant rangeStart = weekStart.atStartOfDay(zoneId).toInstant();
        context.setDatabaseRangeStartAt(timeProvider.storageDateTime(rangeStart));
        context.setDatabaseRangeEndAt(timeProvider.storageDateTime(rangeEnd));
        LocalDateTime cutoff = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        context.setSourceCutoffAt(cutoff);
        context.setDatabaseSourceCutoffAt(timeProvider.storageDateTime(now));
        context.setGeneratedAt(cutoff);
        context.setReportStatus(now.isBefore(rangeEnd) ? "IN_PROGRESS" : "COMPLETED");
        return context;
    }

    private Map<String, Object> idempotencyPayload(RequestContext context,
                                                   boolean forceRefresh,
                                                   Long reportId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", context.getOperation());
        payload.put("reportId", reportId);
        payload.put("weekStartDate", context.getWeekStartDate());
        payload.put("targetJobId", context.getTargetJobId());
        payload.put("targetScopeKey", context.getTargetScopeKey());
        payload.put("timezone", context.getTimezone());
        payload.put("forceRefresh", forceRefresh);
        return payload;
    }

    private ZoneId zoneId(String requestedTimezone) {
        String timezone = StringUtils.hasText(requestedTimezone)
                ? requestedTimezone.trim()
                : featureProperties.getWeeklyReportDefaultTimezone();
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "时区必须是有效的 IANA 时区标识");
        }
    }

    private LocalDate currentWeekStart(Instant now, ZoneId zoneId) {
        LocalDate currentDate = now.atZone(zoneId).toLocalDate();
        return currentDate.minusDays(currentDate.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private LocalDate requireMonday(LocalDate value, String fieldName) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + "不能为空");
        }
        if (value.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + "必须是用户时区中的周一");
        }
        return value;
    }

    private void requireTargetOwner(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return;
        }
        if (targetJobId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位 ID 必须大于 0");
        }
        try {
            Result<TargetJobContextVO> result = resumeAgentContextFeignClient.getTargetJob(userId, targetJobId);
            TargetJobContextVO targetJob = result == null || !result.isSuccess() ? null : result.getData();
            if (targetJob == null
                    || !Objects.equals(targetJobId, targetJob.getId())
                    || !Objects.equals(userId, targetJob.getUserId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不存在或无权访问");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "目标岗位归属暂时无法校验，请稍后重试");
        }
    }

    private String targetScopeKey(Long targetJobId) {
        return targetJobId == null ? "ALL" : "TARGET_JOB:" + targetJobId;
    }

    private String safeIdentifier(String value, String fieldName) {
        String normalized = normalizedIdentifier(value, fieldName);
        return normalized == null ? null : sanitizer.safeText(normalized, 128);
    }

    private String normalizedIdentifier(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.trim().length() > 128) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + "不能超过 128 个字符");
        }
        return value.trim();
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息无效");
        }
    }

    private BusinessException futureWeekError() {
        return new BusinessException(
                ErrorCode.PARAM_ERROR,
                "不能查询、生成或刷新尚未开始的自然周周报");
    }
}
