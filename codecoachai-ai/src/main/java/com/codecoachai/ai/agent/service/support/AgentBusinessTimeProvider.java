package com.codecoachai.ai.agent.service.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Supplies business time to agent services so date-sensitive behavior is deterministic in tests.
 */
@Component
public class AgentBusinessTimeProvider {

    public static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final Clock clock;

    @Autowired
    public AgentBusinessTimeProvider() {
        this(Clock.system(BUSINESS_ZONE_ID));
    }

    public AgentBusinessTimeProvider(Clock clock) {
        this.clock = (clock == null ? Clock.system(BUSINESS_ZONE_ID) : clock).withZone(BUSINESS_ZONE_ID);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
