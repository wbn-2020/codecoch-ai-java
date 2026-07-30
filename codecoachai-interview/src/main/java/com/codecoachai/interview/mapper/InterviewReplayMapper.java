package com.codecoachai.interview.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.interview.domain.entity.InterviewReplay;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

public interface InterviewReplayMapper extends BaseMapper<InterviewReplay> {

    default InterviewReplay selectActiveByIdempotencyKeyForUpdate(
            Long userId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapper<InterviewReplay>()
                .eq(InterviewReplay::getUserId, userId)
                .eq(InterviewReplay::getIdempotencyKey, idempotencyKey)
                .eq(InterviewReplay::getDeleted, CommonConstants.NO)
                .last("limit 1 for update"));
    }

    default InterviewReplay selectOwnedClaimForUpdate(Long id, String claimToken) {
        return selectOne(new LambdaQueryWrapper<InterviewReplay>()
                .eq(InterviewReplay::getId, id)
                .eq(InterviewReplay::getClaimToken, claimToken)
                .eq(InterviewReplay::getStatus, "CREATING")
                .eq(InterviewReplay::getDeleted, CommonConstants.NO)
                .last("limit 1 for update"));
    }

    default int replaceClaim(
            InterviewReplay existing, String claimToken, LocalDateTime claimedAt) {
        LambdaUpdateWrapper<InterviewReplay> update = new LambdaUpdateWrapper<InterviewReplay>()
                .eq(InterviewReplay::getId, existing.getId())
                .eq(InterviewReplay::getStatus, existing.getStatus())
                .eq(InterviewReplay::getDeleted, CommonConstants.NO)
                .set(InterviewReplay::getStatus, "CREATING")
                .set(InterviewReplay::getClaimToken, claimToken)
                .set(InterviewReplay::getClaimedAt, claimedAt);
        if (StringUtils.hasText(existing.getClaimToken())) {
            update.eq(InterviewReplay::getClaimToken, existing.getClaimToken());
        } else {
            update.isNull(InterviewReplay::getClaimToken);
        }
        if (existing.getClaimedAt() != null) {
            update.eq(InterviewReplay::getClaimedAt, existing.getClaimedAt());
        } else {
            update.isNull(InterviewReplay::getClaimedAt);
        }
        return update(null, update);
    }

    default int markCreated(Long id, String claimToken, Long targetSessionId) {
        return update(null, new LambdaUpdateWrapper<InterviewReplay>()
                .eq(InterviewReplay::getId, id)
                .eq(InterviewReplay::getClaimToken, claimToken)
                .eq(InterviewReplay::getStatus, "CREATING")
                .eq(InterviewReplay::getDeleted, CommonConstants.NO)
                .set(InterviewReplay::getTargetSessionId, targetSessionId)
                .set(InterviewReplay::getStatus, "CREATED")
                .set(InterviewReplay::getClaimToken, null)
                .set(InterviewReplay::getClaimedAt, null));
    }

    default int releaseClaim(Long id, String claimToken) {
        return update(null, new LambdaUpdateWrapper<InterviewReplay>()
                .eq(InterviewReplay::getId, id)
                .eq(InterviewReplay::getClaimToken, claimToken)
                .eq(InterviewReplay::getStatus, "CREATING")
                .eq(InterviewReplay::getDeleted, CommonConstants.NO)
                .set(InterviewReplay::getStatus, "FAILED")
                .set(InterviewReplay::getClaimToken, null)
                .set(InterviewReplay::getClaimedAt, null));
    }
}
