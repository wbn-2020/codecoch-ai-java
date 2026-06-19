package com.codecoachai.resume.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.vo.JobApplicationEventAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.JobApplicationAgentContextVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.service.V4ResumeCareerService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/applications")
public class InnerJobApplicationController {

    private final V4ResumeCareerService v4ResumeCareerService;
    private final JobApplicationEventMapper jobApplicationEventMapper;

    @GetMapping("/users/{userId}/agent-context")
    public Result<List<JobApplicationAgentContextVO>> listAgentApplicationContextForUser(
            @PathVariable Long userId,
            @RequestParam(required = false) Long targetJobId) {
        return Result.success(v4ResumeCareerService.listAgentApplicationContextForUser(
                userId, targetJobId, LocalDateTime.now()));
    }

    @GetMapping("/users/{userId}/events/{eventId}/agent-evidence")
    public Result<JobApplicationEventAgentEvidenceVO> getApplicationEventEvidence(@PathVariable Long userId,
                                                                                 @PathVariable Long eventId) {
        JobApplicationEvent event = jobApplicationEventMapper.selectOne(new LambdaQueryWrapper<JobApplicationEvent>()
                .eq(JobApplicationEvent::getId, eventId)
                .eq(JobApplicationEvent::getUserId, userId)
                .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (event == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "job application event evidence not found");
        }
        JobApplicationEventAgentEvidenceVO vo = new JobApplicationEventAgentEvidenceVO();
        vo.setId(event.getId());
        vo.setUserId(event.getUserId());
        vo.setApplicationId(event.getApplicationId());
        vo.setEventType(event.getEventType());
        vo.setEventTime(event.getEventTime());
        return Result.success(vo);
    }
}
