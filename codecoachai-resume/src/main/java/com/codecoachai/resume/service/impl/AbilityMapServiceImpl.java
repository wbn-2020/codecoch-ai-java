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
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.AbilityMapService;
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

    private final AbilitySkillNodeMapper skillNodeMapper;
    private final UserAbilityProfileMapper profileMapper;

    @Override
    public AbilityMapVO getCurrentUserAbilityMap() {
        Long userId = SecurityAssert.requireLoginUserId();
        List<AbilitySkillNode> nodes = listEnabledNodes();
        Map<String, UserAbilityProfile> profiles = profileMap(userId, nodes.stream()
                .map(AbilitySkillNode::getCode)
                .toList());

        List<AbilitySkillNodeVO> skills = nodes.stream()
                .map(node -> toSkillVO(node, profiles.get(node.getCode())))
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
        Map<String, UserAbilityProfile> profiles = profileMap(userId, nodes.stream()
                .map(AbilitySkillNode::getCode)
                .toList());
        return nodes.stream()
                .map(node -> toInnerSummary(node, profiles.get(node.getCode())))
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

    private AbilitySkillNodeVO toSkillVO(AbilitySkillNode node, UserAbilityProfile profile) {
        AbilitySkillNodeVO vo = new AbilitySkillNodeVO();
        vo.setId(node.getId());
        vo.setCode(node.getCode());
        vo.setName(node.getName());
        vo.setDomainCode(node.getDomainCode());
        vo.setDomainName(node.getDomainName());
        vo.setDescription(node.getDescription());
        vo.setSortOrder(node.getSortOrder());
        applyProfile(vo, profile);
        return vo;
    }

    private InnerAbilityProfileSummaryVO toInnerSummary(AbilitySkillNode node, UserAbilityProfile profile) {
        InnerAbilityProfileSummaryVO vo = new InnerAbilityProfileSummaryVO();
        vo.setSkillCode(node.getCode());
        vo.setSkillName(node.getName());
        vo.setDomainCode(node.getDomainCode());
        vo.setDomainName(node.getDomainName());
        if (profile == null || !hasEvidence(profile)) {
            vo.setStatus(STATUS_UNASSESSED);
            vo.setEvidenceCount(0);
            vo.setConfidence(CONFIDENCE_UNKNOWN);
            return vo;
        }
        vo.setStatus(defaultString(profile.getStatus(), STATUS_UNASSESSED));
        vo.setEvidenceCount(defaultInt(profile.getEvidenceCount()));
        vo.setLastEvaluatedAt(profile.getLastEvaluatedAt());
        vo.setConfidence(defaultString(profile.getConfidence(), CONFIDENCE_UNKNOWN));
        vo.setSummary(profile.getSummary());
        return vo;
    }

    private void applyProfile(AbilitySkillNodeVO vo, UserAbilityProfile profile) {
        if (profile == null || !hasEvidence(profile)) {
            vo.setStatus(STATUS_UNASSESSED);
            vo.setEvidenceCount(0);
            vo.setConfidence(CONFIDENCE_UNKNOWN);
            return;
        }
        vo.setStatus(defaultString(profile.getStatus(), STATUS_UNASSESSED));
        vo.setEvidenceCount(defaultInt(profile.getEvidenceCount()));
        vo.setLastEvaluatedAt(profile.getLastEvaluatedAt());
        vo.setConfidence(defaultString(profile.getConfidence(), CONFIDENCE_UNKNOWN));
        vo.setSummary(profile.getSummary());
    }

    private boolean isAssessed(AbilitySkillNodeVO skill) {
        return skill != null && !STATUS_UNASSESSED.equals(skill.getStatus()) && defaultInt(skill.getEvidenceCount()) > 0;
    }

    private boolean hasEvidence(UserAbilityProfile profile) {
        return profile != null && defaultInt(profile.getEvidenceCount()) > 0;
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
}
