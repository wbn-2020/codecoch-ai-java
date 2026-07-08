package com.codecoachai.resume.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.feign.vo.InnerFileDownloadVO;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.service.FileContentService;
import com.codecoachai.resume.service.extractor.ResumeTextExtractorDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历解析任务相关 inner 接口，供 codecoachai-task 异步消费者调用。
 *
 * <ul>
 *   <li>GET  /inner/resumes/analysis-records/{id}/raw         拉取 rawText + 元数据</li>
 *   <li>POST /inner/resumes/analysis-records/{id}/complete-parse 回写解析结果</li>
 * </ul>
 */
@Slf4j
@Tag(name = "Inner-简历解析任务")
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/resumes/analysis-records")
public class InnerResumeAnalysisController {

    private static final String TASK_SERVICE_NAME = "codecoachai-task";
    private static final int CALLBACK_LOG_MAX_LENGTH = 256;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{6}(?:19|20)\\d{2}\\d{2}\\d{2}\\d{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile("(?i)\\b(api[-_ ]?key|authorization|bearer|token|password|secret|access[-_ ]?token|refresh[-_ ]?token)\\b\\s*[:=]\\s*([^\\s,;\"'}]+)");
    private static final Pattern URL_TOKEN = Pattern.compile("(?i)([?&](?:token|access_token|refresh_token|code|signature|sign|secret|key)=)[^&#\\s]+");
    private static final Pattern RESUME_PROMPT_FRAGMENT = Pattern.compile(
            "(?i)(resume|cv|curriculum vitae|rawText|structuredJson|modelTrace|prompt|system prompt|user prompt|简历|教育经历|工作经历|项目经历|求职意向|个人信息)");
    private static final Set<String> MUTABLE_PARSE_STATUSES = Set.of(
            ResumeParseStatus.PENDING.name(),
            ResumeParseStatus.PARSING.name()
    );

    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final FileContentService fileContentService;
    private final ResumeTextExtractorDispatcher textExtractorDispatcher;

    @Operation(summary = "获取简历解析任务原始数据（rawText + 元信息）")
    @GetMapping("/{id}/raw")
    public Result<RawVO> getAnalysisRaw(@PathVariable("id") Long id,
                                        @RequestHeader(value = HeaderConstants.SERVICE_NAME, required = false)
                                        String serviceName) {
        requireTaskService(serviceName);
        ResumeAnalysisRecord record = analysisRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析记录不存在：" + id);
        }
        RawVO vo = new RawVO();
        vo.setId(record.getId());
        vo.setFileId(record.getFileId());
        vo.setUserId(record.getUserId());
        RawTextPayload rawText = resolveRawText(record);
        vo.setRawText(rawText.rawText());
        vo.setOriginalFilename(rawText.originalFilename());
        vo.setFileExt(rawText.fileExt());
        vo.setParseStatus(record.getParseStatus());
        return Result.success(vo);
    }

