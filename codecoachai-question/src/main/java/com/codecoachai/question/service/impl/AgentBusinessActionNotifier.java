package com.codecoachai.question.service.impl;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.AgentBusinessActionFeignClient;
import com.codecoachai.question.feign.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.question.feign.vo.AgentTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class AgentBusinessActionNotifier {

    private final AgentBusinessActionFeignClient agentBusinessActionFeignClient;

    public AgentTaskVO completeQuestionPractice(Long userId, Long targetJobId, Long evidenceBizId) {
        if (userId == null || targetJobId == null || evidenceBizId == null) {
            return null;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    completeQuestionPracticeNow(userId, targetJobId, evidenceBizId);
                }
            });
            return null;
        }
        return completeQuestionPracticeNow(userId, targetJobId, evidenceBizId);
    }

    private AgentTaskVO completeQuestionPracticeNow(Long userId, Long targetJobId, Long evidenceBizId) {
        AgentBusinessActionCompleteDTO event = new AgentBusinessActionCompleteDTO();
        event.setUserId(userId);
        event.setTaskType("QUESTION_PRACTICE");
        event.setRelatedBizType("TARGET_JOB");
        event.setRelatedBizId(targetJobId);
        event.setEvidenceBizType("PRACTICE_RECORD");
        event.setEvidenceBizId(evidenceBizId);
        event.setNote("Practice record #" + evidenceBizId + " submitted");
        try {
            Result<AgentTaskVO> result = agentBusinessActionFeignClient.completeBusinessAction(event);
            return result == null || !result.isSuccess() ? null : result.getData();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
