package com.codecoachai.question.service.impl;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.AgentBusinessActionFeignClient;
import com.codecoachai.question.feign.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.question.feign.vo.AgentTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
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
                    runAfterCommitSafely(userId, targetJobId, evidenceBizId,
                            () -> completeQuestionPracticeNow(userId, targetJobId, evidenceBizId));
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
            if (result == null) {
                log.warn("Question practice after-commit sync failed evidenceBizId={} operation={} reason={}",
                        evidenceBizId, "COMPLETE_QUESTION_PRACTICE", "feign result is null");
                return null;
            }
            if (!result.isSuccess()) {
                log.warn("Question practice after-commit sync failed evidenceBizId={} operation={} reason={}",
                        evidenceBizId, "COMPLETE_QUESTION_PRACTICE", result.getMessage());
                return null;
            }
            return result == null || !result.isSuccess() ? null : result.getData();
        } catch (RuntimeException ex) {
            log.warn("Question practice after-commit sync failed evidenceBizId={} operation={} reason={}",
                    evidenceBizId, "COMPLETE_QUESTION_PRACTICE", buildFailureReason(ex), ex);
            return null;
        }
    }

    private void runAfterCommitSafely(Long userId, Long targetJobId, Long evidenceBizId, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Question practice after-commit sync failed evidenceBizId={} operation={} reason={} userId={} targetJobId={}",
                    evidenceBizId, "COMPLETE_QUESTION_PRACTICE", buildFailureReason(ex), userId, targetJobId, ex);
        }
    }

    private String buildFailureReason(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return ex == null ? "unknown" : ex.getClass().getSimpleName();
        }
        return ex.getMessage();
    }
}
