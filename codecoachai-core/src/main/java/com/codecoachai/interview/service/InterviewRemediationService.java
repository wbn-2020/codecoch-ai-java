package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.InterviewRemediationCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewRemediationOptionsVO;
import com.codecoachai.interview.domain.vo.InterviewRemediationVO;

public interface InterviewRemediationService {

    InterviewRemediationVO create(InterviewRemediationCreateDTO dto);

    InterviewRemediationOptionsVO options(Long interviewId);
}
