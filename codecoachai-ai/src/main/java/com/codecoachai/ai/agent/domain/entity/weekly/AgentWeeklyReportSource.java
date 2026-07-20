package com.codecoachai.ai.agent.domain.entity.weekly;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_weekly_report_source")
public class AgentWeeklyReportSource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long snapshotId;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime sourceTime;
    private LocalDateTime sourceUpdatedAt;
    private String scopeKey;
    private String inclusionStatus;
    private String excludeReason;
    private String sourceHash;
    private String safeSummary;
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
