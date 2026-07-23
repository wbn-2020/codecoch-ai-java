package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import java.time.LocalDateTime;
import com.codecoachai.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobApplicationLifecycleServiceImplTest {

    private JobApplicationMapper applicationMapper;
    private JobApplicationEventMapper eventMapper;
    private JobApplicationLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        applicationMapper = org.mockito.Mockito.mock(JobApplicationMapper.class);
        eventMapper = org.mockito.Mockito.mock(JobApplicationEventMapper.class);
        service = new JobApplicationLifecycleServiceImpl(applicationMapper, eventMapper);
    }

    @Test
    void supportsExistingStatusesAndDistinctOfferOutcomes() {
        assertTrue(service.allowedTransitions("SAVED").contains("PREPARING"));
        assertTrue(service.allowedTransitions("PREPARING").contains("INTERVIEWING"));
        assertTrue(service.allowedTransitions("OFFER").contains("ACCEPTED"));
        assertTrue(service.allowedTransitions("OFFER").contains("DECLINED"));
        assertTrue(service.allowedTransitions("OFFER").contains("REJECTED"));
    }

    @Test
    void repeatedIdempotentRequestDoesNotWriteAnotherEvent() {
        JobApplication application = application(88L, 7L, "OFFER", 2);
        JobApplicationEvent existing = new JobApplicationEvent();
        existing.setId(901L);
        existing.setSummary("OFFER -> DECLINED");
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application);
        when(eventMapper.selectByIdempotencyKey(eq(7L), eq(88L), any(String.class))).thenReturn(existing);

        JobApplication result = service.transitionForUser(7L, 88L, "DECLINED", 2, "offer-decision-1");

        assertEquals(application, result);
        verify(applicationMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any(), any());
        verify(eventMapper, never()).insert(any(JobApplicationEvent.class));
    }

    @Test
    void sameIdempotencyKeyWithDifferentStatusIsRejected() {
        JobApplication application = application(88L, 7L, "OFFER", 2);
        JobApplicationEvent existing = new JobApplicationEvent();
        existing.setSummary("OFFER -> DECLINED");
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application);
        when(eventMapper.selectByIdempotencyKey(eq(7L), eq(88L), any(String.class))).thenReturn(existing);

        assertThrows(BusinessException.class,
                () -> service.transitionForUser(7L, 88L, "ACCEPTED", 2, "offer-decision-1"));
        verify(applicationMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void statusTransitionWritesStatusChangedEvent() {
        JobApplication application = application(88L, 7L, "OFFER", 2);
        JobApplication updated = application(88L, 7L, "ACCEPTED", 3);
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(application, updated);
        when(applicationMapper.transitionStatus(
                eq(88L), eq(7L), eq("OFFER"), eq("ACCEPTED"),
                any(LocalDateTime.class), eq("ACCEPTED"), eq(2))).thenReturn(1);

        JobApplication result = service.transitionForUser(7L, 88L, "ACCEPTED", 2, "offer-decision-2");

        assertEquals("ACCEPTED", result.getStatus());
        verify(eventMapper).insert(any(JobApplicationEvent.class));
    }

    private static JobApplication application(Long id, Long userId, String status, int version) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setUserId(userId);
        application.setStatus(status);
        application.setLockVersion(version);
        application.setDeleted(0);
        return application;
    }
}
