package com.codecoachai.ai.agent.domain.entity.weekly;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_weekly_report")
public class AgentWeeklyReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long targetJobId;
    private String targetScopeKey;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String timezone;
    private Long currentSnapshotId;
    private String reportStatus;
    private Integer snapshotVersion;
    private String summary;
    private String confidenceLevel;
    private Integer fallback;
    private String fallbackReason;
    private String generationClaimFingerprint;
    private String generationClaimToken;
    private String generationClaimIdempotencyKeyHash;
    private String generationClaimPayloadHash;
    private LocalDateTime generationClaimedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    private String liveIdentityKey;
}
