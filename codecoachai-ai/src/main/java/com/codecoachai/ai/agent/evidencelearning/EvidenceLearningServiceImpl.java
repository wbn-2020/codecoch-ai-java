package com.codecoachai.ai.agent.evidencelearning;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import com.codecoachai.ai.agent.campaignreview.mapper.CareerCampaignReviewMemoryCandidateMapper;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewPersistenceService;
import com.codecoachai.ai.agent.config.V9FeatureGate;
import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsFeignClient;
import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.domain.dto.GenerateEvidenceLearningCandidateDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceReuseMaterialDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateEvidenceUsageResultDraftDTO;
import com.codecoachai.ai.domain.vo.EvidenceLearningCandidateDecisionVO;
import com.codecoachai.ai.domain.vo.EvidenceLearningSourceRefVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceLearningCandidateVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceReuseMaterialDraftVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceUsageResultDraftVO;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceLearningServiceImpl implements EvidenceLearningService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final int CANDIDATE_VALIDITY_DAYS = 30;
    private static final String FACTS_UNAVAILABLE_WARNING =
            "服务端事实暂不可用，当前结果不包含可核验候选。";

    private final V9FeatureGate featureGate;
    private final ResumeEvidenceUsageFactsFeignClient factsClient;
    private final AiService aiService;
    private final EvidenceLearningRuleEngine ruleEngine;
    private final CareerCampaignReviewMemoryCandidateMapper candidateMapper;
    private final CareerCampaignReviewPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    @Override
    public EvidenceLearningModels.CandidateList listCandidates(
            Long userId, EvidenceLearningModels.CandidateQuery query) {
        featureGate.requireEvidenceLearning();
        EvidenceLearningModels.CandidateQuery actual =
                query == null ? new EvidenceLearningModels.CandidateQuery() : query;
        String scopeType = scopeType(actual);
        String scopeKey = scopeKey(actual);
        List<CareerCampaignReviewMemoryCandidate> allCandidates =
                candidateRows(candidateMapper.selectByScope(userId, scopeType, scopeKey, null));
        allCandidates.forEach(candidate -> expireIfNeeded(userId, candidate));
        if (!StringUtils.hasText(scopeKey)) {
            List<CareerCampaignReviewMemoryCandidate> visibleCandidates =
                    StringUtils.hasText(actual.getStatus())
                            ? candidateRows(candidateMapper.selectByScope(
                            userId, scopeType, scopeKey, actual.getStatus()))
                            : allCandidates;
            EvidenceLearningModels.CandidateList response = toList(visibleCandidates, null);
            applySavedCandidateEnvelope(response, allCandidates, null, false);
            response.setFallback(false);
            return response;
        }

        ResumeEvidenceUsageFactsVO facts = readFacts(
                userId, actual.getCampaignId(), actual.getApplicationId(),
                actual.getUsageId(), null);
        boolean factsAvailable = factsAvailable(facts);
        GenerateEvidenceLearningCandidateVO generated = null;
        if (factsAvailable) {
            if (expireCandidatesForFactsChange(userId, allCandidates, facts)) {
                allCandidates = candidateRows(
                        candidateMapper.selectByScope(userId, scopeType, scopeKey, null));
            }
            boolean hasActiveCandidate = allCandidates.stream().anyMatch(this::isActiveCandidate);
            if (!hasActiveCandidate
                    && ruleEngine.quality(facts).candidateAllowed()) {
                GenerateEvidenceLearningCandidateDTO request = candidateRequest(
                        userId, actual.getCampaignId(), actual.getApplicationId(), actual.getUsageId());
                generated = generateCandidate(request, facts);
                persistCandidates(userId, request, facts, generated);
                allCandidates = candidateRows(
                        candidateMapper.selectByScope(userId, scopeType, scopeKey, null));
            }
        }

        List<CareerCampaignReviewMemoryCandidate> visibleCandidates =
                StringUtils.hasText(actual.getStatus())
                        ? candidateRows(candidateMapper.selectByScope(
                        userId, scopeType, scopeKey, actual.getStatus()))
                        : allCandidates;
        EvidenceLearningModels.CandidateList response;
        if (!factsAvailable) {
            response = toList(visibleCandidates, null);
            applySavedCandidateEnvelope(response, allCandidates, facts, true);
            response.setFallback(true);
            response.setFallbackReason("服务端事实暂不可用，已展示保存候选的保守快照。");
            return response;
        }

        response = toList(visibleCandidates, facts);
        if (generated != null) {
            response.setFallback(Boolean.TRUE.equals(generated.getFallback()));
            response.setFallbackReason(generated.getFallbackReason());
        } else {
            response.setFallback(false);
        }
        return response;
    }

    private boolean isActiveCandidate(CareerCampaignReviewMemoryCandidate candidate) {
        return candidate != null
                && !List.of("REJECTED", "EXPIRED").contains(candidate.getStatus());
    }

    private boolean expireCandidatesForFactsChange(
            Long userId,
            List<CareerCampaignReviewMemoryCandidate> candidates,
            ResumeEvidenceUsageFactsVO facts) {
        EvidenceLearningRuleEngine.Quality quality = ruleEngine.quality(facts);
        boolean expired = false;
        for (CareerCampaignReviewMemoryCandidate candidate : candidateRows(candidates)) {
            if (!isActiveCandidate(candidate)
                    || !"EVIDENCE_REUSE".equals(candidate.getCandidateType())
                    || sameFactsVersion(candidate, facts, quality)) {
                continue;
            }
            if (candidateMapper.expireForFactsChange(userId, candidate.getId()) == 1) {
                candidate.setStatus("EXPIRED");
                expired = true;
            }
        }
        return expired;
    }

    private boolean sameFactsVersion(
            CareerCampaignReviewMemoryCandidate candidate,
            ResumeEvidenceUsageFactsVO facts,
            EvidenceLearningRuleEngine.Quality quality) {
        return Objects.equals(normalized(candidate.getUsageSourceHash()),
                normalized(facts.getSourceSetHash()))
                && valueOrZero(candidate.getEvidenceCount()) == quality.usageCount()
                && valueOrZero(candidate.getSampleCount()) == quality.sampleCount();
    }

    private List<CareerCampaignReviewMemoryCandidate> candidateRows(
            List<CareerCampaignReviewMemoryCandidate> candidates) {
        return candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
    }

    @Override
    public EvidenceLearningModels.CandidateView getCandidate(Long userId, Long candidateId) {
        featureGate.requireEvidenceLearning();
        CareerCampaignReviewMemoryCandidate candidate =
                candidateMapper.selectOwned(userId, candidateId);
        if (candidate == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "学习候选不存在");
        }
        expireIfNeeded(userId, candidate);
        return toView(candidate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvidenceLearningModels.CandidateView decide(
            Long userId, Long candidateId, EvidenceLearningModels.DecisionCommand command) {
        featureGate.requireEvidenceLearning();
        if (command == null || !StringUtils.hasText(command.getDecisionCode())
                || !StringUtils.hasText(command.getIdempotencyKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "候选决策和幂等键不能为空");
        }
        String code = command.getDecisionCode().trim().toUpperCase(Locale.ROOT);
        if (!List.of("KEEP", "EDIT", "CONTINUE", "REJECT").contains(code)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的候选决策");
        }
        String editedContent = StringUtils.hasText(command.getEditedContent())
                ? command.getEditedContent().trim() : null;
        if ("EDIT".equals(code) && !StringUtils.hasText(editedContent)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "EDIT 决策必须提供修改后的内容");
        }
        String keyHash = AgentAdaptivePlanHashUtils.sha256(
                candidateId + "|" + command.getIdempotencyKey().trim());
        String payloadHash = AgentAdaptivePlanHashUtils.sha256(
                code + "|" + (editedContent == null ? "" : editedContent));
        CareerCampaignReviewMemoryCandidate candidate = persistenceService.decideCandidate(
                userId, candidateId, code, keyHash, payloadHash, editedContent);
        return toView(candidate);
    }

    @Override
    public GenerateEvidenceUsageResultDraftVO resultDraft(
            Long userId, GenerateEvidenceUsageResultDraftDTO request) {
        featureGate.requireEvidenceLearning();
        GenerateEvidenceUsageResultDraftDTO actual = request == null
                ? new GenerateEvidenceUsageResultDraftDTO() : request;
        actual.setUserId(userId);
        ResumeEvidenceUsageFactsVO facts = readFacts(
                userId, actual.getCampaignId(), actual.getApplicationId(),
                actual.getUsageId(), actual.getDataCutoffAt());
        GenerateEvidenceUsageResultDraftVO result;
        try {
            result = aiService.generateEvidenceUsageResultDraft(actual, facts);
        } catch (RuntimeException ex) {
            result = ruleEngine.resultFallback(facts, safeFallbackReason(ex));
        }
        return normalizeResultOutput(result, facts);
    }

    @Override
    public GenerateEvidenceLearningCandidateVO learningCandidate(
            Long userId, GenerateEvidenceLearningCandidateDTO request) {
        featureGate.requireEvidenceLearning();
        GenerateEvidenceLearningCandidateDTO actual = request == null
                ? new GenerateEvidenceLearningCandidateDTO() : request;
        actual.setUserId(userId);
        ResumeEvidenceUsageFactsVO facts = readFacts(
                userId, actual.getCampaignId(), actual.getApplicationId(),
                actual.getUsageId(), actual.getDataCutoffAt());
        GenerateEvidenceLearningCandidateVO generated = generateCandidate(actual, facts);
        persistCandidates(userId, actual, facts, generated);
        return generated;
    }

    @Override
    public GenerateEvidenceReuseMaterialDraftVO reuseMaterialDraft(
            Long userId, GenerateEvidenceReuseMaterialDraftDTO request) {
        featureGate.requireEvidenceLearning();
        GenerateEvidenceReuseMaterialDraftDTO actual = request == null
                ? new GenerateEvidenceReuseMaterialDraftDTO() : request;
        actual.setUserId(userId);
        ResumeEvidenceUsageFactsVO facts = readFacts(
                userId, actual.getCampaignId(), actual.getApplicationId(),
                actual.getUsageId(), actual.getDataCutoffAt());
        GenerateEvidenceReuseMaterialDraftVO result;
        try {
            result = aiService.generateEvidenceReuseMaterialDraft(actual, facts);
        } catch (RuntimeException ex) {
            result = ruleEngine.reuseFallback(facts, safeFallbackReason(ex));
        }
        return normalizeReuseOutput(result, facts);
    }

    private GenerateEvidenceLearningCandidateVO generateCandidate(
            GenerateEvidenceLearningCandidateDTO request, ResumeEvidenceUsageFactsVO facts) {
        GenerateEvidenceLearningCandidateVO result;
        try {
            result = aiService.generateEvidenceLearningCandidate(request, facts);
        } catch (RuntimeException ex) {
            result = ruleEngine.candidateFallback(facts, safeFallbackReason(ex));
        }
        return normalizeCandidateOutput(result, facts);
    }

    private GenerateEvidenceUsageResultDraftVO normalizeResultOutput(
            GenerateEvidenceUsageResultDraftVO result,
            ResumeEvidenceUsageFactsVO facts) {
        GenerateEvidenceUsageResultDraftVO normalized = result == null
                ? ruleEngine.resultFallback(facts, "AI 结果为空，已使用规则降级。")
                : result;
        EvidenceLearningRuleEngine.Quality quality = ruleEngine.quality(facts);
        normalized.setDataCutoffAt(facts.getDataCutoffAt());
        normalized.setSourceSetHash(facts.getSourceSetHash());
        normalized.setConfidenceLevel(quality.confidenceLevel());
        normalized.setLimits(new ArrayList<>(quality.limits()));
        if (quality.usageCount() < 5) {
            normalized.setWeakObservations(new ArrayList<>());
            normalized.setCandidateDecision(new ArrayList<>());
            normalized.setReuseDraft(null);
        }
        return normalized;
    }

    private GenerateEvidenceLearningCandidateVO normalizeCandidateOutput(
            GenerateEvidenceLearningCandidateVO result,
            ResumeEvidenceUsageFactsVO facts) {
        GenerateEvidenceLearningCandidateVO normalized = result == null
                ? ruleEngine.candidateFallback(facts, "AI 结果为空，已使用规则降级。")
                : result;
        EvidenceLearningRuleEngine.Quality quality = ruleEngine.quality(facts);
        normalized.setDataCutoffAt(facts.getDataCutoffAt());
        normalized.setSourceSetHash(facts.getSourceSetHash());
        normalized.setConfidenceLevel(quality.confidenceLevel());
        normalized.setLimits(new ArrayList<>(quality.limits()));
        normalized.setReuseDraft(null);
        if (quality.usageCount() < 5) {
            normalized.setWeakObservations(new ArrayList<>());
        }
        if (!quality.candidateAllowed()) {
            normalized.setCandidateDecision(new ArrayList<>());
            return normalized;
        }
        if (normalized.getCandidateDecision() == null) {
            normalized.setCandidateDecision(new ArrayList<>());
        }
        for (EvidenceLearningCandidateDecisionVO decision : normalized.getCandidateDecision()) {
            if (decision == null) {
                continue;
            }
            decision.setDecisionOptions(List.of("KEEP", "EDIT", "CONTINUE", "REJECT"));
            decision.setRequiresUserConfirmation(true);
            decision.setUsageCount(quality.usageCount());
            decision.setSampleCount(quality.sampleCount());
            decision.setConfidenceLevel(quality.confidenceLevel());
            decision.setLimits(new ArrayList<>(quality.limits()));
        }
        return normalized;
    }

    private GenerateEvidenceReuseMaterialDraftVO normalizeReuseOutput(
            GenerateEvidenceReuseMaterialDraftVO result,
            ResumeEvidenceUsageFactsVO facts) {
        GenerateEvidenceReuseMaterialDraftVO normalized = result == null
                ? ruleEngine.reuseFallback(facts, "AI 结果为空，已使用规则降级。")
                : result;
        EvidenceLearningRuleEngine.Quality quality = ruleEngine.quality(facts);
        normalized.setDataCutoffAt(facts.getDataCutoffAt());
        normalized.setSourceSetHash(facts.getSourceSetHash());
        normalized.setConfidenceLevel(quality.confidenceLevel());
        normalized.setLimits(new ArrayList<>(quality.limits()));
        normalized.setCandidateDecision(new ArrayList<>());
        if (quality.usageCount() < 5) {
            normalized.setWeakObservations(new ArrayList<>());
        }
        if (!quality.candidateAllowed()) {
            normalized.setReuseDraft(null);
        } else if (normalized.getReuseDraft() != null) {
            normalized.getReuseDraft().setRequiresUserConfirmation(true);
        }
        return normalized;
    }

    private void persistCandidates(
            Long userId,
            GenerateEvidenceLearningCandidateDTO request,
            ResumeEvidenceUsageFactsVO facts,
            GenerateEvidenceLearningCandidateVO generated) {
        EvidenceLearningRuleEngine.Quality quality = ruleEngine.quality(facts);
        if (generated == null || generated.getCandidateDecision() == null
                || !quality.candidateAllowed()) {
            return;
        }
        String scopeType = scopeType(request.getCampaignId(), request.getApplicationId(), request.getUsageId());
        String scopeKey = scopeKey(request.getCampaignId(), request.getApplicationId(), request.getUsageId());
        String status = ruleEngine.candidateStatus(facts);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(CANDIDATE_VALIDITY_DAYS);
        for (EvidenceLearningCandidateDecisionVO decision : generated.getCandidateDecision()) {
            if (decision == null || !StringUtils.hasText(decision.getTitle())
                    || !StringUtils.hasText(decision.getContent())
                    || decision.getSourceRefs() == null || decision.getSourceRefs().isEmpty()) {
                continue;
            }
            CareerCampaignReviewMemoryCandidate candidate =
                    new CareerCampaignReviewMemoryCandidate();
            candidate.setUserId(userId);
            candidate.setCandidateScopeType(scopeType);
            candidate.setCandidateScopeKey(scopeKey);
            candidate.setCandidateType("EVIDENCE_REUSE");
            candidate.setUsageSourceHash(facts.getSourceSetHash());
            candidate.setEvidenceCount(quality.usageCount());
            candidate.setSampleCount(quality.sampleCount());
            candidate.setLimitsJson(write(quality.limits()));
            candidate.setCandidateKey(firstText(decision.getCandidateKey(),
                    "evidence-" + AgentAdaptivePlanHashUtils.sha256(decision.getTitle()).substring(0, 16)));
            candidate.setSemanticHash(AgentAdaptivePlanHashUtils.sha256(
                    scopeType + "|" + scopeKey + "|" + candidate.getCandidateKey()
                            + "|" + facts.getSourceSetHash()));
            candidate.setTitle(decision.getTitle().trim());
            candidate.setContent(decision.getContent().trim());
            candidate.setSourceRef(sourceRef(decision.getSourceRefs()));
            candidate.setConfidenceLevel(quality.confidenceLevel());
            candidate.setStatus(status);
            candidate.setValidityDays(CANDIDATE_VALIDITY_DAYS);
            candidate.setExpiresAt(expiresAt);
            candidateMapper.insertCandidate(candidate);
        }
    }

    private EvidenceLearningModels.CandidateList toList(
            List<CareerCampaignReviewMemoryCandidate> candidates,
            ResumeEvidenceUsageFactsVO facts) {
        EvidenceLearningModels.CandidateList result = new EvidenceLearningModels.CandidateList();
        if (candidates != null) {
            for (CareerCampaignReviewMemoryCandidate candidate : candidates) {
                expireIfNeeded(candidate.getUserId(), candidate);
                result.getCandidates().add(toView(candidate));
            }
        }
        applyCandidateCoverage(result, candidateRows(candidates), facts != null);
        result.setSources(savedCandidateSources(candidateRows(candidates)));
        if (facts != null) {
            EvidenceLearningRuleEngine.Quality quality = ruleEngine.quality(facts);
            result.setDataCutoffAt(facts.getDataCutoffAt());
            result.setSourceSetHash(facts.getSourceSetHash());
            result.setConfidenceLevel(quality.confidenceLevel());
            result.setLimits(new ArrayList<>(quality.limits()));
            result.setWarnings(facts.getWarnings() == null
                    ? new ArrayList<>() : new ArrayList<>(facts.getWarnings()));
            List<EvidenceLearningSourceRefVO> factSources = ruleEngine.sourceRefs(facts);
            if (!factSources.isEmpty()) {
                result.setSources(factSources);
            }
        }
        return result;
    }

    private void applySavedCandidateEnvelope(
            EvidenceLearningModels.CandidateList result,
            List<CareerCampaignReviewMemoryCandidate> candidates,
            ResumeEvidenceUsageFactsVO fallbackFacts,
            boolean factsUnavailable) {
        List<CareerCampaignReviewMemoryCandidate> rows = candidateRows(candidates);
        result.setDataCutoffAt(fallbackFacts != null && fallbackFacts.getDataCutoffAt() != null
                ? fallbackFacts.getDataCutoffAt() : candidateCutoff(rows));
        List<String> sourceHashes = rows.stream()
                .map(CareerCampaignReviewMemoryCandidate::getUsageSourceHash)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (sourceHashes.size() == 1) {
            result.setSourceSetHash(sourceHashes.get(0));
        } else if (!sourceHashes.isEmpty()) {
            result.setSourceSetHash(AgentAdaptivePlanHashUtils.sha256(
                    "saved-candidates|" + String.join("|", sourceHashes)));
        } else if (fallbackFacts != null
                && StringUtils.hasText(fallbackFacts.getSourceSetHash())) {
            result.setSourceSetHash(fallbackFacts.getSourceSetHash());
        } else {
            result.setSourceSetHash(AgentAdaptivePlanHashUtils.sha256("saved-candidates|empty"));
        }

        result.setConfidenceLevel(conservativeConfidence(rows, fallbackFacts));
        LinkedHashSet<String> limits = new LinkedHashSet<>();
        for (CareerCampaignReviewMemoryCandidate candidate : rows) {
            limits.addAll(read(candidate.getLimitsJson()));
        }
        if (fallbackFacts != null && fallbackFacts.getLimits() != null) {
            limits.addAll(fallbackFacts.getLimits());
        }
        if (factsUnavailable) {
            limits.add("事实来源暂不可用，当前 envelope 仅聚合已保存候选，不生成新结论。");
        }
        result.setLimits(new ArrayList<>(limits));

        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (fallbackFacts != null && fallbackFacts.getWarnings() != null) {
            warnings.addAll(fallbackFacts.getWarnings());
        }
        if (factsUnavailable) {
            warnings.add("最新事实读取失败，候选 freshness 暂无法确认。");
            result.setUnknowns(List.of("当前无法确认保存候选是否仍对应最新事实版本。"));
        }
        result.setWarnings(new ArrayList<>(warnings));
        result.setSources(savedCandidateSources(rows));
        applyCandidateCoverage(result, rows, !factsUnavailable);
    }

    private void applyCandidateCoverage(
            EvidenceLearningModels.CandidateList result,
            List<CareerCampaignReviewMemoryCandidate> candidates,
            boolean factsAvailable) {
        List<CareerCampaignReviewMemoryCandidate> rows = candidateRows(candidates);
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("candidateCount", rows.size());
        coverage.put("activeCandidateCount", rows.stream().filter(this::isActiveCandidate).count());
        coverage.put("sourceCount", savedCandidateSources(rows).size());
        coverage.put("factsAvailable", factsAvailable);
        result.setCoverage(coverage);
    }

    private LocalDateTime candidateCutoff(
            List<CareerCampaignReviewMemoryCandidate> candidates) {
        LocalDateTime cutoff = null;
        for (CareerCampaignReviewMemoryCandidate candidate : candidateRows(candidates)) {
            LocalDateTime value = candidate.getUpdatedAt() == null
                    ? candidate.getCreatedAt() : candidate.getUpdatedAt();
            if (value != null && (cutoff == null || value.isAfter(cutoff))) {
                cutoff = value;
            }
        }
        return cutoff == null ? LocalDateTime.now() : cutoff;
    }

    private List<EvidenceLearningSourceRefVO> savedCandidateSources(
            List<CareerCampaignReviewMemoryCandidate> candidates) {
        Map<String, EvidenceLearningSourceRefVO> sources = new LinkedHashMap<>();
        for (CareerCampaignReviewMemoryCandidate candidate : candidateRows(candidates)) {
            for (EvidenceLearningSourceRefVO ref : parseSourceRefs(candidate.getSourceRef())) {
                String key = String.join("|",
                        firstText(ref.getSourceType(), ""),
                        firstText(ref.getSourceId(), ""),
                        firstText(ref.getFieldPath(), ""),
                        firstText(ref.getSourceHash(), ""));
                sources.putIfAbsent(key, ref);
            }
        }
        return new ArrayList<>(sources.values());
    }

    private String conservativeConfidence(
            List<CareerCampaignReviewMemoryCandidate> candidates,
            ResumeEvidenceUsageFactsVO fallbackFacts) {
        String confidence = null;
        for (CareerCampaignReviewMemoryCandidate candidate : candidates) {
            String current = normalized(candidate.getConfidenceLevel());
            if (current == null) {
                current = "LOW";
            } else {
                current = current.toUpperCase(Locale.ROOT);
            }
            if (confidence == null || confidenceRank(current) < confidenceRank(confidence)) {
                confidence = current;
            }
        }
        return confidence == null
                ? ruleEngine.quality(fallbackFacts).confidenceLevel()
                : confidence;
    }

    private int confidenceRank(String confidence) {
        String normalized = normalized(confidence);
        return switch (normalized == null ? "" : normalized.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private EvidenceLearningModels.CandidateView toView(
            CareerCampaignReviewMemoryCandidate candidate) {
        EvidenceLearningModels.CandidateView result = EvidenceLearningModels.from(candidate);
        result.setLimits(read(candidate.getLimitsJson()));
        result.setSourceRefs(parseSourceRefs(candidate.getSourceRef()));
        if ("EDIT".equals(candidate.getDecisionCode())) {
            result.setEditDeepLink("/evidence-assets");
        }
        return result;
    }

    private void expireIfNeeded(Long userId, CareerCampaignReviewMemoryCandidate candidate) {
        if (candidate.getExpiresAt() != null
                && !candidate.getExpiresAt().isAfter(LocalDateTime.now())
                && List.of("PENDING", "PENDING_CONFIRMATION", "WEAK_OBSERVATION")
                .contains(candidate.getStatus())) {
            candidateMapper.expire(userId, candidate.getId(), LocalDateTime.now());
            candidate.setStatus("EXPIRED");
        }
    }

    private ResumeEvidenceUsageFactsVO readFacts(
            Long userId, Long campaignId, Long applicationId, Long usageId,
            LocalDateTime dataCutoffAt) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (campaignId == null && applicationId == null && usageId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少需要一个服务端业务 ID");
        }
        try {
            ResumeEvidenceUsageFactsVO facts = FeignResultUtils.unwrap(
                    factsClient.getFacts(
                            userId, campaignId, applicationId, usageId, dataCutoffAt));
            if (facts == null || !userId.equals(facts.getUserId())
                    || !StringUtils.hasText(facts.getSourceSetHash())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "事实信封校验失败");
            }
            facts = constrainFactsToCutoff(facts, dataCutoffAt);
            return facts;
        } catch (RuntimeException ex) {
            log.warn("V9 facts lookup failed; returning bounded fallback userId={}", userId);
            ResumeEvidenceUsageFactsVO fallback = new ResumeEvidenceUsageFactsVO();
            fallback.setUserId(userId);
            fallback.setDataCutoffAt(dataCutoffAt == null
                    ? LocalDateTime.now() : dataCutoffAt);
            fallback.setSourceSetHash(AgentAdaptivePlanHashUtils.sha256(
                    "facts-unavailable|" + userId + "|" + campaignId + "|" + applicationId + "|" + usageId));
            fallback.setWarnings(List.of(FACTS_UNAVAILABLE_WARNING));
            fallback.setLimits(List.of("事实来源不可用时不生成强结论或学习候选。"));
            return fallback;
        }
    }

    private boolean factsAvailable(ResumeEvidenceUsageFactsVO facts) {
        return facts != null
                && (facts.getWarnings() == null
                || !facts.getWarnings().contains(FACTS_UNAVAILABLE_WARNING));
    }

    private ResumeEvidenceUsageFactsVO constrainFactsToCutoff(
            ResumeEvidenceUsageFactsVO facts, LocalDateTime requestedCutoff) {
        LocalDateTime responseCutoff = facts.getDataCutoffAt();
        LocalDateTime cutoff = responseCutoff;
        if (requestedCutoff != null
                && (cutoff == null || requestedCutoff.isBefore(cutoff))) {
            cutoff = requestedCutoff;
        }
        if (cutoff == null) {
            return facts;
        }
        LocalDateTime effectiveCutoff = cutoff;

        List<ResumeEvidenceUsageFactsVO.UsageFact> usages =
                facts.getUsageSnapshots() == null
                        ? new ArrayList<>() : facts.getUsageSnapshots();
        List<ResumeEvidenceUsageFactsVO.ResultFact> results =
                facts.getConfirmedResults() == null
                        ? new ArrayList<>() : facts.getConfirmedResults();
        List<ResumeEvidenceUsageFactsVO.UsageFact> visibleUsages = usages.stream()
                .filter(item -> item != null && !isAfter(item.getUsedAt(), effectiveCutoff))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<ResumeEvidenceUsageFactsVO.ResultFact> visibleResults = results.stream()
                .filter(item -> item != null
                        && !isAfter(item.getOccurredAt(), effectiveCutoff)
                        && !isAfter(item.getConfirmedAt(), effectiveCutoff))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean changed = !effectiveCutoff.equals(responseCutoff)
                || visibleUsages.size() != usages.size()
                || visibleResults.size() != results.size();

        facts.setDataCutoffAt(effectiveCutoff);
        facts.setUsageSnapshots(visibleUsages);
        facts.setConfirmedResults(visibleResults);
        if (changed) {
            List<String> warnings = facts.getWarnings() == null
                    ? new ArrayList<>() : new ArrayList<>(facts.getWarnings());
            String warning = "已按 dataCutoffAt 移除截止时间后的事实。";
            if (!warnings.contains(warning)) {
                warnings.add(warning);
            }
            facts.setWarnings(warnings);
            facts.setSourceSetHash(AgentAdaptivePlanHashUtils.sha256(
                    "v9-cutoff|" + facts.getSourceSetHash() + "|" + effectiveCutoff + "|"
                            + visibleUsages + "|" + visibleResults + "|"
                            + facts.getExperimentAttributions() + "|" + facts.getLimits()
                            + "|" + facts.getCoverage() + "|" + warnings));
        }
        return facts;
    }

    private boolean isAfter(LocalDateTime value, LocalDateTime cutoff) {
        return value != null && value.isAfter(cutoff);
    }

    private String scopeType(EvidenceLearningModels.CandidateQuery query) {
        return scopeType(query.getCampaignId(), query.getApplicationId(), query.getUsageId());
    }

    private String scopeKey(EvidenceLearningModels.CandidateQuery query) {
        return scopeKey(query.getCampaignId(), query.getApplicationId(), query.getUsageId());
    }

    private String scopeType(Long campaignId, Long applicationId, Long usageId) {
        if (usageId != null) {
            return "USAGE";
        }
        if (applicationId != null) {
            return "APPLICATION";
        }
        return campaignId == null ? null : "CAMPAIGN";
    }

    private String scopeKey(Long campaignId, Long applicationId, Long usageId) {
        if (usageId != null) {
            return String.valueOf(usageId);
        }
        if (applicationId != null) {
            return String.valueOf(applicationId);
        }
        return campaignId == null ? null : String.valueOf(campaignId);
    }

    private GenerateEvidenceLearningCandidateDTO candidateRequest(
            Long userId, Long campaignId, Long applicationId, Long usageId) {
        GenerateEvidenceLearningCandidateDTO request = new GenerateEvidenceLearningCandidateDTO();
        request.setUserId(userId);
        request.setCampaignId(campaignId);
        request.setApplicationId(applicationId);
        request.setUsageId(usageId);
        return request;
    }

    private String sourceRef(List<EvidenceLearningSourceRefVO> refs) {
        String selected = null;
        for (EvidenceLearningSourceRefVO ref : refs) {
            if (ref == null || !StringUtils.hasText(ref.getSourceId())
                    || !StringUtils.hasText(ref.getSourceHash())) {
                continue;
            }
            String encoded = String.join("|",
                    "V9",
                    encodeRefPart(firstText(ref.getSourceType(), "SOURCE")),
                    encodeRefPart(ref.getSourceId()),
                    encodeRefPart(firstText(ref.getFieldPath(), "$")),
                    encodeRefPart(ref.getSourceHash()));
            if (encoded.length() <= 255
                    && (selected == null || encoded.length() < selected.length())) {
                selected = encoded;
            }
        }
        return selected;
    }

    private List<EvidenceLearningSourceRefVO> parseSourceRefs(String value) {
        List<EvidenceLearningSourceRefVO> refs = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return refs;
        }
        if (value.startsWith("V9|")) {
            String[] parts = value.split("\\|", 5);
            if (parts.length == 5) {
                EvidenceLearningSourceRefVO ref = new EvidenceLearningSourceRefVO();
                ref.setSourceType(decodeRefPart(parts[1]));
                ref.setSourceId(decodeRefPart(parts[2]));
                ref.setFieldPath(decodeRefPart(parts[3]));
                ref.setSourceHash(decodeRefPart(parts[4]));
                refs.add(ref);
            }
            return refs;
        }
        for (String token : value.split(";")) {
            String[] parts = token.split(":", 3);
            EvidenceLearningSourceRefVO ref = new EvidenceLearningSourceRefVO();
            if (parts.length == 3) {
                ref.setSourceType(parts[0]);
                ref.setSourceId(parts[1]);
                ref.setFieldPath(parts[2]);
            } else {
                ref.setSourceType("SOURCE");
                ref.setSourceId(token);
            }
            refs.add(ref);
        }
        return refs;
    }

    private String encodeRefPart(String value) {
        return value.replace("%", "%25").replace("|", "%7C");
    }

    private String decodeRefPart(String value) {
        return value.replace("%7C", "|").replace("%25", "%");
    }

    private List<String> read(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeFallbackReason(RuntimeException ex) {
        return "AI 结果不可用，已使用规则降级。";
    }
}
