package com.codecoachai.resume.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.resume.config.V12FeatureGate;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResult;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.mapper.CareerEvidenceUsageMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.SkillGapItemMapper;
import com.codecoachai.resume.service.SkillProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * V12: folds trusted evidence-usage outcomes back into skill-profile gap items.
 *
 * <p>Invoked inside the result-mutation transaction only after a real status transition
 * (idempotent replays and no-op mutations never reach it). Every entry point swallows its
 * own failures — profile feedback must never break the confirm/correct/void flow. State is
 * recomputed from persisted data on each call, so repeated invocation is idempotent. The
 * methods are deliberately not transactional themselves: they join the caller's transaction,
 * and a caught mapper failure does not mark it rollback-only.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceProfileFeedbackService {

    static final String CATEGORY = "EVIDENCE_USAGE_FEEDBACK";
    static final String SOURCE_TYPE_RESULT = "EVIDENCE_USAGE_RESULT";
    // Pattern gaps are keyed per asset; the asset type lives in the source type because
    // source_biz_id alone cannot disambiguate ids from different asset tables.
    static final String SOURCE_TYPE_PATTERN_PREFIX = "EVIDENCE_USAGE_PATTERN_";
    static final String OUTCOME_INTERVIEW_NOT_ADVANCED = "INTERVIEW_NOT_ADVANCED";
    static final String OUTCOME_NO_RESPONSE = "NO_RESPONSE";
    static final int NO_RESPONSE_THRESHOLD = 3;

    private static final int SKILL_NAME_MAX_LENGTH = 64;
    private static final int INTERPRETATION_MAX_LENGTH = 200;
    private static final Map<String, String> SCENE_LABELS = Map.of(
            "APPLICATION_PACKAGE", "投递材料包",
            "APPLICATION_SUBMISSION", "投递",
            "INTERVIEW_PREPARATION", "面试准备",
            "INTERVIEW", "面试",
            "FOLLOW_UP", "跟进沟通",
            "OTHER", "其他场景");
    private static final Map<String, String> ASSET_TYPE_LABELS = Map.of(
            "PROJECT_EVIDENCE", "项目证据",
            "PROJECT_SKILL_EVIDENCE", "技能证据",
            "PROJECT_STORY_GENERATION", "项目故事",
            "APPLICATION_PACKAGE_SNAPSHOT", "投递包快照",
            "RESUME_VERSION", "简历版本",
            "MATCH_REPORT", "匹配报告");

    private final V12FeatureGate featureGate;
    private final CareerEvidenceUsageMapper usageMapper;
    private final CareerEvidenceUsageResultMapper resultMapper;
    private final SkillGapItemMapper gapItemMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final ProjectSkillEvidenceMapper projectSkillEvidenceMapper;
    private final SkillProfileService skillProfileService;
    private final ObjectMapper objectMapper;

    public void afterResultTransition(CareerEvidenceUsageResult root,
                                      String outcomeCode,
                                      String userInterpretationText) {
        if (root == null || !featureGate.isEvidenceProfileFeedback()) {
            return;
        }
        try {
            CareerEvidenceUsage usage =
                    usageMapper.selectOwned(root.getUsageId(), root.getUserId());
            if (usage == null) {
                return;
            }
            recomputeResultGap(root, usage, outcomeCode, userInterpretationText);
            recomputeNoResponsePattern(root.getUserId(), usage);
        } catch (Exception ex) {
            log.error("evidence profile feedback failed for result {}", root.getId(), ex);
        }
    }

    private void recomputeResultGap(CareerEvidenceUsageResult root,
                                    CareerEvidenceUsage usage,
                                    String outcomeCode,
                                    String userInterpretationText) {
        boolean trusted = "CONFIRMED".equals(root.getStatus())
                || "CORRECTED".equals(root.getStatus());
        boolean shouldExist = trusted
                && OUTCOME_INTERVIEW_NOT_ADVANCED.equals(outcomeCode)
                && usage.getTargetJobId() != null;
        SkillGapItem existing = findGap(root.getUserId(), SOURCE_TYPE_RESULT, root.getId());
        if (!shouldExist) {
            if (existing != null) {
                gapItemMapper.deleteById(existing.getId());
            }
            return;
        }
        String assetLabel = assetLabel(usage);
        String description = resultGapDescription(usage, assetLabel, userInterpretationText);
        List<String> sources = List.of(
                SOURCE_TYPE_RESULT + ":" + root.getId(),
                "EVIDENCE_USAGE:" + usage.getId());
        if (existing != null) {
            existing.setSkillName(summarize(assetLabel, SKILL_NAME_MAX_LENGTH));
            existing.setGapDescription(description);
            existing.setSeverity("MEDIUM");
            existing.setConfidence(new BigDecimal("0.60"));
            existing.setEvidenceSourcesJson(toJson(sources));
            gapItemMapper.updateById(existing);
            return;
        }
        SkillProfile profile = skillProfileService
                .resolveEvidenceFeedbackProfile(root.getUserId(), usage.getTargetJobId());
        insertGap(profile, usage, summarize(assetLabel, SKILL_NAME_MAX_LENGTH), description,
                "MEDIUM", new BigDecimal("0.60"), SOURCE_TYPE_RESULT, root.getId(),
                sources, List.of(
                        "复盘该证据在面试中的讲述结构与重点",
                        "针对该证据进行表达与复述训练",
                        "结合面试反馈补强证据细节或替换更强证据"));
    }

    private void recomputeNoResponsePattern(Long userId, CareerEvidenceUsage usage) {
        if (usage.getAssetType() == null || usage.getAssetId() == null) {
            return;
        }
        String sourceType = SOURCE_TYPE_PATTERN_PREFIX + usage.getAssetType();
        long count = resultMapper.countTrustedOutcomeByAsset(
                userId, usage.getAssetType(), usage.getAssetId(), OUTCOME_NO_RESPONSE);
        SkillGapItem existing = findGap(userId, sourceType, usage.getAssetId());
        if (count < NO_RESPONSE_THRESHOLD) {
            if (existing != null) {
                gapItemMapper.deleteById(existing.getId());
            }
            return;
        }
        String assetLabel = assetLabel(usage);
        String description = "证据《" + assetLabel + "》累计 " + count
                + " 次使用后未获回应，建议复盘其内容与呈现方式是否匹配目标岗位。";
        List<String> sources = resultMapper.selectTrustedOutcomeUsageIds(
                        userId, usage.getAssetType(), usage.getAssetId(), OUTCOME_NO_RESPONSE)
                .stream()
                .map(usageId -> "EVIDENCE_USAGE:" + usageId)
                .toList();
        if (existing != null) {
            existing.setGapDescription(description);
            existing.setEvidenceSourcesJson(toJson(sources));
            gapItemMapper.updateById(existing);
            return;
        }
        if (usage.getTargetJobId() == null) {
            return;
        }
        SkillProfile profile = skillProfileService
                .resolveEvidenceFeedbackProfile(userId, usage.getTargetJobId());
        insertGap(profile, usage, summarize(assetLabel, SKILL_NAME_MAX_LENGTH), description,
                "LOW", new BigDecimal("0.50"), sourceType, usage.getAssetId(),
                sources, List.of(
                        "检查该证据与目标岗位要求的匹配度",
                        "调整投递材料中该证据的呈现方式",
                        "考虑替换或补强该证据"));
    }

    private void insertGap(SkillProfile profile, CareerEvidenceUsage usage, String skillName,
                           String description, String severity, BigDecimal confidence,
                           String sourceType, Long sourceBizId, List<String> sources,
                           List<String> recommendedActions) {
        SkillGapItem item = new SkillGapItem();
        item.setProfileId(profile.getId());
        item.setUserId(profile.getUserId());
        item.setTargetJobId(usage.getTargetJobId());
        item.setSkillName(skillName);
        item.setCategory(CATEGORY);
        item.setTargetLevel(4);
        item.setCurrentLevel(2);
        item.setGapLevel(2);
        item.setConfidence(confidence);
        item.setSeverity(severity);
        item.setEvidenceSourcesJson(toJson(sources));
        item.setGapDescription(description);
        item.setRecommendedActionsJson(toJson(recommendedActions));
        item.setPriority(nextGapPriority(profile.getId(), profile.getUserId()));
        item.setSourceType(sourceType);
        item.setSourceBizId(sourceBizId);
        gapItemMapper.insert(item);
    }

    private SkillGapItem findGap(Long userId, String sourceType, Long sourceBizId) {
        return gapItemMapper.selectOne(new LambdaQueryWrapper<SkillGapItem>()
                .eq(SkillGapItem::getUserId, userId)
                .eq(SkillGapItem::getSourceType, sourceType)
                .eq(SkillGapItem::getSourceBizId, sourceBizId)
                .eq(SkillGapItem::getDeleted, CommonConstants.NO)
                .orderByDesc(SkillGapItem::getId)
                .last("limit 1"));
    }

    private int nextGapPriority(Long profileId, Long userId) {
        Long count = gapItemMapper.selectCount(new LambdaQueryWrapper<SkillGapItem>()
                .eq(SkillGapItem::getProfileId, profileId)
                .eq(SkillGapItem::getUserId, userId)
                .eq(SkillGapItem::getDeleted, CommonConstants.NO));
        return count == null ? 1 : count.intValue() + 1;
    }

    private String assetLabel(CareerEvidenceUsage usage) {
        try {
            if ("PROJECT_EVIDENCE".equals(usage.getAssetType())) {
                ProjectEvidence evidence = projectEvidenceMapper.selectById(usage.getAssetId());
                if (evidence != null && StringUtils.hasText(evidence.getTitle())) {
                    return evidence.getTitle().trim();
                }
            } else if ("PROJECT_SKILL_EVIDENCE".equals(usage.getAssetType())) {
                ProjectSkillEvidence evidence =
                        projectSkillEvidenceMapper.selectById(usage.getAssetId());
                if (evidence != null && StringUtils.hasText(evidence.getSkillName())) {
                    return evidence.getSkillName().trim();
                }
            }
        } catch (Exception ex) {
            log.warn("failed to resolve evidence asset label for {}:{}",
                    usage.getAssetType(), usage.getAssetId(), ex);
        }
        return ASSET_TYPE_LABELS.getOrDefault(usage.getAssetType(), "求职证据")
                + (usage.getAssetId() == null ? "" : " #" + usage.getAssetId());
    }

    private String resultGapDescription(CareerEvidenceUsage usage, String assetLabel,
                                        String interpretation) {
        StringBuilder text = new StringBuilder()
                .append("证据《").append(assetLabel).append("》在")
                .append(SCENE_LABELS.getOrDefault(usage.getUsageScene(), "求职过程"))
                .append("中使用后，确认结果为「面试未晋级」。");
        if (StringUtils.hasText(interpretation)) {
            text.append("本人复盘：").append(summarize(interpretation, INTERPRETATION_MAX_LENGTH));
        }
        return text.toString();
    }

    private String summarize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "求职证据";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
