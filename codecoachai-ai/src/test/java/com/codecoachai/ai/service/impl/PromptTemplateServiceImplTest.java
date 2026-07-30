package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionCreateDTO;
import com.codecoachai.ai.domain.dto.PromptVersionActionDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptSceneContracts;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PromptTemplateServiceImplTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        if (TableInfoHelper.getTableInfo(PromptTemplate.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    PromptTemplate.class);
        }
    }

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

    @Test
    void activateVersionRejectsManagedPromptThatDoesNotMeetCurrentSceneContract() {
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setId(202L);
        version.setTemplateId(103L);
        version.setScene(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE);
        version.setVersionCode("v4.2-zh-evidence-json");
        version.setStatus("DRAFT");
        version.setContent("context={{contextJson}} candidates={{candidatesJson}} "
                + "count={{taskCount}} max={{maxTotalMinutes}}");
        version.setVariablesJson("contextJson,candidatesJson,taskCount,maxTotalMinutes");

        PromptTemplate template = new PromptTemplate();
        template.setId(103L);
        template.setScene(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE);
        when(promptTemplateVersionMapper.selectById(202L)).thenReturn(version);
        when(promptTemplateMapper.selectById(103L)).thenReturn(template);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.activateVersion(202L, null));

        assertTrue(exception.getMessage().contains("incompatible with scene contract"));
        assertTrue(exception.getMessage().contains(PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION));
        verify(promptTemplateVersionMapper, never()).updateById(any(PromptTemplateVersion.class));
    }

    @Test
    void activateVersionDeactivatesActiveVersionsAcrossTheWholeScene() {
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setId(203L);
        version.setTemplateId(104L);
        version.setScene(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE);
        version.setVersionCode(PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION);
        version.setStatus("DRAFT");
        version.setContent("SKILL_GAP_ITEM context={{contextJson}} candidates={{candidatesJson}} "
                + "count={{taskCount}} max={{maxTotalMinutes}}");
        version.setVariablesJson("contextJson,candidatesJson,taskCount,maxTotalMinutes");

        PromptTemplate template = new PromptTemplate();
        template.setId(104L);
        template.setScene(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE);
        when(promptTemplateVersionMapper.selectById(203L)).thenReturn(version);
        when(promptTemplateMapper.selectById(104L)).thenReturn(template);
        when(promptTemplateMapper.update(isNull(), any())).thenReturn(1);

        service.activateVersion(203L, null);

        verify(promptTemplateMapper).lockSceneTemplatesForActivation(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE);
        verify(promptTemplateVersionMapper).deactivateOtherActiveVersionsForScene(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE, 203L);
        assertEquals("ACTIVE", version.getStatus());
        assertEquals(1, version.getIsActive());
        verify(promptTemplateVersionMapper).updateById(version);
    }

    @Test
    void rollbackVersionDeclaresItsOwnTransactionBoundary() throws Exception {
        Transactional transactional = PromptTemplateServiceImpl.class
                .getMethod("rollbackVersion", Long.class, PromptVersionActionDTO.class)
                .getAnnotation(Transactional.class);

        assertTrue(transactional != null);
    }

    @Test
    void updateStatusRejectsDisablingEnabledManagedPromptAfterTakingSceneLock() {
        PromptTemplate template = new PromptTemplate();
        template.setId(105L);
        template.setScene(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE);
        template.setStatus(1);
        template.setEnabled(1);
        template.setActiveVersionId(205L);
        when(promptTemplateMapper.selectById(105L)).thenReturn(template);

        UpdatePromptStatusDTO dto = new UpdatePromptStatusDTO();
        dto.setStatus(0);
        dto.setExpectedStatus(1);
        dto.setExpectedActiveVersionId(205L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(105L, dto));

        assertTrue(exception.getMessage().contains("must remain enabled"));
        verify(promptTemplateMapper).lockSceneTemplatesForActivation(
                PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE);
        verify(promptTemplateMapper, never()).updateById(any(PromptTemplate.class));
    }

    @Test
    void disableVersionUsesSceneLockAndConditionalInactiveUpdate() {
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setId(206L);
        version.setTemplateId(106L);
        version.setScene("INTERVIEW_ANSWER_EVALUATE");
        version.setStatus("DRAFT");
        version.setIsActive(0);
        version.setChangeLog("old");

        PromptTemplate template = new PromptTemplate();
        template.setId(106L);
        template.setScene("INTERVIEW_ANSWER_EVALUATE");
        template.setActiveVersionId(999L);
        when(promptTemplateVersionMapper.selectById(206L)).thenReturn(version);
        when(promptTemplateMapper.selectById(106L)).thenReturn(template);
        when(promptTemplateVersionMapper.disableInactiveVersion(206L, "disabled by review")).thenReturn(1);

        PromptVersionActionDTO dto = new PromptVersionActionDTO();
        dto.setExpectedCurrentActiveVersionId(999L);
        dto.setChangeLog("disabled by review");

        service.disableVersion(206L, dto);

        verify(promptTemplateMapper).lockSceneTemplatesForActivation("INTERVIEW_ANSWER_EVALUATE");
        verify(promptTemplateVersionMapper).disableInactiveVersion(206L, "disabled by review");
        verify(promptTemplateVersionMapper, never()).updateById(any(PromptTemplateVersion.class));
    }
}
