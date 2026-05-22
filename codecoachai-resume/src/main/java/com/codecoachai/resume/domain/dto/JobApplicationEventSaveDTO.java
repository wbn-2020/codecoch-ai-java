package com.codecoachai.resume.domain.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class JobApplicationEventSaveDTO {
    private String eventType;
    private LocalDateTime eventTime;
    private String summary;
    private Map<String, Object> review;
    private String reviewJson;
}
