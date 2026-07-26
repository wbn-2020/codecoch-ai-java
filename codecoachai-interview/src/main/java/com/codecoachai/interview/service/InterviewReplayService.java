package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.InterviewReplayCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewReplayVO;

public interface InterviewReplayService {

    InterviewReplayVO create(Long sourceSessionId, InterviewReplayCreateDTO dto);
}
