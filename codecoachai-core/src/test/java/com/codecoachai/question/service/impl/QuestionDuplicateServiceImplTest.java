package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.config.QuestionDuplicateProperties;
import com.codecoachai.question.domain.dto.QuestionDuplicateCheckDTO;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionDuplicateReview;
import com.codecoachai.question.domain.entity.QuestionRelation;
import com.codecoachai.question.domain.vo.QuestionDuplicateCheckResultVO;
import com.codecoachai.question.mapper.QuestionDuplicateReviewMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRelationMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class QuestionDuplicateServiceImplTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionGroupMapper groupMapper;
    @Mock
    private QuestionDuplicateReviewMapper duplicateReviewMapper;
    @Mock
    private QuestionRelationMapper relationMapper;
    @Mock
    private QuestionTagRelationMapper tagRelationMapper;
    @Mock
    private QuestionEmbeddingIndexService questionEmbeddingIndexService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private QuestionDuplicateServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(9L).username("reviewer").build());
        QuestionDuplicateProperties properties = new QuestionDuplicateProperties();
        service = new QuestionDuplicateServiceImpl(
                questionMapper,
                groupMapper,
                duplicateReviewMapper,
                relationMapper,
                tagRelationMapper,
                questionEmbeddingIndexService,
                properties,
                new ObjectMapper(),
                transactionTemplate);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void batchCheckPreloadsExistingPairsInsteadOfIssuingPerCandidateCountQueries() {
        Question source = question(1L, "Java 并发基础", "source");
        Question existingCandidate = question(2L, "Java 并发基础", "existing");
        Question newCandidate = question(3L, "Java 并发基础", "new");

        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(source));
        when(questionMapper.selectList(any()))
                .thenReturn(List.of(existingCandidate, newCandidate))
                .thenReturn(List.of(existingCandidate, newCandidate));
        when(questionEmbeddingIndexService.searchSimilarIndexed(eq(1L), anyInt(), anyDouble()))
                .thenReturn(List.of());

        QuestionRelation existingRelation = new QuestionRelation();
        existingRelation.setSourceQuestionId(1L);
        existingRelation.setTargetQuestionId(2L);
        when(relationMapper.selectExistingActivePairs(eq(1L), anyList()))
                .thenReturn(List.of(existingRelation));
        when(duplicateReviewMapper.selectExistingPairs(eq(1L), anyList()))
                .thenReturn(List.of());
        when(duplicateReviewMapper.insert(any(QuestionDuplicateReview.class))).thenAnswer(invocation -> {
            QuestionDuplicateReview review = invocation.getArgument(0);
            review.setId(100L);
            return 1;
        });

        QuestionDuplicateCheckDTO request = new QuestionDuplicateCheckDTO();
        request.setQuestionIds(List.of(1L));
        QuestionDuplicateCheckResultVO result = service.checkDuplicate(request);

        assertEquals(1, result.getCheckedCount());
        assertEquals(1, result.getCreatedCount());
        assertEquals(List.of(100L), result.getReviewIds());
        verify(questionMapper).selectBatchIds(List.of(1L));
        verify(questionMapper, never()).selectById(1L);
        verify(relationMapper, never()).selectCount(any());
        verify(duplicateReviewMapper, never()).selectCount(any());

        ArgumentCaptor<List<Long>> candidateIds = ArgumentCaptor.forClass(List.class);
        verify(relationMapper).selectExistingActivePairs(eq(1L), candidateIds.capture());
        verify(duplicateReviewMapper).selectExistingPairs(eq(1L), anyList());
        assertEquals(2, candidateIds.getValue().size());
        assertTrue(candidateIds.getValue().containsAll(List.of(2L, 3L)));
    }

    private Question question(Long id, String title, String content) {
        Question question = new Question();
        question.setId(id);
        question.setTitle(title);
        question.setContent(content);
        question.setStatus(1);
        return question;
    }
}
