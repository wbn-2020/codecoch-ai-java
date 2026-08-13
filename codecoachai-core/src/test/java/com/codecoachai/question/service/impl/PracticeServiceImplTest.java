package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.PracticeSubmitDTO;
import com.codecoachai.question.domain.entity.PracticeRecord;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.vo.PracticeRecordVO;
import com.codecoachai.question.feign.AiPracticeFeignClient;
import com.codecoachai.question.feign.vo.PracticeReviewVO;
import com.codecoachai.question.mapper.PracticeRecordMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRecommendationBatchMapper;
import com.codecoachai.question.mapper.QuestionRecommendationItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class PracticeServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionRecommendationItemMapper recommendationItemMapper;
    @Mock
    private QuestionRecommendationBatchMapper recommendationBatchMapper;
    @Mock
    private AiPracticeFeignClient aiPracticeFeignClient;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;

    private PracticeServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("tester").build());
        service = new PracticeServiceImpl(
                practiceRecordMapper,
                questionMapper,
                recommendationItemMapper,
                recommendationBatchMapper,
                aiPracticeFeignClient,
                agentBusinessActionNotifier,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        LoginUserContext.clear();
    }

    @Test
    void submitCompletesAgentQuestionPracticeTaskWhenTargetJobContextExists() {
        TransactionSynchronizationManager.initSynchronization();
        Question question = new Question();
        question.setId(101L);
        question.setTitle("MySQL index");
        question.setContent("Explain index optimization.");
        question.setReferenceAnswer("Use indexes based on query predicates.");
        question.setStatus(CommonConstants.YES);
        when(questionMapper.selectById(101L)).thenReturn(question);
        PracticeReviewVO review = new PracticeReviewVO();
        review.setScore(80);
        review.setSummary("Good structure.");
        when(aiPracticeFeignClient.review(any())).thenReturn(Result.success(review));
        doAnswer(invocation -> {
            PracticeRecord record = invocation.getArgument(0);
            record.setId(7001L);
            return 1;
        }).when(practiceRecordMapper).insert(any(PracticeRecord.class));
        PracticeSubmitDTO dto = new PracticeSubmitDTO();
        dto.setAnswerContent("I would start from B+Tree indexes and query predicates.");
        dto.setTargetJobId(501L);

        PracticeRecordVO vo = service.submit(101L, dto);

        ArgumentCaptor<PracticeRecord> recordCaptor = ArgumentCaptor.forClass(PracticeRecord.class);
        verify(practiceRecordMapper).insert(recordCaptor.capture());
        assertEquals("TARGET_JOB", recordCaptor.getValue().getSourceType());
        assertEquals(501L, recordCaptor.getValue().getSourceId());
        verify(agentBusinessActionNotifier).completeQuestionPractice(USER_ID, 501L, 7001L);
        assertNull(vo.getAgentTaskId());
        assertFalse(vo.getAgentTaskCompleted());
    }

    @Test
    void submitDoesNotExposeRawSnapshotsToUserResponse() throws Exception {
        Question question = new Question();
        question.setId(102L);
        question.setTitle("Redis cache");
        question.setContent("Explain cache breakdown.");
        question.setReferenceAnswer("Use mutex, prewarm, and fallback.");
        question.setStatus(CommonConstants.YES);
        when(questionMapper.selectById(102L)).thenReturn(question);
        PracticeReviewVO review = new PracticeReviewVO();
        review.setScore(75);
        review.setSummary("Needs more production evidence.");
        when(aiPracticeFeignClient.review(any())).thenReturn(Result.success(review));

        PracticeSubmitDTO dto = new PracticeSubmitDTO();
        dto.setAnswerContent("I would protect hot keys with mutex and fallback.");

        PracticeRecordVO vo = service.submit(102L, dto);

        assertNull(vo.getQuestionSnapshotJson());
        assertNull(vo.getReviewJson());
        String json = new ObjectMapper().writeValueAsString(vo);
        assertFalse(json.contains("questionSnapshotJson"));
        assertFalse(json.contains("reviewJson"));
    }
}
