package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionDuplicateService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.codecoachai.question.service.QuestionImportService.ImportResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceImplTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionEmbeddingIndexService questionEmbeddingIndexService;
    @Mock
    private QuestionDuplicateService questionDuplicateService;

    private QuestionImportServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(Question.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new QuestionImportServiceImpl(
                questionMapper,
                questionEmbeddingIndexService,
                questionDuplicateService);
    }

    @Test
    void dryRunValidatesDuplicatesWithoutInsertingOrSchedulingSideEffects() {
        when(questionMapper.selectCount(any())).thenReturn(0L);

        ImportResult result = service.importQuestions(
                "questions.md",
                input("""
                        ## Java HashMap 原理
                        请说明 HashMap put 和 resize 流程。
                        """),
                100L,
                true);

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertEquals(0, result.getDuplicateCount());
        verify(questionMapper, never()).insert(any(Question.class));
        verify(questionEmbeddingIndexService, never()).indexQuestions(any());
        verify(questionDuplicateService, never()).checkDuplicateForQuestion(any(), any());
    }

    @Test
    void dryRunStillReportsBankDuplicates() {
        when(questionMapper.selectCount(any())).thenReturn(1L);

        ImportResult result = service.importQuestions(
                "questions.md",
                input("""
                        ## Java HashMap 原理
                        请说明 HashMap put 和 resize 流程。
                        """),
                100L,
                true);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals(1, result.getDuplicateReasonCounts().get("BANK_TITLE_DUPLICATE"));
        verify(questionMapper, never()).insert(any(Question.class));
        verify(questionEmbeddingIndexService, never()).indexQuestions(any());
        verify(questionDuplicateService, never()).checkDuplicateForQuestion(any(), any());
    }

    private ByteArrayInputStream input(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
