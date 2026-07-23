package com.codecoachai.ai.controller;

import com.codecoachai.ai.agent.config.V9FeatureGate;
import com.codecoachai.ai.agent.evidencelearning.EvidenceLearningService;
import com.codecoachai.ai.domain.dto.GenerateEvidenceLearningCandidateDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceReuseMaterialDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceUsageResultDraftDTO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceLearningCandidateVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceReuseMaterialDraftVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceUsageResultDraftVO;
import com.codecoachai.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/inner/evidence-usages/ai", "/inner/evidence-usage-ai"})
public class InnerEvidenceUsageAiController {

    private final EvidenceLearningService service;
    private final V9FeatureGate featureGate;

    @PostMapping("/users/{userId}/result-draft")
    public Result<GenerateEvidenceUsageResultDraftVO> resultDraft(
            @PathVariable Long userId,
            @RequestBody(required = false) GenerateEvidenceUsageResultDraftDTO request) {
        featureGate.requireEvidenceLearning();
        return Result.success(service.resultDraft(userId, request));
    }

    @PostMapping("/users/{userId}/learning-candidate")
    public Result<GenerateEvidenceLearningCandidateVO> learningCandidate(
            @PathVariable Long userId,
            @RequestBody(required = false) GenerateEvidenceLearningCandidateDTO request) {
        featureGate.requireEvidenceLearning();
        return Result.success(service.learningCandidate(userId, request));
    }

    @PostMapping("/users/{userId}/reuse-material-draft")
    public Result<GenerateEvidenceReuseMaterialDraftVO> reuseMaterialDraft(
            @PathVariable Long userId,
            @RequestBody(required = false) GenerateEvidenceReuseMaterialDraftDTO request) {
        featureGate.requireEvidenceLearning();
        return Result.success(service.reuseMaterialDraft(userId, request));
    }
}
