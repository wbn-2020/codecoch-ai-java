package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionMetadataServiceImplTest {

    @Mock
    private QuestionCategoryMapper categoryMapper;
    @Mock
    private QuestionTagMapper tagMapper;
    @Mock
    private QuestionGroupMapper groupMapper;
    @Mock
    private QuestionMapper questionMapper;

    @InjectMocks
    private QuestionMetadataServiceImpl metadataService;

    @BeforeEach
    void setUp() {
        initTableInfo(Question.class);
    }

    @Test
    void deleteTagOnlyCountsActiveQuestionsRelatedToTag() {
        when(questionMapper.selectCount(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<Question>>any())).thenReturn(0L);

        metadataService.deleteTag(13L);

        ArgumentCaptor<LambdaQueryWrapper<Question>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectCount(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("question_tag_relation"), sqlSegment);
        assertTrue(sqlSegment.contains("deleted = 0"), sqlSegment);
        assertTrue(sqlSegment.contains("tag_id = 13"), sqlSegment);
        verify(tagMapper).deleteById(13L);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
