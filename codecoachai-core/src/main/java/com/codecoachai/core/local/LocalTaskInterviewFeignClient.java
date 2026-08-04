package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.controller.InnerInterviewReportController;
import com.codecoachai.interview.controller.InnerStudyPlanController;
import com.codecoachai.task.feign.InterviewFeignClient;
import com.codecoachai.task.feign.dto.CompleteInterviewReportDTO;
import com.codecoachai.task.feign.vo.InterviewReportContextVO;
import com.codecoachai.task.feign.vo.StudyPlanGenerateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalTaskInterviewFeignClient implements InterviewFeignClient {

    private final InnerInterviewReportController innerInterviewReportController;
    private final InnerStudyPlanController innerStudyPlanController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InterviewReportContextVO> getReportContext(Long sessionId) {
        return resultMapper.value(
                innerInterviewReportController.getReportContext(sessionId),
                InterviewReportContextVO.class);
    }

    @Override
    public Result<Void> completeReport(Long sessionId, CompleteInterviewReportDTO dto) {
        return resultMapper.empty(innerInterviewReportController.completeReport(
                sessionId,
                resultMapper.convert(dto, InnerInterviewReportController.CompleteReportDTO.class)));
    }

    @Override
    public Result<StudyPlanGenerateVO> executeStudyPlan(Long planId) {
        return resultMapper.value(innerStudyPlanController.execute(planId), StudyPlanGenerateVO.class);
    }

    @Override
    public Result<StudyPlanGenerateVO> executeStudyPlan(Long userId, Long planId) {
        return resultMapper.value(innerStudyPlanController.execute(userId, planId), StudyPlanGenerateVO.class);
    }
}
