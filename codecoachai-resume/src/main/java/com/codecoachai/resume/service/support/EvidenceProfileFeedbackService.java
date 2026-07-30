package com.codecoachai.resume.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.resume.config.V12FeatureGate;
import com.codecoachai.resume.config.V13FeatureGate;
import com.codecoachai.resume.domain.entity.AbilitySkillNode;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResult;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResultSnapshot;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.mapper.AbilitySkillNodeMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultSnapshotMapper;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.SkillGapItemMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.SkillProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Recomputes the projections affected by one evidence-usage result.
 *
 * <p>Production calls arrive through the durable feedback outbox. The outbox serializes
 * projection work per user and invokes this service in an independent transaction, so every
 * gap/ability mutation for the result either commits together or rolls back together.</p>
 *
 * <p>V12 folds trusted evidence-usage outcomes back into skill-profile gap items.
 * V13: additionally reinforces {@code user_ability_profile} rows from trusted
 * positive outcomes (interview advanced / offer received / offer accepted).</p>
 */
@Service
@RequiredArgsConstructor
public class EvidenceProfileFeedbackService {

    public enum ProjectionDisposition {
        COMPLETED(true, true),
        DEFERRED_EVIDENCE(false, true),
        DEFERRED_ABILITY(true, false),
        DEFERRED_BOTH(false, false);

        private final boolean evidenceCompleted;
        private final boolean abilityCompleted;

        ProjectionDisposition(boolean evidenceCompleted, boolean abilityCompleted) {
            this.evidenceCompleted = evidenceCompleted;
            this.abilityCompleted = abilityCompleted;
        }

        public boolean evidenceCompleted() {
            return evidenceCompleted;
        }

        public boolean abilityCompleted() {
            return abilityCompleted;
        }

        private static ProjectionDisposition from(
                boolean evidenceCompleted, boolean abilityCompleted) {
            if (evidenceCompleted && abilityCompleted) {
                return COMPLETED;
            }
            if (evidenceCompleted) {
                return DEFERRED_ABILITY;
            }
            if (abilityCompleted) {
                return DEFERRED_EVIDENCE;
            }
            return DEFERRED_BOTH;
        }
    }

    static final String CATEGORY = "EVIDENCE_USAGE_FEEDBACK";
    static final String SOURCE_TYPE_RESULT = "EVIDENCE_USAGE_RESULT";
    // Pattern gaps are keyed per asset; the asset type lives in the source type because
    // source_biz_id alone cannot disambiguate ids from different asset tables.
    static final String SOURCE_TYPE_PATTERN_PREFIX = "EVIDENCE_USAGE_PATTERN_";
    static final String OUTCOME_INTERVIEW_NOT_ADVANCED = "INTERVIEW_NOT_ADVANCED";
    static final String OUTCOME_NO_RESPONSE = "NO_RESPONSE";
    static final int NO_RESPONSE_THRESHOLD = 3;
    static final List<String> POSITIVE_OUTCOME_CODES =
            List.of("INTERVIEW_ADVANCED", "OFFER_RECEIVED", "OFFER_ACCEPTED");
    static final String ABILITY_SOURCE_EVIDENCE_USAGE = "EVIDENCE_USAGE";

    private static final int SKILL_NAME_MAX_LENGTH = 64;
    private static final int MAX_REINFORCED_SKILLS = 8;
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
    private final V13FeatureGate v13FeatureGate;
    private final CareerEvidenceUsageMapper usageMapper;
    private final CareerEvidenceUsageResultMapper resultMapper;
    private final CareerEvidenceUsageResultSnapshotMapper resultSnapshotMapper;
    private final AbilitySkillNodeMapper abilitySkillNodeMapper;
    private final EvidenceUsageAbilityProjectionMapper abilityProjectionMapper;
    private final SkillGapItemMapper gapItemMapper;
    private final UserAbilityProfileMapper abilityProfileMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final ProjectSkillEvidenceMapper projectSkillEvidenceMapper;
    private final SkillProfileService skillProfileService;
    private final ObjectMapper objectMapper;

