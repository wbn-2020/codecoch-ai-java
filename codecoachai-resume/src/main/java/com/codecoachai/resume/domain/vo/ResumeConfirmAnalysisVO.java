package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class ResumeConfirmAnalysisVO {

    private Long analysisRecordId;
    private Long resumeId;
    private String parseStatus;
    private ResumeDetailVO resume;
}
