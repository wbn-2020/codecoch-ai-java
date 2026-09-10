package com.codecoachai.interview.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.entity.StudyPlan;
import com.codecoachai.interview.domain.entity.StudyTask;
import com.codecoachai.interview.mapper.StudyPlanMapper;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.mapper.UserQuestionRecordMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudyPlanAdjustControllerTest {
    private final StudyTaskMapper tasks = mock(StudyTaskMapper.class);
    private final StudyPlanMapper plans = mock(StudyPlanMapper.class);
    private final UserQuestionRecordMapper records = mock(UserQuestionRecordMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final StudyPlanAdjustController controller = new StudyPlanAdjustController(tasks, records, plans, json);

    @BeforeEach
    void setup() {
        for (Class<?> type : List.of(StudyPlan.class, StudyTask.class, UserQuestionRecord.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).build());
    }

    @AfterEach
    void cleanup() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsUnavailableParentBeforeReadingOrWritingTasks() {
        assertThrows(BusinessException.class, () -> controller.adjust(99L));
        assertThrows(BusinessException.class, () -> controller.adjustStats(99L));
        verifyNoInteractions(tasks, records);
        ArgumentCaptor<LambdaQueryWrapper<StudyPlan>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(plans, times(2)).selectOne(query.capture());
        assertTrue(query.getAllValues().get(0).getSqlSegment().contains("for update"));
        assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        assertTrue(query.getValue().getSqlSegment().contains("deleted"));
    }

    @Test
    void injectsArrayAndHonorsRemainingCapacity() throws Exception {
        when(plans.selectOne(any())).thenReturn(new StudyPlan());
        when(tasks.selectList(any())).thenReturn(List.of());
        when(tasks.selectCount(any())).thenReturn(4L);
        UserQuestionRecord first = new UserQuestionRecord();
        first.setQuestionId(123L);
        UserQuestionRecord second = new UserQuestionRecord();
        second.setQuestionId(124L);
        when(records.selectList(any())).thenReturn(List.of(first, second));
        assertEquals(1, controller.adjust(99L).getData().getAddedReviewCount());
        ArgumentCaptor<StudyTask> task = ArgumentCaptor.forClass(StudyTask.class);
        verify(tasks).insert(task.capture());
        assertEquals(List.of(123L), json.readValue(task.getValue().getRelatedQuestionIdsJson(),
                new TypeReference<List<Long>>() {}));
    }

    @Test
    void fullTodayDoesNotInjectMoreTasks() {
        when(plans.selectOne(any())).thenReturn(new StudyPlan());
        when(tasks.selectList(any())).thenReturn(List.of());
        when(tasks.selectCount(any())).thenReturn(5L);
        assertEquals(0, controller.adjust(99L).getData().getAddedReviewCount());
        verifyNoInteractions(records);
        verify(tasks, never()).insert(any(StudyTask.class));
    }

    @Test
    void reschedulingSkipsAlreadyFullFutureDays() {
        when(plans.selectOne(any())).thenReturn(new StudyPlan());
        StudyTask overdue = new StudyTask();
        overdue.setId(1L);
        when(tasks.selectList(any())).thenReturn(List.of(overdue));
        when(tasks.selectCount(any())).thenReturn(5L, 5L, 4L, 5L);
        assertEquals(1, controller.adjust(99L).getData().getRescheduledCount());
        ArgumentCaptor<LambdaUpdateWrapper<StudyTask>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tasks).update(isNull(), update.capture());
        assertTrue(update.getValue().getParamNameValuePairs().containsValue(LocalDate.now().plusDays(2)));
    }

    @Test
    void statsAcceptBothCompletedStatuses() {
        when(plans.selectOne(any())).thenReturn(new StudyPlan());
        StudyTask task = new StudyTask();
        task.setTaskStatus("DONE");
        task.setKnowledgePoint("Java");
        when(tasks.selectList(any())).thenReturn(List.of(task));
        var result = controller.adjustStats(99L).getData();
        assertEquals(100, result.getCompletionRate());
        assertEquals(100, result.getKnowledgeStats().get(0).getCompletionRate());
    }
}
