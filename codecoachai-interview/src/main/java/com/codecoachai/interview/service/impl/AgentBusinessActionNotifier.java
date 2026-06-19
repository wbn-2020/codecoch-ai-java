package com.codecoachai.interview.service.impl;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.AgentBusinessActionFeignClient;
import com.codecoachai.interview.feign.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.interview.feign.vo.AgentTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class AgentBusinessActionNotifier {

    private final AgentBusinessActionFeignClient agentBusinessActionFeignClient;

    public AgentTaskVO completeInterviewReport(Long userId, Long targetJobId, Long reportId) {
        if (userId == null || targetJobId == null || reportId == null) {
            return null;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    completeInterviewReportNow(userId, targetJobId, reportId);
                }
            });
            return null;
        }
        return completeInterviewReportNow(userId, targetJobId, reportId);
    }

    private AgentTaskVO completeInterviewReportNow(Long userId, Long targetJobId, Long reportId) {
        AgentBusinessActionCompleteDTO event = new AgentBusinessActionCompleteDTO();
        event.setUserId(userId);
        event.setTaskType("INTERVIEW");
        event.setRelatedBizType("TARGET_JOB");
        event.setRelatedBizId(targetJobId);
        event.setEvidenceBizType("INTERVIEW_REPORT");
        event.setEvidenceBizId(reportId);
        event.setNote("Interview report #" + reportId + " generated");
        try {
            Result<AgentTaskVO> result = agentBusinessActionFeignClient.completeBusinessAction(event);
            return result == null || !result.isSuccess() ? null : result.getData();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
