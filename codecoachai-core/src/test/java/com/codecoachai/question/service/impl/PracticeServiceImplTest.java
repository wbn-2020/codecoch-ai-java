package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.PracticeSubmitDTO;
import com.codecoachai.question.domain.entity.PracticeRecord;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionRecommendationBatch;
import com.codecoachai.question.domain.entity.QuestionRecommendationItem;
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

    @Test
    void submitRecommendationReviewsPrivateDraftWithoutCreatingOfficialQuestion() {
        QuestionRecommendationItem item = new QuestionRecommendationItem();
        item.setId(301L);
        item.setBatchId(401L);
        item.setUserId(USER_ID);
        item.setQuestionTitle("高并发订单扣减如何保证一致性");
        item.setQuestionContent("请结合目标岗位描述，说明库存扣减的并发控制、幂等和失败补偿方案。");
        item.setQuestionType("SCENARIO");
        item.setDifficulty("HARD");
        item.setAnswerHint("说明锁、消息幂等、事务边界和补偿机制。");
        item.setEvaluatePoints("并发控制、幂等、可观测性、失败补偿");
        item.setPracticeStatus("UNPRACTICED");
        when(recommendationItemMapper.selectOne(any())).thenReturn(item);
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(401L);
        batch.setUserId(USER_ID);
        batch.setStatus("SUCCESS");
        batch.setAiCallLogId(9001L);
        batch.setSourceType("RESUME_JOB_MATCH");
        batch.setSourceId(88L);
        when(recommendationBatchMapper.selectOne(any())).thenReturn(batch);
        doAnswer(invocation -> {
            PracticeRecord record = invocation.getArgument(0);
            record.setId(7002L);
            return 1;
        }).when(practiceRecordMapper).insert(any(PracticeRecord.class));
        PracticeReviewVO review = new PracticeReviewVO();
        review.setScore(82);
        review.setSummary("并发控制和补偿路径完整。");
        when(aiPracticeFeignClient.review(any())).thenReturn(Result.success(review));

        PracticeSubmitDTO dto = new PracticeSubmitDTO();
        dto.setAnswerContent("我会用版本号或分布式锁保护扣减，并以幂等键和补偿任务处理失败链路。");

        PracticeRecordVO vo = service.submitRecommendation(301L, dto);

        ArgumentCaptor<PracticeRecord> recordCaptor = ArgumentCaptor.forClass(PracticeRecord.class);
        verify(practiceRecordMapper).insert(recordCaptor.capture());
        assertNull(recordCaptor.getValue().getQuestionId());
        assertEquals("PRIVATE_RECOMMENDATION", recordCaptor.getValue().getSource());
        assertEquals(301L, recordCaptor.getValue().getRecommendationItemId());
        assertEquals("SUCCESS", vo.getReviewStatus());
        verify(recommendationItemMapper).updateById(item);
    }

    @Test
    void recommendationQuestionLoadsOnlyCurrentUsersPrivateDraft() {
        QuestionRecommendationItem item = privateRecommendationItem();
        when(recommendationItemMapper.selectOne(any())).thenReturn(item);
        when(recommendationBatchMapper.selectOne(any())).thenReturn(successfulRecommendationBatch());

        var vo = service.recommendationQuestion(301L);

        assertEquals(301L, vo.getId());
        assertEquals("PRIVATE_RECOMMENDATION", vo.getPracticeKind());
        assertEquals(Boolean.TRUE, vo.getCanPractice());
        assertNull(vo.getPracticeQuestionId());
        assertEquals(item.getQuestionContent(), vo.getQuestionContent());
    }

    @Test
    void recommendationQuestionRejectsAnotherUsersItem() {
        when(recommendationItemMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.recommendationQuestion(301L));

        verify(recommendationBatchMapper, never()).selectOne(any());
    }

    @Test
    void submitRecommendationKeepsPrivateDraftRetryableWhenAiReviewFails() {
        QuestionRecommendationItem item = privateRecommendationItem();
        when(recommendationItemMapper.selectOne(any())).thenReturn(item);
        when(recommendationBatchMapper.selectOne(any())).thenReturn(successfulRecommendationBatch());
        doAnswer(invocation -> {
            PracticeRecord record = invocation.getArgument(0);
            record.setId(7003L);
            return 1;
        }).when(practiceRecordMapper).insert(any(PracticeRecord.class));
        when(aiPracticeFeignClient.review(any())).thenReturn(Result.fail(500, "AI review failed"));
        PracticeSubmitDTO dto = new PracticeSubmitDTO();
        dto.setAnswerContent("我会按锁、幂等和补偿三个层次说明实现方案。");

        PracticeRecordVO vo = service.submitRecommendation(301L, dto);

        ArgumentCaptor<PracticeRecord> recordCaptor = ArgumentCaptor.forClass(PracticeRecord.class);
        verify(practiceRecordMapper).insert(recordCaptor.capture());
        verify(practiceRecordMapper).updateById(recordCaptor.capture());
        PracticeRecord persisted = recordCaptor.getAllValues().get(1);
        assertNull(persisted.getQuestionId());
        assertEquals("FAILED", persisted.getReviewStatus());
        assertEquals("AI 点评暂时不可用，请稍后重试，或先查看参考解析继续练习。", persisted.getErrorMessage());
        assertEquals("FAILED", vo.getReviewStatus());
        assertEquals("UNPRACTICED", item.getPracticeStatus());
        verify(recommendationItemMapper, never()).updateById(any(QuestionRecommendationItem.class));
        verify(agentBusinessActionNotifier, never()).completeQuestionPractice(any(), any(), any());
    }

    private QuestionRecommendationItem privateRecommendationItem() {
        QuestionRecommendationItem item = new QuestionRecommendationItem();
        item.setId(301L);
        item.setBatchId(401L);
        item.setUserId(USER_ID);
        item.setQuestionTitle("高并发订单扣减如何保证一致性");
        item.setQuestionContent("请结合目标岗位描述，说明库存扣减的并发控制、幂等和失败补偿方案。");
        item.setQuestionType("SCENARIO");
        item.setDifficulty("HARD");
        item.setAnswerHint("说明锁、消息幂等、事务边界和补偿机制。");
        item.setEvaluatePoints("并发控制、幂等、可观测性、失败补偿");
        item.setPracticeStatus("UNPRACTICED");
        return item;
    }

    private QuestionRecommendationBatch successfulRecommendationBatch() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(401L);
        batch.setUserId(USER_ID);
        batch.setStatus("SUCCESS");
        batch.setAiCallLogId(9001L);
        batch.setSourceType("RESUME_JOB_MATCH");
        batch.setSourceId(88L);
        return batch;
    }
}
