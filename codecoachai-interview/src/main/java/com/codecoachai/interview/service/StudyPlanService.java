package com.codecoachai.interview.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateFromGapDTO;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateFromMatchReportDTO;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
import com.codecoachai.interview.domain.dto.StudyTaskStatusUpdateDTO;
import com.codecoachai.interview.domain.vo.StudyPlanDailyViewVO;
import com.codecoachai.interview.domain.vo.InnerStudyPlanVO;
import com.codecoachai.interview.domain.vo.StudyPlanDetailVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.domain.vo.StudyPlanSkillRelationVO;
import com.codecoachai.interview.domain.vo.StudyPlanSourceTypeVO;
import com.codecoachai.interview.domain.vo.StudyTaskVO;
import java.util.List;

public interface StudyPlanService {

    StudyPlanGenerateVO generate(StudyPlanGenerateDTO dto);

    StudyPlanGenerateVO generateFromGap(StudyPlanGenerateFromGapDTO dto);

    StudyPlanGenerateVO generateFromMatchReport(StudyPlanGenerateFromMatchReportDTO dto);

    List<StudyPlanSourceTypeVO> sourceTypes();

    PageResult<StudyPlanListVO> list(StudyPlanQueryDTO dto);

    StudyPlanDetailVO detail(Long id);

    InnerStudyPlanVO getInnerPlan(Long id);

    List<StudyTaskVO> tasks(Long planId);

    List<StudyPlanSkillRelationVO> skillRelations(Long planId);

    StudyPlanDailyViewVO dailyView(Long planId, String date);

    StudyTaskVO updateTaskStatus(Long taskId, StudyTaskStatusUpdateDTO dto);

    StudyTaskVO completeTask(Long taskId);

    StudyTaskVO skipTask(Long taskId);

    StudyPlanGenerateVO regenerate(Long id);
}
