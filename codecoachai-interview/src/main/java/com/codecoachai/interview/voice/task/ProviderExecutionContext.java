package com.codecoachai.interview.voice.task;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProviderExecutionContext {

    private final String requestId;
    private final Instant deadline;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public ProviderExecutionContext(String requestId, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.requestId = requestId;
        this.deadline = Instant.now().plus(timeout);
    }

    public String requestId() {
        return requestId;
    }

    public Instant deadline() {
        return deadline;
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isTimedOut() {
        return !Instant.now().isBefore(deadline);
    }

    public Duration remaining() {
        Duration remaining = Duration.between(Instant.now(), deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public void checkActive() {
        if (isCancelled()) {
            throw new ProviderTaskCancelledException("Provider task was cancelled");
        }
        if (isTimedOut()) {
            throw new ProviderTaskTimeoutException("Provider task exceeded its deadline");
        }
        if (Thread.currentThread().isInterrupted()) {
            cancel();
            throw new ProviderTaskCancelledException("Provider task was interrupted");
        }
    }
}
