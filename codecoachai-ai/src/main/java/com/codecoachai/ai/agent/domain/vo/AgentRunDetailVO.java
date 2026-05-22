package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentRunDetailVO {

    private Long id;
    private Long userId;
    private String agentType;
    private Long targetJobId;
    private LocalDate planDate;
    private String triggerType;
    private String status;
    private String inputSnapshotJson;
    private String outputJson;
    private String rawOutputText;
    private String promptType;
    private Long promptVersionId;
    private String modelName;
    private String traceId;
    private Long aiCallLogId;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private List<AgentTaskVO> tasks = new ArrayList<>();
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
