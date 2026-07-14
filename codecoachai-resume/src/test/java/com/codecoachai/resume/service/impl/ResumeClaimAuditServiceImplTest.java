package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.config.ResumeExportProperties;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeClaimAudit;
import com.codecoachai.resume.domain.entity.ResumeClaimAuditFinding;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.vo.ResumeClaimAuditVO;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeClaimAuditFindingMapper;
import com.codecoachai.resume.mapper.ResumeClaimAuditMapper;
import com.codecoachai.resume.service.support.ResumeVersionSnapshotManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeClaimAuditServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private ResumeClaimAuditMapper auditMapper;
    @Mock
    private ResumeClaimAuditFindingMapper findingMapper;
    @Mock
    private ProjectEvidenceMapper evidenceMapper;
    @Mock
    private ResumeVersionSnapshotManager snapshotManager;

    private ResumeClaimAuditServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(ResumeClaimAudit.class);
        init(ResumeClaimAuditFinding.class);
        init(ProjectEvidence.class);
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("audit-user").build());
        service = new ResumeClaimAuditServiceImpl(
                auditMapper, findingMapper, evidenceMapper, snapshotManager,
                new ResumeExportProperties(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void quantifiedClaimIsVerifiedByMatchingProjectEvidence() {
        ResumeVersion version = new ResumeVersion();
        version.setId(2L);
        version.setUserId(USER_ID);
        version.setResumeId(1L);
        version.setSnapshotJson("{\"workExperience\":\"Reduced API latency by 35%.\"}");
        ObjectNode snapshot = new ObjectMapper().createObjectNode()
                .put("workExperience", "Reduced API latency by 35%.");
        ProjectEvidence evidence = new ProjectEvidence();
        evidence.setId(7L);
        evidence.setUserId(USER_ID);
        evidence.setSourceResumeId(1L);
        evidence.setTitle("API performance");
        evidence.setResult("Reduced API latency by 35% after profiling.");

        AtomicReference<ResumeClaimAudit> auditRef = new AtomicReference<>();
        List<ResumeClaimAuditFinding> findings = new ArrayList<>();
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(version);
        when(snapshotManager.readSnapshot(version)).thenReturn(snapshot);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));
        when(auditMapper.insert(any(ResumeClaimAudit.class))).thenAnswer(invocation -> {
            ResumeClaimAudit audit = invocation.getArgument(0);
            audit.setId(11L);
            auditRef.set(audit);
            return 1;
        });
        when(auditMapper.selectOne(any())).thenAnswer(invocation -> auditRef.get());
        when(findingMapper.insert(any(ResumeClaimAuditFinding.class))).thenAnswer(invocation -> {
            ResumeClaimAuditFinding finding = invocation.getArgument(0);
            finding.setId((long) findings.size() + 1L);
            findings.add(finding);
            return 1;
        });
        when(findingMapper.selectList(any())).thenAnswer(invocation -> findings);

        ResumeClaimAuditVO result = service.audit(2L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(1, result.getClaimCount());
        assertEquals(1, result.getVerifiedCount());
        assertEquals("QUANTIFIED", result.getFindings().get(0).getClaimType());
        assertEquals("VERIFIED", result.getFindings().get(0).getEvidenceStatus());
        assertEquals(7L, ((Number) result.getFindings().get(0).getEvidenceRefs().get(0)
                .get("projectEvidenceId")).longValue());
    }
}
