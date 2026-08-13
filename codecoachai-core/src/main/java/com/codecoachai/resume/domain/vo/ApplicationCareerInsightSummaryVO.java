package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ApplicationCareerInsightSummaryVO {
    private Integer rangeDays;
    private LocalDateTime generatedAt;
    private Long applicationCount = 0L;
    private Long followedUpApplicationCount = 0L;
    private Long interviewApplicationCount = 0L;
    private Long offerApplicationCount = 0L;
    private Long rejectedOrClosedApplicationCount = 0L;
    private List<ApplicationInsightItemVO> applications = new ArrayList<>();
    private ApplicationQualityVO quality = new ApplicationQualityVO();
    private ResumeVersionEffectVO resumeVersionEffect = new ResumeVersionEffectVO();
}
