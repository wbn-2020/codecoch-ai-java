package com.codecoachai.ai.agent.evidencelearning;

import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.domain.vo.EvidenceLearningCandidateDecisionVO;
import com.codecoachai.ai.domain.vo.EvidenceLearningReuseDraftVO;
import com.codecoachai.ai.domain.vo.EvidenceLearningSourceRefVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceLearningCandidateVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceReuseMaterialDraftVO;
import com.codecoachai.ai.domain.vo.GenerateEvidenceUsageResultDraftVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EvidenceLearningRuleEngine {

    public GenerateEvidenceUsageResultDraftVO resultFallback(
            ResumeEvidenceUsageFactsVO facts, String reason) {
        GenerateEvidenceUsageResultDraftVO result = new GenerateEvidenceUsageResultDraftVO();
        applyEnvelope(result, facts);
        result.setSummary(summary(facts));
        result.setFacts(facts(facts));
        result.setWeakObservations(weakObservations(facts));
        result.setUnknowns(unknowns(facts));
        result.setLimits(limits(facts));
        result.setSourceRefs(sourceRefs(facts));
        result.setConfidenceLevel(quality(facts).confidenceLevel());
        result.setFallback(true);
        result.setFallbackReason(firstText(reason, "AI 结果不可用，已使用规则结果。"));
        return result;
    }

    public GenerateEvidenceLearningCandidateVO candidateFallback(
            ResumeEvidenceUsageFactsVO facts, String reason) {
        GenerateEvidenceLearningCandidateVO result = new GenerateEvidenceLearningCandidateVO();
        Quality quality = quality(facts);
        applyEnvelope(result, facts);
        result.setSummary(summary(facts));
        result.setFacts(facts(facts));
        result.setWeakObservations(weakObservations(facts));
        result.setUnknowns(unknowns(facts));
        result.setLimits(quality.limits());
        result.setSourceRefs(sourceRefs(facts));
        result.setConfidenceLevel(quality.confidenceLevel());
        if (quality.candidateAllowed()) {
            result.setCandidateDecision(List.of(candidateDecision(facts)));
        }
        result.setFallback(true);
        result.setFallbackReason(firstText(reason, "AI 结果不可用，已使用规则候选。"));
        return result;
    }

    public GenerateEvidenceReuseMaterialDraftVO reuseFallback(
            ResumeEvidenceUsageFactsVO facts, String reason) {
        GenerateEvidenceReuseMaterialDraftVO result = new GenerateEvidenceReuseMaterialDraftVO();
        Quality quality = quality(facts);
        applyEnvelope(result, facts);
        result.setSummary(summary(facts));
        result.setFacts(facts(facts));
        result.setWeakObservations(weakObservations(facts));
        result.setUnknowns(unknowns(facts));
        result.setLimits(quality.limits());
        result.setSourceRefs(sourceRefs(facts));
        result.setConfidenceLevel(quality.confidenceLevel());
        if (quality.candidateAllowed()) {
            EvidenceLearningReuseDraftVO draft = new EvidenceLearningReuseDraftVO();
            draft.setTitle("证据材料待确认草稿");
            draft.setContent("当前材料可作为后续复用草稿，需结合新的业务场景由用户确认后再编辑使用。");
            draft.setEditDeepLink("/evidence-assets");
            draft.setRequiresUserConfirmation(true);
            result.setReuseDraft(draft);
        }
        result.setFallback(true);
        result.setFallbackReason(firstText(reason, "AI 结果不可用，已使用规则草稿。"));
        return result;
    }

    public Quality quality(ResumeEvidenceUsageFactsVO facts) {
        int usageCount = comparableApplicationCount(facts);
        int sampleCount = sampleCount(facts);
        int interviewCount = interviewCount(facts);
        List<String> limits = new ArrayList<>();
        if (usageCount < 5) {
            limits.add("可比较投递样本少于 5 条，只能展示事实、未知项和限制。");
        } else if (usageCount < 15) {
            limits.add("当前样本只能形成弱观察，不能输出强策略、排名或概率。");
        } else if (interviewCount < 3) {
            limits.add("可以观察投递和反馈过程趋势，但不能判断面试能力变化。");
        } else {
            limits.add("样本达到复盘口径，仍需保留岗位、渠道和时间窗口边界。");
        }
        if (!versionComparisonAllowed(facts)) {
            limits.add("每个证据或简历版本使用少于 3 次时，不比较版本优劣。");
        }
        limits.add("不能把结果归因到单一因素，需结合岗位、渠道、证据和时间窗口。");
        if (facts != null && facts.getLimits() != null) {
            limits.addAll(facts.getLimits());
        }
        String confidence = usageCount < 15 ? "LOW"
                : interviewCount < 3 ? "MEDIUM" : "HIGH";
        return new Quality(
                confidence,
                unique(limits),
                usageCount,
                sampleCount,
                usageCount >= 5 && hasUsableSources(facts));
    }

    public int usageCount(ResumeEvidenceUsageFactsVO facts) {
        if (facts == null || facts.getUsageSnapshots() == null) {
            return 0;
        }
        return (int) facts.getUsageSnapshots().stream()
                .filter(this::comparableUsage)
                .count();
    }

    public int sampleCount(ResumeEvidenceUsageFactsVO facts) {
        if (facts == null || facts.getConfirmedResults() == null) {
            return 0;
        }
        Set<Long> eligibleUsageIds = eligibleUsageIds(facts);
        return (int) facts.getConfirmedResults().stream()
                .filter(result -> result != null
                        && (result.getUsageId() == null
                        || eligibleUsageIds.contains(result.getUsageId())))
                .count();
    }

    public int comparableApplicationCount(ResumeEvidenceUsageFactsVO facts) {
        if (facts == null || facts.getUsageSnapshots() == null) {
            return 0;
        }
        Set<String> comparable = new HashSet<>();
        for (ResumeEvidenceUsageFactsVO.UsageFact usage : facts.getUsageSnapshots()) {
            if (!comparableUsage(usage)) {
                continue;
            }
            if (usage.getApplicationId() != null) {
                comparable.add("application:" + usage.getApplicationId());
            } else if (usage.getUsageId() != null) {
                comparable.add("usage:" + usage.getUsageId());
            }
        }
        return comparable.size();
    }

    public List<EvidenceLearningSourceRefVO> sourceRefs(ResumeEvidenceUsageFactsVO facts) {
        Set<String> seen = new LinkedHashSet<>();
        List<EvidenceLearningSourceRefVO> refs = new ArrayList<>();
        if (facts != null && facts.getUsageSnapshots() != null) {
            for (ResumeEvidenceUsageFactsVO.UsageFact usage : facts.getUsageSnapshots()) {
                if (!comparableUsage(usage) || usage.getUsageId() == null) {
                    continue;
                }
                String key = "usage:" + usage.getUsageId();
                if (seen.add(key)) {
                    refs.add(ref("EVIDENCE_USAGE", String.valueOf(usage.getUsageId()),
                            "$.usageSnapshots", usage.getSourceHash()));
                }
                if (usage.getSourceRefs() != null) {
                    for (String source : usage.getSourceRefs()) {
                        if (StringUtils.hasText(source) && seen.add("source:" + source.trim())) {
                            refs.add(ref(sourceType(source), source.trim(),
                                    "$.usageSnapshots[*].sourceRefs", usage.getSourceHash()));
                        }
                    }
                }
            }
        }
        if (facts != null && facts.getConfirmedResults() != null) {
            Set<Long> eligibleUsageIds = eligibleUsageIds(facts);
            for (ResumeEvidenceUsageFactsVO.ResultFact result : facts.getConfirmedResults()) {
                if (result == null || result.getResultId() == null
                        || (result.getUsageId() != null
                        && !eligibleUsageIds.contains(result.getUsageId()))) {
                    continue;
                }
                String key = "result:" + result.getResultId();
                if (seen.add(key)) {
                    refs.add(ref("EVIDENCE_USAGE_RESULT", String.valueOf(result.getResultId()),
                            "$.confirmedResults", result.getSourceHash()));
                }
            }
        }
        return refs;
    }

    public boolean hasUsableSources(ResumeEvidenceUsageFactsVO facts) {
        return sourceRefs(facts).stream().anyMatch(this::completeSourceRef)
                && facts != null
                && StringUtils.hasText(facts.getSourceSetHash());
    }

    public String candidateStatus(ResumeEvidenceUsageFactsVO facts) {
        return "LOW".equals(quality(facts).confidenceLevel())
                ? "WEAK_OBSERVATION" : "PENDING_CONFIRMATION";
    }

    private EvidenceLearningCandidateDecisionVO candidateDecision(ResumeEvidenceUsageFactsVO facts) {
        Quality quality = quality(facts);
        List<EvidenceLearningSourceRefVO> refs = sourceRefs(facts);
        String hash = facts == null ? "" : Objects.toString(facts.getSourceSetHash(), "");
        String key = "evidence-reuse-" + AgentAdaptivePlanHashUtils.sha256(hash).substring(0, 16);
        String content = "当前证据使用方式可继续作为待确认观察，需结合更多可比较样本后再决定是否保留。";
        return EvidenceLearningModels.decision(
                key, "证据使用方式待确认", content,
                quality.usageCount(), quality.sampleCount(), quality.confidenceLevel(),
                quality.limits(), refs);
    }

    private List<String> facts(ResumeEvidenceUsageFactsVO facts) {
        Quality quality = quality(facts);
        List<String> values = new ArrayList<>();
        values.add("已回读可比较投递 " + quality.usageCount() + " 条。");
        values.add("已回读用户确认结果 " + quality.sampleCount() + " 条。");
        if (facts != null && facts.getExperimentAttributions() != null
                && !facts.getExperimentAttributions().isEmpty()) {
            values.add("已回读实验归因摘要 " + facts.getExperimentAttributions().size() + " 条。");
        }
        return values;
    }

    private List<String> weakObservations(ResumeEvidenceUsageFactsVO facts) {
        Quality quality = quality(facts);
        if (quality.usageCount() < 5) {
            return new ArrayList<>();
        }
        if ("LOW".equals(quality.confidenceLevel())) {
            return List.of("当前仅能形成弱观察，不能把观察解释为结果原因。");
        }
        return List.of("不同来源可以继续进行受限比较，但仍不能得出单因素因果结论。");
    }

    private List<String> unknowns(ResumeEvidenceUsageFactsVO facts) {
        List<String> values = new ArrayList<>();
        if (facts != null && facts.getWarnings() != null) {
            values.addAll(facts.getWarnings());
        }
        if (facts == null || facts.getConfirmedResults() == null
                || facts.getConfirmedResults().isEmpty()) {
            values.add("尚无已确认结果，不能判断使用方式与结果之间的关系。");
        }
        return unique(values);
    }

    private List<String> limits(ResumeEvidenceUsageFactsVO facts) {
        return new ArrayList<>(quality(facts).limits());
    }

    private int interviewCount(ResumeEvidenceUsageFactsVO facts) {
        if (facts == null || facts.getConfirmedResults() == null) {
            return 0;
        }
        Set<Long> eligibleUsageIds = eligibleUsageIds(facts);
        int count = 0;
        for (ResumeEvidenceUsageFactsVO.ResultFact result : facts.getConfirmedResults()) {
            if (result != null
                    && (result.getUsageId() == null || eligibleUsageIds.contains(result.getUsageId()))
                    && StringUtils.hasText(result.getEventType())
                    && result.getEventType().toUpperCase(Locale.ROOT).contains("INTERVIEW")) {
                count++;
            }
        }
        return count;
    }

    private Set<Long> eligibleUsageIds(ResumeEvidenceUsageFactsVO facts) {
        Set<Long> ids = new HashSet<>();
        if (facts == null || facts.getUsageSnapshots() == null) {
            return ids;
        }
        for (ResumeEvidenceUsageFactsVO.UsageFact usage : facts.getUsageSnapshots()) {
            if (comparableUsage(usage) && usage.getUsageId() != null) {
                ids.add(usage.getUsageId());
            }
        }
        return ids;
    }

    private boolean comparableUsage(ResumeEvidenceUsageFactsVO.UsageFact usage) {
        if (usage == null || Boolean.TRUE.equals(usage.getStale())) {
            return false;
        }
        String status = usage.getStatus();
        return status == null
                || (!"STALE".equalsIgnoreCase(status)
                && !"SUPERSEDED".equalsIgnoreCase(status));
    }

    private boolean versionComparisonAllowed(ResumeEvidenceUsageFactsVO facts) {
        if (facts == null || facts.getUsageSnapshots() == null) {
            return false;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (ResumeEvidenceUsageFactsVO.UsageFact usage : facts.getUsageSnapshots()) {
            if (!comparableUsage(usage) || !StringUtils.hasText(usage.getAssetVersion())) {
                continue;
            }
            counts.merge(usage.getAssetVersion().trim(), 1, Integer::sum);
        }
        return counts.size() >= 2 && counts.values().stream().allMatch(count -> count >= 3);
    }

    private EvidenceLearningSourceRefVO ref(
            String type, String id, String path, String hash) {
        EvidenceLearningSourceRefVO ref = new EvidenceLearningSourceRefVO();
        ref.setSourceType(type);
        ref.setSourceId(id);
        ref.setFieldPath(path);
        ref.setSourceHash(hash);
        return ref;
    }

    private String sourceType(String sourceRef) {
        if (!StringUtils.hasText(sourceRef)) {
            return "EVIDENCE_SOURCE";
        }
        int separator = sourceRef.indexOf(':');
        return separator > 0 ? sourceRef.substring(0, separator).trim() : "EVIDENCE_SOURCE";
    }

    private boolean completeSourceRef(EvidenceLearningSourceRefVO ref) {
        return ref != null
                && StringUtils.hasText(ref.getSourceType())
                && StringUtils.hasText(ref.getSourceId())
                && StringUtils.hasText(ref.getFieldPath())
                && StringUtils.hasText(ref.getSourceHash());
    }

    private List<String> unique(List<String> values) {
        return values == null ? new ArrayList<>()
                : new ArrayList<>(new LinkedHashSet<>(values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList()));
    }

    private String summary(ResumeEvidenceUsageFactsVO facts) {
        return "本结果仅基于服务端回读的证据使用快照、已确认结果和实验归因摘要生成。";
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void applyEnvelope(
            GenerateEvidenceUsageResultDraftVO result, ResumeEvidenceUsageFactsVO facts) {
        if (facts != null) {
            result.setDataCutoffAt(facts.getDataCutoffAt());
            result.setSourceSetHash(facts.getSourceSetHash());
        }
    }

    private void applyEnvelope(
            GenerateEvidenceLearningCandidateVO result, ResumeEvidenceUsageFactsVO facts) {
        if (facts != null) {
            result.setDataCutoffAt(facts.getDataCutoffAt());
            result.setSourceSetHash(facts.getSourceSetHash());
        }
    }

    private void applyEnvelope(
            GenerateEvidenceReuseMaterialDraftVO result, ResumeEvidenceUsageFactsVO facts) {
        if (facts != null) {
            result.setDataCutoffAt(facts.getDataCutoffAt());
            result.setSourceSetHash(facts.getSourceSetHash());
        }
    }

    public record Quality(
            String confidenceLevel,
            List<String> limits,
            int usageCount,
            int sampleCount,
            boolean candidateAllowed) {
    }
}
