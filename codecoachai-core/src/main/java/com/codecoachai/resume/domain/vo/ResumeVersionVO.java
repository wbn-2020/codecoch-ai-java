package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class ResumeVersionVO {
    private Long id;
    private Long resumeId;
    private Integer versionNo;
    private String versionName;
    private String sourceType;
    private Long sourceId;
    private Integer currentFlag;
    private Map<String, Object> snapshot;
    private LocalDateTime createdAt;
}
