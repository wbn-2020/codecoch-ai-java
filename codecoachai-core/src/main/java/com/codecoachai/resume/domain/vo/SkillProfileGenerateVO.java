package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SkillProfileGenerateVO {

    private Long profileId;
    private Long targetJobId;
    private Long matchReportId;
    private Integer gapCount;
    private String status;
    private String errorMessage;
    private Long aiCallLogId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
