package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeDocumentCreateDTO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeAskVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/knowledge")
public class AgentKnowledgeController {

    private final AgentV4OpsService agentV4OpsService;

    @PostMapping("/documents")
    public Result<KnowledgeDocumentVO> createDocument(@RequestBody KnowledgeDocumentCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.createKnowledgeDocument(userId, dto));
    }

    @GetMapping("/documents")
    public Result<List<KnowledgeDocumentVO>> listDocuments() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listKnowledgeDocuments(userId));
    }

    @GetMapping("/documents/{id}")
    public Result<KnowledgeDocumentVO> document(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.getKnowledgeDocument(userId, id));
    }

    @GetMapping("/search")
    public Result<List<KnowledgeSearchResultVO>> search(@RequestParam String keyword,
                                                        @RequestParam(required = false) Integer limit) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.searchKnowledge(userId, keyword, limit));
    }

    @PostMapping("/ask")
    public Result<KnowledgeAskVO> ask(@RequestBody KnowledgeAskDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.askKnowledge(userId, dto));
    }
}
