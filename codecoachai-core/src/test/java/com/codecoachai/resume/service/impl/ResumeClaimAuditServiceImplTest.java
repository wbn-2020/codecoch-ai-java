package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
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
import org.mockito.ArgumentCaptor;
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
        assertEquals("CLAIM_AUDIT_V1", result.getAuditVersion());
        assertEquals(1, result.getClaimCount());
        assertEquals(1, result.getVerifiedCount());
        assertEquals("QUANTIFIED", result.getFindings().get(0).getClaimType());
        assertEquals("VERIFIED", result.getFindings().get(0).getEvidenceStatus());
        assertEquals(USER_ID, findings.get(0).getUserId());
        assertEquals(7L, ((Number) result.getFindings().get(0).getEvidenceRefs().get(0)
                .get("projectEvidenceId")).longValue());
        verify(snapshotManager).ownedVersion(2L, USER_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ProjectEvidence>> evidenceQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(evidenceMapper).selectList(evidenceQuery.capture());
        assertOwnedQuery(evidenceQuery.getValue(), null, USER_ID);
        assertTrue(queryValues(evidenceQuery.getValue()).contains(1L));
    }

    @Test
    void detailUsesOwnerScopeForAuditAndFindings() {
        ResumeClaimAudit audit = audit(11L, USER_ID);
        when(auditMapper.selectOne(any())).thenReturn(audit);
        when(findingMapper.selectList(any())).thenReturn(List.of());

        ResumeClaimAuditVO result = service.detail(11L);

        assertEquals(11L, result.getId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeClaimAudit>> auditQuery = ArgumentCaptor.forClass(Wrapper.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeClaimAuditFinding>> findingQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper).selectOne(auditQuery.capture());
        verify(findingMapper).selectList(findingQuery.capture());
        assertOwnedQuery(auditQuery.getValue(), 11L, USER_ID);
        assertOwnedQuery(findingQuery.getValue(), null, USER_ID);
        assertTrue(queryValues(findingQuery.getValue()).contains(11L));
    }

    @Test
    void missingOrForeignAuditReturnsNotFoundWithoutReadingFindings() {
        when(auditMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.detail(404L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeClaimAudit>> auditQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper).selectOne(auditQuery.capture());
        assertOwnedQuery(auditQuery.getValue(), 404L, USER_ID);
        verifyNoInteractions(findingMapper);
    }

    @Test
    void listUsesCurrentUserAndOptionalResumeFilter() {
        when(auditMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), service.list(17L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeClaimAudit>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper).selectList(query.capture());
        assertOwnedQuery(query.getValue(), null, USER_ID);
        assertTrue(queryValues(query.getValue()).contains(17L));
    }

    @Test
    void unauthenticatedCallsFailBeforeDataAccess() {
        LoginUserContext.clear();

        BusinessException auditError = assertThrows(BusinessException.class, () -> service.audit(2L));
        BusinessException detailError = assertThrows(BusinessException.class, () -> service.detail(11L));
        BusinessException listError = assertThrows(BusinessException.class, () -> service.list(17L));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), auditError.getCode());
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), detailError.getCode());
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), listError.getCode());
        verifyNoInteractions(auditMapper, findingMapper, evidenceMapper, snapshotManager);
    }

    private ResumeClaimAudit audit(Long id, Long userId) {
        ResumeClaimAudit audit = new ResumeClaimAudit();
        audit.setId(id);
        audit.setUserId(userId);
        audit.setResumeId(1L);
        audit.setResumeVersionId(2L);
        audit.setAuditVersion("CLAIM_AUDIT_V1");
        audit.setStatus("COMPLETED");
        audit.setClaimCount(0);
        audit.setVerifiedCount(0);
        audit.setPartialCount(0);
        audit.setUnsupportedCount(0);
        audit.setRiskCount(0);
        return audit;
    }

    private void assertOwnedQuery(Wrapper<?> wrapper, Long resourceId, Long userId) {
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("deleted"));
        List<Object> values = queryValues(wrapper);
        assertTrue(values.contains(userId));
        assertTrue(values.contains(CommonConstants.NO));
        if (resourceId != null) {
            assertTrue(sql.contains("id"));
            assertTrue(values.contains(resourceId));
        }
    }

    private List<Object> queryValues(Object wrapper) {
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> query) {
            query.getSqlSegment();
            return List.copyOf(query.getParamNameValuePairs().values());
        }
        return List.of();
    }
}
