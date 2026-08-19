package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.entity.AbilitySkillNode;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.vo.AbilityDomainVO;
import com.codecoachai.resume.domain.vo.AbilityMapVO;
import com.codecoachai.resume.domain.vo.AbilitySkillNodeVO;
import com.codecoachai.resume.domain.vo.InnerAbilityProfileSummaryVO;
import com.codecoachai.resume.mapper.AbilitySkillNodeMapper;
import com.codecoachai.resume.mapper.AbilityTrainingEvidenceMapper;
import com.codecoachai.resume.mapper.AbilityTrainingEvidenceMapper.TrainingEvidenceAggregate;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper.SkillUsageAggregate;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.SkillGapItemMapper;
import com.codecoachai.resume.mapper.SkillProfileMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.AbilityMapService;
import com.codecoachai.resume.service.support.ResumeJobMatchTrustPolicy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final String STATUS_BASIC = "BASIC";
    private static final String STATUS_COMPETENT = "COMPETENT";
    private static final String STATUS_STRONG = "STRONG";
    private static final String CONFIDENCE_UNKNOWN = "UNKNOWN";
    private static final String CONFIDENCE_LOW = "LOW";
    private static final String CONFIDENCE_MEDIUM = "MEDIUM";
    private static final String SOURCE_EVIDENCE_USAGE = "EVIDENCE_USAGE";
    private static final String SOURCE_TRAINING_TASK = "TRAINING_TASK";
    private static final String SOURCE_RESUME_JOB_MATCH = "RESUME_JOB_MATCH";
    private static final String SYNCED = "SYNCED";
    private static final Map<String, List<String>> SKILL_ALIASES = Map.ofEntries(
            Map.entry("JAVA_CORE", List.of("JAVA", "JAVA基础", "JAVA核心")),
            Map.entry("COLLECTION_HASHMAP", List.of("集合", "HASHMAP", "CONCURRENTHASHMAP")),
            Map.entry("JUC_THREAD_POOL", List.of("JUC", "并发", "线程池", "AQS")),
            Map.entry("JVM_MEMORY_GC", List.of("JVM", "GC", "垃圾回收", "内存模型")),
            Map.entry("MYSQL_INDEX_TX", List.of("MYSQL", "SQL优化", "数据库索引", "事务")),
            Map.entry("REDIS_CACHE", List.of("REDIS", "缓存", "分布式锁")),
            Map.entry("SPRING_BOOT", List.of("SPRING", "SPRINGBOOT", "IOC", "AOP")),
            Map.entry("MYBATIS_ORM", List.of("MYBATIS", "ORM")),
            Map.entry("MICROSERVICE", List.of("微服务", "SPRINGCLOUD", "NACOS", "网关")),
            Map.entry("MESSAGE_QUEUE", List.of("消息队列", "MQ", "ROCKETMQ", "KAFKA", "RABBITMQ")),
            Map.entry("DISTRIBUTED_SYSTEM", List.of("分布式", "分布式系统", "CAP")),
            Map.entry("SYSTEM_DESIGN", List.of("系统设计", "架构设计")),
            Map.entry("PROJECT_EXPRESSION", List.of("项目表达", "项目经验", "项目证据")),
            Map.entry("ENGINEERING_PRACTICE", List.of("工程实践", "测试", "监控", "发布")));

    private final AbilitySkillNodeMapper skillNodeMapper;
    private final UserAbilityProfileMapper profileMapper;
    private final EvidenceUsageAbilityProjectionMapper evidenceProjectionMapper;
    private final AbilityTrainingEvidenceMapper trainingEvidenceMapper;
    private final SkillProfileMapper skillProfileMapper;
    private final SkillGapItemMapper skillGapItemMapper;
    private final ResumeJobMatchReportMapper matchReportMapper;
    private final ResumeJobMatchTrustPolicy matchTrustPolicy;

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
        SkillNodeResolver resolver = new SkillNodeResolver(nodes);
        Map<String, TrainingSignal> trainingSignals = trainingSignalMap(userId, resolver);
        Map<String, MatchSignal> matchSignals = matchSignalMap(userId, resolver);

        List<AbilitySkillNodeVO> skills = nodes.stream()
                .map(node -> toSkillVO(
                        node,
                        profiles.get(node.getCode()),
                        contributions.get(node.getCode()),
                        trainingSignals.get(node.getCode()),
                        matchSignals.get(node.getCode())))
                .toList();

        AbilityMapVO vo = new AbilityMapVO();
        vo.setUserId(userId);
        vo.setTotalSkillCount(skills.size());
        vo.setAssessedSkillCount((int) skills.stream().filter(this::isAssessed).count());
        vo.setWeakSkillCount((int) skills.stream().filter(skill -> STATUS_WEAK.equals(skill.getStatus())).count());
        vo.setStrongSkillCount((int) skills.stream().filter(skill -> STATUS_STRONG.equals(skill.getStatus())).count());
        vo.setHasTrainingData(skills.stream().anyMatch(skill -> defaultInt(skill.getEvidenceCount()) > 0));
        vo.setSyncStatus(SYNCED);
        vo.setUpdatedAt(skills.stream()
                .map(AbilitySkillNodeVO::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));
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
        SkillNodeResolver resolver = new SkillNodeResolver(nodes);
        Map<String, TrainingSignal> trainingSignals = trainingSignalMap(userId, resolver);
        Map<String, MatchSignal> matchSignals = matchSignalMap(userId, resolver);
        return nodes.stream()
                .map(node -> toInnerSummary(
                        node,
                        profiles.get(node.getCode()),
                        contributions.get(node.getCode()),
                        trainingSignals.get(node.getCode()),
                        matchSignals.get(node.getCode())))
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
            SkillUsageAggregate contribution,
            TrainingSignal training,
            MatchSignal match) {
        AbilitySkillNodeVO vo = new AbilitySkillNodeVO();
        vo.setId(node.getId());
        vo.setCode(node.getCode());
        vo.setName(node.getName());
        vo.setDomainCode(node.getDomainCode());
        vo.setDomainName(node.getDomainName());
        vo.setDescription(node.getDescription());
        vo.setSortOrder(node.getSortOrder());
        applyProfile(vo, effectiveProfile(profile, contribution, training, match));
        return vo;
    }

    private InnerAbilityProfileSummaryVO toInnerSummary(
            AbilitySkillNode node,
            UserAbilityProfile profile,
            SkillUsageAggregate contribution,
            TrainingSignal training,
            MatchSignal match) {
        InnerAbilityProfileSummaryVO vo = new InnerAbilityProfileSummaryVO();
        vo.setSkillCode(node.getCode());
        vo.setSkillName(node.getName());
        vo.setDomainCode(node.getDomainCode());
        vo.setDomainName(node.getDomainName());
        EffectiveAbilityProfile effective = effectiveProfile(profile, contribution, training, match);
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
        vo.setEvidenceSources(effective.evidenceSources());
        vo.setSourceLabels(effective.sourceLabels());
        vo.setSyncStatus(SYNCED);
        vo.setUpdatedAt(effective.updatedAt());
    }

    private boolean isAssessed(AbilitySkillNodeVO skill) {
        return skill != null && !STATUS_UNASSESSED.equals(skill.getStatus()) && defaultInt(skill.getEvidenceCount()) > 0;
    }

    private EffectiveAbilityProfile effectiveProfile(
            UserAbilityProfile profile,
            SkillUsageAggregate contribution,
            TrainingSignal training,
            MatchSignal match) {
        boolean evidenceOwned = profile != null
                && SOURCE_EVIDENCE_USAGE.equals(profile.getSourceType());
        int baseCount = profile == null || evidenceOwned
                ? 0
                : Math.max(0, defaultInt(profile.getEvidenceCount()));
        long projectedCount = contribution == null || contribution.getUsageCount() == null
                ? 0L
                : Math.max(0L, contribution.getUsageCount());
        long trainingCount = training == null ? 0L : training.evidenceCount();
        long matchCount = match == null ? 0L : match.evidenceCount();
        int evidenceCount = toEvidenceCount((long) baseCount + projectedCount + trainingCount + matchCount);
        if (evidenceCount <= 0) {
            return new EffectiveAbilityProfile(
                    STATUS_UNASSESSED,
                    0,
                    null,
                    CONFIDENCE_UNKNOWN,
                    null,
                    List.of(),
                    List.of(),
                    null);
        }

        boolean hasBaseProfile = profile != null && !evidenceOwned && baseCount > 0;
        AssessmentCandidate selected = null;
        if (hasBaseProfile && isAssessedStatus(profile.getStatus())) {
            selected = new AssessmentCandidate(
                    profile.getStatus(),
                    defaultString(profile.getConfidence(), CONFIDENCE_UNKNOWN),
                    latest(profile.getLastEvaluatedAt(), profile.getUpdatedAt()));
        }
        if (match != null && isAssessedStatus(match.status())) {
            selected = newerAssessment(selected, new AssessmentCandidate(
                    match.status(), match.confidence(), match.updatedAt()));
        }
        if (selected == null && trainingCount > 0) {
            selected = new AssessmentCandidate(
                    STATUS_BASIC, training.confidence(), training.updatedAt());
        }

        String status = selected == null ? STATUS_UNASSESSED : selected.status();
        String confidence = selected == null ? CONFIDENCE_UNKNOWN : selected.confidence();
        String summary = hasBaseProfile ? profile.getSummary() : null;
        LocalDateTime lastEvaluatedAt = hasBaseProfile
                ? latest(profile.getLastEvaluatedAt(), profile.getUpdatedAt())
                : null;
        Set<String> evidenceSources = new LinkedHashSet<>();
        Set<String> sourceLabels = new LinkedHashSet<>();
        if (baseCount > 0) {
            String sourceType = defaultString(profile.getSourceType(), "ABILITY_PROFILE");
            evidenceSources.add(sourceType);
            sourceLabels.add(sourceLabel(sourceType));
        }
        if (projectedCount > 0) {
            confidence = raisedConfidence(confidence);
            summary = mergeSummary(summary, positiveEvidenceSummary(projectedCount));
            lastEvaluatedAt = latest(
                    lastEvaluatedAt,
                    contribution == null ? null : contribution.getLastProjectedAt());
            evidenceSources.add(SOURCE_EVIDENCE_USAGE);
            sourceLabels.add(sourceLabel(SOURCE_EVIDENCE_USAGE));
        }
        if (trainingCount > 0) {
            summary = mergeSummary(summary, training.summary());
            lastEvaluatedAt = latest(lastEvaluatedAt, training.updatedAt());
            evidenceSources.add(SOURCE_TRAINING_TASK);
            sourceLabels.add(sourceLabel(SOURCE_TRAINING_TASK));
        }
        if (matchCount > 0) {
            summary = mergeSummary(summary, match.summary());
            lastEvaluatedAt = latest(lastEvaluatedAt, match.updatedAt());
            evidenceSources.add(SOURCE_RESUME_JOB_MATCH);
            sourceLabels.add(sourceLabel(SOURCE_RESUME_JOB_MATCH));
        }
        return new EffectiveAbilityProfile(
                status,
                evidenceCount,
                lastEvaluatedAt,
                confidence,
                summary,
                List.copyOf(evidenceSources),
                List.copyOf(sourceLabels),
                lastEvaluatedAt);
    }

    private String raisedConfidence(String current) {
        return "HIGH".equals(current) || CONFIDENCE_MEDIUM.equals(current)
                ? current
                : CONFIDENCE_MEDIUM;
    }

    private AssessmentCandidate newerAssessment(
            AssessmentCandidate current, AssessmentCandidate candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate.updatedAt() == null) {
            return current.updatedAt() == null ? candidate : current;
        }
        return current.updatedAt() == null || candidate.updatedAt().isAfter(current.updatedAt())
                ? candidate
                : current;
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

    private String sourceLabel(String sourceType) {
        return switch (defaultString(sourceType, "").toUpperCase(Locale.ROOT)) {
            case SOURCE_TRAINING_TASK -> "已完成训练";
            case SOURCE_RESUME_JOB_MATCH -> "可信岗位匹配";
            case SOURCE_EVIDENCE_USAGE -> "真实求职结果";
            case "INTERVIEW_REPORT" -> "面试评估";
            case "QUESTION_PRACTICE" -> "题目训练评估";
            default -> "历史能力评估";
        };
    }

    private boolean isAssessedStatus(String status) {
        return Set.of(STATUS_WEAK, STATUS_BASIC, STATUS_COMPETENT, STATUS_STRONG)
                .contains(defaultString(status, "").toUpperCase(Locale.ROOT));
    }

    private Map<String, TrainingSignal> trainingSignalMap(
            Long userId, SkillNodeResolver resolver) {
        if (userId == null) {
            return Map.of();
        }
        List<TrainingEvidenceAggregate> aggregates =
                trainingEvidenceMapper.selectCompletedSkillAggregates(userId);
        if (aggregates == null || aggregates.isEmpty()) {
            return Map.of();
        }
        Map<String, TrainingSignal> result = new HashMap<>();
        for (TrainingEvidenceAggregate aggregate : aggregates) {
            if (aggregate == null) {
                continue;
            }
            String skillCode = resolver.resolve(aggregate.getSkillCode(), aggregate.getSkillName());
            if (!StringUtils.hasText(skillCode)) {
                continue;
            }
            long count = Math.max(0L, defaultLong(aggregate.getEvidenceCount()));
            if (count <= 0) {
                continue;
            }
            TrainingSignal incoming = new TrainingSignal(
                    count,
                    aggregate.getLastCompletedAt(),
                    count >= 2 ? CONFIDENCE_MEDIUM : CONFIDENCE_LOW,
                    "已完成 " + count + " 项相关训练任务。");
            result.merge(skillCode, incoming, this::mergeTrainingSignals);
        }
        return result;
    }

    private TrainingSignal mergeTrainingSignals(TrainingSignal left, TrainingSignal right) {
        long count = left.evidenceCount() + right.evidenceCount();
        return new TrainingSignal(
                count,
                latest(left.updatedAt(), right.updatedAt()),
                count >= 2 ? CONFIDENCE_MEDIUM : CONFIDENCE_LOW,
                "已完成 " + count + " 项相关训练任务。");
    }

    private Map<String, MatchSignal> matchSignalMap(
            Long userId, SkillNodeResolver resolver) {
        if (userId == null) {
            return Map.of();
        }
        List<SkillProfile> profiles = nullSafe(skillProfileMapper.selectList(
                new LambdaQueryWrapper<SkillProfile>()
                        .eq(SkillProfile::getUserId, userId)
                        .eq(SkillProfile::getSourceType, SOURCE_RESUME_JOB_MATCH)
                        .eq(SkillProfile::getStatus, "SUCCESS")
                        .eq(SkillProfile::getDeleted, CommonConstants.NO)
                        .orderByDesc(SkillProfile::getUpdatedAt)
                        .orderByDesc(SkillProfile::getId)));
        Map<Long, SkillProfile> latestProfilesByReport = profiles.stream()
                .filter(profile -> profile != null && profile.getMatchReportId() != null)
                .collect(Collectors.toMap(
                        SkillProfile::getMatchReportId,
                        Function.identity(),
                        this::newerProfile));
        if (latestProfilesByReport.isEmpty()) {
            return Map.of();
        }

        List<ResumeJobMatchReport> reports = nullSafe(matchReportMapper.selectList(
                new LambdaQueryWrapper<ResumeJobMatchReport>()
                        .eq(ResumeJobMatchReport::getUserId, userId)
                        .in(ResumeJobMatchReport::getId, latestProfilesByReport.keySet())
                        .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO)));
        Set<Long> trustedReportIds = reports.stream()
                .filter(Objects::nonNull)
                .filter(report -> matchTrustPolicy.assess(report).trustedSuccess())
                .map(ResumeJobMatchReport::getId)
                .collect(Collectors.toSet());
        List<Long> trustedProfileIds = latestProfilesByReport.entrySet().stream()
                .filter(entry -> trustedReportIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(SkillProfile::getId)
                .filter(Objects::nonNull)
                .toList();
        if (trustedProfileIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, SkillProfile> trustedProfilesById = latestProfilesByReport.values().stream()
                .filter(profile -> trustedProfileIds.contains(profile.getId()))
                .collect(Collectors.toMap(SkillProfile::getId, Function.identity()));
        List<SkillGapItem> gaps = nullSafe(skillGapItemMapper.selectList(
                new LambdaQueryWrapper<SkillGapItem>()
                        .eq(SkillGapItem::getUserId, userId)
                        .in(SkillGapItem::getProfileId, trustedProfileIds)
                        .eq(SkillGapItem::getDeleted, CommonConstants.NO)
                        .orderByDesc(SkillGapItem::getUpdatedAt)
                        .orderByDesc(SkillGapItem::getId)));
        Map<String, MatchSignalAccumulator> accumulators = new HashMap<>();
        for (SkillGapItem gap : gaps) {
            if (gap == null) {
                continue;
            }
            String skillCode = resolver.resolve(null, gap.getSkillName());
            if (!StringUtils.hasText(skillCode)) {
                continue;
            }
            SkillProfile owner = trustedProfilesById.get(gap.getProfileId());
            LocalDateTime updatedAt = latest(
                    gap.getUpdatedAt(),
                    owner == null ? null : owner.getUpdatedAt());
            accumulators.computeIfAbsent(skillCode, ignored -> new MatchSignalAccumulator())
                    .accept(gap, updatedAt);
        }
        return accumulators.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toSignal()));
    }

    private SkillProfile newerProfile(SkillProfile left, SkillProfile right) {
        LocalDateTime leftTime = latest(left.getUpdatedAt(), left.getCreatedAt());
        LocalDateTime rightTime = latest(right.getUpdatedAt(), right.getCreatedAt());
        if (leftTime == null) {
            return right;
        }
        return rightTime != null && rightTime.isAfter(leftTime) ? right : left;
    }

    private String statusFromLevel(Integer currentLevel, Integer targetLevel) {
        if (currentLevel == null || targetLevel == null) {
            return null;
        }
        if (currentLevel > 5 || targetLevel > 5) {
            if (currentLevel < 40) {
                return STATUS_WEAK;
            }
            if (currentLevel < 60) {
                return STATUS_BASIC;
            }
            if (currentLevel < 80) {
                return STATUS_COMPETENT;
            }
            return STATUS_STRONG;
        }
        if (currentLevel <= 1) {
            return STATUS_WEAK;
        }
        if (currentLevel == 2) {
            return STATUS_BASIC;
        }
        if (currentLevel == 3) {
            return STATUS_COMPETENT;
        }
        return STATUS_STRONG;
    }

    private String confidenceFrom(BigDecimal confidence) {
        if (confidence == null) {
            return CONFIDENCE_LOW;
        }
        if (confidence.compareTo(new BigDecimal("0.80")) >= 0) {
            return "HIGH";
        }
        return confidence.compareTo(new BigDecimal("0.60")) >= 0
                ? CONFIDENCE_MEDIUM
                : CONFIDENCE_LOW;
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

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record EffectiveAbilityProfile(
            String status,
            Integer evidenceCount,
            LocalDateTime lastEvaluatedAt,
            String confidence,
            String summary,
            List<String> evidenceSources,
            List<String> sourceLabels,
            LocalDateTime updatedAt) {
    }

    private record AssessmentCandidate(
            String status, String confidence, LocalDateTime updatedAt) {
    }

    private record TrainingSignal(
            long evidenceCount,
            LocalDateTime updatedAt,
            String confidence,
            String summary) {
    }

    private record MatchSignal(
            long evidenceCount,
            LocalDateTime updatedAt,
            String status,
            String confidence,
            String summary) {
    }

    private final class MatchSignalAccumulator {
        private long evidenceCount;
        private LocalDateTime updatedAt;
        private AssessmentCandidate assessment;
        private String assessmentSummary;

        private void accept(SkillGapItem gap, LocalDateTime evidenceAt) {
            evidenceCount++;
            updatedAt = latest(updatedAt, evidenceAt);
            String status = statusFromLevel(gap.getCurrentLevel(), gap.getTargetLevel());
            if (!StringUtils.hasText(status)) {
                return;
            }
            AssessmentCandidate incoming = new AssessmentCandidate(
                    status, confidenceFrom(gap.getConfidence()), evidenceAt);
            AssessmentCandidate selected = newerAssessment(assessment, incoming);
            if (selected == incoming) {
                assessment = incoming;
                assessmentSummary = "可信岗位匹配评估：当前 "
                        + gap.getCurrentLevel()
                        + " / 目标 "
                        + gap.getTargetLevel()
                        + "。";
            }
        }

        private MatchSignal toSignal() {
            String summary = assessmentSummary;
            if (!StringUtils.hasText(summary)) {
                summary = "已归集 " + evidenceCount + " 条可信岗位匹配证据，当前等级仍待量化。";
            }
            return new MatchSignal(
                    evidenceCount,
                    updatedAt,
                    assessment == null ? null : assessment.status(),
                    assessment == null ? CONFIDENCE_UNKNOWN : assessment.confidence(),
                    summary);
        }
    }

    private static final class SkillNodeResolver {
        private final Map<String, String> exact = new HashMap<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();

        private SkillNodeResolver(List<AbilitySkillNode> nodes) {
            for (AbilitySkillNode node : nodes) {
                if (node == null || !StringUtils.hasText(node.getCode())) {
                    continue;
                }
                String code = node.getCode();
                registerExact(code, code);
                registerExact(node.getName(), code);
                registerExact(node.getDomainName(), code);
                for (String alias : SKILL_ALIASES.getOrDefault(code, List.of())) {
                    registerAlias(alias, code);
                }
            }
        }

        private String resolve(String rawCode, String rawName) {
            for (String value : List.of(
                    rawCode == null ? "" : rawCode,
                    rawName == null ? "" : rawName)) {
                String normalized = normalize(value);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                String direct = exact.get(normalized);
                if (direct != null) {
                    return direct;
                }
                for (Map.Entry<String, String> alias : aliases.entrySet()) {
                    if (normalized.contains(alias.getKey())) {
                        return alias.getValue();
                    }
                }
            }
            return null;
        }

        private void registerExact(String raw, String code) {
            String normalized = normalize(raw);
            if (StringUtils.hasText(normalized)) {
                exact.putIfAbsent(normalized, code);
            }
        }

        private void registerAlias(String raw, String code) {
            String normalized = normalize(raw);
            if (StringUtils.hasText(normalized)) {
                aliases.putIfAbsent(normalized, code);
            }
        }

        private static String normalize(String value) {
            return value == null
                    ? ""
                    : value.trim()
                            .toUpperCase(Locale.ROOT)
                            .replaceAll("[\\s_\\-/()（）.]+", "");
        }
    }
}
