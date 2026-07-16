package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.payload.ResumeOptimizePayload;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import com.codecoachai.resume.convert.ResumeConvert;
import com.codecoachai.resume.domain.dto.ApplyResumeOptimizeResultDTO;
import com.codecoachai.resume.domain.dto.ParsedResumeStructuredDTO;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeOptimizeRecord;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.vo.ApplyResumeOptimizeResultVO;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeAnalysisResultVO;
import com.codecoachai.resume.domain.vo.ResumeConfirmAnalysisVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.domain.vo.ResumeParseStatusVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.domain.vo.ResumeSearchReindexVO;
import com.codecoachai.resume.domain.vo.ResumeUploadVO;
import com.codecoachai.resume.export.ResumeUploadAdmissionGuard;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.feign.vo.ResumeOptimizeAiResponseVO;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeOptimizeRecordMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.ResumeMqDispatcher;
import com.codecoachai.resume.service.ResumeService;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeServiceImpl implements ResumeService {

    private static final String BIZ_TYPE_RESUME = "RESUME";
    private static final String SOURCE_TYPE_FILE_UPLOAD = "FILE_UPLOAD";
    private static final String DEFAULT_AI_RESUME_TITLE = "AI 解析简历";
    private static final String APPLY_MODE_CREATE_DRAFT = "CREATE_DRAFT";
    private static final String APPLY_MODE_STRUCTURED_PATCH = "STRUCTURED_PATCH";
    private static final int RAW_TEXT_SUMMARY_LENGTH = 500;
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "md", "txt");
    private static final Set<String> PATCHABLE_RESUME_FIELDS = Set.of(
            "title", "resumeName", "realName", "email", "phone", "targetPosition",
            "skillStack", "workExperience", "educationExperience", "summary");

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final ResumeOptimizeRecordMapper optimizeRecordMapper;
    private final TargetJobMapper targetJobMapper;
    private final FileFeignClient fileFeignClient;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Optional<ResumeMqDispatcher> resumeMqDispatcher;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;
    private final ResumeTextExtractProperties textExtractProperties;
    private final ResumeSearchSyncOutboxService resumeSearchSyncOutboxService;
    private final ResumeUploadAdmissionGuard uploadAdmissionGuard;

    @Override
    public List<ResumeListVO> listResumes() {
        return listResumes(null, null, null);
    }

    @Override
    public List<ResumeListVO> listResumes(Integer page, Integer size, String keyword) {
        Long userId = requireCurrentUserId();
        Integer limit = null;
        Long offset = null;
        if (page != null || size != null) {
            int effectivePage = page == null || page < 1 ? 1 : page;
            limit = size == null || size < 1 ? 20 : Math.min(size, 100);
            offset = (long) (effectivePage - 1) * limit;
        }
        return resumeMapper.selectResumeList(userId, likePattern(keyword), offset, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVO createResume(ResumeSaveDTO dto) {
        Long userId = requireCurrentUserId();
        Long count = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        Resume resume = new Resume();
        resume.setUserId(userId);
        applyResume(resume, dto);
        resume.setIsDefault(count == null || count == 0 ? CommonConstants.YES : CommonConstants.NO);
        resume.setStatus(CommonConstants.YES);
        resumeMapper.insert(resume);
        if (Objects.equals(resume.getIsDefault(), CommonConstants.YES)) {
            selectDefaultResumeForUser(userId, resume.getId());
        }
        syncResumeSearchAfterCommit(resume.getId(), userId, true);
        return toDetailVO(resume);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadVO uploadResume(MultipartFile file) {
        Long userId = requireCurrentUserId();
        validateUploadFile(file);
        long size = file.getSize();
        long startedAt = System.nanoTime();
        InnerFileUploadVO uploadedFile;
        try {
            uploadedFile = uploadAdmissionGuard.execute(size,
                    () -> FeignResultUtils.unwrap(fileFeignClient.upload(file, BIZ_TYPE_RESUME, userId)));
            log.debug("Resume upload completed userId={} fileSize={} durationMs={}",
                    userId, size, elapsedMillis(startedAt));
        } catch (RuntimeException ex) {
            logUploadFailure("Resume upload failed", userId, size, startedAt, ex);
            throw ex;
        }
        if (uploadedFile == null || uploadedFile.getFileId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历文件上传失败，请稍后重试");
        }

        registerUploadedFileRollbackCleanup(uploadedFile, userId);

        ResumeAnalysisRecord record = new ResumeAnalysisRecord();
        record.setUserId(userId);
        record.setFileId(uploadedFile.getFileId());
        record.setSourceType(SOURCE_TYPE_FILE_UPLOAD);
        record.setParseStatus(ResumeParseStatus.PENDING.getCode());
        analysisRecordMapper.insert(record);
        dispatchResumeParseAfterCommit(record, uploadedFile);
        MqDispatchReceipt receipt = null;
        boolean dispatched = true;

        ResumeUploadVO vo = new ResumeUploadVO();
        vo.setFileId(uploadedFile.getFileId());
        vo.setAnalysisRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setParseStatus(record.getParseStatus());
        vo.setOriginalFilename(uploadedFile.getOriginalFilename());
        vo.setFileSize(uploadedFile.getFileSize());
        vo.setFileExt(uploadedFile.getFileExt());
        vo.setMessage(dispatched ? "上传成功，已提交解析" : "上传成功，等待解析补偿");
        applyAsyncReceipt(vo, receipt);
        return vo;
    }

    @Override
    public ResumeParseStatusVO getParseStatus(Long analysisRecordId) {
        return toParseStatusVO(getOwnedAnalysisRecord(analysisRecordId, requireCurrentUserId()));
    }

    private MqDispatchReceipt dispatchResumeParse(ResumeAnalysisRecord record, InnerFileUploadVO uploadedFile) {
        ResumeParsePayload payload = ResumeParsePayload.builder()
                .resumeId(record.getId())
                .fileId(record.getFileId())
                .ossKey(uploadedFile.getStoragePath())
                .mimeType(uploadedFile.getMimeType())
                .userId(record.getUserId())
                .mode("deep")
                .build();
        MqDispatchReceipt receipt = resumeMqDispatcher
                .map(dispatcher -> dispatcher.dispatchParseWithReceipt(payload))
                .orElse(null);
        if (receipt == null) {
            record.setErrorMessage("异步解析任务暂时不可用，系统将通过补偿任务重试");
            analysisRecordMapper.updateById(record);
        }
        return receipt;
    }

    private void dispatchResumeParseAfterCommit(ResumeAnalysisRecord record, InnerFileUploadVO uploadedFile) {
        Runnable action = () -> dispatchResumeParseSafely(record, uploadedFile);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void dispatchResumeParseSafely(ResumeAnalysisRecord record, InnerFileUploadVO uploadedFile) {
        try {
            MqDispatchReceipt receipt = dispatchResumeParse(record, uploadedFile);
            if (receipt == null) {
                log.warn("Resume parse dispatch deferred recordId={} fileId={} reason=no_dispatch_receipt",
                        record == null ? null : record.getId(),
                        uploadedFile == null ? null : uploadedFile.getFileId());
            }
        } catch (RuntimeException ex) {
            if (record != null && record.getId() != null) {
                record.setErrorMessage("Resume parse dispatch failed after commit; waiting for compensation retry");
                analysisRecordMapper.updateById(record);
            }
            log.error("Resume parse after-commit dispatch failed recordId={} fileId={} failureType={}",
                    record == null ? null : record.getId(),
                    uploadedFile == null ? null : uploadedFile.getFileId(),
                    ex.getClass().getSimpleName());
        }
    }

    private void registerUploadedFileRollbackCleanup(InnerFileUploadVO uploadedFile, Long userId) {
        if (uploadedFile == null || uploadedFile.getFileId() == null || userId == null
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteUploadedFileQuietly(uploadedFile, userId, "resume_upload_tx_rollback");
                }
            }
        });
    }

    private void deleteUploadedFileQuietly(InnerFileUploadVO uploadedFile, Long userId, String reason) {
        if (uploadedFile == null || uploadedFile.getFileId() == null || userId == null) {
            return;
        }
        try {
            fileFeignClient.delete(uploadedFile.getFileId(), userId, BIZ_TYPE_RESUME);
            log.warn("Resume uploaded file cleaned after rollback fileId={} userId={} reason={}",
                    uploadedFile.getFileId(), userId, reason);
        } catch (Exception ex) {
            log.error("Resume uploaded file cleanup failed fileId={} userId={} reason={} failureType={}",
                    uploadedFile.getFileId(), userId, reason, ex.getClass().getSimpleName());
        }
    }

    private void logUploadFailure(String event, Long userId, long size, long startedAt, RuntimeException ex) {
        long durationMs = elapsedMillis(startedAt);
        if (ex instanceof BusinessException businessException
                && Objects.equals(businessException.getCode(), ErrorCode.RESUME_UPLOAD_BUSY.getCode())) {
            log.debug("{} userId={} fileSize={} durationMs={} exceptionType={}",
                    event, userId, size, durationMs, ex.getClass().getSimpleName());
            return;
        }
        log.warn("{} userId={} fileSize={} durationMs={} exceptionType={}",
                event, userId, size, durationMs, ex.getClass().getSimpleName());
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private MqDispatchReceipt dispatchResumeOptimize(ResumeOptimizeRecord record) {
        if (record == null || record.getId() == null) {
            return null;
        }
        ResumeOptimizePayload payload = ResumeOptimizePayload.builder()
                .optimizeRecordId(record.getId())
                .resumeId(record.getResumeId())
                .userId(record.getUserId())
                .build();
        return resumeMqDispatcher
                .map(dispatcher -> dispatcher.dispatchOptimizeWithReceipt(payload))
                .orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseStatusVO reparse(Long analysisRecordId) {
        Long userId = requireCurrentUserId();
        ResumeAnalysisRecord record = getOwnedAnalysisRecord(analysisRecordId, userId);
        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        if (status == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前简历解析状态不支持重新解析");
        }
        if (status == ResumeParseStatus.PENDING) {
            return toParseStatusVO(record);
        }
        if (status == ResumeParseStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历正在解析，请稍后再试");
        }
        if (status == ResumeParseStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历已解析成功，无需重新解析");
        }
        if (status == ResumeParseStatus.WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析结果待确认，请先确认或处理当前结果");
        }

        int affectedRows = analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .set(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.PENDING.getCode())
                .set(ResumeAnalysisRecord::getErrorMessage, null)
                .eq(ResumeAnalysisRecord::getId, analysisRecordId)
                .eq(ResumeAnalysisRecord::getUserId, userId)
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.FAILED.getCode()));
        ResumeAnalysisRecord latestRecord = getOwnedAnalysisRecord(analysisRecordId, userId);
        if (affectedRows > 0) {
            return toParseStatusVO(latestRecord);
        }

        ResumeParseStatus latestStatus = ResumeParseStatus.of(latestRecord.getParseStatus());
        if (latestStatus == ResumeParseStatus.PENDING) {
            return toParseStatusVO(latestRecord);
        }
        if (latestStatus == ResumeParseStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历正在解析中，请稍后再试");
        }
        if (latestStatus == ResumeParseStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历已解析成功，无需重新解析");
        }
        if (latestStatus == ResumeParseStatus.WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析结果待确认，请先确认或处理当前结果");
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重新解析状态更新失败，请稍后重试");
    }

    @Override
    public ResumeAnalysisResultVO getAnalysisResult(Long analysisRecordId) {
        ResumeAnalysisRecord record = getOwnedAnalysisRecord(analysisRecordId, requireCurrentUserId());
        ResumeAnalysisResultVO vo = new ResumeAnalysisResultVO();
        vo.setAnalysisRecordId(record.getId());
        vo.setFileId(record.getFileId());
        vo.setResumeId(record.getResumeId());
        vo.setParseStatus(record.getParseStatus());
        vo.setErrorMessage(safeResumeParseErrorMessage(record.getErrorMessage()));
        vo.setUpdatedAt(record.getUpdatedAt());

        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        if (status == ResumeParseStatus.WAIT_CONFIRM || status == ResumeParseStatus.SUCCESS) {
            vo.setStructuredJson(parseStructuredJsonObject(record.getStructuredJson()));
            vo.setRawTextSummary(summarizeRawText(record.getRawText()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeConfirmAnalysisVO confirmAnalysis(Long analysisRecordId) {
        Long userId = requireCurrentUserId();
        ResumeAnalysisRecord record = getOwnedAnalysisRecord(analysisRecordId, userId);
        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        if (status == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前简历解析状态无法确认");
        }
        if (status == ResumeParseStatus.SUCCESS) {
            return confirmAnalysisSuccess(record);
        }
        if (status == ResumeParseStatus.PENDING || status == ResumeParseStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析尚未完成，请稍后再确认");
        }
        if (status == ResumeParseStatus.FAILED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析失败，请先重新解析");
        }
        if (status != ResumeParseStatus.WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前简历解析状态无法确认");
        }

        ParsedResumeStructuredDTO structuredResume = parseStructuredResume(record.getStructuredJson());
        Resume resume = buildResumeFromStructured(record, structuredResume);
        resumeMapper.insert(resume);
        if (Objects.equals(resume.getIsDefault(), CommonConstants.YES)) {
            selectDefaultResumeForUser(userId, resume.getId());
        }
        insertProjects(resume.getId(), structuredResume.getProjectExperiences());

        int affectedRows = analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .set(ResumeAnalysisRecord::getResumeId, resume.getId())
                .set(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.SUCCESS.getCode())
                .set(ResumeAnalysisRecord::getErrorMessage, null)
                .eq(ResumeAnalysisRecord::getId, analysisRecordId)
                .eq(ResumeAnalysisRecord::getUserId, userId)
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.WAIT_CONFIRM.getCode()));
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历解析确认失败，请稍后重试");
        }
        syncResumeSearchAfterCommit(resume.getId(), userId, true);
        return toConfirmAnalysisVO(record.getId(), resume.getId(), ResumeParseStatus.SUCCESS.getCode(), toDetailVO(resume));
    }

    @Override
    public ResumeOptimizeSubmitVO optimizeResume(Long resumeId, ResumeOptimizeRequestDTO dto) {
        ResumeOptimizeRequestDTO request = dto == null ? new ResumeOptimizeRequestDTO() : dto;
        OptimizeContext context = transactionTemplate.execute(status -> createOptimizeRecord(resumeId, request));
        MqDispatchReceipt receipt = dispatchResumeOptimize(context.record());
        if (receipt != null) {
            ResumeOptimizeSubmitVO vo = toOptimizeSubmitVO(context.record(), null);
            applyAsyncReceipt(vo, receipt);
            return vo;
        }
        return executeOptimizeContext(context);
    }

    @Override
    public ResumeOptimizeSubmitVO executeOptimizeRecord(Long recordId) {
        ResumeOptimizeRecord record = getOptimizeRecord(recordId);
        if (ResumeOptimizeStatus.SUCCESS.getCode().equals(record.getOptimizeStatus())
                || ResumeOptimizeStatus.FAILED.getCode().equals(record.getOptimizeStatus())) {
            return toOptimizeSubmitVO(record, null);
        }
        try {
            ResumeOptimizeAiRequestDTO aiRequest = readOptimizeAiRequest(record);
            getOwnedResume(record.getResumeId(), record.getUserId());
            return executeOptimizeAi(record.getId(), aiRequest);
        } catch (RuntimeException ex) {
            ResumeOptimizeRecord failedRecord = transactionTemplate.execute(status ->
                    markOptimizeFailed(record.getId(), ex));
            return toOptimizeSubmitVO(failedRecord, null);
        }
    }

    private ResumeOptimizeSubmitVO executeOptimizeContext(OptimizeContext context) {
        return executeOptimizeAi(context.record().getId(), context.aiRequest());
    }

    private ResumeOptimizeSubmitVO executeOptimizeAi(Long recordId, ResumeOptimizeAiRequestDTO aiRequest) {
        try {
            ResumeOptimizeAiResponseVO response = FeignResultUtils.unwrap(aiFeignClient.optimizeResume(aiRequest));
            JsonNode resultJson = parseResultJson(response == null ? null : response.getResultJson());
            ResumeOptimizeRecord latestRecord = transactionTemplate.execute(status ->
                    markOptimizeSuccess(recordId, resultJson.toString(),
                            response == null ? null : response.getAiCallLogId()));
            return toOptimizeSubmitVO(latestRecord, resultJson);
        } catch (RuntimeException ex) {
            ResumeOptimizeRecord failedRecord = transactionTemplate.execute(status ->
                    markOptimizeFailed(recordId, ex));
            return toOptimizeSubmitVO(failedRecord, null);
        }
    }

    @Override
    public List<ResumeOptimizeRecordVO> listOptimizeRecords(Long resumeId) {
        Long userId = requireCurrentUserId();
        getOwnedResume(resumeId, userId);
        return optimizeRecordMapper.selectList(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                        .eq(ResumeOptimizeRecord::getResumeId, resumeId)
                        .eq(ResumeOptimizeRecord::getUserId, userId)
                        .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                        .orderByDesc(ResumeOptimizeRecord::getCreatedAt))
                .stream()
                .map(this::toOptimizeRecordVO)
                .toList();
    }

    @Override
    public ResumeOptimizeDetailVO getOptimizeRecordDetail(Long recordId) {
        ResumeOptimizeRecord record = getOwnedOptimizeRecord(recordId, requireCurrentUserId());
        return toOptimizeDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyResumeOptimizeResultVO applyOptimizeResult(Long recordId, ApplyResumeOptimizeResultDTO dto) {
        Long userId = requireCurrentUserId();
        String applyMode = dto == null || !StringUtils.hasText(dto.getApplyMode())
                ? APPLY_MODE_CREATE_DRAFT : dto.getApplyMode();
        if (!APPLY_MODE_CREATE_DRAFT.equals(applyMode) && !APPLY_MODE_STRUCTURED_PATCH.equals(applyMode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前应用方式暂不支持");
        }
        ResumeOptimizeRecord record = getOwnedOptimizeRecord(recordId, userId);
        if (!ResumeOptimizeStatus.SUCCESS.getCode().equals(record.getOptimizeStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只有优化成功的记录可以应用");
        }
        if (record.getResumeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "优化记录缺少来源简历");
        }
        JsonNode resultJson = parseResultJson(record.getResultJson());
        Resume sourceResume = getOwnedResume(record.getResumeId(), userId);
        LocalDateTime appliedAt = LocalDateTime.now();
        Resume draft = copyResumeDraft(sourceResume, record.getId(), appliedAt);
        StructuredApplyResult applyResult = applyStructuredPatches(draft, sourceResume, resultJson, dto);
        resumeMapper.insert(draft);
        copyProjects(sourceResume.getId(), draft.getId());
        syncResumeSearchAfterCommit(draft.getId(), userId, true);

        ApplyResumeOptimizeResultVO vo = new ApplyResumeOptimizeResultVO();
        vo.setSourceResumeId(sourceResume.getId());
        vo.setSourceOptimizeRecordId(record.getId());
        vo.setNewResumeId(draft.getId());
        vo.setAppliedAt(appliedAt);
        vo.setApplyMode(applyMode);
        vo.setAppliedFields(applyResult.appliedFields());
        vo.setSkippedFields(applyResult.skippedFields());
        vo.setFieldDiff(applyResult.fieldDiff());
        vo.setMessage(applyResult.appliedFields().isEmpty()
                ? "AI 优化建议已关联到新的简历草稿，请手动编辑确认。"
                : "已将选中的 AI 优化字段应用到新的简历草稿。");
        vo.setWarnings(applyResult.warnings());
        vo.setResumeDetail(toDetailVO(draft));
        return vo;
    }

    @Override
    public ResumeDetailVO getResume(Long id) {
        return toDetailVO(getOwnedResumeForRead(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVO updateResume(Long id, ResumeSaveDTO dto) {
        Resume resume = getOwnedResume(id);
        applyResume(resume, dto);
        resumeMapper.updateById(resume);
        syncResumeSearchAfterCommit(resume.getId(), resume.getUserId(), true);
        return toDetailVO(resume);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResume(Long id) {
        Resume resume = getOwnedResume(id);
        lockOwnedResume(resume);
        projectMapper.delete(new LambdaQueryWrapper<ResumeProject>().eq(ResumeProject::getResumeId, id));
        resumeMapper.deleteById(resume.getId());
        syncResumeSearchAfterCommit(resume.getId(), resume.getUserId(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVO setDefault(Long id) {
        Resume resume = getOwnedResume(id);
        Long userId = requireCurrentUserId();
        return toDetailVO(selectDefaultResumeForUser(userId, resume.getId()));
    }

    private Resume selectDefaultResumeForUser(Long userId, Long resumeId) {
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .set(Resume::getIsDefault, CommonConstants.NO)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .eq(Resume::getIsDefault, CommonConstants.YES)
                .ne(Resume::getId, resumeId));
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .set(Resume::getIsDefault, CommonConstants.YES)
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO));
        Resume latest = getOwnedResume(resumeId, userId);
        if (!Objects.equals(latest.getIsDefault(), CommonConstants.YES)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "默认简历设置失败，请稍后重试");
        }
        return latest;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeProjectVO createProject(Long resumeId, ResumeProjectSaveDTO dto) {
        Long userId = requireCurrentUserId();
        Resume resume = getOwnedResume(resumeId, userId);
        lockOwnedResume(resume);
        ResumeProject project = new ResumeProject();
        project.setResumeId(resumeId);
        applyProject(project, dto);
        projectMapper.insert(project);
        syncResumeSearchAfterCommit(resumeId, userId, true);
        return ResumeConvert.toProjectVO(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeProjectVO updateProject(Long resumeId, Long projectId, ResumeProjectSaveDTO dto) {
        Long userId = requireCurrentUserId();
        Resume resume = getOwnedResume(resumeId, userId);
        lockOwnedResume(resume);
        ResumeProject project = getProject(resumeId, projectId);
        applyProject(project, dto);
        projectMapper.updateById(project);
        syncResumeSearchAfterCommit(resumeId, userId, true);
        return ResumeConvert.toProjectVO(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeProjectVO updateProject(Long projectId, ResumeProjectSaveDTO dto) {
        ResumeProject project = getOwnedProject(projectId);
        applyProject(project, dto);
        projectMapper.updateById(project);
        syncResumeSearchAfterCommit(project.getResumeId(), LoginUserContext.getUserId(), true);
        return ResumeConvert.toProjectVO(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long resumeId, Long projectId) {
        Resume resume = getOwnedResume(resumeId);
        lockOwnedResume(resume);
        ResumeProject project = getProject(resumeId, projectId);
        projectMapper.deleteById(project.getId());
        syncResumeSearchAfterCommit(resumeId, LoginUserContext.getUserId(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long projectId) {
        ResumeProject project = getOwnedProject(projectId);
        projectMapper.deleteById(project.getId());
        syncResumeSearchAfterCommit(project.getResumeId(), LoginUserContext.getUserId(), true);
    }

    @Override
    public Map<String, Object> getSearchDocument(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null || Objects.equals(resume.getDeleted(), CommonConstants.YES)) {
            return null;
        }
        List<ResumeProjectVO> resumeProjects = projects(id);
        List<String> projectHighlights = resumeProjects.stream()
                .map(project -> firstText(
                        project.getHighlights(),
                        project.getDescription(),
                        project.getResponsibility(),
                        project.getProjectName()))
                .filter(StringUtils::hasText)
                .toList();
        List<String> skills = splitSearchValues(resume.getSkillStack());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("docId", String.valueOf(resume.getId()));
        doc.put("userId", String.valueOf(resume.getUserId()));
        doc.put("title", resume.getTitle());
        doc.put("name", firstText(resume.getTitle(), resume.getRealName()));
        doc.put("summary", resume.getSummary());
        doc.put("skills", skills);
        doc.put("targetPosition", resume.getTargetPosition());
        doc.put("education", resume.getEducationExperience());
        doc.put("workExperience", resume.getWorkExperience());
        doc.put("projectHighlights", projectHighlights);
        doc.put("status", resume.getStatus() == null ? null : String.valueOf(resume.getStatus()));
        doc.put("createdAt", toEpochMillis(resume.getCreatedAt()));
        doc.put("updatedAt", toEpochMillis(resume.getUpdatedAt()));
        doc.put("syncedAt", System.currentTimeMillis());
        doc.put("content", String.join("\n", nonBlankValues(
                resume.getTitle(),
                resume.getRealName(),
                resume.getTargetPosition(),
                resume.getSkillStack(),
                resume.getWorkExperience(),
                resume.getEducationExperience(),
                resume.getSummary(),
                String.join("\n", projectHighlights))));
        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSearchReindexVO reindexSearchDocuments(Long afterId, Integer batchSize) {
        long cursor = afterId == null || afterId < 0 ? 0L : afterId;
        int effectiveBatchSize = batchSize == null || batchSize < 1 ? 100 : Math.min(batchSize, 500);
        List<Resume> rows = resumeMapper.selectActiveAfter(cursor, effectiveBatchSize + 1);
        List<Resume> batch = rows == null
                ? List.of()
                : rows.stream().limit(effectiveBatchSize).toList();
        for (Resume resume : batch) {
            resumeSearchSyncOutboxService.enqueue(
                    resume.getId(), resume.getUserId(), ResumeSearchSyncOutboxService.OP_UPSERT);
        }
        ResumeSearchReindexVO vo = new ResumeSearchReindexVO();
        vo.setAfterId(cursor);
        vo.setBatchSize(effectiveBatchSize);
        vo.setQueued(batch.size());
        vo.setHasMore(rows != null && rows.size() > effectiveBatchSize);
        vo.setNextAfterId(batch.isEmpty() ? cursor : batch.get(batch.size() - 1).getId());
        return vo;
    }

    @Override
    public InnerResumeDetailVO getInnerResume(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历不存在或已不可用");
        }
        return ResumeConvert.toInnerVO(resume, projects(id));
    }

    @Override
    public InnerResumeDetailVO getDefaultInnerResume() {
        Long userId = requireCurrentUserId();
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .eq(Resume::getIsDefault, CommonConstants.YES)
                .last("limit 1"));
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "默认简历不存在，请先选择或创建默认简历");
        }
        return ResumeConvert.toInnerVO(resume, projects(resume.getId()));
    }

    @Override
    public InnerResumeOptimizeRecordVO getInnerOptimizeRecord(Long recordId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectOne(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                .eq(ResumeOptimizeRecord::getId, recordId)
                .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历优化记录不存在或已不可用");
        }
        InnerResumeOptimizeRecordVO vo = new InnerResumeOptimizeRecordVO();
        vo.setOptimizeRecordId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetJobId(record.getTargetJobId());
        vo.setTargetPosition(record.getTargetPosition());
        vo.setExperienceYears(record.getExperienceYears());
        vo.setIndustryDirection(record.getIndustryDirection());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        vo.setResultJson(record.getResultJson());
        vo.setErrorMessage(safeResumeOptimizeErrorMessage(record.getErrorMessage()));
        return vo;
    }

    @Override
    public ResumeOptimizeRecordAgentEvidenceVO getOptimizeRecordEvidence(Long userId, Long recordId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectOne(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                .eq(ResumeOptimizeRecord::getId, recordId)
                .eq(ResumeOptimizeRecord::getUserId, userId)
                .eq(ResumeOptimizeRecord::getOptimizeStatus, ResumeOptimizeStatus.SUCCESS.getCode())
                .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历优化记录不存在或未成功生成");
        }
        ResumeOptimizeRecordAgentEvidenceVO vo = new ResumeOptimizeRecordAgentEvidenceVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetJobId(record.getTargetJobId());
        vo.setStatus(record.getOptimizeStatus());
        vo.setOptimizedAt(record.getUpdatedAt());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private ResumeDetailVO toDetailVO(Resume resume) {
        return ResumeConvert.toDetailVO(resume, projects(resume.getId()));
    }

    private Resume copyResumeDraft(Resume source, Long optimizeRecordId, LocalDateTime appliedAt) {
        Resume draft = new Resume();
        draft.setUserId(source.getUserId());
        draft.setTitle(buildDraftTitle(source.getTitle()));
        draft.setRealName(source.getRealName());
        draft.setEmail(source.getEmail());
        draft.setPhone(source.getPhone());
        draft.setTargetPosition(source.getTargetPosition());
        draft.setSkillStack(source.getSkillStack());
        draft.setWorkExperience(source.getWorkExperience());
        draft.setEducationExperience(source.getEducationExperience());
        draft.setSummary(source.getSummary());
        draft.setIsDefault(CommonConstants.NO);
        draft.setStatus(source.getStatus() == null ? CommonConstants.YES : source.getStatus());
        draft.setSourceResumeId(source.getId());
        draft.setSourceOptimizeRecordId(optimizeRecordId);
        draft.setAppliedAt(appliedAt);
        return draft;
    }

    private String buildDraftTitle(String sourceTitle) {
        String title = StringUtils.hasText(sourceTitle) ? sourceTitle : DEFAULT_AI_RESUME_TITLE;
        String suffix = " - AI优化草稿";
        int maxLength = 128;
        if (title.endsWith(suffix)) {
            return title;
        }
        int allowedTitleLength = maxLength - suffix.length();
        if (title.length() > allowedTitleLength) {
            title = title.substring(0, allowedTitleLength);
        }
        return title + suffix;
    }

    private void copyProjects(Long sourceResumeId, Long draftResumeId) {
        List<ResumeProject> projects = projectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, sourceResumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeProject::getSortOrder)
                .orderByAsc(ResumeProject::getSort)
                .orderByDesc(ResumeProject::getUpdatedAt));
        for (ResumeProject source : projects) {
            ResumeProject draft = new ResumeProject();
            draft.setResumeId(draftResumeId);
            draft.setProjectName(source.getProjectName());
            draft.setProjectPeriod(source.getProjectPeriod());
            draft.setProjectBackground(source.getProjectBackground());
            draft.setRole(source.getRole());
            draft.setTechStack(source.getTechStack());
            draft.setResponsibility(source.getResponsibility());
            draft.setCoreFeatures(source.getCoreFeatures());
            draft.setTechnicalDifficulties(source.getTechnicalDifficulties());
            draft.setOptimizationResults(source.getOptimizationResults());
            draft.setDescription(source.getDescription());
            draft.setHighlights(source.getHighlights());
            draft.setSort(source.getSort());
            draft.setSortOrder(source.getSortOrder());
            projectMapper.insert(draft);
        }
    }

    private ResumeParseStatusVO toParseStatusVO(ResumeAnalysisRecord record) {
        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        ResumeParseStatusVO vo = new ResumeParseStatusVO();
        vo.setAnalysisRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setFileId(record.getFileId());
        vo.setParseStatus(record.getParseStatus());
        vo.setErrorMessage(safeResumeParseErrorMessage(record.getErrorMessage()));
        vo.setMessage(status == null ? "简历解析状态异常" : status.getMessage());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private void applyAsyncReceipt(ResumeUploadVO vo, MqDispatchReceipt receipt) {
        if (vo == null || receipt == null) {
            return;
        }
        vo.setAsyncMessageId(receipt.getMessageId());
        vo.setAsyncTraceId(receipt.getTraceId());
        vo.setAsyncBizType(receipt.getBizType());
        vo.setAsyncBizId(receipt.getBizId());
        vo.setAsyncSendStatus(receipt.getSendStatus());
    }

    private void applyAsyncReceipt(ResumeOptimizeSubmitVO vo, MqDispatchReceipt receipt) {
        if (vo == null || receipt == null) {
            return;
        }
        vo.setAsyncMessageId(receipt.getMessageId());
        vo.setAsyncTraceId(receipt.getTraceId());
        vo.setAsyncBizType(receipt.getBizType());
        vo.setAsyncBizId(receipt.getBizId());
        vo.setAsyncSendStatus(receipt.getSendStatus());
    }

    private ResumeConfirmAnalysisVO confirmAnalysisSuccess(ResumeAnalysisRecord record) {
        if (record.getResumeId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Confirmed resume id is missing");
        }
        Resume resume = getOwnedResume(record.getResumeId());
        return toConfirmAnalysisVO(record.getId(), resume.getId(), ResumeParseStatus.SUCCESS.getCode(), toDetailVO(resume));
    }

    private ResumeConfirmAnalysisVO toConfirmAnalysisVO(Long analysisRecordId, Long resumeId, String parseStatus,
                                                       ResumeDetailVO resume) {
        ResumeConfirmAnalysisVO vo = new ResumeConfirmAnalysisVO();
        vo.setAnalysisRecordId(analysisRecordId);
        vo.setResumeId(resumeId);
        vo.setParseStatus(parseStatus);
        vo.setResume(resume);
        return vo;
    }

    private void syncResumeSearchAfterCommit(Long resumeId, Long userId, boolean upsert) {
        resumeSearchSyncOutboxService.enqueue(
                resumeId,
                userId,
                upsert ? ResumeSearchSyncOutboxService.OP_UPSERT : ResumeSearchSyncOutboxService.OP_DELETE);
    }

    private ParsedResumeStructuredDTO parseStructuredResume(String structuredJson) {
        JsonNode root = parseStructuredJsonObject(structuredJson);
        requireObjectField(root, "basicInfo");
        requireTextField(root, "targetPosition");
        requireArrayField(root, "skills");
        requireArrayField(root, "workExperiences");
        requireArrayField(root, "projectExperiences");
        requireArrayField(root, "educationExperiences");
        for (JsonNode project : root.path("projectExperiences")) {
            if (project == null || !project.isObject()) {
                throw invalidStructuredJson();
            }
            requireArrayField(project, "techStack");
            requireArrayField(project, "responsibilities");
            requireArrayField(project, "technicalDifficulties");
            requireArrayField(project, "achievements");
        }
        try {
            return objectMapper.treeToValue(root, ParsedResumeStructuredDTO.class);
        } catch (Exception ex) {
            throw invalidStructuredJson();
        }
    }

    private JsonNode parseStructuredJsonObject(String structuredJson) {
        if (!StringUtils.hasText(structuredJson)) {
            throw invalidStructuredJson();
        }
        try {
            JsonNode root = objectMapper.readTree(structuredJson);
            if (root == null || !root.isObject()) {
                throw invalidStructuredJson();
            }
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidStructuredJson();
        }
    }

    private void requireObjectField(JsonNode root, String fieldName) {
        if (!root.has(fieldName) || !root.path(fieldName).isObject()) {
            throw invalidStructuredJson();
        }
    }

    private void requireTextField(JsonNode root, String fieldName) {
        if (!root.has(fieldName) || !root.path(fieldName).isTextual()) {
            throw invalidStructuredJson();
        }
    }

    private void requireArrayField(JsonNode root, String fieldName) {
        if (!root.has(fieldName) || !root.path(fieldName).isArray()) {
            throw invalidStructuredJson();
        }
    }

    private BusinessException invalidStructuredJson() {
        return new BusinessException(ErrorCode.PARAM_ERROR, "简历解析结构化结果格式异常");
    }

    private Resume buildResumeFromStructured(ResumeAnalysisRecord record, ParsedResumeStructuredDTO structuredResume) {
        Long existingCount = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, record.getUserId()));
        ParsedResumeStructuredDTO.BasicInfo basicInfo = structuredResume.getBasicInfo();
        String realName = basicInfo == null ? null : basicInfo.getName();
        String targetPosition = structuredResume.getTargetPosition();
        Resume resume = new Resume();
        resume.setUserId(record.getUserId());
        resume.setTitle(buildResumeTitle(realName, targetPosition));
        resume.setRealName(realName);
        resume.setEmail(basicInfo == null ? null : basicInfo.getEmail());
        resume.setPhone(basicInfo == null ? null : basicInfo.getPhone());
        resume.setTargetPosition(targetPosition);
        resume.setSkillStack(joinValues(structuredResume.getSkills(), "、"));
        resume.setWorkExperience(toCompactJson(structuredResume.getWorkExperiences()));
        resume.setEducationExperience(toCompactJson(structuredResume.getEducationExperiences()));
        resume.setSummary(buildResumeSummary(realName, targetPosition, resume.getSkillStack()));
        resume.setIsDefault(existingCount == null || existingCount == 0 ? CommonConstants.YES : CommonConstants.NO);
        resume.setStatus(CommonConstants.YES);
        return resume;
    }

    private void insertProjects(Long resumeId, List<ParsedResumeStructuredDTO.ProjectExperience> projectExperiences) {
        if (projectExperiences == null || projectExperiences.isEmpty()) {
            return;
        }
        int sort = 0;
        for (ParsedResumeStructuredDTO.ProjectExperience item : projectExperiences) {
            if (item == null || !StringUtils.hasText(item.getProjectName())) {
                continue;
            }
            ResumeProject project = new ResumeProject();
            project.setResumeId(resumeId);
            project.setProjectName(item.getProjectName());
            project.setProjectPeriod(item.getPeriod());
            project.setDescription(item.getDescription());
            project.setTechStack(joinValues(item.getTechStack(), "、"));
            project.setResponsibility(joinValues(item.getResponsibilities(), "\n"));
            project.setTechnicalDifficulties(joinValues(item.getTechnicalDifficulties(), "\n"));
            project.setHighlights(joinValues(item.getAchievements(), "\n"));
            project.setSort(sort);
            project.setSortOrder(sort);
            projectMapper.insert(project);
            sort++;
        }
    }

    private String buildResumeTitle(String realName, String targetPosition) {
        if (StringUtils.hasText(realName) && StringUtils.hasText(targetPosition)) {
            return realName + " - " + targetPosition;
        }
        if (StringUtils.hasText(realName)) {
            return realName + " - " + DEFAULT_AI_RESUME_TITLE;
        }
        if (StringUtils.hasText(targetPosition)) {
            return targetPosition;
        }
        return DEFAULT_AI_RESUME_TITLE;
    }

    private String buildResumeSummary(String realName, String targetPosition, String skillStack) {
        StringBuilder summary = new StringBuilder(DEFAULT_AI_RESUME_TITLE);
        if (StringUtils.hasText(realName)) {
            summary.append(": ").append(realName);
        }
        if (StringUtils.hasText(targetPosition)) {
            summary.append(", target ").append(targetPosition);
        }
        if (StringUtils.hasText(skillStack)) {
            summary.append(", skills ").append(skillStack);
        }
        return summary.toString();
    }

    private String joinValues(List<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + delimiter + right)
                .orElse(null);
        return StringUtils.hasText(joined) ? joined : null;
    }

    private String toCompactJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析结构化结果格式异常");
        }
    }

    private String summarizeRawText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        return rawText.length() <= RAW_TEXT_SUMMARY_LENGTH ? rawText : rawText.substring(0, RAW_TEXT_SUMMARY_LENGTH);
    }

    private OptimizeContext createOptimizeRecord(Long resumeId, ResumeOptimizeRequestDTO dto) {
        Long userId = requireCurrentUserId();
        ResumeOptimizeRequestDTO request = normalizeOptimizeRequest(dto);
        Resume resume = getOwnedResume(resumeId, userId);
        List<ResumeProject> selectedProjects = selectProjects(resumeId, request.getSelectedProjectIds());
        Long targetJobId = resolveOptimizeTargetJobId(request.getTargetJobId(), userId);
        String targetPosition = firstText(request.getTargetPosition(), resume.getTargetPosition());

        ResumeOptimizeRecord record = new ResumeOptimizeRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setTargetJobId(targetJobId);
        record.setTargetPosition(targetPosition);
        record.setExperienceYears(request.getExperienceYears());
        record.setIndustryDirection(request.getIndustryDirection());
        record.setOptimizeStatus(ResumeOptimizeStatus.PROCESSING.getCode());
        optimizeRecordMapper.insert(record);

        ResumeOptimizeAiRequestDTO aiRequest = buildOptimizeAiRequest(record.getId(), userId, resume, selectedProjects,
                targetPosition, request);
        record.setRequestJson(toJson(aiRequest));
        optimizeRecordMapper.updateById(record);
        return new OptimizeContext(resume, selectedProjects, record, aiRequest);
    }

    private Long resolveOptimizeTargetJobId(Long targetJobId, Long userId) {
        if (targetJobId == null) {
            return null;
        }
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target job does not exist or is unavailable");
        }
        return targetJob.getId();
    }

    private ResumeOptimizeRequestDTO normalizeOptimizeRequest(ResumeOptimizeRequestDTO source) {
        ResumeOptimizeRequestDTO target = new ResumeOptimizeRequestDTO();
        if (source == null) {
            return target;
        }
        target.setTargetJobId(source.getTargetJobId());
        target.setTargetPosition(repairPossibleMojibake(source.getTargetPosition()));
        target.setExperienceYears(source.getExperienceYears());
        target.setIndustryDirection(repairPossibleMojibake(source.getIndustryDirection()));
        target.setTargetCompany(repairPossibleMojibake(source.getTargetCompany()));
        target.setExtraRequirements(repairPossibleMojibake(source.getExtraRequirements()));
        target.setOptimizeFocus(repairPossibleMojibake(source.getOptimizeFocus()));
        target.setSelectedProjectIds(source.getSelectedProjectIds());
        return target;
    }

    private String repairPossibleMojibake(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String best = value;
        int bestScore = mojibakeScore(value);
        String fromLatin1 = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        int latin1Score = mojibakeScore(fromLatin1);
        if (latin1Score + 4 < bestScore) {
            best = fromLatin1;
            bestScore = latin1Score;
        }
        String fromGb18030 = new String(value.getBytes(GB18030), StandardCharsets.UTF_8);
        int gbScore = mojibakeScore(fromGb18030);
        if (gbScore + 4 < bestScore) {
            best = fromGb18030;
        }
        return best;
    }

    private int mojibakeScore(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        int score = 0;
        int cjkCount = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\uFFFD' || (ch >= '\uE000' && ch <= '\uF8FF')) {
                score += 8;
            } else if ((ch >= '\u0080' && ch <= '\u009F') || "ÃÂâ¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿€".indexOf(ch) >= 0) { // mojibake-check-ignore-line: intentional suspicious-character sample.
                score += 4;
            } else if ("锛銆鐨绠鍘寮鍚庣璇搴撳悜閲".indexOf(ch) >= 0) { // mojibake-check-ignore-line: intentional suspicious-character sample.
                score += 2;
            }
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                cjkCount++;
            }
        }
        return score - Math.min(cjkCount, 10);
    }

    private List<ResumeProject> selectProjects(Long resumeId, List<Long> selectedProjectIds) {
        LambdaQueryWrapper<ResumeProject> query = new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, resumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO);
        if (selectedProjectIds != null && !selectedProjectIds.isEmpty()) {
            Set<Long> distinctIds = new LinkedHashSet<>(selectedProjectIds);
            query.in(ResumeProject::getId, distinctIds);
            List<ResumeProject> projects = projectMapper.selectList(query.orderByAsc(ResumeProject::getSortOrder)
                    .orderByAsc(ResumeProject::getSort)
                    .orderByDesc(ResumeProject::getUpdatedAt));
            if (projects.size() != distinctIds.size()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Selected projects do not belong to the resume");
            }
            return projects;
        }
        return projectMapper.selectList(query.orderByAsc(ResumeProject::getSortOrder)
                .orderByAsc(ResumeProject::getSort)
                .orderByDesc(ResumeProject::getUpdatedAt));
    }

    private ResumeOptimizeAiRequestDTO buildOptimizeAiRequest(Long optimizeRecordId, Long userId, Resume resume,
                                                             List<ResumeProject> projects, String targetPosition,
                                                             ResumeOptimizeRequestDTO dto) {
        ResumeOptimizeAiRequestDTO request = new ResumeOptimizeAiRequestDTO();
        request.setOptimizeRecordId(optimizeRecordId);
        request.setUserId(userId);
        request.setResumeId(resume.getId());
        request.setTargetJobId(dto.getTargetJobId());
        request.setTargetPosition(targetPosition);
        request.setExperienceYears(dto.getExperienceYears());
        request.setIndustryDirection(dto.getIndustryDirection());
        request.setTargetCompany(dto.getTargetCompany());
        request.setExtraRequirements(dto.getExtraRequirements());
        request.setOptimizeFocus(dto.getOptimizeFocus());
        request.setResume(toResumeSnapshot(resume));
        request.setProjects(projects.stream().map(this::toProjectSnapshot).toList());
        return request;
    }

    private ResumeOptimizeAiRequestDTO.ResumeSnapshot toResumeSnapshot(Resume resume) {
        ResumeOptimizeAiRequestDTO.ResumeSnapshot snapshot = new ResumeOptimizeAiRequestDTO.ResumeSnapshot();
        snapshot.setRealName(resume.getRealName());
        snapshot.setTargetPosition(resume.getTargetPosition());
        snapshot.setSkillStack(resume.getSkillStack());
        snapshot.setWorkExperience(resume.getWorkExperience());
        snapshot.setEducationExperience(resume.getEducationExperience());
        snapshot.setSummary(resume.getSummary());
        return snapshot;
    }

    private ResumeOptimizeAiRequestDTO.ProjectSnapshot toProjectSnapshot(ResumeProject project) {
        ResumeOptimizeAiRequestDTO.ProjectSnapshot snapshot = new ResumeOptimizeAiRequestDTO.ProjectSnapshot();
        snapshot.setProjectId(project.getId());
        snapshot.setProjectName(project.getProjectName());
        snapshot.setProjectBackground(project.getProjectBackground());
        snapshot.setRole(project.getRole());
        snapshot.setTechStack(project.getTechStack());
        snapshot.setResponsibility(project.getResponsibility());
        snapshot.setTechnicalDifficulties(project.getTechnicalDifficulties());
        snapshot.setOptimizationResults(project.getOptimizationResults());
        snapshot.setDescription(project.getDescription());
        snapshot.setHighlights(project.getHighlights());
        return snapshot;
    }

    private ResumeOptimizeRecord markOptimizeSuccess(Long recordId, String resultJson, Long aiCallLogId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectById(recordId);
        record.setResultJson(resultJson);
        record.setAiCallLogId(aiCallLogId);
        record.setOptimizeStatus(ResumeOptimizeStatus.SUCCESS.getCode());
        record.setErrorMessage(null);
        optimizeRecordMapper.updateById(record);
        ResumeOptimizeRecord latestRecord = optimizeRecordMapper.selectById(recordId);
        completeAgentResumeOptimizeAfterCommit(latestRecord);
        return latestRecord;
    }

    private void completeAgentResumeOptimizeAfterCommit(ResumeOptimizeRecord record) {
        if (record == null || record.getUserId() == null || record.getTargetJobId() == null || record.getId() == null) {
            return;
        }
        Runnable action = () -> agentBusinessActionNotifier.completeResumeOptimize(
                record.getUserId(), record.getTargetJobId(), record.getId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private ResumeOptimizeRecord markOptimizeFailed(Long recordId, RuntimeException ex) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectById(recordId);
        record.setOptimizeStatus(ResumeOptimizeStatus.FAILED.getCode());
        record.setErrorMessage(resumeOptimizeFailureMessage());
        optimizeRecordMapper.updateById(record);
        return optimizeRecordMapper.selectById(recordId);
    }

    private ResumeOptimizeSubmitVO toOptimizeSubmitVO(ResumeOptimizeRecord record, JsonNode resultJson) {
        ResumeOptimizeSubmitVO vo = new ResumeOptimizeSubmitVO();
        vo.setOptimizeRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetJobId(record.getTargetJobId());
        vo.setAiCallLogId(record.getAiCallLogId());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        fillOptimizeSubmitResult(vo, resultJson == null ? parseNullableJson(record.getResultJson()) : resultJson);
        vo.setErrorMessage(safeResumeOptimizeErrorMessage(record.getErrorMessage()));
        return vo;
    }

    private ResumeOptimizeRecordVO toOptimizeRecordVO(ResumeOptimizeRecord record) {
        JsonNode resultJson = parseNullableJson(record.getResultJson());
        ResumeOptimizeRecordVO vo = new ResumeOptimizeRecordVO();
        vo.setOptimizeRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetJobId(record.getTargetJobId());
        vo.setTargetPosition(record.getTargetPosition());
        vo.setExperienceYears(record.getExperienceYears());
        vo.setIndustryDirection(record.getIndustryDirection());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        vo.setSummary(textField(resultJson, "overallComment"));
        vo.setOverallComment(textField(resultJson, "overallComment"));
        vo.setErrorMessage(safeResumeOptimizeErrorMessage(record.getErrorMessage()));
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private ResumeOptimizeDetailVO toOptimizeDetailVO(ResumeOptimizeRecord record) {
        ResumeOptimizeDetailVO vo = new ResumeOptimizeDetailVO();
        JsonNode resultJson = parseNullableJson(record.getResultJson());
        vo.setOptimizeRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetJobId(record.getTargetJobId());
        vo.setTargetPosition(record.getTargetPosition());
        vo.setExperienceYears(record.getExperienceYears());
        vo.setIndustryDirection(record.getIndustryDirection());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        fillOptimizeDetailResult(vo, resultJson);
        vo.setFieldPatches(extractFieldPatches(resultJson, null));
        vo.setErrorMessage(safeResumeOptimizeErrorMessage(record.getErrorMessage()));
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private void fillOptimizeSubmitResult(ResumeOptimizeSubmitVO vo, JsonNode resultJson) {
        vo.setOverallScore(integerField(resultJson, "overallScore"));
        vo.setOverallComment(textField(resultJson, "overallComment"));
        vo.setRewriteSuggestions(jsonField(resultJson, "rewriteSuggestions"));
        vo.setRiskWarnings(jsonField(resultJson, "riskWarnings"));
        vo.setPossibleInterviewQuestions(jsonField(resultJson, "possibleInterviewQuestions"));
        vo.setNextActions(jsonField(resultJson, "nextActions"));
    }

    private void fillOptimizeDetailResult(ResumeOptimizeDetailVO vo, JsonNode resultJson) {
        String overallComment = textField(resultJson, "overallComment");
        vo.setSummary(overallComment);
        vo.setOverallScore(integerField(resultJson, "overallScore"));
        vo.setOverallComment(overallComment);
        vo.setTargetPositionMatch(jsonField(resultJson, "targetPositionMatch"));
        vo.setSectionScores(jsonField(resultJson, "sectionScores"));
        vo.setProblems(jsonField(resultJson, "problems"));
        vo.setRewriteSuggestions(jsonField(resultJson, "rewriteSuggestions"));
        vo.setRiskWarnings(jsonField(resultJson, "riskWarnings"));
        vo.setPossibleInterviewQuestions(jsonField(resultJson, "possibleInterviewQuestions"));
        vo.setNextActions(jsonField(resultJson, "nextActions"));
    }

    private StructuredApplyResult applyStructuredPatches(Resume draft, Resume source, JsonNode resultJson,
                                                        ApplyResumeOptimizeResultDTO dto) {
        ObjectNode patches = extractFieldPatches(resultJson,
                dto == null ? null : dto.getFieldPatches(),
                dto == null ? null : dto.getSelectedSuggestionIndexes());
        Set<String> selectedFields = normalizeSelectedFields(dto, patches);
        ObjectNode diff = objectMapper.createObjectNode();
        List<String> appliedFields = new ArrayList<>();
        List<String> skippedFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (patches.isEmpty() || selectedFields.isEmpty()) {
            warnings.add("No structured field patch was applied; the new draft keeps original resume fields.");
            return new StructuredApplyResult(appliedFields, skippedFields, diff, warnings);
        }
        for (String field : selectedFields) {
            String normalizedField = normalizePatchField(field);
            if (!PATCHABLE_RESUME_FIELDS.contains(normalizedField)) {
                skippedFields.add(field);
                warnings.add("已跳过暂不支持的简历字段：" + field);
                continue;
            }
            JsonNode patch = patches.get(normalizedField);
            if (patch == null && "title".equals(normalizedField)) {
                patch = patches.get("resumeName");
            }
            if (patch == null && "resumeName".equals(normalizedField)) {
                patch = patches.get("title");
                normalizedField = "title";
            }
            if (isHighRiskResumePatch(patch)) {
                skippedFields.add(field);
                warnings.add("AI suggestion skipped because it contains unverified resume facts: " + field);
                continue;
            }
            String oldValue = resumeFieldValue(source, normalizedField);
            String newValue = patchValue(patch, oldValue);
            if (newValue == null) {
                skippedFields.add(field);
                continue;
            }
            applyResumeField(draft, normalizedField, newValue);
            ObjectNode fieldDiff = objectMapper.createObjectNode();
            fieldDiff.put("before", oldValue);
            fieldDiff.put("after", newValue);
            diff.set(normalizedField, fieldDiff);
            appliedFields.add(normalizedField);
        }
        if (appliedFields.isEmpty()) {
            warnings.add("Structured patch was present but no selected supported field could be applied.");
        }
        return new StructuredApplyResult(appliedFields, skippedFields, diff, warnings);
    }

    private boolean isHighRiskResumePatch(JsonNode patch) {
        return patch != null
                && patch.isObject()
                && (patch.path("fabricationRisk").asBoolean(false)
                || StringUtils.hasText(patch.path("unsupportedFact").asText(null)));
    }

    private ObjectNode extractFieldPatches(JsonNode resultJson, JsonNode requestPatches) {
        return extractFieldPatches(resultJson, requestPatches, null);
    }

    private ObjectNode extractFieldPatches(JsonNode resultJson, JsonNode requestPatches,
                                           List<Integer> selectedSuggestionIndexes) {
        ObjectNode patches = objectMapper.createObjectNode();
        mergePatchNode(patches, resultJson == null ? null : firstExisting(resultJson,
                "fieldPatches", "fieldDiff", "resumePatch", "patches", "structuredPatches"));
        JsonNode rewriteSuggestions = resultJson == null ? null : firstExisting(resultJson, "rewriteSuggestions");
        if (selectedSuggestionIndexes != null && !selectedSuggestionIndexes.isEmpty()) {
            mergeSelectedRewriteSuggestions(patches, rewriteSuggestions, selectedSuggestionIndexes);
        } else {
            mergePatchNode(patches, rewriteSuggestions);
        }
        mergePatchNode(patches, requestPatches);
        return patches;
    }

    private JsonNode firstExisting(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = root.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private void mergePatchNode(ObjectNode target, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String field = patchFieldName(item);
                if (StringUtils.hasText(field)) {
                    target.set(field, item);
                }
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String field = normalizePatchField(entry.getKey());
                if (StringUtils.hasText(field)) {
                    target.set(field, entry.getValue());
                }
            }
        }
    }

    private void mergeSelectedRewriteSuggestions(ObjectNode target, JsonNode suggestions, List<Integer> indexes) {
        if (suggestions == null || !suggestions.isArray()) {
            return;
        }
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= suggestions.size()) {
                continue;
            }
            JsonNode item = suggestions.get(index);
            String field = patchFieldName(item);
            if (StringUtils.hasText(field)) {
                target.set(field, item);
            }
        }
    }

    private String patchFieldName(JsonNode item) {
        return normalizePatchField(firstText(
                textField(item, "field"),
                textField(item, "fieldKey"),
                textField(item, "section"),
                textField(item, "fieldName")));
    }

    private Set<String> normalizeSelectedFields(ApplyResumeOptimizeResultDTO dto, ObjectNode patches) {
        Set<String> selected = new LinkedHashSet<>();
        if (dto != null && dto.getSelectedSuggestionIndexes() != null && !dto.getSelectedSuggestionIndexes().isEmpty()) {
            Iterator<String> names = patches.fieldNames();
            while (names.hasNext()) {
                selected.add(names.next());
            }
            if (!selected.isEmpty()) {
                return selected;
            }
        }
        if (dto != null && dto.getSelectedFields() != null && !dto.getSelectedFields().isEmpty()) {
            dto.getSelectedFields().stream()
                    .map(this::normalizePatchField)
                    .filter(StringUtils::hasText)
                    .forEach(selected::add);
            return selected;
        }
        boolean applyAll = dto != null && Boolean.TRUE.equals(dto.getApplyAll());
        boolean structuredMode = dto != null && APPLY_MODE_STRUCTURED_PATCH.equals(dto.getApplyMode());
        if (applyAll || structuredMode || (dto != null && dto.getFieldPatches() != null)) {
            Iterator<String> names = patches.fieldNames();
            while (names.hasNext()) {
                selected.add(names.next());
            }
        }
        return selected;
    }

    private String normalizePatchField(String field) {
        if (!StringUtils.hasText(field)) {
            return null;
        }
        String value = field.trim();
        String token = value.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        return switch (token) {
            case "title", "resumename", "简历名称", "标题" -> "title";
            case "realname", "name", "姓名", "真实姓名" -> "realName";
            case "email", "邮箱" -> "email";
            case "phone", "mobile", "手机号", "电话", "联系方式" -> "phone";
            case "targetposition", "targetjob", "position", "求职意向", "目标岗位" -> "targetPosition";
            case "skill", "skills", "skillstack", "techstack", "技术栈", "技能", "技能栈" -> "skillStack";
            case "project", "projectexperience", "projects", "work", "workexperience",
                    "experience", "项目", "项目经历", "项目经验", "工作经历", "工作经验" -> "workExperience";
            case "education", "educationexperience", "教育", "教育经历", "学历" -> "educationExperience";
            case "summary", "profile", "selfintroduction", "个人总结", "自我介绍", "个人简介" -> "summary";
            default -> value;
        };
    }

    private String patchValue(JsonNode patch, String oldValue) {
        if (patch == null || patch.isMissingNode() || patch.isNull()) {
            return null;
        }
        if (patch.isTextual() || patch.isNumber() || patch.isBoolean()) {
            return patch.asText();
        }
        String value = firstText(
                textField(patch, "after"),
                textField(patch, "newValue"),
                textField(patch, "value"),
                textField(patch, "optimized"),
                textField(patch, "suggested"));
        if (StringUtils.hasText(value)) {
            String before = textField(patch, "before");
            if (StringUtils.hasText(before)
                    && StringUtils.hasText(oldValue)
                    && oldValue.contains(before)
                    && !before.equals(value)) {
                return oldValue.replace(before, value);
            }
            return value;
        }
        JsonNode after = firstExisting(patch, "after", "newValue", "value", "optimized", "suggested");
        return after == null || after.isMissingNode() || after.isNull() ? null : after.toString();
    }

    private String resumeFieldValue(Resume resume, String field) {
        return switch (field) {
            case "title", "resumeName" -> resume.getTitle();
            case "realName" -> resume.getRealName();
            case "email" -> resume.getEmail();
            case "phone" -> resume.getPhone();
            case "targetPosition" -> resume.getTargetPosition();
            case "skillStack" -> resume.getSkillStack();
            case "workExperience" -> resume.getWorkExperience();
            case "educationExperience" -> resume.getEducationExperience();
            case "summary" -> resume.getSummary();
            default -> null;
        };
    }

    private void applyResumeField(Resume resume, String field, String value) {
        switch (field) {
            case "title", "resumeName" -> resume.setTitle(value);
            case "realName" -> resume.setRealName(value);
            case "email" -> resume.setEmail(value);
            case "phone" -> resume.setPhone(value);
            case "targetPosition" -> resume.setTargetPosition(value);
            case "skillStack" -> resume.setSkillStack(value);
            case "workExperience" -> resume.setWorkExperience(value);
            case "educationExperience" -> resume.setEducationExperience(value);
            case "summary" -> resume.setSummary(value);
            default -> {
            }
        }
    }

    private JsonNode parseResultJson(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历优化结果为空，请稍后重试");
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            if (root == null || !root.isObject()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历优化结果格式异常，请稍后重试");
            }
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历优化结果格式异常，请稍后重试");
        }
    }

    private JsonNode parseNullableJson(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            return root == null || !root.isObject() ? null : root;
        } catch (Exception ex) {
            return null;
        }
    }

    private String textField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            return null;
        }
        return json.path(fieldName).isTextual() ? json.path(fieldName).asText() : json.path(fieldName).toString();
    }

    private Integer integerField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            return null;
        }
        JsonNode value = json.path(fieldName);
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.valueOf(value.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private JsonNode jsonField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            return null;
        }
        return json.path(fieldName);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "结果序列化失败，请稍后重试");
        }
    }

    private ResumeOptimizeAiRequestDTO readOptimizeAiRequest(ResumeOptimizeRecord record) {
        if (record == null || !StringUtils.hasText(record.getRequestJson())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume optimize request is missing");
        }
        try {
            return objectMapper.readValue(record.getRequestJson(), ResumeOptimizeAiRequestDTO.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历优化请求内容异常，请重新提交");
        }
    }

    private String safeResumeParseErrorMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        if ("异步解析任务暂时不可用，系统将通过补偿任务重试".equals(message)) {
            return message;
        }
        return "简历解析失败，请稍后重试";
    }

    private String safeResumeOptimizeErrorMessage(String message) {
        return StringUtils.hasText(message) ? resumeOptimizeFailureMessage() : null;
    }

    private String resumeOptimizeFailureMessage() {
        return "简历优化失败，请稍后重试";
    }

    private List<ResumeProjectVO> projects(Long resumeId) {
        return projectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                        .eq(ResumeProject::getResumeId, resumeId)
                        .eq(ResumeProject::getDeleted, CommonConstants.NO)
                        .orderByAsc(ResumeProject::getSortOrder)
                        .orderByAsc(ResumeProject::getSort)
                        .orderByDesc(ResumeProject::getUpdatedAt))
                .stream()
                .map(ResumeConvert::toProjectVO)
                .toList();
    }

    private Long projectCount(Long resumeId) {
        return projectMapper.selectCount(new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, resumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO));
    }

    private void applyResume(Resume resume, ResumeSaveDTO dto) {
        String title = StringUtils.hasText(dto.getResumeName()) ? dto.getResumeName() : dto.getTitle();
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写简历名称");
        }
        resume.setTitle(title);
        resume.setRealName(dto.getRealName());
        resume.setEmail(dto.getEmail());
        resume.setPhone(dto.getPhone());
        resume.setTargetPosition(dto.getTargetPosition());
        resume.setSkillStack(dto.getSkillStack());
        resume.setWorkExperience(dto.getWorkExperience());
        resume.setEducationExperience(dto.getEducationExperience());
        resume.setSummary(dto.getSummary());
        if (dto.getStatus() != null) {
            resume.setStatus(dto.getStatus());
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件为空，请重新选择");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }
        String normalized = filename.replace('\\', '/');
        String simpleName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(simpleName) || simpleName.contains("..")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不合法，请重新选择");
        }
        int index = simpleName.lastIndexOf('.');
        if (index < 0 || index == simpleName.length() - 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件扩展名不能为空");
        }
        String ext = simpleName.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历文件仅支持 pdf/doc/docx/md/txt");
        }
        if (file.getSize() > textExtractProperties.maxSourceFileBytes()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Resume file size cannot exceed " + textExtractProperties.effectiveMaxSourceFileSizeMb() + "MB");
        }
    }

    private void applyProject(ResumeProject project, ResumeProjectSaveDTO dto) {
        String projectBackground = StringUtils.hasText(dto.getProjectBackground())
                ? dto.getProjectBackground()
                : dto.getDescription();
        project.setProjectName(dto.getProjectName());
        project.setProjectPeriod(dto.getProjectPeriod());
        project.setProjectBackground(projectBackground);
        project.setRole(dto.getRole());
        project.setTechStack(dto.getTechStack());
        project.setResponsibility(dto.getResponsibility());
        project.setCoreFeatures(dto.getCoreFeatures());
        project.setTechnicalDifficulties(dto.getTechnicalDifficulties());
        project.setOptimizationResults(dto.getOptimizationResults());
        project.setDescription(StringUtils.hasText(dto.getDescription()) ? dto.getDescription() : projectBackground);
        project.setHighlights(dto.getHighlights());
        project.setSort(dto.getSort() == null ? 0 : dto.getSort());
        project.setSortOrder(dto.getSortOrder() == null ? project.getSort() : dto.getSortOrder());
    }

    private Resume getOwnedResume(Long id) {
        return getOwnedResume(id, requireCurrentUserId());
    }

    private Resume getOwnedResumeForRead(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume id is required");
        }
        Long userId = requireCurrentUserId();
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, id)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在或已不可用");
        }
        if (!Objects.equals(resume.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该简历");
        }
        return resume;
    }

    private Resume getOwnedResume(Long id, Long userId) {
        return requireOwnedResume(id, userId, ErrorCode.PARAM_ERROR);
    }

    private void lockOwnedResume(Resume resume) {
        Long lockedId = resumeMapper.lockOwnedResume(resume.getId(), resume.getUserId());
        if (!Objects.equals(resume.getId(), lockedId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历不存在或已不可用");
        }
    }

    private Resume requireOwnedResume(Long id, Long userId, ErrorCode missingErrorCode) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume id is required");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, id)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (resume == null) {
            throw new BusinessException(missingErrorCode, "简历不存在或已不可用");
        }
        return resume;
    }

    private ResumeAnalysisRecord getOwnedAnalysisRecord(Long analysisRecordId, Long userId) {
        ResumeAnalysisRecord record = analysisRecordMapper.selectOne(new LambdaQueryWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getId, analysisRecordId)
                .eq(ResumeAnalysisRecord::getUserId, userId)
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析记录不存在或已不可用");
        }
        return record;
    }

    private ResumeProject getProject(Long resumeId, Long projectId) {
        ResumeProject project = projectMapper.selectById(projectId);
        if (project == null || !resumeId.equals(project.getResumeId())
                || CommonConstants.YES.equals(project.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历项目经历不存在或已不可用");
        }
        return project;
    }

    private ResumeProject getOwnedProject(Long projectId) {
        ResumeProject project = projectMapper.selectById(projectId);
        if (project == null || CommonConstants.YES.equals(project.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历项目经历不存在或已不可用");
        }
        Resume resume = getOwnedResume(project.getResumeId());
        lockOwnedResume(resume);
        return project;
    }

    private ResumeOptimizeRecord getOwnedOptimizeRecord(Long recordId, Long userId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectOne(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                .eq(ResumeOptimizeRecord::getId, recordId)
                .eq(ResumeOptimizeRecord::getUserId, userId)
                .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历优化记录不存在或已不可用");
        }
        return record;
    }

    private ResumeOptimizeRecord getOptimizeRecord(Long recordId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectOne(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                .eq(ResumeOptimizeRecord::getId, recordId)
                .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历建议记录不存在或已不可用");
        }
        return record;
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String likePattern(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String escaped = keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private List<String> splitSearchValues(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,，;；|\\r\\n]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(100)
                .toList();
    }

    private List<String> nonBlankValues(String... values) {
        if (values == null) {
            return List.of();
        }
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Long toEpochMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record OptimizeContext(Resume resume, List<ResumeProject> projects, ResumeOptimizeRecord record,
                                   ResumeOptimizeAiRequestDTO aiRequest) {
    }

    private record StructuredApplyResult(List<String> appliedFields, List<String> skippedFields,
                                         JsonNode fieldDiff, List<String> warnings) {
    }
}
