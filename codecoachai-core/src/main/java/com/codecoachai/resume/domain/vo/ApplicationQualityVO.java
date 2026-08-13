package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ApplicationQualityVO {
    private Long totalApplications = 0L;
    private Long withResumeVersionCount = 0L;
    private Long withFollowUpCount = 0L;
    private Long overdueFollowUpCount = 0L;
    private Long staleApplicationCount = 0L;
    private Long noEventApplicationCount = 0L;
    private Double resumeVersionCoverageRate = 0D;
    private Double followUpCoverageRate = 0D;
    private List<CareerInsightItemVO> warnings = new ArrayList<>();
}
