package com.codecoachai.ai.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.dto.InnerKnowledgeSearchDTO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerKnowledgeControllerTest {

    private static final long USER_ID = 10L;

    @Mock
    private AgentV4OpsService agentV4OpsService;
    @Mock
    private V4FeatureGate v4FeatureGate;

    private InnerKnowledgeController controller() {
        return new InnerKnowledgeController(agentV4OpsService, v4FeatureGate);
    }

    private InnerKnowledgeSearchDTO dto(Long userId, String keyword) {
        InnerKnowledgeSearchDTO dto = new InnerKnowledgeSearchDTO();
        dto.setUserId(userId);
        dto.setKeyword(keyword);
        dto.setLimit(3);
        dto.setMinScore(0.35D);
        return dto;
    }

    @Test
    void searchReturnsScopedResultsWhenEnabledAndKeywordPresent() {
        KnowledgeSearchResultVO hit = new KnowledgeSearchResultVO();
        hit.setDocumentId(100L);
        hit.setTitle("Spring 事务传播");
        hit.setSnippet("REQUIRES_NEW 会挂起当前事务");
        hit.setScore(0.9D);
        when(v4FeatureGate.isKnowledgeEnabled()).thenReturn(true);
        when(agentV4OpsService.searchKnowledge(eq(USER_ID), eq("Spring 事务"), eq(3), eq(0.35D), isNull(), isNull()))
                .thenReturn(List.of(hit));

        Result<List<KnowledgeSearchResultVO>> result = controller().search(dto(USER_ID, "Spring 事务"));

        assertEquals(1, result.getData().size());
        assertEquals(100L, result.getData().get(0).getDocumentId());
        verify(agentV4OpsService).searchKnowledge(eq(USER_ID), eq("Spring 事务"), eq(3), eq(0.35D), isNull(), isNull());
    }

    @Test
    void searchReturnsEmptyListWhenFeatureDisabledWithoutCallingService() {
        when(v4FeatureGate.isKnowledgeEnabled()).thenReturn(false);

        Result<List<KnowledgeSearchResultVO>> result = controller().search(dto(USER_ID, "Spring 事务"));

        assertTrue(result.getData().isEmpty());
        verify(agentV4OpsService, never()).searchKnowledge(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void searchReturnsEmptyListWhenKeywordBlankWithoutCallingService() {
        when(v4FeatureGate.isKnowledgeEnabled()).thenReturn(true);

        Result<List<KnowledgeSearchResultVO>> result = controller().search(dto(USER_ID, "   "));

        assertTrue(result.getData().isEmpty());
        verify(agentV4OpsService, never()).searchKnowledge(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void searchRejectsMissingUserId() {
        assertThrows(BusinessException.class, () -> controller().search(dto(null, "Spring 事务")));
        assertThrows(BusinessException.class, () -> controller().search(null));
        verify(agentV4OpsService, never()).searchKnowledge(any(), anyString(), any(), any(), any(), any());
    }
}
