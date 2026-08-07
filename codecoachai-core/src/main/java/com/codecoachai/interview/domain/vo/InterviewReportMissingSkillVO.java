package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InterviewReportMissingSkillVO {

    private Long id;
    private String skillName;
    private String severity;
    private String gapDescription;
    private List<String> recommendedActions;
    private Integer priority;
    private String sourceType;
    private Long sourceBizId;
}
