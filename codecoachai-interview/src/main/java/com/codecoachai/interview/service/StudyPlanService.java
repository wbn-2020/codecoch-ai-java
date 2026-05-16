package com.codecoachai.interview.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
import com.codecoachai.interview.domain.dto.StudyTaskStatusUpdateDTO;
import com.codecoachai.interview.domain.vo.StudyPlanDetailVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.domain.vo.StudyTaskVO;
import java.util.List;

public interface StudyPlanService {

    StudyPlanGenerateVO generate(StudyPlanGenerateDTO dto);

    PageResult<StudyPlanListVO> list(StudyPlanQueryDTO dto);

    StudyPlanDetailVO detail(Long id);

    List<StudyTaskVO> tasks(Long planId);

    StudyTaskVO updateTaskStatus(Long taskId, StudyTaskStatusUpdateDTO dto);

    StudyPlanGenerateVO regenerate(Long id);
}
