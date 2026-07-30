package com.codecoachai.ai.agent.weekly.support;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WeeklyReportTimeProvider {

    private final Clock clock;

    @Autowired
    public WeeklyReportTimeProvider() {
        this(Clock.systemDefaultZone());
    }

    public WeeklyReportTimeProvider(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId storageZone() {
        return clock.getZone();
    }

    public LocalDateTime storageDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, storageZone());
    }
}
