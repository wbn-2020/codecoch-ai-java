package com.codecoachai.ai.agent.weekly.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportQueryDTO;
import com.codecoachai.ai.agent.feign.ResumeAgentContextFeignClient;
import com.codecoachai.ai.agent.weekly.config.WeeklyReportFeatureProperties;
import com.codecoachai.ai.agent.weekly.model.WeeklyReportModels.RequestContext;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportHashUtils;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportSanitizer;
import com.codecoachai.ai.agent.weekly.support.WeeklyReportTimeProvider;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyReportRequestValidatorTest {

    @Mock
    private WeeklyReportFeatureProperties featureProperties;
    @Mock
    private WeeklyReportHashUtils hashUtils;
    @Mock
    private WeeklyReportSanitizer sanitizer;
    @Mock
    private ResumeAgentContextFeignClient resumeAgentContextFeignClient;

    @Test
    void mondayBoundaryUsesInjectedClockAndUserTimezone() {
        when(featureProperties.getWeeklyReportDefaultTimezone()).thenReturn("Asia/Shanghai");

        RequestContext before = validator(Instant.parse("2026-07-19T15:59:59Z"))
                .generate(10L, new AgentWeeklyReportGenerateDTO());
        RequestContext after = validator(Instant.parse("2026-07-19T16:00:00Z"))
                .generate(10L, new AgentWeeklyReportGenerateDTO());

        assertEquals(LocalDate.of(2026, 7, 13), before.getWeekStartDate());
        assertEquals(LocalDate.of(2026, 7, 20), after.getWeekStartDate());
    }

    @Test
    void ordinaryDatetimeBoundariesUseStorageClockZoneWhileSnapshotRangeStaysUtc() {
        AgentWeeklyReportGenerateDTO request = new AgentWeeklyReportGenerateDTO();
        request.setTimezone("America/Los_Angeles");

        RequestContext context = validator(Instant.parse("2026-07-20T04:00:00Z"))
                .generate(10L, request);

        assertEquals(LocalDate.of(2026, 7, 13), context.getWeekStartDate());
        assertEquals(LocalDateTime.of(2026, 7, 13, 7, 0), context.getRangeStartUtc());
        assertEquals(LocalDateTime.of(2026, 7, 20, 7, 0), context.getRangeEndUtc());
        assertEquals(
                LocalDateTime.of(2026, 7, 13, 15, 0),
                context.getDatabaseRangeStartAt());
        assertEquals(
                LocalDateTime.of(2026, 7, 20, 15, 0),
                context.getDatabaseRangeEndAt());
        assertEquals(
                LocalDateTime.of(2026, 7, 20, 12, 0),
                context.getDatabaseSourceCutoffAt());
    }

    @Test
    void futureWeekIsRejectedForCurrentAndHistoryQueries() {
        WeeklyReportRequestValidator validator =
                validator(Instant.parse("2026-07-20T04:00:00Z"));
        AgentWeeklyReportQueryDTO query = new AgentWeeklyReportQueryDTO();
        query.setTimezone("Asia/Shanghai");
        query.setWeekStartDate(LocalDate.of(2026, 7, 27));

        assertThrows(BusinessException.class, () -> validator.query(10L, query, true));
        assertThrows(BusinessException.class, () -> validator.query(10L, query, false));
    }

    private WeeklyReportRequestValidator validator(Instant instant) {
        Clock clock = Clock.fixed(instant, ZoneId.of("Asia/Shanghai"));
        return new WeeklyReportRequestValidator(
                featureProperties,
                new WeeklyReportTimeProvider(clock),
                hashUtils,
                sanitizer,
                resumeAgentContextFeignClient);
    }
}
