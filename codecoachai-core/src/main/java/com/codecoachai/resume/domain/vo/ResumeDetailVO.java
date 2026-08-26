package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

@Data
public class ResumeDetailVO {

    private Long id;
    private Long userId;
    private String title;
    private String realName;
    private String email;
    private String phone;
    private String targetPosition;
    private String skillStack;
    private String workExperience;
    private String educationExperience;
    private String summary;
    private JsonNode presentationConfig;
    private Integer isDefault;
    private Integer status;
    private List<ResumeProjectVO> projects;
    private String contextEligibility;
    private String contextEligibilityReason;
    private Boolean draft;
    private Integer completionPercent;
    private List<String> missingSections;
}
