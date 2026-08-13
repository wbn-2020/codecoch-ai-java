package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.entity.QuestionReview;
import com.codecoachai.question.feign.AiQuestionFeignClient;
import com.codecoachai.question.feign.vo.GenerateQuestionDraftVO;
import com.codecoachai.question.feign.vo.QuestionDraftItemVO;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionReviewMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
import com.codecoachai.question.mq.QuestionMqDispatcher;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class QuestionReviewServiceImplTest {

    private static final long ADMIN_USER_ID = 2001L;

    @Mock
    private AiQuestionFeignClient aiQuestionFeignClient;
    @Mock
    private QuestionReviewMapper questionReviewMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionCategoryMapper categoryMapper;
    @Mock
    private QuestionGroupMapper groupMapper;
    @Mock
    private QuestionTagMapper tagMapper;
    @Mock
    private QuestionTagRelationMapper tagRelationMapper;
    @Mock
    private QuestionDuplicateService questionDuplicateService;
    @Mock
    private QuestionEmbeddingIndexService questionEmbeddingIndexService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private QuestionMqDispatcher questionMqDispatcher;

    private QuestionReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(ADMIN_USER_ID).username("admin").build());
        service = new QuestionReviewServiceImpl(
                aiQuestionFeignClient,
                questionReviewMapper,
                questionMapper,
                categoryMapper,
                groupMapper,
                tagMapper,
                tagRelationMapper,
                questionDuplicateService,
                questionEmbeddingIndexService,
                new ObjectMapper(),
                transactionManager,
                questionMqDispatcher);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void generatePersistsMinimizedRawReviewMetadataInsteadOfFullAiResponse() {
        GenerateQuestionDraftVO response = new GenerateQuestionDraftVO();
        response.setAiCallLogId(901L);
        response.setRawResponse("{\"provider\":\"secret raw llm draft\",\"pii\":\"张三 13812345678\"}");
        QuestionDraftItemVO item = new QuestionDraftItemVO();
        item.setTitle("Explain Redis cache breakdown");
        item.setContent("Describe mutex, prewarm, and fallback strategies.");
        response.setQuestions(List.of(item));
        when(aiQuestionFeignClient.generateQuestions(any())).thenReturn(Result.success(response));
        AtomicLong idSequence = new AtomicLong(7000L);
        doAnswer(invocation -> {
            QuestionReview review = invocation.getArgument(0);
            review.setId(idSequence.incrementAndGet());
            return 1;
        }).when(questionReviewMapper).insert(any(QuestionReview.class));

        AiQuestionGenerateRequestDTO dto = new AiQuestionGenerateRequestDTO();
        dto.setTargetPosition("Java Backend");
        dto.setTechnologyStack("Spring Boot, Redis");
        dto.setCount(1);

        var result = service.generate(dto);

        ArgumentCaptor<QuestionReview> reviewCaptor = ArgumentCaptor.forClass(QuestionReview.class);
        verify(questionReviewMapper).insert(reviewCaptor.capture());
        String storedRaw = reviewCaptor.getValue().getRawAiResultJson();
        assertTrue(storedRaw.contains("\"storageMode\":\"MINIMIZED_METADATA\""));
        assertTrue(storedRaw.contains("\"aiCallLogId\":901"));
        assertTrue(storedRaw.contains("\"batchId\":\"QG"));
        assertTrue(storedRaw.contains("\"questionCount\":1"));
        assertTrue(storedRaw.contains("\"questionReviewRawStored\":false"));
        assertFalse(storedRaw.contains("secret raw llm draft"));
        assertFalse(storedRaw.contains("13812345678"));
        assertEquals(List.of(7001L), result.getReviewIds());
    }
}
