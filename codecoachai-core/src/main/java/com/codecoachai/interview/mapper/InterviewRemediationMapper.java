package com.codecoachai.interview.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.interview.domain.entity.InterviewRemediation;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

public interface InterviewRemediationMapper extends BaseMapper<InterviewRemediation> {

    default InterviewRemediation selectActiveByIdempotencyKeyForUpdate(
            Long userId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapper<InterviewRemediation>()
                .eq(InterviewRemediation::getUserId, userId)
                .eq(InterviewRemediation::getIdempotencyKey, idempotencyKey)
                .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                .last("limit 1 for update"));
    }

    default InterviewRemediation selectOwnedClaimForUpdate(Long id, String claimToken) {
        return selectOne(new LambdaQueryWrapper<InterviewRemediation>()
                .eq(InterviewRemediation::getId, id)
                .eq(InterviewRemediation::getClaimToken, claimToken)
                .eq(InterviewRemediation::getStatus, "CREATING")
                .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                .last("limit 1 for update"));
    }

    default InterviewRemediation selectPreferredBySourceReport(
            Long userId, Long sourceReportId) {
        InterviewRemediation created = selectOne(
                new LambdaQueryWrapper<InterviewRemediation>()
                        .eq(InterviewRemediation::getUserId, userId)
                        .eq(InterviewRemediation::getSourceReportId, sourceReportId)
                        .eq(InterviewRemediation::getStatus, "CREATED")
                        .isNotNull(InterviewRemediation::getTargetSessionId)
                        .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                        .orderByDesc(InterviewRemediation::getCreatedAt)
                        .orderByDesc(InterviewRemediation::getId)
                        .last("limit 1"));
        if (created != null) {
            return created;
        }
        return selectOne(new LambdaQueryWrapper<InterviewRemediation>()
                .eq(InterviewRemediation::getUserId, userId)
                .eq(InterviewRemediation::getSourceReportId, sourceReportId)
                .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewRemediation::getCreatedAt)
                .orderByDesc(InterviewRemediation::getId)
                .last("limit 1"));
    }

    default int replaceClaim(
            InterviewRemediation existing, String claimToken, LocalDateTime claimedAt) {
        LambdaUpdateWrapper<InterviewRemediation> update =
                new LambdaUpdateWrapper<InterviewRemediation>()
                        .eq(InterviewRemediation::getId, existing.getId())
                        .eq(InterviewRemediation::getStatus, existing.getStatus())
                        .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                        .set(InterviewRemediation::getStatus, "CREATING")
                        .set(InterviewRemediation::getClaimToken, claimToken)
                        .set(InterviewRemediation::getClaimedAt, claimedAt);
        if (StringUtils.hasText(existing.getClaimToken())) {
            update.eq(InterviewRemediation::getClaimToken, existing.getClaimToken());
        } else {
            update.isNull(InterviewRemediation::getClaimToken);
        }
        if (existing.getClaimedAt() != null) {
            update.eq(InterviewRemediation::getClaimedAt, existing.getClaimedAt());
        } else {
            update.isNull(InterviewRemediation::getClaimedAt);
        }
        return update(null, update);
    }

    default int markCreated(Long id, String claimToken, Long targetSessionId) {
        return update(null, new LambdaUpdateWrapper<InterviewRemediation>()
                .eq(InterviewRemediation::getId, id)
                .eq(InterviewRemediation::getClaimToken, claimToken)
                .eq(InterviewRemediation::getStatus, "CREATING")
                .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                .set(InterviewRemediation::getTargetSessionId, targetSessionId)
                .set(InterviewRemediation::getStatus, "CREATED")
                .set(InterviewRemediation::getClaimToken, null)
                .set(InterviewRemediation::getClaimedAt, null));
    }

    default int releaseClaim(Long id, String claimToken) {
        return update(null, new LambdaUpdateWrapper<InterviewRemediation>()
                .eq(InterviewRemediation::getId, id)
                .eq(InterviewRemediation::getClaimToken, claimToken)
                .eq(InterviewRemediation::getStatus, "CREATING")
                .eq(InterviewRemediation::getDeleted, CommonConstants.NO)
                .set(InterviewRemediation::getStatus, "FAILED")
                .set(InterviewRemediation::getClaimToken, null)
                .set(InterviewRemediation::getClaimedAt, null));
    }
}
