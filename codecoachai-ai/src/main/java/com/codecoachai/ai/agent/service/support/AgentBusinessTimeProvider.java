package com.codecoachai.ai.agent.service.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Supplies business time to agent services so date-sensitive behavior is deterministic in tests.
 */
@Component
public class AgentBusinessTimeProvider {

    private final Clock clock;

    public AgentBusinessTimeProvider() {
        this(Clock.systemDefaultZone());
    }

    public AgentBusinessTimeProvider(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
