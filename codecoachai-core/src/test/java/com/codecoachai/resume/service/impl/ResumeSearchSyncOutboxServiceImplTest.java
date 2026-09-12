package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.mq.ResumeMqDispatcher;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 搜索同步 outbox 的并发/乱序验收：
 * 同一简历短时间内多次变更时，只有最新一条事件会被投递，
 * 更旧的事件必须被标记 SUPERSEDED 而不是按到达顺序盲目发送，
 * 从而避免旧快照覆盖 ES 中的新文档。
 */
@ExtendWith(MockitoExtension.class)
class ResumeSearchSyncOutboxServiceImplTest {

    private static final Long OUTBOX_ID = 42L;
    private static final Long RESUME_ID = 9709050L;
    private static final Long USER_ID = 1L;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResumeMqDispatcher dispatcher;

    private final List<SqlUpdate> updates = new ArrayList<>();
    private long newerEventCount;

    private ResumeSearchSyncOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        updates.clear();
        newerEventCount = 0L;
        lenient().when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object[] invocationArgs = invocation.getArguments();
                    updates.add(new SqlUpdate(
                            invocation.getArgument(0),
                            Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length)));
                    return 1;
                });
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(OUTBOX_ID);
                    when(resultSet.getLong("resume_id")).thenReturn(RESUME_ID);
                    when(resultSet.getLong("user_id")).thenReturn(USER_ID);
                    when(resultSet.getString("operation")).thenReturn("UPSERT");
                    when(resultSet.getInt("retry_count")).thenReturn(0);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> newerEventCount);
        service = new ResumeSearchSyncOutboxServiceImpl(jdbcTemplate, Optional.of(dispatcher));
    }

    @Test
    void staleEventWithNewerSiblingIsSupersededWithoutDispatch() {
        newerEventCount = 1L;

        assertTrue(service.dispatch(OUTBOX_ID));

        verify(dispatcher, never()).dispatchResumeSearchUpsert(anyLong(), anyLong());
        verify(dispatcher, never()).dispatchResumeSearchDelete(anyLong(), anyLong());
        assertEquals(1, updatesContaining("status = 'SUPERSEDED'").size());
        assertTrue(updatesContaining("status = 'DONE'").isEmpty());
    }

    @Test
    void latestEventIsDispatchedAndMarkedDone() {
        when(dispatcher.dispatchResumeSearchUpsert(RESUME_ID, USER_ID)).thenReturn(true);

        assertTrue(service.dispatch(OUTBOX_ID));

        verify(dispatcher).dispatchResumeSearchUpsert(RESUME_ID, USER_ID);
        assertEquals(1, updatesContaining("status = 'DONE'").size());
        assertTrue(updatesContaining("status = 'SUPERSEDED'").isEmpty());
    }

    @Test
    void concurrentClaimContentionSkipsDispatchForOtherWorker() {
        when(jdbcTemplate.update(contains("SET status = 'PROCESSING'"), any(Object[].class)))
                .thenReturn(0);

        assertFalse(service.dispatch(OUTBOX_ID));

        verifyNoInteractions(dispatcher);
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
        assertTrue(updatesContaining("status = 'DONE'").isEmpty());
    }

    @Test
    void failedDispatchRecordsBackoffWithoutMarkingDone() {
        when(dispatcher.dispatchResumeSearchUpsert(RESUME_ID, USER_ID)).thenReturn(false);

        assertFalse(service.dispatch(OUTBOX_ID));

        SqlUpdate failed = singleUpdateContaining("status = 'FAILED'");
        assertEquals(1, failed.args()[0]);
        assertEquals(10L, failed.args()[2]);
        assertTrue(updatesContaining("status = 'DONE'").isEmpty());
    }

    @Test
    void retryScanSelectsOnlyDueOrStaleProcessingRows() {
        List<String> queries = new ArrayList<>();
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    queries.add(invocation.getArgument(0));
                    return List.of();
                });

        assertEquals(0, service.retryPending(50));

        assertEquals(1, queries.size());
        assertTrue(queries.get(0).contains("status IN ('PENDING', 'FAILED')"));
        assertTrue(queries.get(0).contains("next_retry_at <= NOW()"));
        assertTrue(queries.get(0).contains("INTERVAL 5 MINUTE"));
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