    public ProjectionDisposition recomputeResult(Long resultId, Long userId) {
        return recomputeResult(resultId, userId, false, false);
    }

    public ProjectionDisposition recomputeResult(
            Long resultId,
            Long userId,
            boolean evidenceProjectionDone,
            boolean abilityProjectionDone) {
        if (resultId == null || userId == null) {
            return ProjectionDisposition.COMPLETED;
        }
        boolean evidenceFeedbackEnabled = featureGate.isEvidenceProfileFeedback();
        boolean abilityReinforcementEnabled =
                v13FeatureGate.isPositiveAbilityReinforcement();
        if ((evidenceProjectionDone || !evidenceFeedbackEnabled)
                && (abilityProjectionDone || !abilityReinforcementEnabled)) {
            return ProjectionDisposition.from(
                    evidenceProjectionDone, abilityProjectionDone);
        }
        CareerEvidenceUsageResult root = resultMapper.selectOwned(resultId, userId);
        if (root == null || root.getCurrentSnapshotId() == null) {
            return ProjectionDisposition.COMPLETED;
        }
        CareerEvidenceUsageResultSnapshot snapshot = resultSnapshotMapper.selectOwned(
                root.getCurrentSnapshotId(), root.getId(), root.getUserId());
        if (snapshot == null) {
            throw new IllegalStateException(
                    "Current evidence result snapshot is missing for result " + root.getId());
        }
        return recomputeProjection(
                root,
                snapshot.getOutcomeCode(),
                snapshot.getUserInterpretationText(),
                evidenceProjectionDone,
                abilityProjectionDone,
                evidenceFeedbackEnabled,
                abilityReinforcementEnabled);
    }

    /**
     * Kept for focused unit tests and compatibility with old in-process callers.
     * Production result mutations enqueue the durable outbox instead.
     */
    @Deprecated
    public void afterResultTransition(CareerEvidenceUsageResult root,
                                      String outcomeCode,
                                      String userInterpretationText) {
        if (root == null) {
            return;
        }
        boolean evidenceFeedbackEnabled = featureGate.isEvidenceProfileFeedback();
        boolean abilityReinforcementEnabled =
                v13FeatureGate.isPositiveAbilityReinforcement();
        if (!evidenceFeedbackEnabled && !abilityReinforcementEnabled) {
            return;
        }
        recomputeProjection(
                root,
                outcomeCode,
                userInterpretationText,
                !evidenceFeedbackEnabled,
                !abilityReinforcementEnabled,
                evidenceFeedbackEnabled,
                abilityReinforcementEnabled);
    }

    private ProjectionDisposition recomputeProjection(
            CareerEvidenceUsageResult root,
            String outcomeCode,
            String userInterpretationText,
            boolean evidenceProjectionDone,
            boolean abilityProjectionDone,
            boolean evidenceFeedbackEnabled,
            boolean abilityReinforcementEnabled) {
        CareerEvidenceUsage usage = usageMapper.selectOwned(root.getUsageId(), root.getUserId());
        if (usage == null) {
            return ProjectionDisposition.COMPLETED;
        }
        boolean evidenceCompleted = evidenceProjectionDone;
        boolean abilityCompleted = abilityProjectionDone;
        if (!evidenceCompleted && evidenceFeedbackEnabled) {
            recomputeResultGap(root, usage, outcomeCode, userInterpretationText);
            recomputeNoResponsePattern(root.getUserId(), usage);
            evidenceCompleted = true;
        }
        if (!abilityCompleted && abilityReinforcementEnabled) {
            recomputePositiveReinforcement(root, usage, outcomeCode);
            abilityCompleted = true;
        }
        return ProjectionDisposition.from(evidenceCompleted, abilityCompleted);
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
        SkillGapItem existing = usage.getTargetJobId() == null
                ? null
                : findGap(root.getUserId(), usage.getTargetJobId(),
                        SOURCE_TYPE_RESULT, root.getId());
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
        SkillProfile profile = skillProfileService
                .resolveEvidenceFeedbackProfile(root.getUserId(), usage.getTargetJobId());
        if (existing != null) {
            existing.setProfileId(profile.getId());
            existing.setTargetJobId(usage.getTargetJobId());
            existing.setSkillName(summarize(assetLabel, SKILL_NAME_MAX_LENGTH));
            existing.setGapDescription(description);
            existing.setSeverity("MEDIUM");
            existing.setConfidence(new BigDecimal("0.60"));
            existing.setEvidenceSourcesJson(toJson(sources));
            gapItemMapper.updateById(existing);
            return;
        }
        insertGap(profile, usage, summarize(assetLabel, SKILL_NAME_MAX_LENGTH), description,
                "MEDIUM", new BigDecimal("0.60"), SOURCE_TYPE_RESULT, root.getId(),
                sources, List.of(
                        "复盘该证据在面试中的讲述结构与重点",
                        "针对该证据进行表达与复述训练",
                        "结合面试反馈补强证据细节或替换更强证据"));
    }

