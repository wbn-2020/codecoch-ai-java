package com.codecoachai.resume.careerimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.resume.careerimport.CareerImportApplicationWriter.WriteOutcome;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportDedupeGuardMapper;
import java.lang.reflect.Method;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.TransactionDefinition;

@ExtendWith(MockitoExtension.class)
class CareerImportApplicationWriterTest {

    @Mock
    private CareerImportDedupeGuardMapper guardMapper;
    @Mock
    private JobApplicationMapper applicationMapper;

    private CareerImportApplicationWriter writer;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(JobApplication.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    JobApplication.class);
        }
    }

    @BeforeEach
    void setUp() {
        writer = new CareerImportApplicationWriter(
                guardMapper, applicationMapper, new CareerImportCanonicalSupport());
    }

    @Test
    void publicSkipWriterJoinsTheRowTransaction() throws Exception {
        Method method = CareerImportApplicationWriter.class
                .getMethod("writeSkip", JobApplication.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, CareerImportApplicationWriter.class);

        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertTrue(List.of(transactional.noRollbackFor()).contains(DuplicateKeyException.class));
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED,
                attribute.getPropagationBehavior());
        assertFalse(attribute.rollbackOn(mysqlDuplicate()));
    }

    @Test
    void publicCreateWriterKeepsDuplicateFailuresAuditable() throws Exception {
        Method method = CareerImportApplicationWriter.class
                .getMethod("writeCreate", JobApplication.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, CareerImportApplicationWriter.class);

        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertTrue(List.of(transactional.noRollbackFor()).contains(DuplicateKeyException.class));
        assertFalse(attribute.rollbackOn(mysqlDuplicate()));
        assertTrue(attribute.rollbackOn(new IllegalStateException("non-duplicate failure")));
    }

    @Test
    void skipLocksThenRequeriesDateWindowAndOnlyThenInserts() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, "2026-07-10");
        when(applicationMapper.selectCareerImportCandidatesInDateWindow(
                10L,
                LocalDateTime.of(2026, 7, 3, 0, 0),
                LocalDateTime.of(2026, 7, 17, 23, 59, 59, 999_999_999)))
                .thenReturn(List.of());
        when(applicationMapper.insert(incoming)).thenAnswer(invocation -> {
            incoming.setId(99L);
            return 1;
        });

        WriteOutcome outcome = writer.writeSkip(incoming);

        assertEquals("INSERTED", outcome.disposition());
        assertEquals(99L, outcome.applicationId());
        InOrder order = inOrder(guardMapper, applicationMapper);
        order.verify(guardMapper).insertIgnore(10L, writer.guardIdentityHash(incoming));
        order.verify(guardMapper).selectForUpdate(10L, writer.guardIdentityHash(incoming));
        order.verify(applicationMapper).selectCareerImportCandidatesInDateWindow(
                10L,
                LocalDateTime.of(2026, 7, 3, 0, 0),
                LocalDateTime.of(2026, 7, 17, 23, 59, 59, 999_999_999));
        order.verify(applicationMapper).insert(incoming);
    }

    @Test
    void skipReturnsOrdinaryDuplicateAfterLockedDatabaseRequery() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, "2026-07-10");
        JobApplication existing = application("ACME", "Backend Engineer", null, "2026-07-03");
        existing.setId(77L);
        when(applicationMapper.selectCareerImportCandidatesInDateWindow(any(), any(), any()))
                .thenReturn(List.of(existing));

        WriteOutcome outcome = writer.writeSkip(incoming);

        assertEquals("SKIPPED_DUPLICATE", outcome.disposition());
        assertNull(outcome.errorCode());
        assertEquals(77L, outcome.duplicateCandidates().get(0).getApplicationId());
        verify(applicationMapper, never()).insert(any(JobApplication.class));
    }

    @Test
    void undatedSkipRequeriesAllActiveCandidatesForTheUser() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, null);
        when(applicationMapper.selectCareerImportCandidatesForUndated(10L)).thenReturn(List.of());

        writer.writeSkip(incoming);

        verify(applicationMapper).selectCareerImportCandidatesForUndated(10L);
    }

    @Test
    void skipWithoutReliableIdentityDoesNotLockOrFingerprintAndStillInserts() {
        JobApplication incoming = application(null, "Backend Engineer", null, "2026-07-10");

        WriteOutcome outcome = writer.writeSkip(incoming);

        assertEquals("INSERTED", outcome.disposition());
        assertNull(incoming.getImportFingerprint());
        verify(guardMapper, never()).insertIgnore(any(), any());
        verify(guardMapper, never()).selectForUpdate(any(), any());
        verify(applicationMapper, never()).selectCareerImportCandidatesInDateWindow(any(), any(), any());
        verify(applicationMapper).insert(incoming);
    }

    @Test
    void createNeverLocksAndAlwaysClearsFingerprint() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, "2026-07-10");
        incoming.setImportFingerprint("stale");

        WriteOutcome outcome = writer.writeCreate(incoming);

        assertEquals("INSERTED", outcome.disposition());
        assertNull(incoming.getImportFingerprint());
        verify(guardMapper, never()).insertIgnore(any(), any());
        verify(guardMapper, never()).selectForUpdate(any(), any());
        verify(applicationMapper).insert(incoming);
    }

    @Test
    void mysqlFingerprintConflictRequiresMatchingActiveWinner() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, "2026-07-10");
        when(applicationMapper.selectCareerImportCandidatesInDateWindow(any(), any(), any()))
                .thenReturn(List.of());
        when(applicationMapper.insert(incoming)).thenThrow(mysqlDuplicate());
        JobApplication winner = application("Acme", "Backend Engineer", null, "2026-07-10");
        winner.setId(88L);
        winner.setImportFingerprint(new CareerImportCanonicalSupport().importFingerprint(incoming));
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(winner);

        WriteOutcome outcome = writer.writeSkip(incoming);

        assertEquals("SKIPPED_DUPLICATE", outcome.disposition());
        assertEquals("CONCURRENT_DUPLICATE", outcome.errorCode());
        ArgumentCaptor<LambdaQueryWrapper<JobApplication>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(applicationMapper).selectOne(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"), sql);
        assertTrue(sql.contains("import_fingerprint"), sql);
        assertTrue(sql.contains("deleted"), sql);
        assertTrue(sql.toLowerCase().contains("limit 1 for update"), sql);
    }

    @Test
    void mysqlFingerprintConflictWithoutMatchingWinnerIsRethrown() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, "2026-07-10");
        when(applicationMapper.selectCareerImportCandidatesInDateWindow(any(), any(), any()))
                .thenReturn(List.of());
        DuplicateKeyException conflict = mysqlDuplicate();
        when(applicationMapper.insert(incoming)).thenThrow(conflict);

        assertSame(conflict, assertThrows(
                DuplicateKeyException.class, () -> writer.writeSkip(incoming)));
    }

    @Test
    void fingerprintConstraintMessageWithoutMysqlMetadataIsRethrownWithoutWinnerRead() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, "2026-07-10");
        when(applicationMapper.selectCareerImportCandidatesInDateWindow(any(), any(), any()))
                .thenReturn(List.of());
        DuplicateKeyException conflict =
                duplicateKey("uk_job_application_import_fingerprint");
        when(applicationMapper.insert(incoming)).thenThrow(conflict);

        assertSame(conflict, assertThrows(
                DuplicateKeyException.class, () -> writer.writeSkip(incoming)));

        verify(applicationMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void unrelatedDuplicateKeyAndRuntimeExceptionRemainApplicationInsertFailures() {
        JobApplication unrelated = application("Acme", "Backend Engineer", null, "2026-07-10");
        when(applicationMapper.selectCareerImportCandidatesInDateWindow(any(), any(), any()))
                .thenReturn(List.of());
        DuplicateKeyException unrelatedConstraint = duplicateKey("uk_other");
        when(applicationMapper.insert(unrelated)).thenThrow(unrelatedConstraint);

        RuntimeException duplicateFailure =
                assertThrows(RuntimeException.class, () -> writer.writeSkip(unrelated));
        assertSame(unrelatedConstraint, duplicateFailure);

        JobApplication runtime = application("Beta", "Data Engineer", null, "2026-07-10");
        when(applicationMapper.insert(runtime)).thenThrow(new IllegalStateException("database down"));

        RuntimeException runtimeFailure =
                assertThrows(RuntimeException.class, () -> writer.writeSkip(runtime));

        assertTrue(runtimeFailure.getMessage().contains("database down"));
    }

    @Test
    void datedCandidateSentinelFailsInsteadOfInsertingAfterAThousandCandidates() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, "2026-07-10");
        when(applicationMapper.selectCareerImportCandidatesInDateWindow(any(), any(), any()))
                .thenReturn(Collections.nCopies(1001,
                        application("Other", "Other Role", null, "2026-07-10")));

        RuntimeException failure =
                assertThrows(RuntimeException.class, () -> writer.writeSkip(incoming));

        assertTrue(failure.getMessage().contains("Too many active application candidates"));
        verify(applicationMapper, never()).insert(any(JobApplication.class));
    }

    @Test
    void undatedCandidateSentinelFailsInsteadOfInsertingAfterAThousandCandidates() {
        JobApplication incoming = application("Acme", "Backend Engineer", null, null);
        when(applicationMapper.selectCareerImportCandidatesForUndated(10L))
                .thenReturn(Collections.nCopies(1001,
                        application("Other", "Other Role", null, null)));

        RuntimeException failure =
                assertThrows(RuntimeException.class, () -> writer.writeSkip(incoming));

        assertTrue(failure.getMessage().contains("Too many active application candidates"));
        verify(applicationMapper, never()).insert(any(JobApplication.class));
    }

    private static JobApplication application(
            String company, String title, String source, String appliedDate) {
        JobApplication application = new JobApplication();
        application.setUserId(10L);
        application.setCompanyName(company);
        application.setJobTitle(title);
        application.setSource(source);
        application.setAppliedAt(appliedDate == null
                ? null
                : LocalDateTime.parse(appliedDate + "T09:00:00"));
        return application;
    }

    private static DuplicateKeyException duplicateKey(String constraintName) {
        return new DuplicateKeyException(
                "outer",
                new IllegalStateException(
                        "middle",
                        new RuntimeException("Duplicate entry for key '" + constraintName + "'")));
    }

    private static DuplicateKeyException mysqlDuplicate() {
        return new DuplicateKeyException(
                "outer",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry", "23000", 1062));
    }
}
