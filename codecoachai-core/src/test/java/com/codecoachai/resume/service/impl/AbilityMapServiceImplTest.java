package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.resume.domain.entity.AbilitySkillNode;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.vo.InnerAbilityProfileSummaryVO;
import com.codecoachai.resume.mapper.AbilitySkillNodeMapper;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper.SkillUsageAggregate;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

    private AbilityMapServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(AbilitySkillNode.class);
        initTableInfo(UserAbilityProfile.class);
    }

    @BeforeEach
    void setUp() {
        service = new AbilityMapServiceImpl(
                skillNodeMapper, profileMapper, evidenceProjectionMapper);
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

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