    private RawTextPayload resolveRawText(ResumeAnalysisRecord record) {
        if (StringUtils.hasText(record.getRawText())) {
            return new RawTextPayload(record.getRawText(), null, null);
        }
        InnerFileDownloadVO file = fileContentService.downloadResumeFile(record.getFileId(), record.getUserId());
        String rawText = textExtractorDispatcher.extract(file.getFileExt(), file.getContent());
        analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getId, record.getId())
                .set(ResumeAnalysisRecord::getRawText, rawText)
                .set(ResumeAnalysisRecord::getUpdatedAt, LocalDateTime.now()));
        return new RawTextPayload(rawText, file.getOriginalFilename(), file.getFileExt());
    }

    @Operation(summary = "回写 AI 解析结果（task-service 调用）")
    @PostMapping("/{id}/complete-parse")
    public Result<Void> completeParse(@PathVariable("id") Long id,
                                      @RequestHeader(value = HeaderConstants.SERVICE_NAME, required = false)
                                      String serviceName,
                                      @Valid @RequestBody CompleteDTO dto) {
        requireTaskService(serviceName);
        ResumeAnalysisRecord existing = analysisRecordMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析记录不存在：" + id);
        }
        String status = StringUtils.hasText(dto.getParseStatus()) ? dto.getParseStatus() : "SUCCESS";
        ResumeParseStatus parseStatus = ResumeParseStatus.of(status);
        if (parseStatus == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported resume parse status: " + status);
        }
        logCallbackDiagnostics(status, dto);

        LambdaUpdateWrapper<ResumeAnalysisRecord> upd = new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getId, id)
                .eq(ResumeAnalysisRecord::getDeleted, 0)
                .and(wrapper -> wrapper.in(ResumeAnalysisRecord::getParseStatus, MUTABLE_PARSE_STATUSES)
                        .or()
                        .isNull(ResumeAnalysisRecord::getParseStatus))
                .set(ResumeAnalysisRecord::getParseStatus, status)
                .set(ResumeAnalysisRecord::getUpdatedAt, LocalDateTime.now());
        if (StringUtils.hasText(dto.getStructuredJson())) {
            upd.set(ResumeAnalysisRecord::getStructuredJson, dto.getStructuredJson());
        }
        if (StringUtils.hasText(dto.getRawText())) {
            upd.set(ResumeAnalysisRecord::getRawText, dto.getRawText());
        }
        if (StringUtils.hasText(dto.getErrorMessage())) {
            upd.set(ResumeAnalysisRecord::getErrorMessage, safeParseErrorMessage());
        }
        int updated = analysisRecordMapper.update(null, upd);
        if (updated <= 0) {
            ResumeAnalysisRecord latest = analysisRecordMapper.selectById(id);
            if (isIdempotentDuplicate(latest, status, dto)) {
                log.info("Resume analysis record {} duplicate callback ignored, status={}", id, status);
                return Result.success();
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Resume analysis callback rejected because current status is not mutable: " + id);
        }
        log.info("Resume analysis record {} updated to {}", id, status);
        return Result.success();
    }

    @Data
    public static class RawVO {
        private Long id;
        private Long fileId;
        private Long userId;
        private String rawText;
        private String originalFilename;
        private String fileExt;
        private String parseStatus;
    }

    @Data
    public static class CompleteDTO {
        private String structuredJson;
        private String rawText;
        private String parseStatus;
        private String errorMessage;
        private String modelTrace;
    }

    private String safeParseErrorMessage() {
        return "简历解析失败，请稍后重试";
    }

    private void requireTaskService(String serviceName) {
        if (!TASK_SERVICE_NAME.equals(serviceName)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Resume analysis inner API only accepts task service");
        }
    }

    private void logCallbackDiagnostics(String status, CompleteDTO dto) {
        log.info("Resume analysis callback received, traceId={}, status={}, rawTextLength={}, rawTextHash={}, "
                        + "structuredJsonLength={}, structuredJsonHash={}, modelTraceLength={}, modelTraceHash={}, errorCategory={}",
                currentTraceId(), status, length(dto.getRawText()), sha256Prefix(dto.getRawText()),
                length(dto.getStructuredJson()), sha256Prefix(dto.getStructuredJson()),
                length(dto.getModelTrace()), sha256Prefix(dto.getModelTrace()),
                classifyError(dto.getErrorMessage()));
        if (StringUtils.hasText(dto.getErrorMessage())) {
            log.warn("Resume analysis callback error received, traceId={}, status={}, errorCategory={}, "
                            + "errorLength={}, errorHash={}, errorSummary={}",
                    currentTraceId(), status, classifyError(dto.getErrorMessage()),
                    length(dto.getErrorMessage()), sha256Prefix(dto.getErrorMessage()), safeLogText(dto.getErrorMessage()));
        }
    }

    private boolean isIdempotentDuplicate(ResumeAnalysisRecord latest, String status, CompleteDTO dto) {
        if (latest == null || !Objects.equals(latest.getParseStatus(), status)) {
            return false;
        }
        if (StringUtils.hasText(dto.getStructuredJson())
                && !Objects.equals(latest.getStructuredJson(), dto.getStructuredJson())) {
            return false;
        }
        if (StringUtils.hasText(dto.getRawText()) && !Objects.equals(latest.getRawText(), dto.getRawText())) {
            return false;
        }
        return true;
    }

    private String safeLogText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        normalized = EMAIL.matcher(normalized).replaceAll("***@***");
        normalized = CHINA_MOBILE.matcher(normalized).replaceAll("1**********");
        normalized = ID_CARD.matcher(normalized).replaceAll("******************");
        normalized = BEARER_TOKEN.matcher(normalized).replaceAll("Bearer ******");
        normalized = KEY_VALUE_SECRET.matcher(normalized).replaceAll("$1=******");
        normalized = URL_TOKEN.matcher(normalized).replaceAll("$1******");
        if (RESUME_PROMPT_FRAGMENT.matcher(normalized).find()) {
            return "[redacted-sensitive-fragment]";
        }
        if (normalized.length() <= CALLBACK_LOG_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, CALLBACK_LOG_MAX_LENGTH) + "...";
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }

    private String sha256Prefix(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }

    private String classifyError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "none";
        }
        String lower = errorMessage.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时")) {
            return "timeout";
        }
        if (lower.contains("unauthorized") || lower.contains("forbidden") || lower.contains("鉴权")
                || lower.contains("权限")) {
            return "auth";
        }
        if (lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("限流")) {
            return "rate_limit";
        }
        if (lower.contains("json") || lower.contains("schema") || lower.contains("parse")
                || lower.contains("解析")) {
            return "parse";
        }
        if (lower.contains("ai") || lower.contains("model") || lower.contains("llm")
                || lower.contains("模型")) {
            return "model";
        }
        return "unknown";
    }

    private String currentTraceId() {
        String traceId = MDC.get("traceId");
        return StringUtils.hasText(traceId) ? traceId : "";
    }

    private record RawTextPayload(String rawText, String originalFilename, String fileExt) {
    }
}
