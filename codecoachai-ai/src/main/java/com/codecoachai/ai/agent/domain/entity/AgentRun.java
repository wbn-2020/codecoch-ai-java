package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_run")
public class AgentRun extends BaseEntity {

    private Long userId;
    private String agentType;
    private Long targetJobId;
    private LocalDate planDate;
    private String triggerType;
    private String status;
    private String executionToken;
    private String inputSnapshotJson;
    private String outputJson;
    private String rawOutputText;
    private String promptType;
    private Long promptVersionId;
    private String modelName;
    private String traceId;
    private Long aiCallLogId;
    private String resultSource;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
