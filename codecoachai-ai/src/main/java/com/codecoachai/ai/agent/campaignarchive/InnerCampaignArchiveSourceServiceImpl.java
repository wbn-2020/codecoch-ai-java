package com.codecoachai.ai.agent.campaignarchive;

import com.codecoachai.ai.agent.mapper.InnerCampaignArchiveSourceMapper;
import com.codecoachai.ai.agent.mapper.InnerCampaignArchiveSourceMapper.PulseRow;
import com.codecoachai.ai.agent.mapper.InnerCampaignArchiveSourceMapper.ReviewRow;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InnerCampaignArchiveSourceServiceImpl implements InnerCampaignArchiveSourceService {

    private final InnerCampaignArchiveSourceMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public InnerCampaignArchiveSourceVO get(Long userId, Long campaignId, LocalDateTime dataCutoffAt) {
        if (userId == null || campaignId == null || dataCutoffAt == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId、campaignId 和 dataCutoffAt 不能为空");
        }

        InnerCampaignArchiveSourceVO result = new InnerCampaignArchiveSourceVO();
        result.setUserId(userId);
        result.setCampaignId(campaignId);
        result.setDataCutoffAt(dataCutoffAt);

        try {
            ReviewRow review = mapper.selectReview(userId, campaignId, dataCutoffAt);
            if (review == null) {
                result.getMissingSections().add("campaign-review");
            } else {
                result.setReview(toReview(review));
            }
        } catch (RuntimeException exception) {
            result.getMissingSections().add("campaign-review");
        }

        try {
            List<PulseRow> pulseRows = mapper.selectPulses(userId, campaignId, dataCutoffAt);
            if (pulseRows != null) {
                result.setPulses(pulseRows.stream().map(this::toPulse).toList());
            }
        } catch (RuntimeException exception) {
            result.getMissingSections().add("agent-pulses");
        }

        result.setSourceStatus(result.getMissingSections().isEmpty() ? "READY" : "PARTIAL");
        result.setSourceHash(hash(canonical(result)));
        return result;
    }

    private InnerCampaignArchiveSourceVO.Review toReview(ReviewRow row) {
        InnerCampaignArchiveSourceVO.Review review = new InnerCampaignArchiveSourceVO.Review();
        review.setReviewId(row.getReviewId());
        review.setSnapshotId(row.getSnapshotId());
        review.setCampaignId(row.getCampaignId());
        review.setSnapshotVersion(row.getSnapshotVersion());
        review.setReviewStatus(row.getReviewStatus());
        review.setDataCutoffAt(row.getDataCutoffAt());
        review.setSummary(row.getSummary());
        review.setConfidenceLevel(row.getConfidenceLevel());
        review.setResultSource(row.getResultSource());
        review.setFallback(row.getFallback() != null && row.getFallback() == 1);
        review.setFallbackReason(row.getFallbackReason());
        review.setFacts(read(row.getFactsJson()));
        review.setCoverage(read(row.getCoverageJson()));
        review.setLimits(read(row.getLimitsJson()));
        review.setSignals(read(row.getSignalsJson()));
        review.setMemoryCandidates(read(row.getMemoryCandidatesJson()));
        review.setExperimentCandidates(read(row.getExperimentCandidatesJson()));
        review.setNextCycleActions(read(row.getNextCycleActionsJson()));
        return review;
    }

    private InnerCampaignArchiveSourceVO.Pulse toPulse(PulseRow row) {
        InnerCampaignArchiveSourceVO.Pulse pulse = new InnerCampaignArchiveSourceVO.Pulse();
        pulse.setPulseId(row.getPulseId());
        pulse.setSnapshotId(row.getSnapshotId());
        pulse.setCampaignId(row.getCampaignId());
        pulse.setSnapshotVersion(row.getSnapshotVersion());
        pulse.setDataCutoffAt(row.getDataCutoffAt());
        pulse.setInputHash(row.getInputHash());
        pulse.setConfidenceLevel(row.getConfidenceLevel());
        pulse.setFallback(row.getFallback() != null && row.getFallback() == 1);
        pulse.setFacts(read(row.getFactsJson()));
        pulse.setMetrics(read(row.getMetricsJson()));
        pulse.setChanges(read(row.getChangesJson()));
        pulse.setDriftSignals(read(row.getDriftSignalsJson()));
        pulse.setLimits(read(row.getLimitsJson()));
        pulse.setActionSeeds(read(row.getActionSeedsJson()));
        pulse.setNarrative(read(row.getNarrativeJson()));
        return pulse;
    }

    private JsonNode read(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, Object> canonical(InnerCampaignArchiveSourceVO source) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sourceSchemaVersion", source.getSourceSchemaVersion());
        value.put("userId", source.getUserId());
        value.put("campaignId", source.getCampaignId());
        value.put("dataCutoffAt", source.getDataCutoffAt());
        value.put("sourceStatus", source.getSourceStatus());
        value.put("missingSections", source.getMissingSections());
        value.put("review", source.getReview());
        value.put("pulses", source.getPulses());
        return value;
    }

    private String hash(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("周期档案来源指纹生成失败", exception);
        }
    }
}
