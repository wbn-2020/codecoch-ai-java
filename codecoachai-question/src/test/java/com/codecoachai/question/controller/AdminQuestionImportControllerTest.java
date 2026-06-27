package com.codecoachai.question.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.config.QuestionImportProperties;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionImportService;
import com.codecoachai.question.service.QuestionImportService.ImportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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
                operationConfirmationGuard,
                new QuestionImportProperties());
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
    void importRejectsOversizedFileBeforeConfirmationOrParsing() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1L);

        Result<ImportResult> result =
                controller.importQuestions(file, true, false, "import questions", "question-import-too-large");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Question import file cannot exceed 10MB."));
        assertEquals(400, result.getCode());
        verify(adminPermissionGuard).require("admin:question:import");
        verify(operationConfirmationGuard, never()).requireConfirmed(any(), any(), any(), any(), any());
        verify(questionImportService, never()).importQuestions(anyString(), any(InputStream.class), anyLong(), anyBoolean());
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
        when(questionMapper.selectPage(any(), any())).thenReturn(pageOf(List.of(question)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportJson(response, 1L, "HARD", "SHORT_ANSWER",
                true, false, "export scoped questions", "question-export-5678");

        String json = response.getContentAsString();
        assertTrue(json.contains("\"referenceAnswer\""));
        assertTrue(json.contains("\"analysis\""));
        assertFalse(json.contains("contentHash"));
        assertFalse(json.contains("normalizedTitleHash"));
    }

    @Test
    void exportJsonReadsQuestionsInBatchesInsteadOfSingleFullList() throws Exception {
        when(operationConfirmationGuard.requireConfirmed(
                eq("question-export:json:all:all:all"),
                eq(true),
                eq(false),
                eq("export all questions"),
                eq("question-export-9999")))
                .thenReturn("lock-key");
        List<Question> firstBatch = java.util.stream.LongStream.rangeClosed(1, 500)
                .mapToObj(id -> exportQuestion(id, "Question-" + id))
                .toList();
        Question second = exportQuestion(501L, "ConcurrentHashMap");
        when(questionMapper.selectPage(any(), any()))
                .thenReturn(pageOf(firstBatch))
                .thenReturn(pageOf(List.of(second)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportJson(response, null, null, null,
                true, false, "export all questions", "question-export-9999");

        String json = response.getContentAsString();
        assertTrue(json.contains("Question-1"));
        assertTrue(json.contains("ConcurrentHashMap"));
        verify(questionMapper, times(2)).selectPage(any(), any());
        verify(questionMapper, never()).selectList(any());
    }

    @Test
    void exportEndpointsUseAsciiSafeMessagesFilenamesAndHeaders() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0]);

        Result<ImportResult> importResult =
                controller.importQuestions(emptyFile, true, false, "import questions", "question-import-empty");

        assertFalse(importResult.isSuccess());
        assertTrue(importResult.getMessage().contains("File cannot be empty"));

        when(operationConfirmationGuard.requireConfirmed(
                eq("question-export:excel:all:all:all"),
                eq(true),
                eq(false),
                eq("export questions"),
                eq("question-export-ascii")))
                .thenReturn("lock-key");
        when(questionMapper.selectPage(any(), any())).thenReturn(pageOf(List.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportExcel(response, null, null, null, "excel", true, false,
                "export questions", "question-export-ascii");

        assertTrue(response.getHeader("Content-Disposition").contains("question-export.xlsx"));
        assertExcelHeader("title", "Title");
        assertExcelHeader("content", "Content");
        assertExcelHeader("referenceAnswer", "Reference Answer");
        assertExcelHeader("analysis", "Analysis");
        assertExcelHeader("difficulty", "Difficulty");
        assertExcelHeader("questionType", "Question Type");
        assertExcelHeader("experienceLevel", "Experience Level");
    }

    private void assertExcelHeader(String fieldName, String expectedHeader) throws NoSuchFieldException {
        Field field = AdminQuestionImportController.QuestionExportRow.class.getDeclaredField(fieldName);
        assertTrue(field.getAnnotation(com.alibaba.excel.annotation.ExcelProperty.class).value()[0].equals(expectedHeader));
    }

    private Page<Question> pageOf(List<Question> records) {
        Page<Question> page = new Page<>();
        page.setRecords(records);
        return page;
    }

    private Question exportQuestion(Long id, String title) {
        Question question = new Question();
        question.setId(id);
        question.setTitle(title);
        question.setContent("Explain " + title);
        question.setReferenceAnswer("reference answer");
        question.setAnalysis("analysis");
        question.setDifficulty("MEDIUM");
        question.setQuestionType("SHORT_ANSWER");
        question.setExperienceLevel("MID");
        question.setStatus(1);
        return question;
    }
}
