package com.codecoachai.question.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionImportService;
import com.codecoachai.question.service.QuestionImportService.ImportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AdminQuestionImportControllerTest {

    @Mock
    private QuestionImportService questionImportService;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private AdminPermissionGuard adminPermissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminQuestionImportController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminQuestionImportController(
                questionImportService,
                questionMapper,
                new ObjectMapper(),
                adminPermissionGuard,
                operationConfirmationGuard);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void importPassesDryRunFlagToImportService() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(1L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "title,content".getBytes());
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-import:questions.xlsx"),
                eq(true),
                eq(true),
                eq("import questions"),
                eq("question-import-1234")))
                .thenReturn("lock-key");
        ImportResult importResult = new ImportResult();
        when(questionImportService.importQuestions(eq("questions.xlsx"), any(InputStream.class), eq(1L), eq(true)))
                .thenReturn(importResult);

        Result<ImportResult> result =
                controller.importQuestions(file, true, true, "import questions", "question-import-1234");

        assertTrue(result.isSuccess());
        verify(adminPermissionGuard).require("admin:question:import");
        verify(questionImportService).importQuestions(eq("questions.xlsx"), any(InputStream.class), eq(1L), eq(true));
    }

    @Test
    void importReleasesIdempotencyLockWhenServiceFails() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(1L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "title,content".getBytes());
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-import:questions.xlsx"),
                eq(true),
                eq(false),
                eq("import questions"),
                eq("question-import-1234")))
                .thenReturn("lock-key");
        doThrow(new RuntimeException("parser failed"))
                .when(questionImportService)
                .importQuestions(eq("questions.xlsx"), any(InputStream.class), eq(1L), eq(false));

        Result<ImportResult> result =
                controller.importQuestions(file, true, false, "import questions", "question-import-1234");

        assertFalse(result.isSuccess());
        verify(operationConfirmationGuard).release("lock-key");
    }

    @Test
    void exportExcelRejectsMissingConfirmationBeforeQueryingQuestions() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-export:excel:all:all:all"),
                eq(false),
                eq(false),
                eq("export questions"),
                eq("question-export-1234")))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class,
                () -> controller.exportExcel(new MockHttpServletResponse(),
                        null, null, null, "excel", false, false, "export questions", "question-export-1234"));

        verify(adminPermissionGuard).require("admin:question:export");
        verify(questionMapper, never()).selectList(any());
    }

    @Test
    void exportJsonRequiresConfirmationAndUsesExportRowsInsteadOfRawEntities() throws Exception {
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-export:json:1:HARD:SHORT_ANSWER"),
                eq(true),
                eq(false),
                eq("export scoped questions"),
                eq("question-export-5678")))
                .thenReturn("lock-key");
        Question question = new Question();
        question.setId(10L);
        question.setTitle("HashMap");
        question.setContent("Explain HashMap internals");
        question.setReferenceAnswer("hash bucket resize");
        question.setAnalysis("JDK8 array plus list/tree");
        question.setDifficulty("HARD");
        question.setQuestionType("SHORT_ANSWER");
        question.setExperienceLevel("SENIOR");
        question.setStatus(1);
        question.setContentHash("internal-content-hash");
        question.setNormalizedTitleHash("internal-title-hash");
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportJson(response, 1L, "HARD", "SHORT_ANSWER",
                true, false, "export scoped questions", "question-export-5678");

        String json = response.getContentAsString();
        assertTrue(json.contains("\"referenceAnswer\""));
        assertTrue(json.contains("\"analysis\""));
        assertFalse(json.contains("contentHash"));
        assertFalse(json.contains("normalizedTitleHash"));
    }
}
