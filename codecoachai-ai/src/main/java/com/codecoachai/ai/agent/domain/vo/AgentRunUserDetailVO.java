package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentRunUserDetailVO {

    private Long id;
    private String agentType;
    private Long targetJobId;
    private LocalDate planDate;
    private String triggerType;
    private String status;
    private String summary;
    private List<SkillTagVO> focusSkills = new ArrayList<>();
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private List<AgentTaskVO> tasks = new ArrayList<>();
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
