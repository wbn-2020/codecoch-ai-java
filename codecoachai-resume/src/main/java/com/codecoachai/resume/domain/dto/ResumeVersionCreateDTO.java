package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class ResumeVersionCreateDTO {
    private String versionName;
    private String sourceType;
    private Long sourceId;
}
