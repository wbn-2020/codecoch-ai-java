package com.codecoachai.resume.careerinterview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewCalendarLinkDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRoundCreateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewTransitionDTO;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewProcess;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRound;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRoundEvent;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewProcessMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundEventMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundMapper;
import com.codecoachai.resume.careerinterview.service.CareerInterviewPreparationReader;
import com.codecoachai.resume.careerinterview.service.CareerInterviewServiceImpl;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerInterviewServiceImplTest {

    @Mock
    private CareerInterviewProcessMapper processMapper;
    @Mock
    private CareerInterviewRoundMapper roundMapper;
    @Mock
    private CareerInterviewRoundEventMapper eventMapper;
    @Mock
    private CareerCalendarEventMapper calendarEventMapper;
    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private CareerInterviewPreparationReader preparationReader;

    private CareerInterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("interview-user").build());
        service = new CareerInterviewServiceImpl(
                processMapper, roundMapper, eventMapper, calendarEventMapper,
                applicationMapper, preparationReader, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsInvalidTransitionWithoutWriting() {
        CareerInterviewRound round = round(1L, "PLANNED");
        when(roundMapper.selectOwned(1L, 10L)).thenReturn(round);
        CareerInterviewTransitionDTO request = new CareerInterviewTransitionDTO();
        request.setTargetStatus("COMPLETED");
        request.setExpectedLockVersion(1);
        request.setIdempotencyKey("transition-1");

        assertThrows(BusinessException.class, () -> service.transition(1L, request));

        verify(roundMapper, never()).transition(anyLong(), any(), any(), any());
        verify(eventMapper, never()).insert(any(
                com.codecoachai.resume.careerinterview.entity.CareerInterviewRoundEvent.class));
    }

    @Test
    void rejectsStalePreparationWhenLinkingCalendar() {
        CareerInterviewRound round = round(1L, "SCHEDULED");
        CareerInterviewProcess process = process(20L, 30L);
        CareerCalendarEvent event = new CareerCalendarEvent();
        event.setId(99L);
        event.setUserId(10L);
        event.setApplicationId(30L);
        event.setEventType("TECHNICAL_INTERVIEW");
        when(roundMapper.selectOwned(1L, 10L)).thenReturn(round);
        when(processMapper.selectOwned(20L, 10L)).thenReturn(process);
        when(calendarEventMapper.selectById(99L)).thenReturn(event);
        when(preparationReader.read(event))
                .thenReturn(new CareerInterviewPreparationReader.PreparationSnapshot("STALE", "hash", true));
        CareerInterviewCalendarLinkDTO request = new CareerInterviewCalendarLinkDTO();
        request.setCalendarEventId(99L);
        request.setExpectedLockVersion(1);
        request.setIdempotencyKey("link-1");

        assertThrows(BusinessException.class, () -> service.linkCalendarEvent(1L, request));

        verify(roundMapper, never()).linkCalendar(anyLong(), anyLong(), any(), any());
    }

    @Test
    void keepsRealInterviewIdentitySeparateFromTraining() {
        CareerInterviewProcess process = process(20L, 30L);
        when(applicationMapper.selectById(30L)).thenReturn(application(30L));
        when(processMapper.selectActiveByApplication(30L, 10L)).thenReturn(process);
        when(roundMapper.selectByProcess(20L)).thenReturn(java.util.List.of());

        assertEquals("REAL_RECRUITING_INTERVIEW", service.getProcess(30L).getInterviewIdentity());
    }

    @Test
    void createsRoundWithOwnedEventAndUtcDefaultTimezone() {
        CareerInterviewProcess process = process(20L, 30L);
        when(processMapper.selectOwned(20L, 10L)).thenReturn(process);
        when(processMapper.claimNextRound(20L, 10L, 1)).thenAnswer(invocation -> {
            process.setCurrentRoundNo(1);
            process.setLockVersion(2);
            return 1;
        });
        when(roundMapper.insert(any(CareerInterviewRound.class))).thenAnswer(invocation -> {
            CareerInterviewRound inserted = invocation.getArgument(0);
            inserted.setId(40L);
            return 1;
        });
        when(eventMapper.selectCreatedByProcessIdempotency(20L, 10L, any()))
                .thenReturn(null);
        when(eventMapper.selectByRound(40L, 10L)).thenReturn(List.of());

        CareerInterviewRoundCreateDTO request = new CareerInterviewRoundCreateDTO();
        request.setRoundType("technical");
        request.setTitle("Technical interview");
        request.setIdempotencyKey("round-key");

        service.createRound(20L, request);

        ArgumentCaptor<CareerInterviewRound> roundCaptor =
                ArgumentCaptor.forClass(CareerInterviewRound.class);
        verify(roundMapper).insert(roundCaptor.capture());
        assertEquals("UTC", roundCaptor.getValue().getTimezone());

        ArgumentCaptor<CareerInterviewRoundEvent> eventCaptor =
                ArgumentCaptor.forClass(CareerInterviewRoundEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertEquals(10L, eventCaptor.getValue().getUserId());
        assertEquals(20L, eventCaptor.getValue().getProcessId());
        assertEquals(40L, eventCaptor.getValue().getRoundId());
    }

    @Test
    void rejectsSameInterviewIdempotencyKeyWithDifferentRequest() {
        CareerInterviewRound round = round(1L, "SCHEDULED");
        AtomicReference<CareerInterviewRoundEvent> stored = new AtomicReference<>();
        when(roundMapper.selectOwned(1L, 10L)).thenReturn(round);
        when(eventMapper.selectByIdempotency(1L, 10L, any()))
                .thenAnswer(invocation -> stored.get());
        when(roundMapper.transition(1L, "SCHEDULED", "PREPARING", 1))
                .thenAnswer(invocation -> {
                    round.setStatus("PREPARING");
                    return 1;
                });
        when(eventMapper.selectByRound(1L, 10L)).thenReturn(List.of());
        when(eventMapper.insert(any(CareerInterviewRoundEvent.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });

        CareerInterviewTransitionDTO first = new CareerInterviewTransitionDTO();
        first.setTargetStatus("PREPARING");
        first.setExpectedLockVersion(1);
        first.setIdempotencyKey("same-key");
        service.transition(1L, first);

        CareerInterviewTransitionDTO conflicting = new CareerInterviewTransitionDTO();
        conflicting.setTargetStatus("CANCELLED");
        conflicting.setExpectedLockVersion(1);
        conflicting.setIdempotencyKey("same-key");

        assertThrows(BusinessException.class, () -> service.transition(1L, conflicting));
        verify(roundMapper).transition(1L, "SCHEDULED", "PREPARING", 1);
    }

    private CareerInterviewRound round(Long id, String status) {
        CareerInterviewRound round = new CareerInterviewRound();
        round.setId(id);
        round.setProcessId(20L);
        round.setStatus(status);
        round.setLockVersion(1);
        return round;
    }

    private CareerInterviewProcess process(Long id, Long applicationId) {
        CareerInterviewProcess process = new CareerInterviewProcess();
        process.setId(id);
        process.setApplicationId(applicationId);
        process.setUserId(10L);
        process.setStatus("ACTIVE");
        process.setCurrentRoundNo(0);
        process.setLockVersion(1);
        return process;
    }

    private JobApplication application(Long id) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setUserId(10L);
        application.setDeleted(0);
        return application;
    }
}
