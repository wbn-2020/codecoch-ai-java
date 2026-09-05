package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.question.domain.entity.QuestionTagRelation;
import com.codecoachai.question.mapper.QuestionCategoryMapper;
import com.codecoachai.question.mapper.QuestionGroupMapper;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionTagMapper;
import com.codecoachai.question.mapper.QuestionTagRelationMapper;
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
    @Mock
    private QuestionTagRelationMapper tagRelationMapper;

    @InjectMocks
    private QuestionMetadataServiceImpl metadataService;

    @BeforeEach
    void setUp() {
        initTableInfo(QuestionTagRelation.class);
    }

    @Test
    void deleteTagOnlyCountsActiveQuestionsRelatedToTag() {
        when(tagRelationMapper.selectCount(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<QuestionTagRelation>>any()))
                .thenReturn(0L);

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
}
