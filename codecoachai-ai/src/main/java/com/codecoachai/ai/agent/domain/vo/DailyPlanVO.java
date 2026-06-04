package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DailyPlanVO {

    private Long runId;
    private Long targetJobId;
    private LocalDate date;
    private String summary;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private List<SkillTagVO> focusSkills = new ArrayList<>();
    private List<AgentTaskVO> tasks = new ArrayList<>();
    private Boolean empty = false;
    private String emptyMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
