package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.vo.UserAsyncTaskVO;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Task User")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class UserTaskController {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{6}(?:19|20)\\d{2}\\d{2}\\d{2}\\d{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\"(?:api[-_]?key|authorization|bearer|token|password|secret)\"\\s*:\\s*\")[^\"]+(\")");
    private static final Pattern TECHNICAL_DETAIL = Pattern.compile("(?i)\\b(json|schema|payload|raw|prompt|exception|stacktrace|authorization|token|secret|password)\\b");

    private final AsyncTaskMapper asyncTaskMapper;

    @Operation(summary = "Page current user's async tasks")
    @GetMapping
    public Result<PageResult<UserAsyncTaskVO>> pageTasks(
            @RequestParam(required = false) Long pageNo,
            @RequestParam(required = false) Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) String keyword) {
        Long userId = SecurityAssert.requireLoginUserId();
        String resolvedBizType = StringUtils.hasText(bizType) ? bizType : type;
        long currentPage = normalizePageNo(pageNo != null ? pageNo : pageNum);
        long safePageSize = normalizePageSize(pageSize);
        Page<AsyncTask> page = asyncTaskMapper.selectPage(
                Page.of(currentPage, safePageSize),
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getUserId, userId)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(AsyncTask::getMessageId, trim(keyword))
                                .or().like(AsyncTask::getTraceId, trim(keyword))
                                .or().like(AsyncTask::getBizId, trim(keyword))
                                .or().like(AsyncTask::getBizType, trim(keyword)))
                        .eq(StringUtils.hasText(resolvedBizType), AsyncTask::getBizType, resolvedBizType)
                        .eq(StringUtils.hasText(status), AsyncTask::getStatus, status)
                        .eq(StringUtils.hasText(messageId), AsyncTask::getMessageId, trim(messageId))
                        .eq(StringUtils.hasText(traceId), AsyncTask::getTraceId, trim(traceId))
                        .eq(StringUtils.hasText(bizId), AsyncTask::getBizId, trim(bizId))
                        .orderByDesc(AsyncTask::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords().stream().map(this::toUserTaskVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "Get current user's async task detail")
    @GetMapping("/{id}")
    public Result<UserAsyncTaskVO> detail(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        AsyncTask task = asyncTaskMapper.selectOne(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getId, id)
                .eq(AsyncTask::getUserId, userId)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务不存在或无权访问");
        }
        return Result.success(toUserTaskVO(task));
    }

    private UserAsyncTaskVO toUserTaskVO(AsyncTask task) {
        UserAsyncTaskVO vo = new UserAsyncTaskVO();
        vo.setId(task.getId());
        vo.setMessageId(task.getMessageId());
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setUserId(task.getUserId());
        vo.setTraceId(task.getTraceId());
        vo.setStatus(task.getStatus());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setMaxRetryCount(task.getMaxRetry());
        vo.setFailureReason(safeFailureReason(task.getFailureReason()));
        vo.setPayloadPreview(payloadPreview(task));
        vo.setPayloadHash(sha256Prefix(task.getPayload()));
        vo.setResultPreview(resultPreview(task));
        vo.setResultHash(sha256Prefix(task.getResult()));
        vo.setRawFieldsAvailable(StringUtils.hasText(task.getPayload()) || StringUtils.hasText(task.getResult()));
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private String payloadPreview(AsyncTask task) {
        if (!StringUtils.hasText(task.getPayload())) {
            return null;
        }
        return "生成依据已安全保存；当前任务关联" + bizLabel(task.getBizType())
                + (StringUtils.hasText(task.getBizId()) ? "，记录 " + task.getBizId() : "")
                + "。如仍无法恢复，请复制问题反馈码反馈。";
    }

    private String resultPreview(AsyncTask task) {
        if (!StringUtils.hasText(task.getResult())) {
            return null;
        }
        String status = task.getStatus() == null ? "" : task.getStatus().trim().toUpperCase();
        if ("SUCCESS".equals(status)) {
            return "任务已完成，完整生成内容请回到相关页面查看。";
        }
        if ("FAILED".equals(status) || "DEAD".equals(status) || "ERROR".equals(status)) {
            return "任务未完成，处理线索已保存；可回到相关页面重试，或复制问题反馈码反馈。";
        }
        return "任务结果正在整理，请稍后刷新任务状态。";
    }

    private String safeFailureReason(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String masked = maskText(value.trim());
        if (TECHNICAL_DETAIL.matcher(masked).find()) {
            return "任务执行失败，处理线索已保存；请回到相关页面重试，或复制问题反馈码反馈。";
        }
        return truncate(masked.replaceAll("\\s+", " "), 180);
    }

    private String maskText(String value) {
        String masked = EMAIL.matcher(value).replaceAll("***@***");
        masked = CHINA_MOBILE.matcher(masked).replaceAll("1**********");
        masked = ID_CARD.matcher(masked).replaceAll("******************");
        return JSON_SECRET.matcher(masked).replaceAll("$1******$2");
    }

    private String sha256Prefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }

    private String bizLabel(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return "相关功能";
        }
        return switch (bizType) {
            case "resume.optimize" -> "简历建议";
            case "resume.parse" -> "简历解析";
            case "job-target.parse" -> "岗位分析";
            case "resume-job-match.analyze" -> "岗位匹配";
            case "question-recommendation.generate" -> "推荐训练";
            case "question.generate", "question.ai-generate" -> "题目生成";
            case "interview.report" -> "面试报告";
            case "study-plan.generate" -> "学习计划";
            case "agent.daily-plan" -> "今日计划";
            default -> "相关功能";
        };
    }

    private long normalizePageNo(Long value) {
        return value == null || value < 1 ? 1L : value;
    }

    private long normalizePageSize(Long value) {
        if (value == null || value < 1) {
            return 20L;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
