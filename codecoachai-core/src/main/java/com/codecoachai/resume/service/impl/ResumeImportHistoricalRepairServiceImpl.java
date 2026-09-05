package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeImportRepairRollbackDTO;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeImportRepairAudit;
import com.codecoachai.resume.domain.vo.ResumeImportRepairRecordVO;
import com.codecoachai.resume.domain.vo.ResumeImportRepairResultVO;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeImportRepairAuditMapper;
import com.codecoachai.resume.service.ResumeImportHistoricalRepairService;
import com.codecoachai.resume.service.support.ResumeImportNormalizer;
import com.codecoachai.resume.service.support.ResumeImportNormalizer.NormalizationResult;
import com.codecoachai.resume.service.support.ResumeImportRepairSnapshotCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ResumeImportHistoricalRepairServiceImpl implements ResumeImportHistoricalRepairService {

    private static final int DEFAULT_MAX_RECORDS = 20;
    private static final int MAX_RECORDS = 100;
    private static final Pattern BATCH_ID = Pattern.compile("[A-Za-z0-9._:-]{8,64}");
    private static final String OPERATION_REPAIR = "REPAIR";
    private static final String OPERATION_ROLLBACK = "ROLLBACK";
    private static final String AUDIT_APPLIED = "APPLIED";
    private static final String AUDIT_RESTORED = "RESTORED";

    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final ResumeImportRepairAuditMapper repairAuditMapper;
    private final ResumeImportNormalizer resumeImportNormalizer;
    private final ResumeImportRepairSnapshotCipher snapshotCipher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeImportRepairResultVO repair(ResumeImportRepairRequestDTO request, Long actorUserId) {
        ResumeImportRepairRequestDTO safeRequest =
                request == null ? new ResumeImportRepairRequestDTO() : request;
        String repairBatchId = requireRepairBatchId(safeRequest.getRepairBatchId());
        int maxRecords = requireMaxRecords(safeRequest.getMaxRecords());
        boolean dryRun = !Boolean.FALSE.equals(safeRequest.getDryRun());
        TargetScope scope = requireTargetScope(safeRequest);
        List<ResumeAnalysisRecord> records = analysisRecordMapper.selectList(
                new LambdaQueryWrapper<ResumeAnalysisRecord>()
                        .in(!scope.analysisRecordIds().isEmpty(),
                                ResumeAnalysisRecord::getId, scope.analysisRecordIds())
                        .in(!scope.resumeIds().isEmpty(),
                                ResumeAnalysisRecord::getResumeId, scope.resumeIds())
                        .in(!scope.userIds().isEmpty(),
                                ResumeAnalysisRecord::getUserId, scope.userIds())
                        .orderByAsc(ResumeAnalysisRecord::getId)
                        .last("LIMIT " + maxRecords));

        ResumeImportRepairResultVO result = result(dryRun, repairBatchId, records.size());
        for (ResumeAnalysisRecord record : records) {
            RepairAssessment assessment = assessRepair(record, repairBatchId);
            ResumeImportRepairRecordVO item = toRecordVo(record, assessment);
            if (assessment.manualResumeReconciliationRequired()) {
                result.setManualActionRequired(result.getManualActionRequired() + 1);
            }
            if (dryRun || !assessment.canRepair()) {
                item.setStatus(dryRun && assessment.canRepair()
                        ? "WOULD_REPAIR" : assessment.status());
                append(result, item);
                continue;
            }
            if (hasOperationAudit(repairBatchId, record.getId(), OPERATION_REPAIR)) {
                item.setStatus("SKIPPED_ALREADY_ATTEMPTED");
                item.setReasonCode("DUPLICATE_REPAIR_BATCH");
                append(result, item);
                continue;
            }

            MutableAnalysisSnapshot before = MutableAnalysisSnapshot.from(record);
            ResumeImportRepairAudit audit = repairAudit(
                    repairBatchId,
                    record,
                    actorUserId,
                    OPERATION_REPAIR,
                    before,
                    assessment.after(),
                    assessment.beforeValidationStatus(),
                    assessment.afterValidationStatus(),
                    assessment.reasonCode(),
                    assessment.manualResumeReconciliationRequired()
                            ? "Linked generated resume requires manual reconciliation."
                            : null);
            repairAuditMapper.insert(audit);

            int updated = updateRecord(record, assessment.after());
            if (updated != 1) {
                audit.setStatus("SKIPPED_CONCURRENT_UPDATE");
                repairAuditMapper.updateById(audit);
                item.setStatus("SKIPPED_CONCURRENT_UPDATE");
                item.setReasonCode("RECORD_CHANGED_DURING_REPAIR");
                append(result, item);
                continue;
            }

            ResumeAnalysisRecord persisted = analysisRecordMapper.selectById(record.getId());
            verifyPersistedRepair(persisted, assessment.after());
            audit.setStatus(AUDIT_APPLIED);
            repairAuditMapper.updateById(audit);

            item.setStatus(assessment.manualResumeReconciliationRequired()
                    ? "REPAIRED_ANALYSIS_MANUAL_RESUME_RECONCILIATION" : "REPAIRED");
            result.setChangedRecords(result.getChangedRecords() + 1);
            append(result, item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeImportRepairResultVO rollback(
            String repairBatchId, ResumeImportRepairRollbackDTO request, Long actorUserId) {
        String safeRepairBatchId = requireRepairBatchId(repairBatchId);
        ResumeImportRepairRollbackDTO safeRequest =
                request == null ? new ResumeImportRepairRollbackDTO() : request;
        int maxRecords = requireMaxRecords(safeRequest.getMaxRecords());
        boolean dryRun = !Boolean.FALSE.equals(safeRequest.getDryRun());
        List<Long> auditIds = requireIds(safeRequest.getAuditIds(), "auditIds");

        List<ResumeImportRepairAudit> audits = repairAuditMapper.selectList(
                new LambdaQueryWrapper<ResumeImportRepairAudit>()
                        .eq(ResumeImportRepairAudit::getRepairBatchId, safeRepairBatchId)
                        .eq(ResumeImportRepairAudit::getOperation, OPERATION_REPAIR)
                        .eq(ResumeImportRepairAudit::getStatus, AUDIT_APPLIED)
                        .in(ResumeImportRepairAudit::getId, auditIds)
                        .orderByAsc(ResumeImportRepairAudit::getId)
                        .last("LIMIT " + maxRecords));
        ResumeImportRepairResultVO result = result(dryRun, safeRepairBatchId, audits.size());
        for (ResumeImportRepairAudit audit : audits) {
            ResumeAnalysisRecord current = analysisRecordMapper.selectById(audit.getAnalysisRecordId());
            ResumeImportRepairRecordVO item = new ResumeImportRepairRecordVO();
            item.setAnalysisRecordId(audit.getAnalysisRecordId());
            item.setResumeId(audit.getResumeId());
            item.setBeforeHash(audit.getAfterHash());
            item.setAfterHash(audit.getBeforeHash());
            item.setBeforeValidationStatus(audit.getAfterValidationStatus());
            item.setAfterValidationStatus(audit.getBeforeValidationStatus());

            if (current == null) {
                item.setStatus("MANUAL_ACTION_REQUIRED");
                item.setReasonCode("ANALYSIS_RECORD_NOT_FOUND");
                append(result, item);
                result.setManualActionRequired(result.getManualActionRequired() + 1);
                continue;
            }
            if (!Objects.equals(snapshotHash(MutableAnalysisSnapshot.from(current)), audit.getAfterHash())) {
                item.setStatus("MANUAL_ACTION_REQUIRED");
                item.setReasonCode("RECORD_CHANGED_AFTER_REPAIR");
                append(result, item);
                result.setManualActionRequired(result.getManualActionRequired() + 1);
                continue;
            }
            if (hasOperationAudit(safeRepairBatchId, audit.getAnalysisRecordId(), OPERATION_ROLLBACK)) {
                item.setStatus("SKIPPED_ALREADY_RESTORED");
                item.setReasonCode("DUPLICATE_ROLLBACK_BATCH");
                append(result, item);
                continue;
            }
            if (dryRun) {
                item.setStatus("WOULD_RESTORE");
                item.setReasonCode("ROLLBACK_PREVIEW");
                append(result, item);
                continue;
            }

            MutableAnalysisSnapshot before = MutableAnalysisSnapshot.from(current);
            MutableAnalysisSnapshot target = readSnapshot(audit.getBeforeSnapshotCiphertext());
            ResumeImportRepairAudit rollbackAudit = repairAudit(
                    safeRepairBatchId,
                    current,
                    actorUserId,
                    OPERATION_ROLLBACK,
                    before,
                    target,
                    audit.getAfterValidationStatus(),
                    audit.getBeforeValidationStatus(),
                    "ROLLBACK_RESTORE",
                    null);
            repairAuditMapper.insert(rollbackAudit);
            int updated = updateRecord(current, target);
            if (updated != 1) {
                rollbackAudit.setStatus("SKIPPED_CONCURRENT_UPDATE");
                repairAuditMapper.updateById(rollbackAudit);
                item.setStatus("SKIPPED_CONCURRENT_UPDATE");
                item.setReasonCode("RECORD_CHANGED_DURING_ROLLBACK");
                append(result, item);
                continue;
            }
            ResumeAnalysisRecord restored = analysisRecordMapper.selectById(current.getId());
            if (!Objects.equals(snapshotHash(MutableAnalysisSnapshot.from(restored)), audit.getBeforeHash())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历导入修复回滚后的快照校验失败");
            }
            rollbackAudit.setStatus(AUDIT_RESTORED);
            repairAuditMapper.updateById(rollbackAudit);
            item.setStatus("RESTORED");
            item.setReasonCode("ROLLBACK_RESTORED");
            result.setChangedRecords(result.getChangedRecords() + 1);
            append(result, item);
        }
        return result;
    }

    private RepairAssessment assessRepair(ResumeAnalysisRecord record, String repairBatchId) {
        if (!"WAIT_CONFIRM".equals(record.getParseStatus()) && !"SUCCESS".equals(record.getParseStatus())) {
            return RepairAssessment.notRepairable(
                    "SKIPPED_UNSUPPORTED_STATUS", "UNSUPPORTED_PARSE_STATUS",
                    record.getValidationStatus(), record.getValidationStatus(), false);
        }
        if (!StringUtils.hasText(record.getStructuredJson())) {
            return RepairAssessment.notRepairable(
                    "MANUAL_ACTION_REQUIRED", "MISSING_STRUCTURED_JSON",
                    record.getValidationStatus(), "INVALID_HISTORICAL", record.getResumeId() != null);
        }

        try {
            NormalizationResult normalized = resumeImportNormalizer.normalize(record.getStructuredJson());
            if (!normalized.qualityReport().isConfirmable()) {
                return RepairAssessment.notRepairable(
                        "MANUAL_ACTION_REQUIRED", "NORMALIZATION_BLOCKED",
                        record.getValidationStatus(), normalized.qualityReport().getValidationStatus(),
                        record.getResumeId() != null);
            }
            MutableAnalysisSnapshot before = MutableAnalysisSnapshot.from(record);
            MutableAnalysisSnapshot after = before.withNormalized(
                    normalized,
                    resumeImportNormalizer.sourceHash(record.getRawText()),
                    repairBatchId,
                    record.getGeneratedAt() == null ? LocalDateTime.now() : record.getGeneratedAt());
            boolean metadataChanged = !before.equals(after);
            if (!metadataChanged) {
                boolean linkedGeneratedResume =
                        record.getResumeId() != null && "SUCCESS".equals(record.getParseStatus());
                return RepairAssessment.notRepairable(
                        linkedGeneratedResume ? "MANUAL_ACTION_REQUIRED" : "NO_CHANGE",
                        linkedGeneratedResume ? "LINKED_RESUME_RECONCILIATION_REQUIRED" : "ALREADY_CANONICAL",
                        record.getValidationStatus(), normalized.qualityReport().getValidationStatus(),
                        linkedGeneratedResume);
            }
            return new RepairAssessment(
                    true,
                    "READY",
                    "CANONICALIZATION_REQUIRED",
                    record.getValidationStatus(),
                    normalized.qualityReport().getValidationStatus(),
                    after,
                    record.getResumeId() != null && "SUCCESS".equals(record.getParseStatus()));
        } catch (BusinessException ex) {
            return RepairAssessment.notRepairable(
                    "MANUAL_ACTION_REQUIRED", "INVALID_STRUCTURED_SCHEMA",
                    record.getValidationStatus(), "INVALID_HISTORICAL", record.getResumeId() != null);
        }
    }

    private ResumeImportRepairAudit repairAudit(
            String repairBatchId,
            ResumeAnalysisRecord record,
            Long actorUserId,
            String operation,
            MutableAnalysisSnapshot before,
            MutableAnalysisSnapshot after,
            String beforeValidationStatus,
            String afterValidationStatus,
            String reasonCode,
            String note) {
        ResumeImportRepairAudit audit = new ResumeImportRepairAudit();
        audit.setRepairBatchId(repairBatchId);
        audit.setAnalysisRecordId(record.getId());
        audit.setUserId(record.getUserId());
        audit.setResumeId(record.getResumeId());
        audit.setActorUserId(actorUserId);
        audit.setOperation(operation);
        audit.setStatus("RUNNING");
        audit.setBeforeSnapshotCiphertext(snapshotCipher.encrypt(writeSnapshot(before)));
        audit.setAfterSnapshotCiphertext(snapshotCipher.encrypt(writeSnapshot(after)));
        audit.setBeforeHash(snapshotHash(before));
        audit.setAfterHash(snapshotHash(after));
        audit.setBeforeValidationStatus(beforeValidationStatus);
        audit.setAfterValidationStatus(afterValidationStatus);
        audit.setReasonCode(reasonCode);
        audit.setNote(note);
        return audit;
    }

    private int updateRecord(ResumeAnalysisRecord record, MutableAnalysisSnapshot snapshot) {
        LambdaUpdateWrapper<ResumeAnalysisRecord> update = new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getId, record.getId())
                .eq(record.getUpdatedAt() != null, ResumeAnalysisRecord::getUpdatedAt, record.getUpdatedAt())
                .set(ResumeAnalysisRecord::getStructuredJson, snapshot.structuredJson())
                .set(ResumeAnalysisRecord::getSchemaVersion, snapshot.schemaVersion())
                .set(ResumeAnalysisRecord::getPolicyVersion, snapshot.policyVersion())
                .set(ResumeAnalysisRecord::getSourceHash, snapshot.sourceHash())
                .set(ResumeAnalysisRecord::getValidationStatus, snapshot.validationStatus())
                .set(ResumeAnalysisRecord::getQualityReportJson, snapshot.qualityReportJson())
                .set(ResumeAnalysisRecord::getGeneratedAt, snapshot.generatedAt())
                .set(ResumeAnalysisRecord::getRepairBatchId, snapshot.repairBatchId())
                .set(ResumeAnalysisRecord::getErrorMessage, snapshot.errorMessage());
        return analysisRecordMapper.update(null, update);
    }

    private void verifyPersistedRepair(ResumeAnalysisRecord persisted, MutableAnalysisSnapshot expected) {
        if (persisted == null || !Objects.equals(snapshotHash(MutableAnalysisSnapshot.from(persisted)),
                snapshotHash(expected))) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历导入修复后快照校验失败");
        }
        NormalizationResult normalized = resumeImportNormalizer.normalize(persisted.getStructuredJson());
        if (!"VALID".equals(normalized.qualityReport().getValidationStatus())
                || !normalized.qualityReport().isConfirmable()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历导入修复后结构化校验失败");
        }
    }

    private boolean hasOperationAudit(String repairBatchId, Long analysisRecordId, String operation) {
        return repairAuditMapper.selectCount(new LambdaQueryWrapper<ResumeImportRepairAudit>()
                .eq(ResumeImportRepairAudit::getRepairBatchId, repairBatchId)
                .eq(ResumeImportRepairAudit::getAnalysisRecordId, analysisRecordId)
                .eq(ResumeImportRepairAudit::getOperation, operation)) > 0;
    }

    private ResumeImportRepairResultVO result(boolean dryRun, String repairBatchId, int matchedRecords) {
        ResumeImportRepairResultVO result = new ResumeImportRepairResultVO();
        result.setDryRun(dryRun);
        result.setRepairBatchId(repairBatchId);
        result.setMatchedRecords(matchedRecords);
        return result;
    }

    private void append(ResumeImportRepairResultVO result, ResumeImportRepairRecordVO item) {
        result.getRecords().add(item);
        result.setProcessedRecords(result.getProcessedRecords() + 1);
        result.getStatusCounts().merge(item.getStatus(), 1, Integer::sum);
    }

    private ResumeImportRepairRecordVO toRecordVo(
            ResumeAnalysisRecord record, RepairAssessment assessment) {
        ResumeImportRepairRecordVO item = new ResumeImportRepairRecordVO();
        item.setAnalysisRecordId(record.getId());
        item.setResumeId(record.getResumeId());
        item.setStatus(assessment.status());
        item.setReasonCode(assessment.reasonCode());
        item.setBeforeValidationStatus(assessment.beforeValidationStatus());
        item.setAfterValidationStatus(assessment.afterValidationStatus());
        item.setBeforeHash(snapshotHash(MutableAnalysisSnapshot.from(record)));
        item.setAfterHash(assessment.after() == null ? item.getBeforeHash() : snapshotHash(assessment.after()));
        item.setManualResumeReconciliationRequired(assessment.manualResumeReconciliationRequired());
        return item;
    }

    private String writeSnapshot(MutableAnalysisSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "简历导入修复快照序列化失败");
        }
    }

    private MutableAnalysisSnapshot readSnapshot(String ciphertext) {
        try {
            return objectMapper.readValue(snapshotCipher.decrypt(ciphertext), MutableAnalysisSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历导入修复审计快照无法解析");
        }
    }

    private String snapshotHash(MutableAnalysisSnapshot snapshot) {
        return resumeImportNormalizer.sourceHash(writeSnapshot(snapshot));
    }

    private String requireRepairBatchId(String repairBatchId) {
        if (!StringUtils.hasText(repairBatchId) || !BATCH_ID.matcher(repairBatchId.trim()).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "repairBatchId 必须为 8-64 位字母、数字、点、下划线、冒号或连字符");
        }
        return repairBatchId.trim();
    }

    private int requireMaxRecords(Integer maxRecords) {
        int resolved = maxRecords == null ? DEFAULT_MAX_RECORDS : maxRecords;
        if (resolved < 1 || resolved > MAX_RECORDS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "maxRecords 必须在 1 到 " + MAX_RECORDS + " 之间");
        }
        return resolved;
    }

    private TargetScope requireTargetScope(ResumeImportRepairRequestDTO request) {
        List<Long> analysisRecordIds = safeIds(request.getAnalysisRecordIds());
        List<Long> resumeIds = safeIds(request.getResumeIds());
        List<Long> userIds = safeIds(request.getUserIds());
        if (analysisRecordIds.isEmpty() && resumeIds.isEmpty() && userIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "必须指定 analysisRecordIds、resumeIds 或 userIds 中至少一项，禁止无范围修复");
        }
        return new TargetScope(analysisRecordIds, resumeIds, userIds);
    }

    private List<Long> requireIds(List<Long> values, String fieldName) {
        List<Long> ids = safeIds(values);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " 至少包含一个有效 ID");
        }
        return ids;
    }

    private List<Long> safeIds(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null && value > 0) {
                ids.add(value);
            }
        }
        if (ids.size() > MAX_RECORDS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "单次最多指定 " + MAX_RECORDS + " 个精确 ID");
        }
        return new ArrayList<>(ids);
    }

    private record TargetScope(
            List<Long> analysisRecordIds, List<Long> resumeIds, List<Long> userIds) {
    }

    private record RepairAssessment(
            boolean canRepair,
            String status,
            String reasonCode,
            String beforeValidationStatus,
            String afterValidationStatus,
            MutableAnalysisSnapshot after,
            boolean manualResumeReconciliationRequired) {

        private static RepairAssessment notRepairable(
                String status,
                String reasonCode,
                String beforeValidationStatus,
                String afterValidationStatus,
                boolean manualResumeReconciliationRequired) {
            return new RepairAssessment(
                    false,
                    status,
                    reasonCode,
                    beforeValidationStatus,
                    afterValidationStatus,
                    null,
                    manualResumeReconciliationRequired);
        }
    }

    private record MutableAnalysisSnapshot(
            String structuredJson,
            String schemaVersion,
            String policyVersion,
            String sourceHash,
            String validationStatus,
            String qualityReportJson,
            LocalDateTime generatedAt,
            String repairBatchId,
            String errorMessage) {

        private static MutableAnalysisSnapshot from(ResumeAnalysisRecord record) {
            return new MutableAnalysisSnapshot(
                    record.getStructuredJson(),
                    record.getSchemaVersion(),
                    record.getPolicyVersion(),
                    record.getSourceHash(),
                    record.getValidationStatus(),
                    record.getQualityReportJson(),
                    record.getGeneratedAt(),
                    record.getRepairBatchId(),
                    record.getErrorMessage());
        }

        private MutableAnalysisSnapshot withNormalized(
                NormalizationResult normalized,
                String sourceHash,
                String repairBatchId,
                LocalDateTime generatedAt) {
            return new MutableAnalysisSnapshot(
                    normalized.normalizedJson(),
                    normalized.structuredResume().getSchemaVersion(),
                    ResumeImportNormalizer.POLICY_VERSION,
                    sourceHash,
                    normalized.qualityReport().getValidationStatus(),
                    normalized.qualityReportJson(),
                    generatedAt,
                    repairBatchId,
                    errorMessage);
        }
    }
}
