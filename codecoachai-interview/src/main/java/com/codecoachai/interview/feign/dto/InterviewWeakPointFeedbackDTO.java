package com.codecoachai.interview.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class InterviewWeakPointFeedbackDTO {

    private Long userId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private Long interviewId;
    private Long reportId;
    private List<String> weakPoints;
}