    private void recomputeNoResponsePattern(Long userId, CareerEvidenceUsage usage) {
        if (usage.getTargetJobId() == null
                || usage.getAssetType() == null
                || usage.getAssetId() == null) {
            return;
        }
        String sourceType = SOURCE_TYPE_PATTERN_PREFIX + usage.getAssetType();
        long count = resultMapper.countTrustedOutcomeByAsset(
                userId, usage.getTargetJobId(), usage.getAssetType(),
                usage.getAssetId(), OUTCOME_NO_RESPONSE);
        SkillGapItem existing = findGap(
                userId, usage.getTargetJobId(), sourceType, usage.getAssetId());
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
                        userId, usage.getTargetJobId(), usage.getAssetType(),
                        usage.getAssetId(), OUTCOME_NO_RESPONSE)
                .stream()
                .map(usageId -> "EVIDENCE_USAGE:" + usageId)
                .toList();
        SkillProfile profile = skillProfileService
                .resolveEvidenceFeedbackProfile(userId, usage.getTargetJobId());
        if (existing != null) {
            existing.setProfileId(profile.getId());
            existing.setTargetJobId(usage.getTargetJobId());
            existing.setSkillName(summarize(assetLabel, SKILL_NAME_MAX_LENGTH));
            existing.setGapDescription(description);
            existing.setEvidenceSourcesJson(toJson(sources));
            gapItemMapper.updateById(existing);
            return;
        }
        insertGap(profile, usage, summarize(assetLabel, SKILL_NAME_MAX_LENGTH), description,
                "LOW", new BigDecimal("0.50"), sourceType, usage.getAssetId(),
                sources, List.of(
                        "检查该证据与目标岗位要求的匹配度",
                        "调整投递材料中该证据的呈现方式",
                        "考虑替换或补强该证据"));
    }

    /**
     * V13: reconciles the result's persisted skill contributions and affected ability rows.
     *
     * <p>Runs on every real transition regardless of the transitioned outcome — corrections
     * away from a positive outcome and VOID retractions must remove prior contributions.
     * Persisting the previous contribution set also lets evidence renames, unconfirmation,
     * and deletion converge the old skill row instead of only touching the current name.</p>
     */
    private void recomputePositiveReinforcement(
            CareerEvidenceUsageResult root,
            CareerEvidenceUsage usage,
            String outcomeCode) {
        Long userId = root.getUserId();
        List<String> persisted = abilityProjectionMapper.selectSkillCodes(root.getId(), userId);
        Set<String> previousSkillCodes = new LinkedHashSet<>(
                persisted == null ? List.of() : persisted);
        Set<String> desiredSkillCodes = new LinkedHashSet<>();
        if (isTrustedPositive(root, outcomeCode)) {
            List<String> skillNames = reinforcableSkillNames(usage);
            if (!skillNames.isEmpty()) {
                List<AbilitySkillNode> nodes =
                        abilitySkillNodeMapper.selectEnabledForEvidenceMapping();
                for (String skillName : skillNames) {
                    String skillCode = resolveCanonicalSkillCode(
                            skillName, nodes == null ? List.of() : nodes);
                    if (StringUtils.hasText(skillCode)) {
                        desiredSkillCodes.add(skillCode);
                    }
                }
            }
        }

        List<String> removed = previousSkillCodes.stream()
                .filter(skillCode -> !desiredSkillCodes.contains(skillCode))
                .toList();
        if (!removed.isEmpty()) {
            abilityProjectionMapper.deleteSkillCodes(root.getId(), userId, removed);
        }
        List<String> added = desiredSkillCodes.stream()
                .filter(skillCode -> !previousSkillCodes.contains(skillCode))
                .toList();
        if (!added.isEmpty()) {
            abilityProjectionMapper.insertSkillCodes(
                    root.getId(), root.getUsageId(), userId, added);
        }

        Set<String> affectedSkillCodes = new LinkedHashSet<>(previousSkillCodes);
        affectedSkillCodes.addAll(desiredSkillCodes);
        for (String skillCode : affectedSkillCodes) {
            long positiveCount = abilityProjectionMapper.countDistinctUsageBySkillCode(
                    userId, skillCode);
            applyReinforcement(userId, skillCode, positiveCount);
        }
    }

    private List<String> reinforcableSkillNames(CareerEvidenceUsage usage) {
        if (usage.getAssetId() == null) {
            return List.of();
        }
        if ("PROJECT_SKILL_EVIDENCE".equals(usage.getAssetType())) {
            ProjectSkillEvidence evidence = projectSkillEvidenceMapper.selectById(usage.getAssetId());
            if (evidence == null
                    || !Objects.equals(usage.getUserId(), evidence.getUserId())
                    || !isActiveProject(evidence.getProjectEvidenceId(), usage.getUserId())
                    || !CommonConstants.YES.equals(evidence.getConfirmed())
                    || !StringUtils.hasText(evidence.getSkillName())) {
                return List.of();
            }
            return List.of(evidence.getSkillName().trim());
        }
        if ("PROJECT_EVIDENCE".equals(usage.getAssetType())) {
            if (!isActiveProject(usage.getAssetId(), usage.getUserId())) {
                return List.of();
            }
            return projectSkillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                            .eq(ProjectSkillEvidence::getProjectEvidenceId, usage.getAssetId())
                            .eq(ProjectSkillEvidence::getUserId, usage.getUserId())
                            .eq(ProjectSkillEvidence::getConfirmed, CommonConstants.YES)
                            .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                            .orderByAsc(ProjectSkillEvidence::getId)
                            .last("limit 64"))
                    .stream()
                    .map(ProjectSkillEvidence::getSkillName)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .limit(MAX_REINFORCED_SKILLS)
                    .toList();
        }
        return List.of();
    }

    private void applyReinforcement(Long userId, String skillCode, long positiveCount) {
        if (!StringUtils.hasText(skillCode)) {
            return;
        }
        UserAbilityProfile profile = findAbilityProfile(userId, skillCode);
        if (positiveCount <= 0) {
            if (profile != null && ABILITY_SOURCE_EVIDENCE_USAGE.equals(profile.getSourceType())) {
                // Reset instead of logical delete: a deleted row would keep occupying
                // uk(user_id, skill_code) while staying invisible to @TableLogic queries,
                // and evidence_count = 0 already means "no evidence" to the ability map.
                profile.setEvidenceCount(0);
                profile.setConfidence("UNKNOWN");
                profile.setSummary("该技能的实战正向验证已撤回。");
                profile.setLastEvaluatedAt(LocalDateTime.now());
                abilityProfileMapper.updateById(profile);
            }
            return;
        }
        if (profile == null) {
            UserAbilityProfile desired =
                    newEvidenceUsageProfile(userId, skillCode, positiveCount);
            if (restoreDeletedAbilityProfile(desired)) {
                return;
            }
            try {
                abilityProfileMapper.insert(desired);
                return;
            } catch (DuplicateKeyException ex) {
                profile = findAbilityProfile(userId, skillCode);
                if (profile == null) {
                    if (restoreDeletedAbilityProfile(desired)) {
                        return;
                    }
                    profile = findAbilityProfile(userId, skillCode);
                    if (profile == null) {
                        throw ex;
                    }
                }
            }
        }
        if (ABILITY_SOURCE_EVIDENCE_USAGE.equals(profile.getSourceType())) {
            profile.setEvidenceCount(toEvidenceCount(positiveCount));
            profile.setConfidence(raisedConfidence(profile.getConfidence()));
            profile.setSummary(positiveSummary(positiveCount));
            profile.setLastEvaluatedAt(LocalDateTime.now());
            abilityProfileMapper.updateById(profile);
        }
    }

    private UserAbilityProfile newEvidenceUsageProfile(
            Long userId, String skillCode, long positiveCount) {
        UserAbilityProfile profile = new UserAbilityProfile();
        profile.setUserId(userId);
        profile.setSkillCode(skillCode);
        // UNASSESSED: the skill was battle-validated, not ability-assessed.
        profile.setStatus("UNASSESSED");
        profile.setEvidenceCount(toEvidenceCount(positiveCount));
        profile.setConfidence("MEDIUM");
        profile.setSummary(positiveSummary(positiveCount));
        profile.setLastEvaluatedAt(LocalDateTime.now());
        profile.setSourceType(ABILITY_SOURCE_EVIDENCE_USAGE);
        return profile;
    }

    private boolean restoreDeletedAbilityProfile(UserAbilityProfile desired) {
        return abilityProfileMapper.restoreDeletedEvidenceUsageProfile(desired) == 1;
    }

    private boolean isTrustedPositive(CareerEvidenceUsageResult root, String outcomeCode) {
        boolean trusted = "CONFIRMED".equals(root.getStatus())
                || "CORRECTED".equals(root.getStatus());
        if (!trusted || !StringUtils.hasText(outcomeCode)) {
            return false;
        }
        return POSITIVE_OUTCOME_CODES.contains(
                outcomeCode.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isActiveProject(Long projectEvidenceId, Long userId) {
        if (projectEvidenceId == null || userId == null) {
            return false;
        }
        ProjectEvidence project = projectEvidenceMapper.selectById(projectEvidenceId);
        return project != null
                && Objects.equals(userId, project.getUserId())
                && !CommonConstants.YES.equals(project.getDeleted());
    }

    private String resolveCanonicalSkillCode(
            String skillName, List<AbilitySkillNode> nodes) {
        String normalizedSkillName = normalizeSkillIdentity(skillName);
        if (!StringUtils.hasText(normalizedSkillName) || nodes == null || nodes.isEmpty()) {
            return null;
        }
        Set<String> exactCodes = new LinkedHashSet<>();
        for (AbilitySkillNode node : nodes) {
            if (!validSkillNode(node)) {
                continue;
            }
            if (normalizedSkillName.equals(normalizeSkillIdentity(node.getCode()))
                    || canonicalAliases(node).contains(normalizedSkillName)) {
                exactCodes.add(node.getCode());
            }
        }
        if (exactCodes.size() == 1) {
            return exactCodes.iterator().next();
        }
        if (!exactCodes.isEmpty()) {
            return null;
        }
        String bestCode = null;
        SkillMatchScore bestScore = null;
        boolean ambiguousBest = false;
        for (AbilitySkillNode node : nodes) {
            if (!validSkillNode(node)) {
                continue;
            }
            SkillMatchScore nodeScore = bestContainmentScore(
                    normalizedSkillName, canonicalAliases(node));
            if (nodeScore == null) {
                continue;
            }
            int comparison = compareMatchScore(nodeScore, bestScore);
            if (comparison > 0) {
                bestCode = node.getCode();
                bestScore = nodeScore;
                ambiguousBest = false;
            } else if (comparison == 0 && !Objects.equals(bestCode, node.getCode())) {
                ambiguousBest = true;
            }
        }
        return ambiguousBest ? null : bestCode;
    }

    private SkillMatchScore bestContainmentScore(
            String normalizedSkillName, Set<String> aliases) {
        SkillMatchScore best = null;
        int skillNameLength = normalizedSkillName.codePointCount(
                0, normalizedSkillName.length());
        for (String alias : aliases) {
            int aliasLength = alias.codePointCount(0, alias.length());
            if (aliasLength < 2) {
                continue;
            }
            SkillMatchScore candidate = null;
            if (normalizedSkillName.contains(alias)) {
                candidate = new SkillMatchScore(2, aliasLength);
            } else if (skillNameLength >= 2 && alias.contains(normalizedSkillName)) {
                candidate = new SkillMatchScore(1, skillNameLength);
            }
            if (compareMatchScore(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private int compareMatchScore(SkillMatchScore left, SkillMatchScore right) {
        if (left == null) {
            return right == null ? 0 : -1;
        }
        if (right == null) {
            return 1;
        }
        int relationshipComparison =
                Integer.compare(left.relationshipRank(), right.relationshipRank());
        if (relationshipComparison != 0) {
            return relationshipComparison;
        }
        return Integer.compare(left.matchedLength(), right.matchedLength());
    }

    private record SkillMatchScore(int relationshipRank, int matchedLength) {
    }

    private boolean validSkillNode(AbilitySkillNode node) {
        return node != null && StringUtils.hasText(node.getCode());
    }

    private Set<String> canonicalAliases(AbilitySkillNode node) {
        Set<String> aliases = new LinkedHashSet<>();
        addSkillAlias(aliases, node.getName());
        addSkillAlias(aliases, node.getDomainCode());
        addSkillAlias(aliases, node.getDomainName());
        if (StringUtils.hasText(node.getName())) {
            for (String part : node.getName().split("[/、|,，]+")) {
                addSkillAlias(aliases, part);
            }
            for (String token : node.getName().split("\\s+")) {
                if (token.codePoints().anyMatch(codePoint ->
                        codePoint < 128 && Character.isLetterOrDigit(codePoint))) {
                    addSkillAlias(aliases, token);
                }
            }
        }
        return aliases;
    }

    private void addSkillAlias(Set<String> aliases, String value) {
        String normalized = normalizeSkillIdentity(value);
        if (StringUtils.hasText(normalized)) {
            aliases.add(normalized);
        }
    }

    private String normalizeSkillIdentity(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private UserAbilityProfile findAbilityProfile(Long userId, String skillCode) {
        return abilityProfileMapper.selectOne(new LambdaQueryWrapper<UserAbilityProfile>()
                .eq(UserAbilityProfile::getUserId, userId)
                .eq(UserAbilityProfile::getSkillCode, skillCode)
                .last("limit 1"));
    }

    private String raisedConfidence(String current) {
        return "HIGH".equals(current) || "MEDIUM".equals(current) ? current : "MEDIUM";
    }

    private int toEvidenceCount(long value) {
        return (int) Math.min(Math.max(0L, value), Integer.MAX_VALUE);
    }

    private String positiveSummary(long positiveCount) {
        return "该技能在真实求职中获得 " + positiveCount + " 次正向结果（面试晋级/获得 offer）验证。";
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

    private SkillGapItem findGap(Long userId, Long targetJobId,
                                 String sourceType, Long sourceBizId) {
        return gapItemMapper.selectOne(new LambdaQueryWrapper<SkillGapItem>()
                .eq(SkillGapItem::getUserId, userId)
                .eq(SkillGapItem::getTargetJobId, targetJobId)
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
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize evidence feedback projection", ex);
        }
    }
}
