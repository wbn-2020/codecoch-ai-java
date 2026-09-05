package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PortfolioDemoStatusVO {

    private Boolean loaded;
    private String datasetKey;
    private String datasetName;
    private String status;
    private String version;
    private Long demoUserId;
    private Boolean demoData;
    private Boolean readOnly;
    private LocalDateTime loadedAt;
    private LocalDateTime resetAt;
    private String message;
}
