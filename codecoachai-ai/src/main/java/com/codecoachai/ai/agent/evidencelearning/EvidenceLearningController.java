package com.codecoachai.ai.agent.evidencelearning;

import com.codecoachai.ai.agent.config.V9FeatureGate;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/agent/evidence-learning/candidates", "/evidence-assets/candidates"})
public class EvidenceLearningController {

    private final EvidenceLearningService service;
    private final V9FeatureGate featureGate;

    @GetMapping
    public Result<EvidenceLearningModels.CandidateList> list(
            @ModelAttribute EvidenceLearningModels.CandidateQuery query) {
        Long userId = requireEnabledUser();
        return Result.success(service.listCandidates(userId, query));
    }

    @GetMapping("/{candidateId}")
    public Result<EvidenceLearningModels.CandidateView> detail(
            @PathVariable Long candidateId) {
        Long userId = requireEnabledUser();
        return Result.success(service.getCandidate(userId, candidateId));
    }

    @PostMapping("/{candidateId}/decisions")
    public Result<EvidenceLearningModels.CandidateView> decide(
            @PathVariable Long candidateId,
            @RequestBody EvidenceLearningModels.DecisionCommand command) {
        Long userId = requireEnabledUser();
        return Result.success(service.decide(userId, candidateId, command));
    }

    private Long requireEnabledUser() {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireEvidenceLearning();
        return userId;
    }
}
