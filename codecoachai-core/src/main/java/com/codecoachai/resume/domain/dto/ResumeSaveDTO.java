package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class ResumeSaveDTO {

    private String title;
    private String resumeName;

    private String realName;
    private String email;
    private String phone;
    private String targetPosition;
    private String skillStack;
    private String workExperience;
    private String educationExperience;
    private String summary;
    private Integer status;
}
