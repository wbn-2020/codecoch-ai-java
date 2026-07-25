package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.dto.InnerKnowledgeSearchDTO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal knowledge retrieval endpoint for service-to-service calls (e.g. the interview module
 * enriching an interview's training context with the candidate's personal knowledge base).
 *
 * <p>Auth is handled transparently by {@code InternalCallFilter} (HMAC over {@code /inner/**}); no
 * per-method guard is needed here. Results are always scoped to the {@code userId} carried in the
 * request body, so a calling service can never read another user's knowledge.
 *
 * <p>When the knowledge feature is disabled, or no keyword is supplied, this returns an empty list
 * rather than failing, so callers can degrade gracefully without breaking their own flow.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/agent/knowledge")
public class InnerKnowledgeController {

    private final AgentV4OpsService agentV4OpsService;
    private final V4FeatureGate v4FeatureGate;

    @PostMapping("/search")
    public Result<List<KnowledgeSearchResultVO>> search(@RequestBody InnerKnowledgeSearchDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        if (!v4FeatureGate.isKnowledgeEnabled() || !StringUtils.hasText(dto.getKeyword())) {
            return Result.success(List.of());
        }
        return Result.success(agentV4OpsService.searchKnowledge(
                dto.getUserId(), dto.getKeyword(), dto.getLimit(), dto.getMinScore(),
                dto.getDocumentId(), dto.getDocumentType()));
    }
}
