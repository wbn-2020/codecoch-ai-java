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
import com.codecoachai.ai.domain.entity.PromptTemplate;
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
}
