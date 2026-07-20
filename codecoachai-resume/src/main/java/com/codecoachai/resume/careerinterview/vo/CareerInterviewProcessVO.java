package com.codecoachai.resume.careerinterview.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerInterviewProcessVO {
    private Long id;
    private Long applicationId;
    private String status;
    private Integer currentRoundNo;
    private String outcome;
    private Integer lockVersion;
    private String interviewIdentity = "REAL_RECRUITING_INTERVIEW";
    private List<CareerInterviewRoundVO> rounds = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
