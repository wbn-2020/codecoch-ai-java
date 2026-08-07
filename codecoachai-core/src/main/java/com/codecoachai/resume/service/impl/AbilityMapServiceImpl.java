package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.entity.AbilitySkillNode;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.vo.AbilityDomainVO;
import com.codecoachai.resume.domain.vo.AbilityMapVO;
import com.codecoachai.resume.domain.vo.AbilitySkillNodeVO;
import com.codecoachai.resume.domain.vo.InnerAbilityProfileSummaryVO;
import com.codecoachai.resume.mapper.AbilitySkillNodeMapper;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper.SkillUsageAggregate;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.AbilityMapService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AbilityMapServiceImpl implements AbilityMapService {

    private static final String STATUS_UNASSESSED = "UNASSESSED";
    private static final String STATUS_WEAK = "WEAK";
    private static final String STATUS_STRONG = "STRONG";
    private static final String CONFIDENCE_UNKNOWN = "UNKNOWN";
    private static final String CONFIDENCE_MEDIUM = "MEDIUM";
    private static final String SOURCE_EVIDENCE_USAGE = "EVIDENCE_USAGE";

    private final AbilitySkillNodeMapper skillNodeMapper;
    private final UserAbilityProfileMapper profileMapper;
    private final EvidenceUsageAbilityProjectionMapper evidenceProjectionMapper;

    @Override
    public AbilityMapVO getCurrentUserAbilityMap() {
        Long userId = SecurityAssert.requireLoginUserId();
        List<AbilitySkillNode> nodes = listEnabledNodes();
        List<String> skillCodes = nodes.stream()
                .map(AbilitySkillNode::getCode)
                .toList();
        Map<String, UserAbilityProfile> profiles = profileMap(userId, skillCodes);
        Map<String, SkillUsageAggregate> contributions =
                evidenceContributionMap(userId, skillCodes);

        List<AbilitySkillNodeVO> skills = nodes.stream()
                .map(node -> toSkillVO(
                        node,
                        profiles.get(node.getCode()),
                        contributions.get(node.getCode())))
                .toList();

        AbilityMapVO vo = new AbilityMapVO();
        vo.setUserId(userId);
        vo.setTotalSkillCount(skills.size());
        vo.setAssessedSkillCount((int) skills.stream().filter(this::isAssessed).count());
        vo.setWeakSkillCount((int) skills.stream().filter(skill -> STATUS_WEAK.equals(skill.getStatus())).count());
        vo.setStrongSkillCount((int) skills.stream().filter(skill -> STATUS_STRONG.equals(skill.getStatus())).count());
        vo.setHasTrainingData(vo.getAssessedSkillCount() > 0);
        vo.setDomains(toDomains(skills));
        return vo;
    }

    @Override
    public List<InnerAbilityProfileSummaryVO> listProfileSummary(Long userId, List<String> skillCodes) {
        if (userId == null) {
            return List.of();
        }
        List<AbilitySkillNode> nodes = listEnabledNodes();
        List<String> requestedCodes = sanitizeCodes(skillCodes);
        if (!requestedCodes.isEmpty()) {
            nodes = nodes.stream()
                    .filter(node -> requestedCodes.contains(node.getCode()))
                    .toList();
        }
        List<String> effectiveCodes = nodes.stream()
                .map(AbilitySkillNode::getCode)
                .toList();
        Map<String, UserAbilityProfile> profiles = profileMap(userId, effectiveCodes);
        Map<String, SkillUsageAggregate> contributions =
                evidenceContributionMap(userId, effectiveCodes);
        return nodes.stream()
                .map(node -> toInnerSummary(
                        node,
                        profiles.get(node.getCode()),
                        contributions.get(node.getCode())))
                .toList();
    }

    private List<AbilitySkillNode> listEnabledNodes() {
        return skillNodeMapper.selectList(new LambdaQueryWrapper<AbilitySkillNode>()
                .eq(AbilitySkillNode::getEnabled, CommonConstants.YES)
                .orderByAsc(AbilitySkillNode::getSortOrder)
                .orderByAsc(AbilitySkillNode::getId));
    }

    private Map<String, UserAbilityProfile> profileMap(Long userId, List<String> skillCodes) {
        List<String> codes = sanitizeCodes(skillCodes);
        if (userId == null || codes.isEmpty()) {
            return Map.of();
        }
        return profileMapper.selectList(new LambdaQueryWrapper<UserAbilityProfile>()
                        .eq(UserAbilityProfile::getUserId, userId)
                        .in(UserAbilityProfile::getSkillCode, codes))
                .stream()
                .collect(Collectors.toMap(UserAbilityProfile::getSkillCode, Function.identity(), (left, right) -> left));
    }

    private Map<String, SkillUsageAggregate> evidenceContributionMap(
            Long userId, List<String> skillCodes) {
        List<String> codes = sanitizeCodes(skillCodes);
        if (userId == null || codes.isEmpty()) {
            return Map.of();
        }
        List<SkillUsageAggregate> aggregates =
                evidenceProjectionMapper.selectUsageAggregates(userId, codes);
        if (aggregates == null || aggregates.isEmpty()) {
            return Map.of();
        }
        return aggregates.stream()
                .filter(Objects::nonNull)
                .filter(aggregate -> StringUtils.hasText(aggregate.getSkillCode()))
                .collect(Collectors.toMap(
                        SkillUsageAggregate::getSkillCode,
                        Function.identity(),
                        this::newerAggregate));
    }

    private SkillUsageAggregate newerAggregate(
            SkillUsageAggregate left, SkillUsageAggregate right) {
        LocalDateTime leftTime = left.getLastProjectedAt();
        LocalDateTime rightTime = right.getLastProjectedAt();
        if (leftTime == null) {
            return right;
        }
        return rightTime != null && rightTime.isAfter(leftTime) ? right : left;
    }

    private List<AbilityDomainVO> toDomains(List<AbilitySkillNodeVO> skills) {
        Map<String, List<AbilitySkillNodeVO>> grouped = new LinkedHashMap<>();
        for (AbilitySkillNodeVO skill : skills) {
            grouped.computeIfAbsent(skill.getDomainCode(), key -> new ArrayList<>()).add(skill);
        }
        List<AbilityDomainVO> domains = new ArrayList<>();
        for (Map.Entry<String, List<AbilitySkillNodeVO>> entry : grouped.entrySet()) {
            List<AbilitySkillNodeVO> domainSkills = entry.getValue();
            AbilityDomainVO domain = new AbilityDomainVO();
            domain.setDomainCode(entry.getKey());
            domain.setDomainName(domainSkills.isEmpty() ? entry.getKey() : domainSkills.get(0).getDomainName());
            domain.setTotalCount(domainSkills.size());
            domain.setAssessedCount((int) domainSkills.stream().filter(this::isAssessed).count());
            domain.setWeakCount((int) domainSkills.stream().filter(skill -> STATUS_WEAK.equals(skill.getStatus())).count());
            domain.setSkills(domainSkills);
            domains.add(domain);
        }
        return domains;
    }

    private AbilitySkillNodeVO toSkillVO(
            AbilitySkillNode node,
            UserAbilityProfile profile,
            SkillUsageAggregate contribution) {
        AbilitySkillNodeVO vo = new AbilitySkillNodeVO();
        vo.setId(node.getId());
        vo.setCode(node.getCode());
        vo.setName(node.getName());
        vo.setDomainCode(node.getDomainCode());
        vo.setDomainName(node.getDomainName());
        vo.setDescription(node.getDescription());
        vo.setSortOrder(node.getSortOrder());
        applyProfile(vo, effectiveProfile(profile, contribution));
        return vo;
    }

    private InnerAbilityProfileSummaryVO toInnerSummary(
            AbilitySkillNode node,
            UserAbilityProfile profile,
            SkillUsageAggregate contribution) {
        InnerAbilityProfileSummaryVO vo = new InnerAbilityProfileSummaryVO();
        vo.setSkillCode(node.getCode());
        vo.setSkillName(node.getName());
        vo.setDomainCode(node.getDomainCode());
        vo.setDomainName(node.getDomainName());
        EffectiveAbilityProfile effective = effectiveProfile(profile, contribution);
        vo.setStatus(effective.status());
        vo.setEvidenceCount(effective.evidenceCount());
        vo.setLastEvaluatedAt(effective.lastEvaluatedAt());
        vo.setConfidence(effective.confidence());
        vo.setSummary(effective.summary());
        return vo;
    }

    private void applyProfile(AbilitySkillNodeVO vo, EffectiveAbilityProfile effective) {
        vo.setStatus(effective.status());
        vo.setEvidenceCount(effective.evidenceCount());
        vo.setLastEvaluatedAt(effective.lastEvaluatedAt());
        vo.setConfidence(effective.confidence());
        vo.setSummary(effective.summary());
    }

    private boolean isAssessed(AbilitySkillNodeVO skill) {
        return skill != null && !STATUS_UNASSESSED.equals(skill.getStatus()) && defaultInt(skill.getEvidenceCount()) > 0;
    }

    private EffectiveAbilityProfile effectiveProfile(
            UserAbilityProfile profile, SkillUsageAggregate contribution) {
        boolean evidenceOwned = profile != null
                && SOURCE_EVIDENCE_USAGE.equals(profile.getSourceType());
        int baseCount = profile == null || evidenceOwned
                ? 0
                : Math.max(0, defaultInt(profile.getEvidenceCount()));
        long projectedCount = contribution == null || contribution.getUsageCount() == null
                ? 0L
                : Math.max(0L, contribution.getUsageCount());
        int evidenceCount = toEvidenceCount((long) baseCount + projectedCount);
        if (evidenceCount <= 0) {
            return new EffectiveAbilityProfile(
                    STATUS_UNASSESSED, 0, null, CONFIDENCE_UNKNOWN, null);
        }

        boolean hasBaseProfile = profile != null && !evidenceOwned && baseCount > 0;
        String status = hasBaseProfile
                ? defaultString(profile.getStatus(), STATUS_UNASSESSED)
                : STATUS_UNASSESSED;
        String confidence = hasBaseProfile
                ? defaultString(profile.getConfidence(), CONFIDENCE_UNKNOWN)
                : CONFIDENCE_UNKNOWN;
        String summary = hasBaseProfile ? profile.getSummary() : null;
        LocalDateTime lastEvaluatedAt =
                hasBaseProfile ? profile.getLastEvaluatedAt() : null;
        if (projectedCount > 0) {
            confidence = raisedConfidence(confidence);
            summary = mergeSummary(summary, positiveEvidenceSummary(projectedCount));
            lastEvaluatedAt = latest(
                    lastEvaluatedAt,
                    contribution == null ? null : contribution.getLastProjectedAt());
        }
        return new EffectiveAbilityProfile(
                status, evidenceCount, lastEvaluatedAt, confidence, summary);
    }

    private String raisedConfidence(String current) {
        return "HIGH".equals(current) || CONFIDENCE_MEDIUM.equals(current)
                ? current
                : CONFIDENCE_MEDIUM;
    }

    private String mergeSummary(String current, String contribution) {
        if (!StringUtils.hasText(current)) {
            return contribution;
        }
        return StringUtils.hasText(contribution)
                ? current + "；" + contribution
                : current;
    }

    private String positiveEvidenceSummary(long projectedCount) {
        return "真实求职结果提供 " + projectedCount + " 次正向验证。";
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        return right != null && right.isAfter(left) ? right : left;
    }

    private int toEvidenceCount(long value) {
        return (int) Math.min(Math.max(0L, value), Integer.MAX_VALUE);
    }

    private List<String> sanitizeCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record EffectiveAbilityProfile(
            String status,
            Integer evidenceCount,
            LocalDateTime lastEvaluatedAt,
            String confidence,
            String summary) {
    }
}
