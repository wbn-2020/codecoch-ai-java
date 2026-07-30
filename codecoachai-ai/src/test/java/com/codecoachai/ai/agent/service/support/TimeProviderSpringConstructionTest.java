package com.codecoachai.ai.agent.service.support;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.weekly.support.WeeklyReportTimeProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TimeProviderSpringConstructionTest {

    @Test
    void springUsesExplicitProductionConstructorsEvenWhenClockBeanExists() throws Exception {
        Instant fixedInstant = Instant.parse("2000-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Clock.class, () -> fixedClock);
            context.register(AgentBusinessTimeProvider.class, WeeklyReportTimeProvider.class);
            context.refresh();

            assertNotEquals(
                    LocalDate.of(2000, 1, 1),
                    context.getBean(AgentBusinessTimeProvider.class).today());
            assertNotEquals(
                    fixedInstant,
                    context.getBean(WeeklyReportTimeProvider.class).now());
        }

        assertTrue(AgentBusinessTimeProvider.class
                .getConstructor()
                .isAnnotationPresent(Autowired.class));
        assertTrue(WeeklyReportTimeProvider.class
                .getConstructor()
                .isAnnotationPresent(Autowired.class));
    }
}
