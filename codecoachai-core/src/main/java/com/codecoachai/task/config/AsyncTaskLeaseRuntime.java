package com.codecoachai.task.config;

import java.time.LocalDateTime;

/**
 * Runtime inputs for async task leases.
 *
 * <p>Keeping time and token generation behind this contract makes claim races
 * deterministic in tests without weakening production randomness.
 */
public interface AsyncTaskLeaseRuntime {

    LocalDateTime now();

    String newLeaseToken();
}
