package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.AbilitySkillNode;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
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
import com.codecoachai.resume.service.support.ResumeJobMatchTrustPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbilityMapServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private AbilitySkillNodeMapper skillNodeMapper;
    @Mock
    private UserAbilityProfileMapper profileMapper;
    @Mock
    private EvidenceUsageAbilityProjectionMapper evidenceProjectionMapper;
    @Mock
    private AbilityTrainingEvidenceMapper trainingEvidenceMapper;
    @Mock
    private SkillProfileMapper skillProfileMapper;
    @Mock
    private SkillGapItemMapper skillGapItemMapper;
    @Mock
    private ResumeJobMatchReportMapper matchReportMapper;

    private AbilityMapServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(AbilitySkillNode.class);
        initTableInfo(UserAbilityProfile.class);
        initTableInfo(SkillProfile.class);
        initTableInfo(SkillGapItem.class);
        initTableInfo(ResumeJobMatchReport.class);
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("ability-map-test")
                .build());
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AbilityMapServiceImpl(
                skillNodeMapper,
                profileMapper,
                evidenceProjectionMapper,
                trainingEvidenceMapper,
                skillProfileMapper,
                skillGapItemMapper,
                matchReportMapper,
                new ResumeJobMatchTrustPolicy(objectMapper));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void profileSummaryMergesRetractableLedgerWithoutMutatingSourceRows() {
        List<AbilitySkillNode> nodes = List.of(
                node("REDIS_CACHE", "Redis", 10),
                node("SPRING_BOOT", "Spring / Spring Boot", 20),
                node("MICROSERVICE", "微服务", 30));
        UserAbilityProfile interviewOwned = profile(
                "REDIS_CACHE", "INTERVIEW_REPORT", "WEAK", 5, "LOW", "面试反馈总结");
        UserAbilityProfile evidenceOwned = profile(
                "SPRING_BOOT", "EVIDENCE_USAGE", "UNASSESSED", 99, "HIGH", "旧聚合");
        LocalDateTime projectedAt = LocalDateTime.of(2026, 7, 27, 11, 30);

        when(skillNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(nodes);
        when(profileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(interviewOwned, evidenceOwned));
        when(evidenceProjectionMapper.selectUsageAggregates(
                USER_ID,
                List.of("REDIS_CACHE", "SPRING_BOOT", "MICROSERVICE")))
                .thenReturn(List.of(
                        aggregate("REDIS_CACHE", 2L, projectedAt),
                        aggregate("SPRING_BOOT", 3L, projectedAt),
                        aggregate("MICROSERVICE", 1L, projectedAt)));

        Map<String, InnerAbilityProfileSummaryVO> summaries =
                service.listProfileSummary(USER_ID, null).stream()
                        .collect(Collectors.toMap(
                                InnerAbilityProfileSummaryVO::getSkillCode,
                                Function.identity()));

        InnerAbilityProfileSummaryVO redis = summaries.get("REDIS_CACHE");
        assertEquals("WEAK", redis.getStatus());
        assertEquals(7, redis.getEvidenceCount());
        assertEquals("MEDIUM", redis.getConfidence());
        assertTrue(redis.getSummary().contains("面试反馈总结"));
        assertTrue(redis.getSummary().contains("2 次正向验证"));
        assertEquals(projectedAt, redis.getLastEvaluatedAt());

        InnerAbilityProfileSummaryVO spring = summaries.get("SPRING_BOOT");
        assertEquals("UNASSESSED", spring.getStatus());
        assertEquals(3, spring.getEvidenceCount());
        assertEquals("MEDIUM", spring.getConfidence());

        InnerAbilityProfileSummaryVO microservice = summaries.get("MICROSERVICE");
        assertEquals("UNASSESSED", microservice.getStatus());
        assertEquals(1, microservice.getEvidenceCount());
        assertEquals("MEDIUM", microservice.getConfidence());

        assertEquals(5, interviewOwned.getEvidenceCount());
        assertEquals("LOW", interviewOwned.getConfidence());
        assertEquals("面试反馈总结", interviewOwned.getSummary());
        verify(profileMapper, never()).updateById(any(UserAbilityProfile.class));
    }

    @Test
    void completedTrainingAndTrustedMatchGapsUpdateCanonicalAbilityNodes() {
        LocalDateTime trainedAt = LocalDateTime.of(2026, 8, 16, 9, 30);
        LocalDateTime matchedAt = LocalDateTime.of(2026, 8, 16, 11, 0);
        List<AbilitySkillNode> nodes = List.of(
                node("MYSQL_INDEX_TX", "MySQL", 10),
                node("REDIS_CACHE", "Redis", 20),
                node("SYSTEM_DESIGN", "系统设计", 30));

        when(skillNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(nodes);
        when(profileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(evidenceProjectionMapper.selectUsageAggregates(USER_ID, List.of(
                "MYSQL_INDEX_TX", "REDIS_CACHE", "SYSTEM_DESIGN"))).thenReturn(List.of());
        when(trainingEvidenceMapper.selectCompletedSkillAggregates(USER_ID)).thenReturn(List.of(
                training(null, "MySQL 索引与事务", 2L, trainedAt),
                training("UNKNOWN_SKILL", "未知技能", 4L, trainedAt)));

        SkillProfile profile = new SkillProfile();
        profile.setId(501L);
        profile.setUserId(USER_ID);
        profile.setMatchReportId(901L);
        profile.setSourceType("RESUME_JOB_MATCH");
        profile.setStatus("SUCCESS");
        profile.setUpdatedAt(matchedAt);
        when(skillProfileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(profile));
        when(matchReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(trustedReport(901L)));

        SkillGapItem redisGap = new SkillGapItem();
        redisGap.setId(701L);
        redisGap.setProfileId(501L);
        redisGap.setUserId(USER_ID);
        redisGap.setSkillName("Redis 缓存治理");
        redisGap.setCurrentLevel(1);
        redisGap.setTargetLevel(4);
        redisGap.setConfidence(new BigDecimal("0.85"));
        redisGap.setUpdatedAt(matchedAt);
        SkillGapItem unknownGap = new SkillGapItem();
        unknownGap.setId(702L);
        unknownGap.setProfileId(501L);
        unknownGap.setUserId(USER_ID);
        unknownGap.setSkillName("Rust 所有权");
        unknownGap.setCurrentLevel(2);
        unknownGap.setTargetLevel(4);
        unknownGap.setUpdatedAt(matchedAt);
        when(skillGapItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(redisGap, unknownGap));

        AbilityMapVO result = service.getCurrentUserAbilityMap();
        Map<String, AbilitySkillNodeVO> skills = result.getDomains().stream()
                .flatMap(domain -> domain.getSkills().stream())
                .collect(Collectors.toMap(AbilitySkillNodeVO::getCode, Function.identity()));

        AbilitySkillNodeVO mysql = skills.get("MYSQL_INDEX_TX");
        assertEquals("BASIC", mysql.getStatus());
        assertEquals(2, mysql.getEvidenceCount());
        assertEquals("MEDIUM", mysql.getConfidence());
        assertEquals(List.of("TRAINING_TASK"), mysql.getEvidenceSources());
        assertEquals(List.of("已完成训练"), mysql.getSourceLabels());
        assertEquals(trainedAt, mysql.getUpdatedAt());

        AbilitySkillNodeVO redis = skills.get("REDIS_CACHE");
        assertEquals("WEAK", redis.getStatus());
        assertEquals(1, redis.getEvidenceCount());
        assertEquals("HIGH", redis.getConfidence());
        assertTrue(redis.getSummary().contains("当前 1 / 目标 4"));
        assertEquals(List.of("RESUME_JOB_MATCH"), redis.getEvidenceSources());

        AbilitySkillNodeVO systemDesign = skills.get("SYSTEM_DESIGN");
        assertEquals("UNASSESSED", systemDesign.getStatus());
        assertEquals(0, systemDesign.getEvidenceCount());
        assertFalse(systemDesign.getSummary() != null && systemDesign.getSummary().contains("Rust"));

        assertEquals(2, result.getAssessedSkillCount());
        assertTrue(result.getHasTrainingData());
        assertEquals("SYNCED", result.getSyncStatus());
        assertEquals(matchedAt, result.getUpdatedAt());
    }

    @Test
    void unquantifiedTrustedMatchEvidenceIsVisibleWithoutInventingAssessment() {
        List<AbilitySkillNode> nodes = List.of(node("REDIS_CACHE", "Redis", 10));
        when(skillNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(nodes);
        when(profileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(evidenceProjectionMapper.selectUsageAggregates(USER_ID, List.of("REDIS_CACHE")))
                .thenReturn(List.of());
        when(trainingEvidenceMapper.selectCompletedSkillAggregates(USER_ID)).thenReturn(List.of());

        SkillProfile profile = new SkillProfile();
        profile.setId(501L);
        profile.setUserId(USER_ID);
        profile.setMatchReportId(901L);
        profile.setSourceType("RESUME_JOB_MATCH");
        profile.setStatus("SUCCESS");
        when(skillProfileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(profile));
        when(matchReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(trustedReport(901L)));
        SkillGapItem gap = new SkillGapItem();
        gap.setProfileId(501L);
        gap.setUserId(USER_ID);
        gap.setSkillName("Redis");
        when(skillGapItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(gap));

        AbilityMapVO result = service.getCurrentUserAbilityMap();
        AbilitySkillNodeVO redis = result.getDomains().get(0).getSkills().get(0);

        assertEquals("UNASSESSED", redis.getStatus());
        assertEquals(1, redis.getEvidenceCount());
        assertEquals(0, result.getAssessedSkillCount());
        assertTrue(result.getHasTrainingData());
        assertTrue(redis.getSummary().contains("当前等级仍待量化"));
    }

    private AbilitySkillNode node(String code, String name, int sortOrder) {
        AbilitySkillNode node = new AbilitySkillNode();
        node.setId((long) sortOrder);
        node.setCode(code);
        node.setName(name);
        node.setDomainCode(code);
        node.setDomainName(name);
        node.setSortOrder(sortOrder);
        node.setEnabled(1);
        return node;
    }

    private UserAbilityProfile profile(
            String skillCode,
            String sourceType,
            String status,
            int evidenceCount,
            String confidence,
            String summary) {
        UserAbilityProfile profile = new UserAbilityProfile();
        profile.setUserId(USER_ID);
        profile.setSkillCode(skillCode);
        profile.setSourceType(sourceType);
        profile.setStatus(status);
        profile.setEvidenceCount(evidenceCount);
        profile.setConfidence(confidence);
        profile.setSummary(summary);
        return profile;
    }

    private SkillUsageAggregate aggregate(
            String skillCode, long usageCount, LocalDateTime lastProjectedAt) {
        SkillUsageAggregate aggregate = new SkillUsageAggregate();
        aggregate.setSkillCode(skillCode);
        aggregate.setUsageCount(usageCount);
        aggregate.setLastProjectedAt(lastProjectedAt);
        return aggregate;
    }

    private TrainingEvidenceAggregate training(
            String skillCode,
            String skillName,
            long evidenceCount,
            LocalDateTime lastCompletedAt) {
        TrainingEvidenceAggregate aggregate = new TrainingEvidenceAggregate();
        aggregate.setSkillCode(skillCode);
        aggregate.setSkillName(skillName);
        aggregate.setEvidenceCount(evidenceCount);
        aggregate.setLastCompletedAt(lastCompletedAt);
        return aggregate;
    }

    private ResumeJobMatchReport trustedReport(Long id) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setId(id);
        report.setUserId(USER_ID);
        report.setResumeId(101L);
        report.setTargetJobId(201L);
        report.setOverallScore(82);
        report.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        report.setAiCallLogId(301L);
        report.setSummary("可信匹配报告");
        report.setRawResultJson("""
                {"trustStatus":"VERIFIED","fallback":false,"schemaWarnings":[]}
                """);
        return report;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
