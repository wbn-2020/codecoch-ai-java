package com.codecoachai.ai.agent.campaignreview;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.feign.CareerCampaignReviewEvidenceVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CareerCampaignReviewEvidenceEnvelope {

    private final Long userId;
    private final Long campaignId;
    private final String campaignStatus;
    private final String campaignTitle;
    private final Boolean completed;
    private final Boolean allOpportunitiesClosed;
    private final Integer sampleSize;
    private final LocalDateTime dataCutoffAt;
    private final List<CareerCampaignReviewGenerateDTO.Fact> facts;
    private final List<CareerCampaignReviewGenerateDTO.Source> sources;
    private final String evidenceHash;
    private final String inputHash;

    private CareerCampaignReviewEvidenceEnvelope(
            Long userId,
            Long campaignId,
            String campaignStatus,
            String campaignTitle,
            Boolean completed,
            Boolean allOpportunitiesClosed,
            Integer sampleSize,
            LocalDateTime dataCutoffAt,
            List<CareerCampaignReviewGenerateDTO.Fact> facts,
            List<CareerCampaignReviewGenerateDTO.Source> sources) {
        this.userId = userId;
        this.campaignId = campaignId;
        this.campaignStatus = campaignStatus;
        this.campaignTitle = campaignTitle;
        this.completed = completed;
        this.allOpportunitiesClosed = allOpportunitiesClosed;
        this.sampleSize = sampleSize;
        this.dataCutoffAt = dataCutoffAt;
        this.facts = List.copyOf(facts);
        this.sources = List.copyOf(sources);
        this.evidenceHash = AgentAdaptivePlanHashUtils.sha256(canonicalEvidence());
        this.inputHash = AgentAdaptivePlanHashUtils.sha256(String.join("\n",
                "evidenceHash=" + evidenceHash,
                "evidenceSchemaVersion=" + CareerCampaignReviewVersions.EVIDENCE_SCHEMA_VERSION,
                "ruleVersion=" + CareerCampaignReviewVersions.RULE_VERSION,
                "outputSchemaVersion=" + CareerCampaignReviewVersions.OUTPUT_SCHEMA_VERSION));
    }

    public static CareerCampaignReviewEvidenceEnvelope from(
            CareerCampaignReviewEvidenceVO evidence,
            LocalDateTime fallbackCutoffAt) {
        Objects.requireNonNull(evidence, "evidence is required");
        LocalDateTime cutoffAt = evidence.getDataCutoffAt() == null
                ? Objects.requireNonNull(fallbackCutoffAt, "fallbackCutoffAt is required")
                : evidence.getDataCutoffAt();
        return new CareerCampaignReviewEvidenceEnvelope(
                evidence.getUserId(),
                evidence.getCampaignId(),
                evidence.getCampaignStatus(),
                evidence.getCampaignTitle(),
                evidence.getCompleted(),
                evidence.getAllOpportunitiesClosed(),
                evidence.getSampleSize(),
                cutoffAt,
                facts(evidence.getFacts()),
                sources(evidence.getSources()));
    }

    public CareerCampaignReviewGenerateDTO trustedRequest(
            CareerCampaignReviewGenerateDTO publicRequest) {
        CareerCampaignReviewGenerateDTO trusted = new CareerCampaignReviewGenerateDTO();
        trusted.setCampaignId(campaignId);
        trusted.setIdempotencyKey(publicRequest == null
                ? null : publicRequest.getIdempotencyKey());
        trusted.setRequestId(publicRequest == null ? null : publicRequest.getRequestId());
        trusted.setCampaignStatus(campaignStatus);
        trusted.setCampaignTitle(campaignTitle);
        trusted.setCompleted(completed);
        trusted.setAllOpportunitiesClosed(allOpportunitiesClosed);
        trusted.setSampleSize(sampleSize);
        trusted.setDataCutoffAt(dataCutoffAt);
        trusted.setFacts(new ArrayList<>(facts));
        trusted.setSources(new ArrayList<>(sources));
        trusted.setMemoryCandidateSeeds(new ArrayList<>());
        trusted.setExperimentCandidateSeeds(new ArrayList<>());
        trusted.setNextCycleActionSeeds(new ArrayList<>());
        return trusted;
    }

    public String payloadHash() {
        return AgentAdaptivePlanHashUtils.sha256(String.join("\n",
                "campaignId=" + Objects.toString(campaignId, ""),
                "inputHash=" + inputHash));
    }

    public String generationFingerprint() {
        return AgentAdaptivePlanHashUtils.sha256(String.join("\n",
                "scene=" + CareerCampaignReviewAiScene.NAME,
                "campaignId=" + Objects.toString(campaignId, ""),
                "inputHash=" + inputHash));
    }

    public String sourceMetadataJson(Integer sourceVersion) {
        return "{"
                + "\"sourceVersion\":" + (sourceVersion == null ? "null" : sourceVersion)
                + ",\"evidenceHash\":\"" + evidenceHash + "\""
                + ",\"evidenceSchemaVersion\":\""
                + CareerCampaignReviewVersions.EVIDENCE_SCHEMA_VERSION + "\""
                + ",\"ruleVersion\":\"" + CareerCampaignReviewVersions.RULE_VERSION + "\""
                + ",\"outputSchemaVersion\":\""
                + CareerCampaignReviewVersions.OUTPUT_SCHEMA_VERSION + "\""
                + "}";
    }

    public String manifestJson() {
        return "{"
                + "\"evidenceHash\":\"" + evidenceHash + "\""
                + ",\"inputHash\":\"" + inputHash + "\""
                + ",\"evidenceSchemaVersion\":\""
                + CareerCampaignReviewVersions.EVIDENCE_SCHEMA_VERSION + "\""
                + ",\"ruleVersion\":\"" + CareerCampaignReviewVersions.RULE_VERSION + "\""
                + ",\"outputSchemaVersion\":\""
                + CareerCampaignReviewVersions.OUTPUT_SCHEMA_VERSION + "\""
                + ",\"dataCutoffAt\":\"" + dataCutoffAt + "\""
                + ",\"sourceCount\":" + sources.size()
                + ",\"factCount\":" + facts.size()
                + "}";
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public List<CareerCampaignReviewGenerateDTO.Source> getSources() {
        return sources;
    }

    public String getEvidenceHash() {
        return evidenceHash;
    }

    public String getInputHash() {
        return inputHash;
    }

    public LocalDateTime getDataCutoffAt() {
        return dataCutoffAt;
    }

    private String canonicalEvidence() {
        List<String> values = new ArrayList<>();
        values.add("evidenceSchemaVersion="
                + CareerCampaignReviewVersions.EVIDENCE_SCHEMA_VERSION);
        values.add("userId=" + Objects.toString(userId, ""));
        values.add("campaignId=" + Objects.toString(campaignId, ""));
        values.add("campaignStatus=" + Objects.toString(campaignStatus, ""));
        values.add("campaignTitle=" + Objects.toString(campaignTitle, ""));
        values.add("completed=" + Objects.toString(completed, ""));
        values.add("allOpportunitiesClosed="
                + Objects.toString(allOpportunitiesClosed, ""));
        values.add("sampleSize=" + Objects.toString(sampleSize, ""));
        values.add("dataCutoffAt=" + Objects.toString(dataCutoffAt, ""));
        facts.stream().map(CareerCampaignReviewEvidenceEnvelope::canonicalFact)
                .sorted()
                .forEach(value -> values.add("fact=" + value));
        sources.stream().map(CareerCampaignReviewEvidenceEnvelope::canonicalSource)
                .sorted()
                .forEach(value -> values.add("source=" + value));
        return String.join("\n", values);
    }

    private static List<CareerCampaignReviewGenerateDTO.Fact> facts(
            List<CareerCampaignReviewEvidenceVO.Fact> values) {
        List<CareerCampaignReviewGenerateDTO.Fact> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (CareerCampaignReviewEvidenceVO.Fact value : values) {
            if (value == null) {
                continue;
            }
            CareerCampaignReviewGenerateDTO.Fact fact =
                    new CareerCampaignReviewGenerateDTO.Fact();
            fact.setKey(value.getKey());
            fact.setLabel(value.getLabel());
            fact.setValue(value.getValue());
            fact.setSourceRef(value.getSourceRef());
            result.add(fact);
        }
        return result;
    }

    private static List<CareerCampaignReviewGenerateDTO.Source> sources(
            List<CareerCampaignReviewEvidenceVO.Source> values) {
        List<CareerCampaignReviewGenerateDTO.Source> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (CareerCampaignReviewEvidenceVO.Source value : values) {
            if (value == null) {
                continue;
            }
            CareerCampaignReviewGenerateDTO.Source source =
                    new CareerCampaignReviewGenerateDTO.Source();
            source.setSourceType(value.getSourceType());
            source.setSourceId(value.getSourceId());
            source.setSourceVersion(value.getSourceVersion());
            source.setSourceTime(value.getSourceTime());
            source.setSourceUpdatedAt(value.getSourceUpdatedAt());
            source.setSourceHash(value.getSourceHash());
            result.add(source);
        }
        return result;
    }

    private static String canonicalFact(CareerCampaignReviewGenerateDTO.Fact value) {
        return String.join("|",
                Objects.toString(value.getKey(), ""),
                Objects.toString(value.getLabel(), ""),
                Objects.toString(value.getValue(), ""),
                Objects.toString(value.getSourceRef(), ""));
    }

    private static String canonicalSource(CareerCampaignReviewGenerateDTO.Source value) {
        return String.join("|",
                Objects.toString(value.getSourceType(), ""),
                Objects.toString(value.getSourceId(), ""),
                Objects.toString(value.getSourceVersion(), ""),
                Objects.toString(value.getSourceTime(), ""),
                Objects.toString(value.getSourceUpdatedAt(), ""),
                Objects.toString(value.getSourceHash(), ""));
    }
}
