package com.codecoachai.task.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "codecoachai.task.lease")
public class AsyncTaskLeaseProperties {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration MIN_LEASE_DURATION = Duration.ofSeconds(10);
    private static final Duration MIN_HEARTBEAT_INTERVAL = Duration.ofSeconds(1);

    private Duration duration = DEFAULT_LEASE_DURATION;
    private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;

    public Duration effectiveDuration() {
        if (duration == null || duration.compareTo(MIN_LEASE_DURATION) < 0) {
            return DEFAULT_LEASE_DURATION;
        }
        return duration;
    }

    public Duration effectiveHeartbeatInterval() {
        Duration leaseDuration = effectiveDuration();
        Duration maximumInterval = leaseDuration.dividedBy(2);
        Duration configured = heartbeatInterval;
        if (configured == null || configured.compareTo(MIN_HEARTBEAT_INTERVAL) < 0) {
            configured = DEFAULT_HEARTBEAT_INTERVAL;
        }
        return configured.compareTo(maximumInterval) > 0 ? maximumInterval : configured;
    }
}
