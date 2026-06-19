package com.codecoachai.search.controller;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.search.service.IndexManageService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSearchControllerTest {

    @Mock
    private IndexManageService indexManageService;
    @Mock
    private ElasticsearchClient esClient;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminSearchController(indexManageService, esClient, permissionGuard, operationConfirmationGuard);
    }

    @Test
    void userScopedResumeSearchRequiresSharedConfirmationBeforeEsAccess() throws IOException {
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"))
                .when(operationConfirmationGuard)
                .requireConfirmed(
                        eq("search-sensitive:resume:user:9"),
                        eq(null),
                        eq(true),
                        eq("audit user resume search"),
                        eq("resume-search-1234"));

        assertThrows(BusinessException.class, () -> controller.searchResumes(
                "Java",
                1,
                10,
                9L,
                null,
                null,
                "audit user resume search",
                null,
                true,
                null,
                "resume-search-1234"));

        verify(permissionGuard).require("admin:search:resume");
        verifyNoInteractions(esClient);
    }

    @Test
    void sensitiveInterviewSearchReleasesConfirmationLockWhenEsFails() throws IOException {
        when(operationConfirmationGuard.requireConfirmed(
                eq("search-sensitive:interview:user:10"),
                eq(true),
                eq(false),
                eq("audit interview search"),
                eq("interview-search-1234"))).thenReturn("search-lock");
        when(esClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenThrow(new IOException("es down"));

        assertThrows(IOException.class, () -> controller.searchInterviews(
                "Spring",
                1,
                10,
                10L,
                null,
                null,
                null,
                true,
                false,
                "audit interview search",
                "interview-search-1234"));

        verify(operationConfirmationGuard).release("search-lock");
    }

    @Test
    void rebuildIndexReleasesConfirmationLockWhenServiceFails() throws IOException {
        when(operationConfirmationGuard.requireConfirmed(
                eq("search-index-rebuild:index:codecoachai_question"),
                eq(true),
                eq(false),
                eq("rebuild search index"),
                eq("search-rebuild-1234"))).thenReturn("rebuild-lock");
        doThrow(new IOException("rebuild failed"))
                .when(indexManageService).rebuild("codecoachai_question", true);

        assertThrows(IOException.class, () -> controller.rebuildIndex(
                "codecoachai_question",
                true,
                false,
                "rebuild search index",
                "search-rebuild-1234"));

        verify(operationConfirmationGuard).release("rebuild-lock");
    }

    @Test
    void rebuildIndexRejectsDryRunBeforeMutatingService() throws IOException {
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dry run rejected"))
                .when(operationConfirmationGuard)
                .requireConfirmed(
                        eq("search-index-rebuild:index:codecoachai_question"),
                        eq(true),
                        eq(true),
                        eq("preview rebuild"),
                        eq("search-rebuild-preview"));

        assertThrows(BusinessException.class, () -> controller.rebuildIndex(
                "codecoachai_question",
                true,
                true,
                "preview rebuild",
                "search-rebuild-preview"));

        verify(indexManageService, never()).rebuild(any(), eq(true));
    }

    @Test
    void questionSearchCapsDeepOffsetAndDisablesFuzzyForShortKeywords() throws IOException {
        when(esClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("stop after request capture"));

        assertThrows(RuntimeException.class, () -> controller.searchQuestions("AI", 100, 100, null, null));

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(esClient).search(requestCaptor.capture(), eq(JsonNode.class));
        SearchRequest request = requestCaptor.getValue();
        assertTrue(request.from() <= 1900);
        assertNull(request.query().bool().must().get(0).multiMatch().fuzziness());
    }
}
