package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.service.support.EvidenceProfileFeedbackService;
import com.codecoachai.resume.service.support.EvidenceProfileFeedbackService.ProjectionDisposition;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class EvidenceProfileFeedbackOutboxServiceImplTest {

    private static final Long OUTBOX_ID = 7L;
    private static final Long RESULT_ID = 101L;
    private static final Long USER_ID = 10L;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private EvidenceProfileFeedbackService feedbackService;

    private final List<SqlUpdate> updates = new ArrayList<>();
    private EvidenceProfileFeedbackOutboxServiceImpl service;
    private boolean evidenceProjectionDone;
    private boolean abilityProjectionDone;

    @BeforeEach
    void setUp() throws Exception {
        evidenceProjectionDone = false;
        abilityProjectionDone = false;
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        lenient().when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object[] invocationArgs = invocation.getArguments();
                    updates.add(new SqlUpdate(
                            invocation.getArgument(0),
                            Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length)));
                    return 1;
                });
        lenient().when(jdbcTemplate.query(
                        anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    Object[] invocationArgs = invocation.getArguments();
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(OUTBOX_ID);
                    when(resultSet.getLong("result_id")).thenReturn(RESULT_ID);
                    when(resultSet.getLong("user_id")).thenReturn(USER_ID);
                    when(resultSet.getInt("snapshot_version")).thenReturn(3);
                    when(resultSet.getBoolean("evidence_projection_done"))
                            .thenReturn(evidenceProjectionDone);
                    when(resultSet.getBoolean("ability_projection_done"))
                            .thenReturn(abilityProjectionDone);
                    when(resultSet.getInt("retry_count")).thenReturn(0);
                    when(resultSet.getString("locked_by"))
                            .thenReturn(invocationArgs[invocationArgs.length - 1].toString());
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });
        lenient().when(jdbcTemplate.queryForObject(
                        anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(USER_ID);
        service = new EvidenceProfileFeedbackOutboxServiceImpl(
                jdbcTemplate, transactionManager, feedbackService);
    }

    @Test
    void partialProjectionProgressIsPersistedWithoutReplayingCompletedBranches() {
        when(feedbackService.recomputeResult(RESULT_ID, USER_ID, false, false))
                .thenReturn(ProjectionDisposition.DEFERRED_ABILITY);

        boolean deferred = service.dispatch(OUTBOX_ID);

        assertFalse(deferred);
        verify(feedbackService).recomputeResult(RESULT_ID, USER_ID, false, false);
        SqlUpdate deferredUpdate = singleUpdateContaining("status = 'DEFERRED'");
        assertEquals(1, deferredUpdate.args()[0]);
        assertEquals(0, deferredUpdate.args()[1]);
        assertEquals(300, deferredUpdate.args()[2]);
        assertEquals(OUTBOX_ID, deferredUpdate.args()[3]);
        assertTrue(updatesContaining("status = 'DONE'").isEmpty());
        assertTrue(updatesContaining("status = 'FAILED'").isEmpty());
    }

    @Test
    void completedProjectionMarksBothProjectionFlagsDone() {
        when(feedbackService.recomputeResult(RESULT_ID, USER_ID, false, false))
                .thenReturn(ProjectionDisposition.COMPLETED);

        assertTrue(service.dispatch(OUTBOX_ID));

        assertEquals(1, updatesContaining("status = 'DONE'").size());
        assertTrue(updatesContaining("status = 'DEFERRED'").isEmpty());
    }

    @Test
    void retryPassesPersistedProjectionProgressToFeedbackService() {
        evidenceProjectionDone = true;
        when(feedbackService.recomputeResult(RESULT_ID, USER_ID, true, false))
                .thenReturn(ProjectionDisposition.COMPLETED);

        assertTrue(service.dispatch(OUTBOX_ID));

        verify(feedbackService).recomputeResult(RESULT_ID, USER_ID, true, false);
        assertEquals(1, updatesContaining("status = 'DONE'").size());
        assertTrue(updatesContaining("status = 'DEFERRED'").isEmpty());
    }

    @Test
    void projectionFailureKeepsExistingFailedBackoffTransition() {
        when(feedbackService.recomputeResult(RESULT_ID, USER_ID, false, false))
                .thenThrow(new IllegalStateException("db unavailable"));

        boolean completed = service.dispatch(OUTBOX_ID);

        assertFalse(completed);
        SqlUpdate failedUpdate = singleUpdateContaining("status = 'FAILED'");
        assertEquals(1, failedUpdate.args()[0]);
        assertEquals("db unavailable", failedUpdate.args()[1]);
        assertEquals(10L, failedUpdate.args()[2]);
        assertTrue(updatesContaining("status = 'DONE'").isEmpty());
        assertTrue(updatesContaining("status = 'DEFERRED'").isEmpty());
    }

    @Test
    void retryQueryIncludesOnlyDueDeferredRows() {
        List<String> queries = new ArrayList<>();
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    queries.add(invocation.getArgument(0));
                    return List.of();
                });

        assertEquals(0, service.retryPending(50));

        assertEquals(1, queries.size());
        assertTrue(queries.get(0).contains("status IN ('PENDING', 'FAILED', 'DEFERRED')"));
        assertTrue(queries.get(0).contains("next_retry_at <= NOW()"));
    }

    @Test
    void projectEvidenceMutationRequeuesAbilityBranchAndInvalidatesActiveClaim() {
        Long projectEvidenceId = 55L;

        assertEquals(1,
                service.requeueAbilityProjectionForProject(USER_ID, projectEvidenceId));

        SqlUpdate requeue = singleUpdateContaining(
                "INSERT INTO evidence_profile_feedback_outbox");
        assertTrue(requeue.sql().contains("u.asset_type = 'PROJECT_EVIDENCE'"));
        assertTrue(requeue.sql().contains("u.asset_type = 'PROJECT_SKILL_EVIDENCE'"));
        assertTrue(requeue.sql().contains("ability_projection_done = 0"));
        assertTrue(requeue.sql().contains("locked_by = NULL"));
        assertEquals(USER_ID, requeue.args()[0]);
        assertEquals(projectEvidenceId, requeue.args()[1]);
        assertEquals(USER_ID, requeue.args()[2]);
        assertEquals(projectEvidenceId, requeue.args()[3]);
    }

    private SqlUpdate singleUpdateContaining(String fragment) {
        List<SqlUpdate> matching = updatesContaining(fragment);
        assertEquals(1, matching.size());
        return matching.get(0);
    }

    private List<SqlUpdate> updatesContaining(String fragment) {
        return updates.stream()
                .filter(update -> update.sql().contains(fragment))
                .toList();
    }

    private record SqlUpdate(String sql, Object[] args) {
    }
}
