package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionCreateDTO;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptTemplateServiceImplTest {

    @Mock
    private PromptTemplateMapper promptTemplateMapper;
    @Mock
    private PromptTemplateVersionMapper promptTemplateVersionMapper;
    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private AiProperties aiProperties;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PromptTemplateServiceImpl service;

    @Test
    void updatePromptRejectsContentChangeAndDirectsUserToVersionManagement() {
        PromptTemplate template = new PromptTemplate();
        template.setId(101L);
        template.setName("项目深挖模板");
        template.setDescription("old");
        template.setStatus(0);
        template.setActiveVersionId(88L);
        when(promptTemplateMapper.selectById(101L)).thenReturn(template);

        PromptTemplateSaveDTO dto = new PromptTemplateSaveDTO();
        dto.setName("项目深挖模板");
        dto.setDescription("new");
        dto.setContent("new prompt body");
        dto.setExpectedStatus(0);
        dto.setExpectedActiveVersionId(88L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updatePrompt(101L, dto));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("version management"));
        verify(promptTemplateMapper, never()).updateById(any(PromptTemplate.class));
    }

    @Test
    void createVersionRejectsVariableDeclarationMismatchBeforeInsert() {
        PromptTemplate template = new PromptTemplate();
        template.setId(102L);
        template.setScene("INTERVIEW_ANSWER_EVALUATE");
        when(promptTemplateMapper.selectById(102L)).thenReturn(template);
        when(promptTemplateVersionMapper.selectCount(any())).thenReturn(0L);

        PromptTemplateVersionCreateDTO dto = new PromptTemplateVersionCreateDTO();
        dto.setVersionCode("v-next");
        dto.setContent("Question={{questionContent}}");
        dto.setVariablesJson("questionContent,userAnswer");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createVersion(102L, dto));

        assertTrue(exception.getMessage().contains("unused=[userAnswer]"));
        verify(promptTemplateVersionMapper, never()).insert(any(PromptTemplateVersion.class));
    }

    @Test
    void activateVersionRejectsVariableDeclarationMismatchBeforePublishing() {
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setId(201L);
        version.setTemplateId(102L);
        version.setStatus("DRAFT");
        version.setContent("Question={{questionContent}}");
        version.setVariablesJson("questionContent,userAnswer");
        when(promptTemplateVersionMapper.selectById(201L)).thenReturn(version);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateVersion(201L, null));

        assertTrue(exception.getMessage().contains("unused=[userAnswer]"));
        verify(promptTemplateMapper, never()).selectById(102L);
    }
}
