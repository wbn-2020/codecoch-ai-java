package com.codecoachai.question.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.domain.entity.UserQuestionRecord;
import com.codecoachai.question.mapper.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionStudyControllerTest {
    @Test
    void masteredReviewingQuestionCountsAsCorrectAndFavoritesAreExcludedByQuery() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserQuestionRecord.class);
        QuestionMapper questions = mock(QuestionMapper.class);
        UserQuestionRecordMapper records = mock(UserQuestionRecordMapper.class);
        var controller = new QuestionStudyController(questions, records,
                mock(QuestionRelationMapper.class), mock(QuestionCategoryMapper.class));
        UserQuestionRecord record = new UserQuestionRecord();
        record.setQuestionId(1L);
        record.setWrong(1);
        record.setMasteryStatus("MASTERED");
        record.setLastAnswerAt(LocalDateTime.now());
        Question question = new Question();
        question.setId(1L);
        when(records.selectList(any())).thenReturn(List.of(record));
        when(questions.selectList(any())).thenReturn(List.of(question));
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).build());
        try {
            var result = controller.weaknessAnalysis().getData();
            assertEquals(100, result.getCorrectRate());
            assertEquals(1, result.getTotalAnswered());
            ArgumentCaptor<LambdaQueryWrapper<UserQuestionRecord>> query =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(records).selectList(query.capture());
            assertTrue(query.getValue().getSqlSegment().contains("last_answer_at IS NOT NULL"));
            assertTrue(query.getValue().getSqlSegment().contains("mastery_status IN"));
        } finally {
            LoginUserContext.clear();
        }
    }
}
