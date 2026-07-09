package com.codecoachai.ai.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionRunDTO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionResultVO;
import com.codecoachai.ai.agent.security.AdminOperationConfirmationGuard;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AdminPromptRegressionControllerTest {

    @Mock
    private AgentV4OpsService agentV4OpsService;
    @Mock
    private V4AdminPermissionGuard permissionGuard;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminOperationConfirmationGuard operationConfirmationGuard;
    private AdminPromptRegressionController controller;

    @BeforeEach
    void setUp() {
        operationConfirmationGuard = new AdminOperationConfirmationGuard(stringRedisTemplate);
        controller = new AdminPromptRegressionController(agentV4OpsService, permissionGuard, operationConfirmationGuard);
    }

    @Test
    void createCaseRejectsMissingConfirmationBeforeSaving() {
        PromptRegressionCaseSaveDTO dto = caseDto();

        assertThrows(BusinessException.class, () -> controller.createCase(dto));

        verify(permissionGuard).require("admin:agent:prompt-regression:write");
        verify(agentV4OpsService, never()).savePromptCase(any());
    }

    @Test
    void casesDoesNotLoadWhenListPermissionDenied() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:agent:prompt-regression:list");

        assertThrows(BusinessException.class, () -> controller.cases("JOB_COACH_DAILY_PLAN", 1));

        verify(agentV4OpsService, never()).listPromptCases(any(), any());
    }

    @Test
    void createCaseDoesNotConfirmOrSaveWhenWritePermissionDenied() {
        PromptRegressionCaseSaveDTO dto = confirmedCaseDto("prompt-case-create-1234");
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:agent:prompt-regression:write");

        assertThrows(BusinessException.class, () -> controller.createCase(dto));

        verify(agentV4OpsService, never()).savePromptCase(any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void createCaseRequiresConfirmedIdempotencyKey() {
        PromptRegressionCaseSaveDTO dto = confirmedCaseDto("prompt-case-create-1234");
        PromptRegressionCaseVO saved = new PromptRegressionCaseVO();
        saved.setId(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:PROMPT_REGRESSION_CASE_CREATE:new:prompt-case-create-1234"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(true);
        when(agentV4OpsService.savePromptCase(dto)).thenReturn(saved);

        PromptRegressionCaseVO result = controller.createCase(dto).getData();

        assertEquals(1L, result.getId());
        verify(agentV4OpsService).savePromptCase(dto);
    }

    @Test
    void updateCaseUsesPathIdAndReleasesLockWhenSaveFails() {
        PromptRegressionCaseSaveDTO dto = confirmedCaseDto("prompt-case-update-1234");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:PROMPT_REGRESSION_CASE_UPDATE:9:prompt-case-update-1234"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(true);
        doThrow(new IllegalArgumentException("case invalid"))
                .when(agentV4OpsService).savePromptCase(dto);

        assertThrows(IllegalArgumentException.class, () -> controller.updateCase(9L, dto));

        assertEquals(9L, dto.getId());
        verify(stringRedisTemplate).delete(
                "codecoachai:admin-confirmed-operation:PROMPT_REGRESSION_CASE_UPDATE:9:prompt-case-update-1234");
    }

    @Test
    void runCaseRejectsMissingConfirmationBeforeRunning() {
        assertThrows(BusinessException.class, () -> controller.runCase(5L, new PromptRegressionRunDTO()));

        verify(permissionGuard).require("admin:agent:prompt-regression:run");
        verify(agentV4OpsService, never()).runPromptCase(any(), any());
    }

    @Test
    void runCaseDoesNotConfirmOrRunWhenRunPermissionDenied() {
        PromptRegressionRunDTO dto = confirmedRunDto("prompt-run-1234");
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:agent:prompt-regression:run");

        assertThrows(BusinessException.class, () -> controller.runCase(5L, dto));

        verify(agentV4OpsService, never()).runPromptCase(any(), any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void runCaseRequiresConfirmedIdempotencyKey() {
        PromptRegressionRunDTO dto = confirmedRunDto("prompt-run-1234");
        dto.setPromptVersionId(8L);
        PromptRegressionResultVO saved = new PromptRegressionResultVO();
        saved.setId(2L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:PROMPT_REGRESSION_RUN:5:8:prompt-run-1234"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(true);
        when(agentV4OpsService.runPromptCase(5L, 8L)).thenReturn(saved);

        PromptRegressionResultVO result = controller.runCase(5L, dto).getData();

        assertEquals(2L, result.getId());
        verify(agentV4OpsService).runPromptCase(5L, 8L);
    }

    @Test
    void resultsDoesNotLoadWhenListPermissionDenied() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(permissionGuard).require("admin:agent:prompt-regression:list");

        assertThrows(BusinessException.class, () -> controller.results(5L, 1L, 20L));

        verify(agentV4OpsService, never()).pagePromptResults(any(), any(), any());
    }

    private static PromptRegressionCaseSaveDTO caseDto() {
        PromptRegressionCaseSaveDTO dto = new PromptRegressionCaseSaveDTO();
        dto.setCaseName("Daily plan schema");
        dto.setPromptType("JOB_COACH_DAILY_PLAN");
        dto.setInputJson("{}");
        dto.setExpectedSchemaJson("{}");
        dto.setEnabled(1);
        return dto;
    }

    private static PromptRegressionCaseSaveDTO confirmedCaseDto(String idempotencyKey) {
        PromptRegressionCaseSaveDTO dto = caseDto();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm prompt regression case change");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private static PromptRegressionRunDTO confirmedRunDto(String idempotencyKey) {
        PromptRegressionRunDTO dto = new PromptRegressionRunDTO();
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason("confirm prompt regression run");
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }
}
