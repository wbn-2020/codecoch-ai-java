package com.codecoachai.resume.service.impl;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.AgentBusinessActionFeignClient;
import com.codecoachai.resume.feign.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.resume.feign.vo.AgentTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentBusinessActionNotifier {

    private final AgentBusinessActionFeignClient agentBusinessActionFeignClient;

    public AgentTaskVO completeApplicationFollowUp(Long userId, Long applicationId, Long eventId) {
        if (userId == null || applicationId == null || eventId == null) {
            return null;
        }
        AgentBusinessActionCompleteDTO event = new AgentBusinessActionCompleteDTO();
        event.setUserId(userId);
        event.setTaskType("APPLICATION_FOLLOW_UP");
        event.setRelatedBizType("JOB_APPLICATION");
        event.setRelatedBizId(applicationId);
        event.setEvidenceBizType("JOB_APPLICATION_EVENT");
        event.setEvidenceBizId(eventId);
        event.setNote("Job application event #" + eventId + " submitted");
        try {
            Result<AgentTaskVO> result = agentBusinessActionFeignClient.completeBusinessAction(event);
            return result == null || !result.isSuccess() ? null : result.getData();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public AgentTaskVO completeResumeOptimize(Long userId, Long targetJobId, Long optimizeRecordId) {
        if (userId == null || targetJobId == null || optimizeRecordId == null) {
            return null;
        }
        AgentBusinessActionCompleteDTO event = new AgentBusinessActionCompleteDTO();
        event.setUserId(userId);
        event.setTaskType("RESUME_OPTIMIZE");
        event.setRelatedBizType("TARGET_JOB");
        event.setRelatedBizId(targetJobId);
        event.setEvidenceBizType("RESUME_OPTIMIZE_RECORD");
        event.setEvidenceBizId(optimizeRecordId);
        event.setNote("Resume optimize record #" + optimizeRecordId + " generated");
        try {
            Result<AgentTaskVO> result = agentBusinessActionFeignClient.completeBusinessAction(event);
            return result == null || !result.isSuccess() ? null : result.getData();
        } catch (RuntimeException ex) {
            return null;
        }
    }

}
