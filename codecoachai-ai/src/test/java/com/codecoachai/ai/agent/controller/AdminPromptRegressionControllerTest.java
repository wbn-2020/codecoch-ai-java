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
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.security.AdminOperationConfirmationGuard;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
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
}
