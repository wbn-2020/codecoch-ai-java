package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.controller.InnerResumeAnalysisController;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.resume.service.ResumeService;
import com.codecoachai.resume.service.TargetJobService;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.codecoachai.task.domain.vo.ReminderCandidateVO;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.dto.CompleteResumeParseDTO;
import com.codecoachai.task.feign.dto.JobDescriptionParseDTO;
import com.codecoachai.task.feign.vo.JobDescriptionAnalysisVO;
import com.codecoachai.task.feign.vo.ResumeAnalysisRawVO;
import com.codecoachai.task.feign.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.task.feign.vo.ResumeOptimizeSubmitVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalTaskResumeFeignClient implements ResumeFeignClient {

    private final InnerResumeAnalysisController innerResumeAnalysisController;
    private final ResumeJobMatchService resumeJobMatchService;
    private final ResumeService resumeService;
    private final TargetJobService targetJobService;
    private final V4ResumeCareerService v4ResumeCareerService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<ResumeAnalysisRawVO> getAnalysisRaw(Long analysisRecordId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(analysisRecordId, "analysisRecordId");
            return resultMapper.value(
                    innerResumeAnalysisController.getAnalysisRawForTask(analysisRecordId),
                    ResumeAnalysisRawVO.class);
        });
    }

    @Override
    public Result<Void> completeParse(Long analysisRecordId, CompleteResumeParseDTO dto) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(analysisRecordId, "analysisRecordId");
            return resultMapper.empty(innerResumeAnalysisController.completeParseForTask(
                    analysisRecordId,
                    resultMapper.convertValidatedBody(
                            dto,
                            InnerResumeAnalysisController.CompleteDTO.class)));
        });
    }

    @Override
    public Result<ResumeJobMatchSubmitVO> executeJobMatchReport(Long reportId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(reportId, "reportId");
            return resultMapper.value(
                    Result.success(resumeJobMatchService.executeReport(reportId)),
                    ResumeJobMatchSubmitVO.class);
        });
    }

    @Override
    public Result<ResumeJobMatchSubmitVO> failJobMatchReport(Long reportId, String reason) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(reportId, "reportId");
            return resultMapper.value(
                    Result.success(resumeJobMatchService.failExecution(reportId, reason)),
                    ResumeJobMatchSubmitVO.class);
        });
    }

    @Override
    public Result<ResumeOptimizeSubmitVO> executeResumeOptimize(Long recordId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(recordId, "recordId");
            return resultMapper.value(
                    Result.success(resumeService.executeOptimizeRecord(recordId)),
                    ResumeOptimizeSubmitVO.class);
        });
    }

    @Override
    public Result<JobDescriptionAnalysisVO> executeJobDescriptionParse(
            Long userId, Long targetJobId, JobDescriptionParseDTO dto) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            resultMapper.requireParameter(targetJobId, "targetJobId");
            return resultMapper.value(
                    Result.success(targetJobService.executeJobDescriptionParseForUser(
                            targetJobId,
                            userId,
                            resultMapper.convert(
                                    dto,
                                    com.codecoachai.resume.domain.dto.JobDescriptionParseDTO.class))),
                    JobDescriptionAnalysisVO.class);
        });
    }

    @Override
    public Result<List<ReminderCandidateVO>> listApplicationReminderCandidates(Long userId, LocalDate date) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(userId, "userId");
            return resultMapper.values(
                    Result.success(v4ResumeCareerService.listApplicationReminderCandidates(
                            userId,
                            date,
                            LocalDateTime.now())),
                    ReminderCandidateVO.class);
        });
    }
}
