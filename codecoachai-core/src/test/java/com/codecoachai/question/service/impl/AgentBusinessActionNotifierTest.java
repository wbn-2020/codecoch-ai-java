package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.question.feign.AgentBusinessActionFeignClient;
import com.codecoachai.question.feign.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.question.feign.vo.AgentTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AgentBusinessActionNotifierTest {

    @Mock
    private AgentBusinessActionFeignClient agentBusinessActionFeignClient;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void completeQuestionPracticeDefersFeignCallbackUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AgentTaskVO task = new AgentTaskVO();
        task.setId(99L);
        task.setStatus("DONE");
        when(agentBusinessActionFeignClient.completeBusinessAction(any())).thenReturn(Result.success(task));
        AgentBusinessActionNotifier notifier = new AgentBusinessActionNotifier(agentBusinessActionFeignClient);

        AgentTaskVO result = notifier.completeQuestionPractice(10L, 501L, 7001L);

        assertNull(result);
        verify(agentBusinessActionFeignClient, never()).completeBusinessAction(any());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        ArgumentCaptor<AgentBusinessActionCompleteDTO> captor =
                ArgumentCaptor.forClass(AgentBusinessActionCompleteDTO.class);
        verify(agentBusinessActionFeignClient).completeBusinessAction(captor.capture());
        AgentBusinessActionCompleteDTO event = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(10L, event.getUserId());
        org.junit.jupiter.api.Assertions.assertEquals("QUESTION_PRACTICE", event.getTaskType());
        org.junit.jupiter.api.Assertions.assertEquals("TARGET_JOB", event.getRelatedBizType());
        org.junit.jupiter.api.Assertions.assertEquals(501L, event.getRelatedBizId());
        org.junit.jupiter.api.Assertions.assertEquals("PRACTICE_RECORD", event.getEvidenceBizType());
        org.junit.jupiter.api.Assertions.assertEquals(7001L, event.getEvidenceBizId());
    }

    @Test
    void completeQuestionPracticeSkipsCallbackWithoutPracticeRecordEvidence() {
        AgentBusinessActionNotifier notifier = new AgentBusinessActionNotifier(agentBusinessActionFeignClient);

        AgentTaskVO result = notifier.completeQuestionPractice(10L, 501L, null);

        assertNull(result);
        verify(agentBusinessActionFeignClient, never()).completeBusinessAction(any());
    }
}
