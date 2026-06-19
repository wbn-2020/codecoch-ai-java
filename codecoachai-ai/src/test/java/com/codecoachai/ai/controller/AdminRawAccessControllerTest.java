package com.codecoachai.ai.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.controller.AdminAgentController;
import com.codecoachai.ai.agent.domain.dto.AdminAgentRunQueryDTO;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.security.AdminOperationConfirmationGuard;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.domain.dto.AiLogRawAccessDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptVersionTestDTO;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateDetailVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.service.PromptTemplateService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AdminRawAccessControllerTest {

    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private AdminPermissionGuard adminPermissionGuard;
    @Mock
    private JobCoachAgentService jobCoachAgentService;
    @Mock
    private V4AdminPermissionGuard v4AdminPermissionGuard;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminOperationConfirmationGuard operationConfirmationGuard;
    private AdminAiController adminAiController;
    private AdminAgentController adminAgentController;

    @BeforeEach
    void setUp() {
        operationConfirmationGuard = new AdminOperationConfirmationGuard(stringRedisTemplate);
        adminAiController = new AdminAiController(promptTemplateService, adminPermissionGuard, operationConfirmationGuard);
        adminAgentController = new AdminAgentController(jobCoachAgentService, v4AdminPermissionGuard, operationConfirmationGuard);
    }

    @Test
    void getLogRawRejectsMissingIdempotencyBeforeLoadingRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto(null);

        assertThrows(BusinessException.class, () -> adminAiController.getLogRaw(9L, dto));

        verify(adminPermissionGuard).require("admin:ai:log:raw:view");
        verify(promptTemplateService, never()).getLogRaw(any());
    }

    @Test
    void getLogRawRequiresIdempotencyLockBeforeReturningRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto("ai-log-raw-1234");
        AiCallLogVO raw = new AiCallLogVO();
        raw.setId(9L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:ai-log-raw:9:ai-log-raw-1234"),
                eq("1"),
                any(Duration.class))).thenReturn(true);
        when(promptTemplateService.getLogRaw(9L)).thenReturn(raw);

        AiCallLogVO result = adminAiController.getLogRaw(9L, dto).getData();

        assertEquals(9L, result.getId());
        verify(promptTemplateService).getLogRaw(9L);
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    @Test
    void getLogRawReleasesIdempotencyLockWhenRawLoadFails() {
        AiLogRawAccessDTO dto = rawAccessDto("ai-log-raw-5678");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:ai-log-raw:9:ai-log-raw-5678"),
                eq("1"),
                any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("raw unavailable"))
                .when(promptTemplateService).getLogRaw(9L);

        assertThrows(IllegalStateException.class, () -> adminAiController.getLogRaw(9L, dto));

        verify(stringRedisTemplate).delete(
                "codecoachai:admin-confirmed-operation:ai-log-raw:9:ai-log-raw-5678");
    }

    @Test
    void promptTemplateRawRejectsMissingIdempotencyBeforeLoadingRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto(null);

        assertThrows(BusinessException.class, () -> adminAiController.getPromptRaw(3L, dto));

        verify(adminPermissionGuard).require("admin:ai:prompt:raw:view");
        verify(promptTemplateService, never()).getPromptRaw(any());
    }

    @Test
    void promptTemplateRawRequiresIdempotencyLockBeforeReturningRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto("prompt-template-raw-1234");
        PromptTemplateDetailVO raw = new PromptTemplateDetailVO();
        raw.setId(3L);
        raw.setTemplateContent("raw prompt");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:prompt-template-raw:3:prompt-template-raw-1234"),
                eq("1"),
                any(Duration.class))).thenReturn(true);
        when(promptTemplateService.getPromptRaw(3L)).thenReturn(raw);

        PromptTemplateDetailVO result = adminAiController.getPromptRaw(3L, dto).getData();

        assertEquals(3L, result.getId());
        assertEquals("raw prompt", result.getTemplateContent());
        verify(promptTemplateService).getPromptRaw(3L);
    }

    @Test
    void promptVersionRawRejectsMissingIdempotencyBeforeLoadingRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto(null);

        assertThrows(BusinessException.class, () -> adminAiController.getPromptVersionRaw(5L, dto));

        verify(adminPermissionGuard).require("admin:ai:prompt:raw:view");
        verify(promptTemplateService, never()).getVersionRaw(any());
    }

    @Test
    void promptVersionRawRequiresIdempotencyLockBeforeReturningRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto("prompt-version-raw-1234");
        PromptTemplateVersionVO raw = new PromptTemplateVersionVO();
        raw.setId(5L);
        raw.setContent("raw version prompt");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:prompt-template-version-raw:5:prompt-version-raw-1234"),
                eq("1"),
                any(Duration.class))).thenReturn(true);
        when(promptTemplateService.getVersionRaw(5L)).thenReturn(raw);

        PromptTemplateVersionVO result = adminAiController.getPromptVersionRaw(5L, dto).getData();

        assertEquals(5L, result.getId());
        assertEquals("raw version prompt", result.getContent());
        verify(promptTemplateService).getVersionRaw(5L);
    }

    @Test
    void rawGetCompatEndpointsRejectWithoutLoadingRawContent() {
        assertThrows(BusinessException.class, () -> adminAiController.getPromptRawCompat(3L));
        assertThrows(BusinessException.class, () -> adminAiController.getPromptVersionRawCompat(5L));
        assertThrows(BusinessException.class, () -> adminAiController.getLogRawCompat(9L));

        verify(promptTemplateService, never()).getPromptRaw(any());
        verify(promptTemplateService, never()).getVersionRaw(any());
        verify(promptTemplateService, never()).getLogRaw(any());
    }

    @Test
    void createPromptReleasesIdempotencyLockWhenServiceFails() {
        PromptTemplateSaveDTO dto = promptSaveDto("prompt-create-1234");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:prompt-template-create:INTERVIEW:prompt-create-1234"),
                eq("1"),
                any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("prompt create failed"))
                .when(promptTemplateService).createPrompt(dto);

        assertThrows(IllegalStateException.class, () -> adminAiController.createPrompt(dto));

        verify(stringRedisTemplate).delete(
                "codecoachai:admin-confirmed-operation:prompt-template-create:INTERVIEW:prompt-create-1234");
    }

    @Test
    void realAiPromptVersionTestReleasesIdempotencyLockWhenServiceFails() {
        PromptVersionTestDTO dto = promptVersionTestDto("prompt-version-test-1234");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:prompt-template-version-test:5:prompt-version-test-1234"),
                eq("1"),
                any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("prompt test failed"))
                .when(promptTemplateService).testVersion(5L, dto);

        assertThrows(IllegalStateException.class, () -> adminAiController.testPromptVersion(5L, dto));

        verify(stringRedisTemplate).delete(
                "codecoachai:admin-confirmed-operation:prompt-template-version-test:5:prompt-version-test-1234");
    }

    @Test
    void agentRunRawRejectsMissingIdempotencyBeforeLoadingRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto(null);

        assertThrows(BusinessException.class, () -> adminAgentController.runRaw(12L, dto));

        verify(v4AdminPermissionGuard).require("admin:agent:run:list");
        verify(v4AdminPermissionGuard).require("admin:ai:log:raw:view");
        verify(jobCoachAgentService, never()).adminGetRunDetail(any());
    }

    @Test
    void agentRunDetailMasksRawDiagnosticsByDefaultButShowsAvailability() {
        AgentRunDetailVO raw = agentRunDetailWithRawDiagnostics(12L);
        when(jobCoachAgentService.adminGetRunDetail(12L)).thenReturn(raw);

        AgentRunDetailVO result = adminAgentController.runDetail(12L).getData();

        assertEquals(12L, result.getId());
        assertNull(result.getInputSnapshotJson());
        assertNull(result.getOutputJson());
        assertNull(result.getRawOutputText());
        assertTrue(result.getRawAvailable());
        assertEquals("admin:ai:log:raw:view", result.getRawAccessPermission());
    }

    @Test
    void agentRunPageMasksRawDiagnosticsByDefaultButShowsAvailability() {
        AgentRunDetailVO raw = agentRunDetailWithRawDiagnostics(12L);
        when(jobCoachAgentService.adminPageRuns(any())).thenReturn(PageResult.of(List.of(raw), 1L, 1L, 10L));

        AgentRunDetailVO result = adminAgentController.pageRuns(new AdminAgentRunQueryDTO())
                .getData()
                .getRecords()
                .get(0);

        assertEquals(12L, result.getId());
        assertNull(result.getInputSnapshotJson());
        assertNull(result.getOutputJson());
        assertNull(result.getRawOutputText());
        assertTrue(result.getRawAvailable());
        assertEquals("admin:ai:log:raw:view", result.getRawAccessPermission());
    }

    @Test
    void agentRunRawUsesIdempotencyLockBeforeReturningRawContent() {
        AiLogRawAccessDTO dto = rawAccessDto("agent-run-raw-1234");
        AgentRunDetailVO raw = new AgentRunDetailVO();
        raw.setId(12L);
        raw.setRawOutputText("raw output");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:agent-run-raw:12:agent-run-raw-1234"),
                eq("1"),
                any(Duration.class))).thenReturn(true);
        when(jobCoachAgentService.adminGetRunDetail(12L)).thenReturn(raw);

        AgentRunDetailVO result = adminAgentController.runRaw(12L, dto).getData();

        assertEquals(12L, result.getId());
        assertEquals("raw output", result.getRawOutputText());
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    private static AiLogRawAccessDTO rawAccessDto(String idempotencyKey) {
        AiLogRawAccessDTO dto = new AiLogRawAccessDTO();
        dto.setAccessReason("investigate user feedback");
        dto.setConfirmSensitiveAccess(true);
        dto.setDryRun(false);
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static AgentRunDetailVO agentRunDetailWithRawDiagnostics(Long id) {
        AgentRunDetailVO raw = new AgentRunDetailVO();
        raw.setId(id);
        raw.setInputSnapshotJson("{\"resume\":\"private\"}");
        raw.setOutputJson("{\"tasks\":[]}");
        raw.setRawOutputText("private raw output");
        return raw;
    }

    private static PromptTemplateSaveDTO promptSaveDto(String idempotencyKey) {
        PromptTemplateSaveDTO dto = new PromptTemplateSaveDTO();
        dto.setScene("INTERVIEW");
        dto.setContent("Prompt content");
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm prompt create");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static PromptVersionTestDTO promptVersionTestDto(String idempotencyKey) {
        PromptVersionTestDTO dto = new PromptVersionTestDTO();
        dto.setInputVariables(Map.of("position", "Java developer"));
        dto.setCallAi(true);
        dto.setConfirmSensitiveAccess(true);
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm real AI prompt test");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }
}
