package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeJobMatchDetailItemVO {

    private Long id;
    private String dimension;
    private String skillName;
    private String matchLevel;
    private Integer score;
    private String evidence;
    private String gapDescription;
    private String suggestion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
