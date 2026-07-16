package com.codecoachai.common.feign.multipart;

import feign.codec.EncodeException;
import java.lang.ref.Cleaner;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps multipart sources alive until the corresponding Feign request executes.
 *
 * <p>The small token byte array is installed as the Feign request body. Feign
 * retains that exact array, so the cleaner cannot release the strong source
 * references while the request is reachable. The client removes the lease
 * before network I/O; requests abandoned before execution are cleaned when
 * their token body becomes unreachable.
 */
final class MultipartLeaseRegistry {

    private static final int MAX_PENDING_LEASES = 256;
    private static final Cleaner CLEANER = Cleaner.create();
    private static final Map<String, Lease> LEASES = new ConcurrentHashMap<>();
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger();

    private MultipartLeaseRegistry() {
    }

    static Registration register(StreamingMultipartEncoder.PreparedMultipart prepared) {
        acquireSlot();
        byte[] tokenBody = prepared.token().getBytes(StandardCharsets.US_ASCII);
        Lease lease = new Lease(prepared);
        Cleaner.Cleanable cleanable = CLEANER.register(tokenBody, lease);
        lease.setCleanable(cleanable);
        Lease previous = LEASES.putIfAbsent(prepared.token(), lease);
        if (previous != null) {
            cleanable.clean();
            throw new EncodeException("Streaming multipart request token collision");
        }
        return new Registration(prepared.token(), tokenBody);
    }

    static StreamingMultipartEncoder.PreparedMultipart take(String token, byte[] tokenBody) {
        if (token == null || tokenBody == null
                || !Arrays.equals(token.getBytes(StandardCharsets.US_ASCII), tokenBody)) {
            return null;
        }
        Lease lease = LEASES.remove(token);
        if (lease == null) {
            return null;
        }
        lease.cleanable().clean();
        return lease.prepared();
    }

    static void release(String token) {
        Lease lease = LEASES.remove(token);
        if (lease != null) {
            lease.cleanable().clean();
        }
    }

    static int pendingLeaseCount() {
        return PENDING_COUNT.get();
    }

    static void releaseAll() {
        List.copyOf(LEASES.values()).forEach(lease -> lease.cleanable().clean());
    }

    private static void acquireSlot() {
        while (true) {
            int current = PENDING_COUNT.get();
            if (current >= MAX_PENDING_LEASES) {
                throw new EncodeException("Too many pending streaming multipart requests");
            }
            if (PENDING_COUNT.compareAndSet(current, current + 1)) {
                return;
            }
        }
    }

    record Registration(String token, byte[] tokenBody) {
    }

    private static final class Lease implements Runnable {

        private final StreamingMultipartEncoder.PreparedMultipart prepared;
        private final AtomicBoolean released = new AtomicBoolean();
        private Cleaner.Cleanable cleanable;

        private Lease(StreamingMultipartEncoder.PreparedMultipart prepared) {
            this.prepared = prepared;
        }

        private StreamingMultipartEncoder.PreparedMultipart prepared() {
            return prepared;
        }

        private Cleaner.Cleanable cleanable() {
            return cleanable;
        }

        private void setCleanable(Cleaner.Cleanable cleanable) {
            this.cleanable = cleanable;
        }

        @Override
        public void run() {
            if (released.compareAndSet(false, true)) {
                LEASES.remove(prepared.token(), this);
                PENDING_COUNT.decrementAndGet();
            }
        }
    }
}
