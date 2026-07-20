package com.codecoachai.resume.careerimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventView;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.ImportedEvent;
import com.codecoachai.resume.careercalendar.CareerCalendarService;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportPreview;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportResult;
import com.codecoachai.resume.careerimport.CareerImportModels.DuplicateCandidate;
import com.codecoachai.resume.careerimport.CareerImportApplicationWriter.WriteOutcome;
import com.codecoachai.resume.careerimport.entity.CareerImportBatch;
import com.codecoachai.resume.careerimport.entity.CareerImportRow;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportBatchMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportDedupeGuardMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportRowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class CareerImportServiceImplTest {

    @Mock
    private CareerImportBatchMapper batchMapper;
    @Mock
    private CareerImportRowMapper rowMapper;
    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private CareerImportApplicationWriter applicationWriter;
    @Mock
    private CareerCalendarEventMapper calendarEventMapper;
    @Mock
    private CareerCalendarService calendarService;

    private CareerImportServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(JobApplication.class);
        init(CareerCalendarEvent.class);
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("import-user").build());
        lenient().when(applicationWriter.writeSkip(any(JobApplication.class)))
                .thenAnswer(invocation -> inserted(invocation.getArgument(0), 101L));
        lenient().when(applicationWriter.writeCreate(any(JobApplication.class)))
                .thenAnswer(invocation -> inserted(invocation.getArgument(0), 101L));
        lenient().when(batchMapper.updateById(any(CareerImportBatch.class))).thenReturn(1);
        CareerImportRowTransaction rowTransaction = new CareerImportRowTransaction(
                rowMapper, new ObjectMapper().findAndRegisterModules());
        service = new CareerImportServiceImpl(
                batchMapper, rowMapper, applicationMapper, applicationWriter,
                new CareerImportCanonicalSupport(), calendarEventMapper, calendarService,
                rowTransaction, new CsvCodec(), new IcsCodec());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void previewMarksDuplicateCandidateWithoutWritingBusinessData() {
        JobApplication existing = new JobApplication();
        existing.setId(7L);
        existing.setUserId(10L);
        existing.setCompanyName("Acme");
        existing.setJobTitle("Backend Engineer");
        existing.setAppliedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        when(applicationMapper.selectList(any())).thenReturn(List.of(existing));
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportPreview preview = service.previewCsv("applications.csv", csv, "Asia/Shanghai");

        assertEquals(1, preview.getDuplicateCount());
        assertEquals("DUPLICATE_CANDIDATE", preview.getRows().get(0).getDisposition());
        assertEquals(7L, preview.getRows().get(0).getDuplicateCandidates().get(0).getApplicationId());
    }

    @Test
    void csvImportKeepsValidRowsWhenAnotherRowHasValidationError() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(30L);
            return 1;
        });
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                Beta,Data Engineer,not-a-date
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "SKIP");

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getErrorCount());
        assertEquals("SUCCESS", result.getRows().get(0).getDisposition());
        assertEquals("ERROR", result.getRows().get(1).getDisposition());
        assertTrue(result.getRows().get(1).getErrorMessage().contains("ISO-8601"));
    }

    @Test
    void previewReturnsHeadersAndSuggestedMappingForUserColumns() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        byte[] csv = """
                公司,职位,申请时间
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportPreview preview = service.previewCsv("applications.csv", csv, "Asia/Shanghai", Map.of());

        assertEquals(List.of("公司", "职位", "申请时间"), preview.getHeaders());
        assertEquals("公司", preview.getSuggestedMapping().get("company_name"));
        assertEquals("职位", preview.getSuggestedMapping().get("job_title"));
        assertEquals("申请时间", preview.getSuggestedMapping().get("applied_at"));
        assertTrue(preview.getSupportedFields().contains("event_start"));
    }

    @Test
    void csvImportAppliesExplicitCanonicalToSourceMapping() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(31L);
            return 1;
        });
        byte[] csv = """
                employer,role,submitted
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv(
                "applications.csv",
                csv,
                "Asia/Shanghai",
                "SKIP",
                Map.of("company_name", "employer", "job_title", "role", "applied_at", "submitted"));

        assertEquals(1, result.getSuccessCount());
        org.mockito.ArgumentCaptor<JobApplication> captor =
                org.mockito.ArgumentCaptor.forClass(JobApplication.class);
        verify(applicationWriter).writeSkip(captor.capture());
        assertEquals("Acme", captor.getValue().getCompanyName());
        assertEquals("Backend Engineer", captor.getValue().getJobTitle());
    }

    @Test
    void concurrentDuplicateInsertIsReportedAsSkippedDuplicate() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(32L);
            return 1;
        });
        when(applicationWriter.writeSkip(any(JobApplication.class)))
                .thenReturn(WriteOutcome.concurrentDuplicate());
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "SKIP");

        assertEquals(0, result.getErrorCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals("SKIPPED_DUPLICATE", result.getRows().get(0).getDisposition());
        assertEquals("CONCURRENT_DUPLICATE", result.getRows().get(0).getErrorCode());
    }

    @Test
    void skipPolicyDoesNotMisreportUnrelatedUniqueConstraintAsConcurrentDuplicate() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(35L);
            return 1;
        });
        when(applicationWriter.writeSkip(any(JobApplication.class)))
                .thenReturn(WriteOutcome.failed("some_other_unique_constraint"));
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "SKIP");

        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals("ERROR", result.getRows().get(0).getDisposition());
        assertEquals("APPLICATION_INSERT_FAILED", result.getRows().get(0).getErrorCode());
    }

    @Test
    void createPolicyLeavesImportFingerprintEmptySoExplicitDuplicatesCanBeCreated() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(33L);
            return 1;
        });
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "CREATE");

        org.mockito.ArgumentCaptor<JobApplication> captor =
                org.mockito.ArgumentCaptor.forClass(JobApplication.class);
        verify(applicationWriter).writeCreate(captor.capture());
        assertEquals(null, captor.getValue().getImportFingerprint());
        assertEquals("SUCCESS", result.getRows().get(0).getDisposition());
    }

    @Test
    void createPolicyDoesNotMisreportUnrelatedUniqueConstraintAsConcurrentDuplicate() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(34L);
            return 1;
        });
        when(applicationWriter.writeCreate(any(JobApplication.class)))
                .thenReturn(WriteOutcome.failed("some_other_unique_constraint"));
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "CREATE");

        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals("ERROR", result.getRows().get(0).getDisposition());
        assertEquals("APPLICATION_INSERT_FAILED", result.getRows().get(0).getErrorCode());
    }

    @Test
    void skipUsesLockedWriterDuplicateOutcomeForCandidatesAndBatchCounts() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        prepareBatch(38L);
        DuplicateCandidate candidate = new DuplicateCandidate();
        candidate.setApplicationId(701L);
        candidate.setCompanyName("Acme");
        candidate.setJobTitle("Backend Engineer");
        when(applicationWriter.writeSkip(any(JobApplication.class)))
                .thenReturn(WriteOutcome.duplicate(List.of(candidate)));
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "SKIP");

        assertEquals(0, result.getErrorCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals("SKIPPED_DUPLICATE", result.getRows().get(0).getDisposition());
        assertEquals(701L, result.getRows().get(0).getDuplicateCandidates().get(0).getApplicationId());
        verify(applicationWriter).writeSkip(any(JobApplication.class));
    }

    @Test
    void writerLockFailureMarksRowErrorPersistsAuditAndContinuesNextRow() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        prepareBatch(39L);
        when(applicationWriter.writeSkip(any(JobApplication.class)))
                .thenThrow(new CannotAcquireLockException("guard lock timeout"))
                .thenAnswer(invocation -> inserted(invocation.getArgument(0), 702L));
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                Beta,Data Engineer,2026-07-04T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "SKIP");

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getErrorCount());
        assertEquals("ERROR", result.getRows().get(0).getDisposition());
        assertEquals("APPLICATION_INSERT_FAILED", result.getRows().get(0).getErrorCode());
        assertTrue(result.getRows().get(0).getErrorMessage().contains("guard lock timeout"));
        assertEquals("SUCCESS", result.getRows().get(1).getDisposition());
        verify(rowMapper, times(2)).insert(any(CareerImportRow.class));
        verify(applicationWriter, times(2)).writeSkip(any(JobApplication.class));
    }

    @Test
    void writerQueryFailureStillPersistsRowAndFinishesFailedBatch() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        prepareBatch(40L);
        when(applicationWriter.writeSkip(any(JobApplication.class)))
                .thenThrow(new IllegalStateException("candidate query failed"));
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        ImportResult result = service.importCsv("applications.csv", csv, "Asia/Shanghai", "SKIP");

        assertEquals("FAILED", result.getStatus());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getErrorCount());
        assertEquals("APPLICATION_INSERT_FAILED", result.getRows().get(0).getErrorCode());
        verify(rowMapper).insert(any(CareerImportRow.class));
        verify(batchMapper).updateById(any(CareerImportBatch.class));
    }

    @Test
    void secondaryFailureAuditFailureIsPropagatedAndBatchIsNotFinalized() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        prepareBatch(43L);
        when(rowMapper.insert(any(CareerImportRow.class)))
                .thenThrow(
                        new IllegalStateException("row audit failed"),
                        new IllegalStateException("failure audit failed"));
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.importCsv(
                        "applications.csv", csv, "Asia/Shanghai", "CREATE"));

        assertTrue(failure.getMessage().contains("failure audit"));
        assertTrue(failure.getMessage().contains("batch 43"));
        assertEquals("failure audit failed", failure.getCause().getMessage());
        verify(applicationWriter, times(1)).writeCreate(any(JobApplication.class));
        verify(rowMapper, times(2)).insert(any(CareerImportRow.class));
        verify(batchMapper, never()).updateById(any(CareerImportBatch.class));
    }

    @Test
    void finishBatchFailureIsPropagatedInsteadOfReturningUnpersistedSummary() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        prepareBatch(45L);
        when(batchMapper.updateById(any(CareerImportBatch.class))).thenReturn(0);
        byte[] csv = """
                company_name,job_title,applied_at
                Acme,Backend Engineer,2026-07-03T09:00:00
                """.getBytes(StandardCharsets.UTF_8);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.importCsv(
                        "applications.csv", csv, "Asia/Shanghai", "CREATE"));

        assertTrue(failure.getMessage().contains("finalize import batch 45"));
    }

    @Test
    void initialApplicationPreviewUsesFiveThousandAndOneSentinelAndRejectsOverflow() {
        when(applicationMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<JobApplication> wrapper = invocation.getArgument(0);
            assertTrue(wrapper.getCustomSqlSegment().contains("LIMIT 5001"));
            return Collections.nCopies(5001, new JobApplication());
        });
        byte[] csv = """
                company_name,job_title
                Acme,Backend Engineer
                """.getBytes(StandardCharsets.UTF_8);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.importCsv("applications.csv", csv, "Asia/Shanghai", "SKIP"));

        assertTrue(failure.getMessage().contains("Too many active applications"));
        verify(batchMapper, never()).insert(any(CareerImportBatch.class));
        verify(applicationWriter, never()).writeSkip(any(JobApplication.class));
    }

    @Test
    void icsUidUniqueConflictReturnsActiveWinner() {
        prepareBatch(36L);
        when(calendarEventMapper.selectList(any())).thenReturn(List.of());
        when(calendarService.createImported(any(), any()))
                .thenThrow(mysqlDuplicate());
        CareerCalendarEvent winner = new CareerCalendarEvent();
        winner.setId(801L);
        winner.setUserId(10L);
        winner.setExternalUid("case-sensitive-UID");
        when(calendarEventMapper.selectActiveByExternalUidBinaryForUpdate(
                10L, "case-sensitive-UID")).thenReturn(winner);

        ImportResult result = service.importIcs(
                "calendar.ics", ics("case-sensitive-UID"), "Asia/Shanghai");

        assertEquals(0, result.getErrorCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals("SKIPPED_DUPLICATE", result.getRows().get(0).getDisposition());
        assertEquals("CONCURRENT_DUPLICATE", result.getRows().get(0).getErrorCode());
        assertEquals(801L, result.getRows().get(0).getCalendarEventId());
        verify(calendarEventMapper).selectActiveByExternalUidBinaryForUpdate(
                10L, "case-sensitive-UID");
    }

    @Test
    void interviewIcsMapsToPreparationSupportedEventType() {
        prepareBatch(45L);
        when(calendarEventMapper.selectList(any())).thenReturn(List.of());
        when(calendarService.createImported(any(), any())).thenAnswer(invocation -> {
            EventView view = new EventView();
            view.setId(901L);
            return view;
        });

        ImportResult result = service.importIcs(
                "interview.ics", ics("interview-UID"), "Asia/Shanghai");

        ArgumentCaptor<ImportedEvent> captor = ArgumentCaptor.forClass(ImportedEvent.class);
        verify(calendarService).createImported(eq(10L), captor.capture());
        assertEquals("TECHNICAL_INTERVIEW", captor.getValue().eventType());
        assertEquals("SUCCESS", result.getRows().get(0).getDisposition());
        assertEquals(901L, result.getRows().get(0).getCalendarEventId());
    }

    @Test
    void exportingErrorsForMissingBatchUsesResourceNotFoundContract() {
        when(batchMapper.selectById(404L)).thenReturn(null);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.exportErrorRowsCsv(404L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void icsUidUniqueConflictWithoutActiveWinnerRemainsInsertFailure() {
        prepareBatch(41L);
        when(calendarEventMapper.selectList(any())).thenReturn(List.of());
        when(calendarService.createImported(any(), any()))
                .thenThrow(mysqlDuplicate());

        ImportResult result = service.importIcs(
                "calendar.ics", ics("case-sensitive-UID"), "Asia/Shanghai");

        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals("ERROR", result.getRows().get(0).getDisposition());
        assertEquals("ICS_EVENT_INSERT_FAILED", result.getRows().get(0).getErrorCode());
    }

    @Test
    void icsEmptyUidUniqueConflictRemainsInsertFailureWithoutWinnerLookup() {
        prepareBatch(42L);
        when(calendarEventMapper.selectList(any())).thenReturn(List.of());
        when(calendarService.createImported(any(), any()))
                .thenThrow(mysqlDuplicate());

        ImportResult result = service.importIcs(
                "calendar.ics", icsWithoutUid(), "Asia/Shanghai");

        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals("ERROR", result.getRows().get(0).getDisposition());
        assertEquals("ICS_EVENT_INSERT_FAILED", result.getRows().get(0).getErrorCode());
        verify(calendarEventMapper, never())
                .selectActiveByExternalUidBinaryForUpdate(any(), any());
    }

    @Test
    void icsOtherUniqueConflictRemainsInsertFailure() {
        prepareBatch(37L);
        when(calendarEventMapper.selectList(any())).thenReturn(List.of());
        when(calendarService.createImported(any(), any()))
                .thenThrow(duplicateKey("uk_cce_other_constraint"));

        ImportResult result = service.importIcs(
                "calendar.ics", ics("case-sensitive-UID"), "Asia/Shanghai");

        assertEquals(1, result.getErrorCount());
        assertEquals("ERROR", result.getRows().get(0).getDisposition());
        assertEquals("ICS_EVENT_INSERT_FAILED", result.getRows().get(0).getErrorCode());
        verify(calendarEventMapper, never())
                .selectActiveByExternalUidBinaryForUpdate(any(), any());
    }

    @Test
    void icsMysqlDuplicateWithDifferentCaseWinnerRemainsInsertFailure() {
        prepareBatch(44L);
        when(calendarEventMapper.selectList(any())).thenReturn(List.of());
        when(calendarService.createImported(any(), any())).thenThrow(mysqlDuplicate());
        CareerCalendarEvent wrongWinner = new CareerCalendarEvent();
        wrongWinner.setId(802L);
        wrongWinner.setUserId(10L);
        wrongWinner.setExternalUid("CASE-SENSITIVE-UID");
        when(calendarEventMapper.selectActiveByExternalUidBinaryForUpdate(
                10L, "case-sensitive-UID")).thenReturn(wrongWinner);

        ImportResult result = service.importIcs(
                "calendar.ics", ics("case-sensitive-UID"), "Asia/Shanghai");

        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals("ICS_EVENT_INSERT_FAILED", result.getRows().get(0).getErrorCode());
    }

    @Test
    void initialIcsEventsUseFiveThousandAndOneSentinelAndRejectOverflow() {
        when(calendarEventMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<CareerCalendarEvent> wrapper = invocation.getArgument(0);
            assertTrue(wrapper.getCustomSqlSegment().contains("LIMIT 5001"));
            return Collections.nCopies(5001, new CareerCalendarEvent());
        });

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.importIcs(
                        "calendar.ics", ics("case-sensitive-UID"), "Asia/Shanghai"));

        assertTrue(failure.getMessage().contains("Too many active calendar events"));
        verify(batchMapper, never()).insert(any(CareerImportBatch.class));
        verify(calendarService, never()).createImported(any(), any());
    }

    @Test
    void explicitEmptyMappingDisablesAnAutomaticSuggestion() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        byte[] csv = """
                job_title
                Backend Engineer
                """.getBytes(StandardCharsets.UTF_8);

        ImportPreview preview = service.previewCsv(
                "applications.csv", csv, "Asia/Shanghai", Map.of("job_title", ""));

        assertEquals(1, preview.getErrorCount());
        assertTrue(preview.getRows().get(0).getErrorMessage().contains("job_title is required"));
    }

    @Test
    void errorCsvOnlyExportsRowsOwnedByTheRequestedBatch() {
        CareerImportBatch batch = new CareerImportBatch();
        batch.setId(30L);
        batch.setUserId(10L);
        when(batchMapper.selectById(30L)).thenReturn(batch);
        CareerImportRow row = new CareerImportRow();
        row.setBatchId(30L);
        row.setUserId(10L);
        row.setRowNumber(3);
        row.setDisposition("ERROR");
        row.setErrorCode("VALIDATION_ERROR");
        row.setErrorMessage("job_title is required");
        row.setRawDataJson("{\"company_name\":\"Acme\"}");
        when(rowMapper.selectList(any())).thenReturn(List.of(row));

        String csv = new String(service.exportErrorRowsCsv(30L), StandardCharsets.UTF_8);

        assertTrue(csv.contains("row_number,disposition,error_code,error_message,raw_data"));
        assertTrue(csv.contains("job_title is required"));
        assertTrue(csv.contains("{\"\"company_name\"\":\"\"Acme\"\"}"));
    }

    @Test
    void csvAndIcsCodecsRejectRowsAsSoonAsConfiguredLimitIsExceeded() {
        byte[] csv = """
                job_title
                One
                Two
                """.getBytes(StandardCharsets.UTF_8);
        byte[] ics = """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                DTSTART:20260711T090000
                END:VEVENT
                BEGIN:VEVENT
                DTSTART:20260712T090000
                END:VEVENT
                END:VCALENDAR
                """.getBytes(StandardCharsets.UTF_8);

        assertThrows(BusinessException.class, () -> new CsvCodec().parse(csv, 1));
        assertThrows(BusinessException.class, () -> new IcsCodec().parse(ics, "Asia/Shanghai", 1));
    }

    @Test
    void createPolicyAcceptsFiveHundredRowsAndDoesNotMisreportUnrelatedInsertFailure() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        prepareBatch(46L);
        CareerImportDedupeGuardMapper guardMapper = mock(CareerImportDedupeGuardMapper.class);
        CareerImportApplicationWriter realWriter = new CareerImportApplicationWriter(
                guardMapper, applicationMapper, new CareerImportCanonicalSupport());
        CareerImportRowTransaction rowTransaction = new CareerImportRowTransaction(
                rowMapper, new ObjectMapper().findAndRegisterModules());
        service = new CareerImportServiceImpl(
                batchMapper, rowMapper, applicationMapper, realWriter,
                new CareerImportCanonicalSupport(), calendarEventMapper, calendarService,
                rowTransaction, new CsvCodec(), new IcsCodec());
        AtomicLong applicationIds = new AtomicLong(2_000L);
        List<JobApplication> attempted = new ArrayList<>();
        when(applicationMapper.insert(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication application = invocation.getArgument(0);
            attempted.add(application);
            if ("Backend Engineer 249".equals(application.getJobTitle())) {
                throw mysqlDuplicate("uk_job_application_unrelated");
            }
            application.setId(applicationIds.incrementAndGet());
            return 1;
        });

        ImportResult result = service.importCsv(
                "applications-create-500.csv",
                csvApplications(500),
                "Asia/Shanghai",
                "CREATE");

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(500, result.getTotalCount());
        assertEquals(499, result.getSuccessCount());
        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals(500, attempted.size());
        assertTrue(attempted.stream().allMatch(item -> item.getImportFingerprint() == null));
        CareerImportModels.ImportRowView failed = result.getRows().stream()
                .filter(row -> "ERROR".equals(row.getDisposition()))
                .findFirst()
                .orElseThrow();
        assertEquals("APPLICATION_INSERT_FAILED", failed.getErrorCode());
        assertTrue(!"CONCURRENT_DUPLICATE".equals(failed.getErrorCode()));
        verify(applicationMapper, times(500)).insert(any(JobApplication.class));
        verify(guardMapper, never()).insertIgnore(any(), any());
        verify(guardMapper, never()).selectForUpdate(any(), any());
        verify(rowMapper, times(500)).insert(any(CareerImportRow.class));
    }

    @Test
    void concurrentDuplicateSubmissionAtFiveHundredRowBoundaryCreatesEachApplicationOnce()
            throws Exception {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        AtomicLong batchIds = new AtomicLong(100L);
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(batchIds.incrementAndGet());
            return 1;
        });
        Set<String> insertedKeys = ConcurrentHashMap.newKeySet();
        AtomicLong applicationIds = new AtomicLong(1_000L);
        when(applicationWriter.writeSkip(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication application = invocation.getArgument(0);
            String key = String.join(
                    "|",
                    String.valueOf(application.getCompanyName()),
                    String.valueOf(application.getJobTitle()),
                    String.valueOf(application.getAppliedAt()));
            if (insertedKeys.add(key)) {
                return inserted(application, applicationIds.incrementAndGet());
            }
            return WriteOutcome.concurrentDuplicate();
        });
        byte[] csv = csvApplications(500);
        assertTrue(csv.length < 2 * 1024 * 1024);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ImportResult> first = executor.submit(
                    () -> importCsvAfterBarrier(csv, ready, start));
            Future<ImportResult> second = executor.submit(
                    () -> importCsvAfterBarrier(csv, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<ImportResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));
            int successCount = results.stream().mapToInt(ImportResult::getSuccessCount).sum();
            int duplicateCount = results.stream().mapToInt(ImportResult::getDuplicateCount).sum();
            int errorCount = results.stream().mapToInt(ImportResult::getErrorCount).sum();

            assertEquals(500, successCount);
            assertEquals(500, duplicateCount);
            assertEquals(0, errorCount);
            assertEquals(500, insertedKeys.size());
            assertTrue(results.stream().allMatch(result -> "COMPLETED".equals(result.getStatus())));
            verify(applicationWriter, times(1_000)).writeSkip(any(JobApplication.class));
            verify(rowMapper, times(1_000)).insert(any(CareerImportRow.class));
            verify(batchMapper, times(2)).insert(any(CareerImportBatch.class));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private ImportResult importCsvAfterBarrier(
            byte[] csv,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        LoginUserContext.setLoginUser(
                LoginUser.builder().userId(10L).username("import-user").build());
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent import start");
            }
            return service.importCsv(
                    "applications-500.csv", csv, "Asia/Shanghai", "SKIP");
        } finally {
            LoginUserContext.clear();
        }
    }

    private static byte[] csvApplications(int rows) {
        StringBuilder csv = new StringBuilder(
                "company_name,job_title,applied_at\n");
        for (int index = 0; index < rows; index++) {
            csv.append("Company-")
                    .append(index)
                    .append(",Backend Engineer ")
                    .append(index)
                    .append(",2026-07-03T09:00:00\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void prepareBatch(Long batchId) {
        when(batchMapper.insert(any(CareerImportBatch.class))).thenAnswer(invocation -> {
            CareerImportBatch batch = invocation.getArgument(0);
            batch.setId(batchId);
            return 1;
        });
    }

    private static byte[] ics(String uid) {
        return ("""
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:%s
                SUMMARY:Technical interview
                DTSTART:20260713T090000
                DTEND:20260713T100000
                END:VEVENT
                END:VCALENDAR
                """.formatted(uid)).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] icsWithoutUid() {
        return """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                SUMMARY:Technical interview
                DTSTART:20260713T090000
                DTEND:20260713T100000
                END:VEVENT
                END:VCALENDAR
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static DuplicateKeyException duplicateKey(String constraintName) {
        return new DuplicateKeyException(
                "outer",
                new IllegalStateException("middle", new RuntimeException("Duplicate entry for key '"
                        + constraintName + "'")));
    }

    private static DuplicateKeyException mysqlDuplicate() {
        return mysqlDuplicate("uk_career_calendar_external_uid");
    }

    private static DuplicateKeyException mysqlDuplicate(String constraintName) {
        return new DuplicateKeyException(
                "outer",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key '" + constraintName + "'", "23000", 1062));
    }

    private static WriteOutcome inserted(JobApplication application, Long id) {
        application.setId(id);
        return WriteOutcome.inserted(id);
    }
}
