package com.codecoachai.resume.careerresearch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.careerresearch.entity.CareerResearchReport;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshot;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshotSource;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSourceVersion;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchReportMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotSourceMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareerResearchClaimManager {
    private final CareerResearchReportMapper reportMapper;
    private final CareerResearchSnapshotMapper snapshotMapper;
    private final CareerResearchSnapshotSourceMapper snapshotSourceMapper;

    @Transactional(rollbackFor = Exception.class)
    public Claim claim(Long userId, Long applicationId, String claimToken) {
        CareerResearchReport report = findReport(userId, applicationId);
        if (report == null) {
            report = new CareerResearchReport();
            report.setUserId(userId);
            report.setApplicationId(applicationId);
            report.setGenerationStatus("IDLE");
            report.setLockVersion(0);
            try {
                reportMapper.insert(report);
            } catch (DuplicateKeyException ex) {
                report = findReport(userId, applicationId);
            }
        }
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Research report could not be created or loaded");
        }
        LocalDateTime now = LocalDateTime.now();
        int claimed = reportMapper.claimGeneration(
                report.getId(), userId, claimToken, now, now.minusMinutes(10));
        if (claimed != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Research generation is already in progress");
        }
        return new Claim(report.getId(), claimToken);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long complete(Claim claim, Long userId, Long applicationId, String sourceSetHash,
                         String snapshotJson, String confidenceLevel, String fallbackReason,
                         Long aiCallLogId, List<CareerResearchSourceVersion> sourceVersions) {
        CareerResearchSnapshot snapshot = new CareerResearchSnapshot();
        snapshot.setUserId(userId);
        snapshot.setReportId(claim.reportId());
        snapshot.setApplicationId(applicationId);
        snapshot.setSourceSetHash(sourceSetHash);
        snapshot.setGenerationClaimToken(claim.claimToken());
        snapshot.setSnapshotJson(snapshotJson);
        snapshot.setConfidenceLevel(confidenceLevel);
        snapshot.setFallbackReason(truncate(fallbackReason, 64));
        snapshot.setAiCallLogId(aiCallLogId);
        try {
            snapshotMapper.insert(snapshot);
        } catch (DuplicateKeyException ex) {
            CareerResearchSnapshot winner = findSnapshot(
                    userId, claim.reportId(), sourceSetHash);
            if (winner != null) {
                reportMapper.completeGeneration(
                        claim.reportId(), userId, claim.claimToken(), winner.getId());
                return winner.getId();
            }
            throw ex;
        }
        for (CareerResearchSourceVersion version : sourceVersions) {
            CareerResearchSnapshotSource relation = new CareerResearchSnapshotSource();
            relation.setUserId(userId);
            relation.setSnapshotId(snapshot.getId());
            relation.setSourceId(version.getSourceId());
            relation.setSourceVersionId(version.getId());
            relation.setContentHash(version.getContentHash());
            snapshotSourceMapper.insert(relation);
        }
        if (reportMapper.completeGeneration(
                claim.reportId(), userId, claim.claimToken(), snapshot.getId()) != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Research generation claim is no longer valid");
        }
        return snapshot.getId();
    }

    @Transactional(readOnly = true)
    public CareerResearchSnapshot findReusableSnapshot(Long userId, Long applicationId,
                                                       String sourceSetHash) {
        CareerResearchReport report = findReport(userId, applicationId);
        return report == null ? null : findSnapshot(userId, report.getId(), sourceSetHash);
    }

    @Transactional(rollbackFor = Exception.class)
    public void fail(Claim claim, Long userId) {
        reportMapper.releaseFailedGeneration(claim.reportId(), userId, claim.claimToken());
    }

    private CareerResearchReport findReport(Long userId, Long applicationId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<CareerResearchReport>()
                .eq(CareerResearchReport::getUserId, userId)
                .eq(CareerResearchReport::getApplicationId, applicationId)
                .eq(CareerResearchReport::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
    }

    private CareerResearchSnapshot findSnapshot(Long userId, Long reportId, String sourceSetHash) {
        return snapshotMapper.selectWinner(userId, reportId, sourceSetHash);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record Claim(Long reportId, String claimToken) {
    }
}
