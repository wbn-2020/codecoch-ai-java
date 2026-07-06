package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.AdminQuestionSaveDTO;
import com.codecoachai.question.domain.dto.QuestionQueryDTO;
import com.codecoachai.question.domain.dto.SubmitQuestionAnswerDTO;
import com.codecoachai.question.domain.entity.PracticeRecord;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.QuestionTagRelation;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.feign.vo.AgentTaskVO;
import com.codecoachai.question.mapper.PracticeRecordMapper;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRelationMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
import com.codecoachai.question.mapper.UserQuestionRecordMapper;
import com.codecoachai.question.mq.QuestionMqDispatcher;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionCategoryMapper categoryMapper;
    @Mock
    private QuestionGroupMapper groupMapper;
    @Mock
    private QuestionRelationMapper relationMapper;
    @Mock
    private QuestionTagMapper tagMapper;
    @Mock
    private QuestionTagRelationMapper tagRelationMapper;
    @Mock
    private UserQuestionRecordMapper recordMapper;
    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private QuestionDuplicateService questionDuplicateService;
    @Mock
    private QuestionEmbeddingIndexService questionEmbeddingIndexService;
    @Mock
    private QuestionMqDispatcher questionMqDispatcher;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;

    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        initTableInfo(Question.class);
        initTableInfo(QuestionTagRelation.class);
        questionService = new QuestionServiceImpl(
                questionMapper,
                categoryMapper,
                groupMapper,
                relationMapper,
                tagMapper,
                tagRelationMapper,
                recordMapper,
                practiceRecordMapper,
                questionDuplicateService,
                questionEmbeddingIndexService,
                questionMqDispatcher,
                agentBusinessActionNotifier);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1001L);
        LoginUserContext.setLoginUser(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void pageFavoritesBatchLoadsQuestionsAndFiltersUnavailableQuestions() {
        QuestionQueryDTO query = new QuestionQueryDTO();
        Page<UserQuestionRecord> page = Page.of(1, 10);
        page.setTotal(3);
        page.setRecords(List.of(record(11L), record(12L), record(13L)));
        when(recordMapper.selectPage(any(), any())).thenReturn(page);
        when(questionMapper.selectBatchIds(List.of(11L, 12L, 13L))).thenReturn(List.of(
                question(11L, CommonConstants.YES),
                question(12L, CommonConstants.NO)
        ));

        var result = questionService.pageFavorites(query);

        assertEquals(1, result.getRecords().size());
        assertEquals(11L, result.getRecords().get(0).getId());
        verify(questionMapper).selectBatchIds(List.of(11L, 12L, 13L));
        verify(questionMapper, never()).selectById(any());
    }

    @Test
    void pageWrongRecordsBatchLoadsQuestionsAndSkipsMissingQuestions() {
        QuestionQueryDTO query = new QuestionQueryDTO();
        Page<UserQuestionRecord> page = Page.of(1, 10);
        page.setTotal(2);
        page.setRecords(List.of(record(21L), record(22L)));
        when(recordMapper.selectPage(any(), any())).thenReturn(page);
        when(questionMapper.selectBatchIds(List.of(21L, 22L))).thenReturn(List.of(question(21L, CommonConstants.YES)));

        var result = questionService.pageWrongRecords(query);

        assertEquals(1, result.getRecords().size());
        assertEquals(21L, result.getRecords().get(0).getQuestionId());
        verify(questionMapper).selectBatchIds(List.of(21L, 22L));
        verify(questionMapper, never()).selectById(any());
    }

    @Test
    void submitAnswerDoesNotCompleteAgentPracticeTaskWithoutTargetJobContext() {
        when(questionMapper.selectById(31L)).thenReturn(question(31L, CommonConstants.YES));
        when(recordMapper.selectOne(any())).thenReturn(null);
        SubmitQuestionAnswerDTO dto = new SubmitQuestionAnswerDTO();
        dto.setAnswerContent("Use B+Tree indexes for selective predicates.");

        var result = questionService.submitAnswer(31L, dto);

        assertNull(result.getAgentTaskId());
        assertFalse(result.getAgentTaskCompleted());
        verify(practiceRecordMapper, never()).insert(any(PracticeRecord.class));
        verify(agentBusinessActionNotifier, never()).completeQuestionPractice(any(), any(), any());
    }

    @Test
    void submitAnswerCompletesAgentPracticeTaskWhenTargetJobContextExists() {
        when(questionMapper.selectById(32L)).thenReturn(question(32L, CommonConstants.YES));
        when(recordMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            UserQuestionRecord record = invocation.getArgument(0);
            record.setId(3001L);
            return 1;
        }).when(recordMapper).insert(any(UserQuestionRecord.class));
        doAnswer(invocation -> {
            PracticeRecord record = invocation.getArgument(0);
            record.setId(8001L);
            return 1;
        }).when(practiceRecordMapper).insert(any(PracticeRecord.class));
        AgentTaskVO task = new AgentTaskVO();
        task.setId(9001L);
        task.setTitle("练习 MySQL 高频题");
        task.setStatus("DONE");
        task.setReviewSummary("已完成今日题库练习。");
        when(agentBusinessActionNotifier.completeQuestionPractice(1001L, 501L, 8001L)).thenReturn(task);
        SubmitQuestionAnswerDTO dto = new SubmitQuestionAnswerDTO();
        dto.setAnswerContent("Use B+Tree indexes for selective predicates.");
        dto.setTargetJobId(501L);

        var result = questionService.submitAnswer(32L, dto);

        var practiceCaptor = org.mockito.ArgumentCaptor.forClass(PracticeRecord.class);
        verify(practiceRecordMapper).insert(practiceCaptor.capture());
        assertEquals(1001L, practiceCaptor.getValue().getUserId());
        assertEquals(32L, practiceCaptor.getValue().getQuestionId());
        assertEquals("TARGET_JOB", practiceCaptor.getValue().getSourceType());
        assertEquals(501L, practiceCaptor.getValue().getSourceId());
        assertEquals(9001L, result.getAgentTaskId());
        assertEquals("练习 MySQL 高频题", result.getAgentTaskTitle());
        assertEquals("DONE", result.getAgentTaskStatus());
        assertEquals("已完成今日题库练习。", result.getAgentReviewSummary());
        assertTrue(Boolean.TRUE.equals(result.getAgentTaskCompleted()));
    }

    @Test
    void updateQuestionRequiresCurrentUserBeforeMutatingQuestion() {
        LoginUserContext.clear();
        AdminQuestionSaveDTO dto = questionSaveDto();

        BusinessException ex = assertThrows(BusinessException.class, () -> questionService.updateQuestion(41L, dto));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(questionMapper, never()).updateById(any(Question.class));
        verify(tagRelationMapper, never()).delete(any());
        verify(questionDuplicateService, never()).checkDuplicateForQuestion(any(), any());
        verify(questionMqDispatcher, never()).dispatchQuestionSearchUpsert(any(), any());
    }

    @Test
    void deleteQuestionRemovesTagRelationsBeforeDeletingQuestion() {
        questionService.deleteQuestion(51L);

        var inOrder = inOrder(tagRelationMapper, questionMapper);
        inOrder.verify(tagRelationMapper).delete(any());
        inOrder.verify(questionMapper).deleteById(51L);
    }

    @Test
    void deleteTagOnlyCountsActiveRelationsToExistingQuestions() {
        QuestionMetadataServiceImpl metadataService = new QuestionMetadataServiceImpl(
                categoryMapper,
                tagMapper,
                groupMapper,
                questionMapper,
                tagRelationMapper);
        when(tagRelationMapper.selectCount(any())).thenReturn(0L);

        metadataService.deleteTag(13L);

        ArgumentCaptor<LambdaQueryWrapper<QuestionTagRelation>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tagRelationMapper).selectCount(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("tag_id"), sqlSegment);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(13L),
                wrapperCaptor.getValue().getParamNameValuePairs().toString());
        verify(tagMapper).deleteById(13L);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    private static UserQuestionRecord record(Long questionId) {
        UserQuestionRecord record = new UserQuestionRecord();
        record.setQuestionId(questionId);
        return record;
    }

    private static Question question(Long id, Integer status) {
        Question question = new Question();
        question.setId(id);
        question.setTitle("Question " + id);
        question.setContent("Content " + id);
        question.setStatus(status);
        return question;
    }

    private static AdminQuestionSaveDTO questionSaveDto() {
        AdminQuestionSaveDTO dto = new AdminQuestionSaveDTO();
        dto.setTitle("Updated question");
        dto.setContent("Updated content");
        dto.setReferenceAnswer("Updated answer");
        dto.setAnalysis("Updated analysis");
        dto.setStatus(CommonConstants.YES);
        return dto;
    }
}
