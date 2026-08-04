package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.controller.InnerJobApplicationController;
import com.codecoachai.resume.controller.InnerResumeAnalysisController;
import com.codecoachai.resume.controller.InnerResumeController;
import com.codecoachai.resume.controller.InnerResumeJobMatchController;
import com.codecoachai.resume.controller.InnerTargetJobController;
import com.codecoachai.task.domain.vo.ReminderCandidateVO;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.dto.CompleteResumeParseDTO;
import com.codecoachai.task.feign.dto.JobDescriptionParseDTO;
import com.codecoachai.task.feign.vo.JobDescriptionAnalysisVO;
import com.codecoachai.task.feign.vo.ResumeAnalysisRawVO;
import com.codecoachai.task.feign.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.task.feign.vo.ResumeOptimizeSubmitVO;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalTaskResumeFeignClient implements ResumeFeignClient {

    private final InnerResumeAnalysisController innerResumeAnalysisController;
    private final InnerResumeJobMatchController innerResumeJobMatchController;
    private final InnerResumeController innerResumeController;
    private final InnerTargetJobController innerTargetJobController;
    private final InnerJobApplicationController innerJobApplicationController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<ResumeAnalysisRawVO> getAnalysisRaw(Long analysisRecordId) {
        return resultMapper.value(
                innerResumeAnalysisController.getAnalysisRawForTask(analysisRecordId),
                ResumeAnalysisRawVO.class);
    }

    @Override
    public Result<Void> completeParse(Long analysisRecordId, CompleteResumeParseDTO dto) {
        return resultMapper.empty(innerResumeAnalysisController.completeParseForTask(
                analysisRecordId,
                resultMapper.convert(dto, InnerResumeAnalysisController.CompleteDTO.class)));
    }

    @Override
    public Result<ResumeJobMatchSubmitVO> executeJobMatchReport(Long reportId) {
        return resultMapper.value(
                innerResumeJobMatchController.executeReport(reportId),
                ResumeJobMatchSubmitVO.class);
    }

    @Override
    public Result<ResumeOptimizeSubmitVO> executeResumeOptimize(Long recordId) {
        return resultMapper.value(innerResumeController.executeOptimizeRecord(recordId), ResumeOptimizeSubmitVO.class);
    }

    @Override
    public Result<JobDescriptionAnalysisVO> executeJobDescriptionParse(
            Long userId, Long targetJobId, JobDescriptionParseDTO dto) {
        return resultMapper.value(
                innerTargetJobController.parse(
                        userId,
                        targetJobId,
                        resultMapper.convert(dto, com.codecoachai.resume.domain.dto.JobDescriptionParseDTO.class)),
                JobDescriptionAnalysisVO.class);
    }

    @Override
    public Result<List<ReminderCandidateVO>> listApplicationReminderCandidates(Long userId, LocalDate date) {
        return resultMapper.values(
                innerJobApplicationController.listApplicationReminderCandidates(userId, date),
                ReminderCandidateVO.class);
    }
}
