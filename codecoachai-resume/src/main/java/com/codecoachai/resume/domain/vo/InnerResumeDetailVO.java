package com.codecoachai.resume.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerResumeDetailVO {

    private Long id;
    private Long userId;
    private String title;
    private String realName;
    private String targetPosition;
    private String skillStack;
    private String workExperience;
    private String educationExperience;
    private String summary;
    private List<ResumeProjectVO> projects;
}
