package com.codecoachai.ai.agent.evidencelearning;

import com.codecoachai.ai.domain.dto.GenerateEvidenceLearningCandidateDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceReuseMaterialDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceUsageResultDraftDTO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceLearningCandidateVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceReuseMaterialDraftVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceUsageResultDraftVO;

public interface EvidenceLearningService {

    EvidenceLearningModels.CandidateList listCandidates(
            Long userId, EvidenceLearningModels.CandidateQuery query);

    EvidenceLearningModels.CandidateView getCandidate(Long userId, Long candidateId);

    EvidenceLearningModels.CandidateView decide(
            Long userId, Long candidateId, EvidenceLearningModels.DecisionCommand command);

    GenerateEvidenceUsageResultDraftVO resultDraft(
            Long userId, GenerateEvidenceUsageResultDraftDTO request);

    GenerateEvidenceLearningCandidateVO learningCandidate(
            Long userId, GenerateEvidenceLearningCandidateDTO request);

    GenerateEvidenceReuseMaterialDraftVO reuseMaterialDraft(
            Long userId, GenerateEvidenceReuseMaterialDraftDTO request);
}
