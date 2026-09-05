package com.codecoachai.resume.domain.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class ApplicationPackageActionExecuteDTO {

    private String note;
    private String status;
    private String source;
    private LocalDateTime appliedAt;
    private LocalDateTime nextFollowUpAt;
    private Map<String, Object> payload;
}
