package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.controller.InnerInterviewReportController;
import com.codecoachai.interview.service.StudyPlanService;
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
    private final StudyPlanService studyPlanService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InterviewReportContextVO> getReportContext(Long sessionId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(sessionId, "sessionId");
            return resultMapper.value(
                    innerInterviewReportController.getReportContext(sessionId),
                    InterviewReportContextVO.class);
        });
    }

    @Override
    public Result<Void> completeReport(Long sessionId, CompleteInterviewReportDTO dto) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(sessionId, "sessionId");
            return resultMapper.empty(innerInterviewReportController.completeReport(
                    sessionId,
                    resultMapper.convertRequiredBody(
                            dto,
                            InnerInterviewReportController.CompleteReportDTO.class)));
        });
    }

    @Override
    public Result<StudyPlanGenerateVO> executeStudyPlan(Long planId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(planId, "planId");
            return resultMapper.value(
                    Result.success(studyPlanService.executeGeneration(planId)),
                    StudyPlanGenerateVO.class);
        });
    }

    @Override
    public Result<StudyPlanGenerateVO> executeStudyPlan(Long userId, Long planId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            resultMapper.requireParameter(planId, "planId");
            return resultMapper.value(
                    Result.success(studyPlanService.executeGeneration(planId, userId)),
                    StudyPlanGenerateVO.class);
        });
    }
}
