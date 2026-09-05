package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromGapDTO;
import com.codecoachai.question.domain.entity.QuestionRecommendationBatch;
import com.codecoachai.question.domain.entity.QuestionRecommendationItem;
import com.codecoachai.question.domain.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.question.feign.AiQuestionRecommendationFeignClient;
import com.codecoachai.question.feign.ResumeProfileFeignClient;
import com.codecoachai.question.feign.StudyPlanFeignClient;
import com.codecoachai.question.feign.vo.GenerateQuestionRecommendationVO;
import com.codecoachai.question.feign.vo.InnerSkillGapItemVO;
import com.codecoachai.question.feign.vo.InnerSkillProfileVO;
import com.codecoachai.question.feign.vo.QuestionRecommendationDraftItemVO;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRecommendationBatchMapper;
import com.codecoachai.question.mapper.QuestionRecommendationItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRecommendationServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private QuestionRecommendationBatchMapper batchMapper;
    @Mock
    private QuestionRecommendationItemMapper itemMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private ResumeProfileFeignClient resumeProfileFeignClient;
    @Mock
    private StudyPlanFeignClient studyPlanFeignClient;
    @Mock
    private AiQuestionRecommendationFeignClient aiRecommendationFeignClient;

    private QuestionRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("tester").build());
        service = new QuestionRecommendationServiceImpl(
                batchMapper,
                itemMapper,
                questionMapper,
                resumeProfileFeignClient,
                studyPlanFeignClient,
                aiRecommendationFeignClient,
                new ObjectMapper(),
                Optional.empty());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void generatesPracticeReadyPrivateDraftWhenNoOfficialQuestionMatches() {
        InnerSkillProfileVO profile = successfulProfile();
        when(resumeProfileFeignClient.getSkillProfile(5L)).thenReturn(Result.success(profile));
        doAnswer(invocation -> {
            QuestionRecommendationBatch batch = invocation.getArgument(0);
            batch.setId(101L);
            return 1;
        }).when(batchMapper).insert(any(QuestionRecommendationBatch.class));
        when(batchMapper.selectById(101L)).thenAnswer(invocation -> generatedBatch());
        when(questionMapper.selectOne(any())).thenReturn(null);
        when(aiRecommendationFeignClient.generate(any())).thenReturn(Result.success(aiResult()));

        QuestionRecommendationGenerateFromGapDTO dto = new QuestionRecommendationGenerateFromGapDTO();
        dto.setSkillProfileId(5L);
        dto.setGapItemIds(List.of(61L));
        dto.setQuestionCount(1);

        QuestionRecommendationGenerateVO result = service.generateFromGap(dto);

        ArgumentCaptor<QuestionRecommendationItem> itemCaptor =
                ArgumentCaptor.forClass(QuestionRecommendationItem.class);
        verify(itemMapper).insert(itemCaptor.capture());
        QuestionRecommendationItem item = itemCaptor.getValue();
        assertNull(item.getQuestionId());
        assertEquals("UNMATCHED_DRAFT", item.getMatchStatus());
        assertEquals("UNPRACTICED", item.getPracticeStatus());
        assertEquals("高并发订单扣减如何保证一致性", item.getQuestionTitle());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getQuestionCount());
        assertEquals(9001L, result.getAiCallLogId());
    }

    private InnerSkillProfileVO successfulProfile() {
        InnerSkillGapItemVO gap = new InnerSkillGapItemVO();
        gap.setId(61L);
        gap.setSkillName("分布式事务");
        gap.setSeverity("HIGH");
        gap.setPriority(1);
        gap.setGapDescription("缺少库存一致性方案的可解释证据。");
        InnerSkillProfileVO profile = new InnerSkillProfileVO();
        profile.setProfileId(5L);
        profile.setUserId(USER_ID);
        profile.setTargetJobId(88L);
        profile.setStatus("SUCCESS");
        profile.setGapItems(List.of(gap));
        return profile;
    }

    private GenerateQuestionRecommendationVO aiResult() {
        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("高并发订单扣减如何保证一致性");
        draft.setContent("请结合目标岗位描述，说明库存扣减的并发控制、幂等和失败补偿方案。");
        draft.setQuestionType("SCENARIO");
        draft.setDifficulty("HARD");
        draft.setSkillName("分布式事务");
        draft.setGapSeverity("HIGH");
        draft.setAnswerHint("说明锁、消息幂等和补偿机制。");
        draft.setEvaluatePoints("并发控制、幂等、失败补偿");
        GenerateQuestionRecommendationVO result = new GenerateQuestionRecommendationVO();
        result.setAiCallLogId(9001L);
        result.setQuestions(List.of(draft));
        return result;
    }

    private QuestionRecommendationBatch generatedBatch() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(101L);
        batch.setUserId(USER_ID);
        batch.setSourceType("JD_GAP");
        batch.setSourceId(5L);
        batch.setJobTargetId(88L);
        batch.setSkillProfileId(5L);
        batch.setStatus("SUCCESS");
        batch.setQuestionCount(1);
        batch.setAiCallLogId(9001L);
        return batch;
    }
}
