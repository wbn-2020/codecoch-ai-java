package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.ReadinessRepairRequestDTO;
import com.codecoachai.resume.domain.entity.JobReadinessSnapshot;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.domain.vo.ReadinessRepairRecordVO;
import com.codecoachai.resume.domain.vo.ReadinessRepairResultVO;
import com.codecoachai.resume.mapper.JobReadinessSnapshotMapper;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.ReadinessHistoricalRepairService;
import com.codecoachai.resume.service.support.ReadinessDimensionCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReadinessHistoricalRepairServiceImpl implements ReadinessHistoricalRepairService {

    private static final int DEFAULT_MAX_RECORDS = 20;
    private static final int MAX_RECORDS = 100;
    private static final Pattern BATCH_ID = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private final JobReadinessSnapshotMapper snapshotMapper;
    private final JobReadinessService jobReadinessService;
    private final ReadinessDimensionCodec dimensionCodec;

    @Override
    public ReadinessRepairResultVO repair(ReadinessRepairRequestDTO request) {
        ReadinessRepairRequestDTO safeRequest = request == null ? new ReadinessRepairRequestDTO() : request;
        String repairBatchId = requireRepairBatchId(safeRequest.getRepairBatchId());
        int maxRecords = requireMaxRecords(safeRequest.getMaxRecords());
        requireScope(safeRequest);
        boolean dryRun = !Boolean.FALSE.equals(safeRequest.getDryRun());

        List<JobReadinessSnapshot> snapshots = findSnapshots(safeRequest, maxRecords);
        ReadinessRepairResultVO result = new ReadinessRepairResultVO();
        result.setDryRun(dryRun);
        result.setRepairBatchId(repairBatchId);
        result.setMatchedRecords(snapshots.size());

        for (JobReadinessSnapshot snapshot : snapshots) {
            ReadinessRepairRecordVO record = assess(snapshot);
            result.setProcessedRecords(result.getProcessedRecords() + 1);
            if ("ALREADY_VALID".equals(record.getStatus())) {
                append(result, record);
                continue;
            }
            if (dryRun) {
                record.setStatus("WOULD_REGENERATE");
                record.setReasonCode("INVALID_DIMENSIONS");
                append(result, record);
                continue;
            }

            applyRepair(snapshot, repairBatchId, record, result);
            append(result, record);
        }
        return result;
    }

    private List<JobReadinessSnapshot> findSnapshots(ReadinessRepairRequestDTO request, int maxRecords) {
        LambdaQueryWrapper<JobReadinessSnapshot> query = new LambdaQueryWrapper<JobReadinessSnapshot>()
                .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO);
        if (hasValues(request.getSnapshotIds())) {
            query.in(JobReadinessSnapshot::getId, request.getSnapshotIds());
        }
        if (hasValues(request.getTargetJobIds())) {
            query.in(JobReadinessSnapshot::getTargetJobId, request.getTargetJobIds());
        }
        if (hasValues(request.getUserIds())) {
            query.in(JobReadinessSnapshot::getUserId, request.getUserIds());
        }
        List<JobReadinessSnapshot> snapshots = snapshotMapper.selectList(query
                .orderByDesc(JobReadinessSnapshot::getGeneratedAt)
                .orderByDesc(JobReadinessSnapshot::getId)
                .last("limit " + maxRecords));
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(snapshots.stream().limit(maxRecords).toList());
    }

    private ReadinessRepairRecordVO assess(JobReadinessSnapshot snapshot) {
        ReadinessRepairRecordVO record = new ReadinessRepairRecordVO();
        record.setSnapshotId(snapshot.getId());
        record.setUserId(snapshot.getUserId());
        record.setTargetJobId(snapshot.getTargetJobId());
        record.setBeforeSnapshotHash(snapshot.getSnapshotHash());
        ReadinessDimensionCodec.DecodeResult validation =
                dimensionCodec.decode(snapshot.getDimensionJson(), snapshot.getSchemaVersion());
        record.setBeforeValidationStatus(validation.status().name());
        if (validation.valid()) {
            record.setStatus("ALREADY_VALID");
            record.setReasonCode("VALID_DIMENSIONS");
        } else {
            record.setStatus("PENDING_REGENERATION");
            record.setReasonCode(validation.status().name());
        }
        return record;
    }

    private void applyRepair(JobReadinessSnapshot snapshot,
                             String repairBatchId,
                             ReadinessRepairRecordVO record,
                             ReadinessRepairResultVO result) {
        try {
            JobReadinessSnapshotVO regenerated = jobReadinessService.regenerateForRepair(
                    snapshot.getUserId(), snapshot.getTargetJobId(), repairBatchId);
            record.setRegeneratedSnapshotId(regenerated.getId());
            record.setAfterSnapshotHash(regenerated.getSnapshotHash());
            record.setAfterValidationStatus(regenerated.getValidationStatus());
            if (isValid(regenerated.getValidationStatus())) {
                record.setStatus("REGENERATED");
                record.setReasonCode("INVALID_DIMENSIONS_REGENERATED");
                result.setChangedRecords(result.getChangedRecords() + 1);
            } else {
                record.setStatus("MANUAL_ACTION_REQUIRED");
                record.setReasonCode("REGENERATED_SNAPSHOT_INVALID");
                result.setManualActionRequired(result.getManualActionRequired() + 1);
            }
        } catch (RuntimeException ex) {
            record.setStatus("MANUAL_ACTION_REQUIRED");
            record.setReasonCode("REGENERATION_FAILED");
            result.setManualActionRequired(result.getManualActionRequired() + 1);
        }
    }

    private void append(ReadinessRepairResultVO result, ReadinessRepairRecordVO record) {
        result.getRecords().add(record);
        result.getStatusCounts().merge(record.getStatus(), 1, Integer::sum);
    }

    private void requireScope(ReadinessRepairRequestDTO request) {
        if (!hasValues(request.getSnapshotIds())
                && !hasValues(request.getTargetJobIds())
                && !hasValues(request.getUserIds())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "snapshotIds, targetJobIds, or userIds must be provided");
        }
    }

    private int requireMaxRecords(Integer maxRecords) {
        int value = maxRecords == null ? DEFAULT_MAX_RECORDS : maxRecords;
        if (value < 1 || value > MAX_RECORDS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "maxRecords must be between 1 and 100");
        }
        return value;
    }

    private String requireRepairBatchId(String repairBatchId) {
        if (!StringUtils.hasText(repairBatchId) || !BATCH_ID.matcher(repairBatchId.trim()).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "repairBatchId must be 8-64 letters, digits, dot, underscore, colon, or dash");
        }
        return repairBatchId.trim();
    }

    private boolean hasValues(Collection<Long> values) {
        return values != null && values.stream().anyMatch(value -> value != null && value > 0);
    }

    private boolean isValid(String status) {
        return "VALID".equalsIgnoreCase(status) || "VALID_LEGACY".equalsIgnoreCase(status);
    }
}
