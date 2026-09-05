package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.InterviewComparisonCreateDTO;
import com.codecoachai.interview.domain.vo.InterviewComparisonVO;
import java.util.List;

public interface InterviewComparisonService {

    InterviewComparisonVO compare(InterviewComparisonCreateDTO dto);

    List<InterviewComparisonVO> list(Integer limit);

    InterviewComparisonVO detail(Long id);
}
