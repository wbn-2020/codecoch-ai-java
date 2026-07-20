package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.JobApplicationEventReviewGenerateDTO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO;

public interface JobApplicationEventReviewService {

    JobApplicationEventStructuredReviewVO generate(
            Long applicationId,
            Long eventId,
            JobApplicationEventReviewGenerateDTO request);
}
