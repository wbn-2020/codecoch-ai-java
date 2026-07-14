package com.codecoachai.resume.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeExportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class ResumeUploadAdmissionGuardTest {

    private static final int TEN_MIB = 10 * 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    void effectiveUploadSettingsUseDefaultsAndClampConfiguredBounds() {
        ResumeExportProperties properties = new ResumeExportProperties();

        assertEquals(2, properties.effectiveMaxConcurrentUploads());
        assertEquals(250L, properties.effectiveUploadAcquireTimeoutMillis());

        properties.setMaxConcurrentUploads(0);
        properties.setUploadAcquireTimeoutMillis(-1);
        assertEquals(2, properties.effectiveMaxConcurrentUploads());
        assertEquals(250L, properties.effectiveUploadAcquireTimeoutMillis());

        properties.setMaxConcurrentUploads(99);
        properties.setUploadAcquireTimeoutMillis(99_000);
        assertEquals(16, properties.effectiveMaxConcurrentUploads());
        assertEquals(5_000L, properties.effectiveUploadAcquireTimeoutMillis());

        properties.setUploadAcquireTimeoutMillis(0);
        assertEquals(0L, properties.effectiveUploadAcquireTimeoutMillis());
    }

    @Test
    void effectiveArtifactLimitClampsConfiguredTwentyMibToTenMib() {
        ResumeExportProperties properties = new ResumeExportProperties();
        properties.setMaxArtifactBytes(20L * 1024L * 1024L);

        assertEquals(TEN_MIB, properties.effectiveMaxArtifactBytes());
    }

    @Test
    void allowsArtifactWhoseSizeEqualsTenMibHardLimit() throws Exception {
        Path artifact = artifact("exact.bin", TEN_MIB);
        ResumeUploadAdmissionGuard guard = guard(1, 0, 20L * 1024L * 1024L);

        String result = guard.execute(artifact, () -> "uploaded");

        assertEquals("uploaded", result);
    }

    @Test
    void rejectsOversizeBeforeInvokingUploadSupplier() throws Exception {
        Path artifact = artifact("oversize.bin", 5);
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);
        AtomicBoolean invoked = new AtomicBoolean();

        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.execute(artifact, () -> {
                    invoked.set(true);
                    return "uploaded";
                }));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        assertFalse(invoked.get());
    }

    @Test
    void rejectsOversizeMultipartSizeBeforeInvokingUploadSupplier() {
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);
        AtomicBoolean invoked = new AtomicBoolean();

        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.execute(5L, () -> {
                    invoked.set(true);
                    return "uploaded";
                }));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        assertFalse(invoked.get());
    }

    @Test
    void sizeAndPathAdmissionsShareTheSameSemaphore() throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);
        Path artifact = artifact("shared.bin", 1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<String> holder = executor.submit(() -> guard.execute(1L, () -> {
            entered.countDown();
            await(release);
            return "incoming-upload";
        }));
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> guard.execute(artifact, () -> "export-upload"));

            assertEquals(ErrorCode.RESUME_UPLOAD_BUSY.getCode(), error.getCode());
        } finally {
            release.countDown();
            assertEquals("incoming-upload", holder.get(1, TimeUnit.SECONDS));
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsMissingOrNonRegularArtifactAsParameterError() {
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> guard.execute(tempDir.resolve("missing.bin"), () -> "uploaded"));
        BusinessException directory = assertThrows(BusinessException.class,
                () -> guard.execute(tempDir, () -> "uploaded"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), missing.getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), directory.getCode());
    }

    @Test
    void saturatedGuardFailsImmediatelyWhenTimeoutIsZero() throws Exception {
        assertSaturationReturnsTooManyRequests(0);
    }

    @Test
    void saturatedGuardWaitsForConfiguredTimeoutThenFails() throws Exception {
        assertSaturationReturnsTooManyRequests(120);
    }

    @Test
    void interruptedWaitRestoresInterruptAndReturnsServiceUnavailable() throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, 1_000, 4);
        Path artifact = artifact("interrupted.bin", 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<String> holder = executor.submit(() -> guard.execute(artifact, () -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return "holder";
        }));
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            Future<InterruptedResult> interrupted = executor.submit(() -> {
                Thread.currentThread().interrupt();
                try {
                    guard.execute(artifact, () -> "unexpected");
                    throw new AssertionError("Expected interrupted admission to fail");
                } catch (BusinessException ex) {
                    return new InterruptedResult(ex.getCode(), Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            });

            InterruptedResult result = interrupted.get(1, TimeUnit.SECONDS);
            assertEquals(ErrorCode.UPLOAD_INTERRUPTED.getCode(), result.code());
            assertTrue(result.interrupted());
        } finally {
            release.countDown();
            assertEquals("holder", holder.get(1, TimeUnit.SECONDS));
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsSymbolicLinkArtifact() throws Exception {
        Path target = artifact("target.bin", 1);
        Path link = tempDir.resolve("artifact-link.bin");
        createSymbolicLinkOrSkip(link, target);
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);

        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.execute(link, () -> "unexpected"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void revalidatesPathAfterPermitAcquisitionBeforeSupplier() throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, 1_000, 4);
        Path artifact = artifact("growing.bin", 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch holderEntered = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicBoolean supplierInvoked = new AtomicBoolean();
        Future<String> holder = executor.submit(() -> guard.execute(1L, () -> {
            holderEntered.countDown();
            await(releaseHolder);
            return "holder";
        }));
        try {
            assertTrue(holderEntered.await(1, TimeUnit.SECONDS));
            Future<String> waiting = executor.submit(() -> guard.execute(artifact, () -> {
                supplierInvoked.set(true);
                return "unexpected";
            }));
            Files.write(artifact, new byte[5]);
            releaseHolder.countDown();

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> waiting.get(1, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof BusinessException);
            assertEquals(ErrorCode.PARAM_ERROR.getCode(),
                    ((BusinessException) failure.getCause()).getCode());
            assertFalse(supplierInvoked.get());
        } finally {
            releaseHolder.countDown();
            assertEquals("holder", holder.get(1, TimeUnit.SECONDS));
            executor.shutdownNow();
        }
    }

    @Test
    void releasesPermitAfterSuccessfulSupplier() throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);
        Path artifact = artifact("success.bin", 1);

        assertEquals("first", guard.execute(artifact, () -> "first"));
        assertEquals("second", guard.execute(artifact, () -> "second"));
    }

    @Test
    void releasesPermitAfterCheckedIOException() throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);
        Path artifact = artifact("checked.bin", 1);

        assertThrows(IOException.class, () -> guard.execute(artifact, () -> {
            throw new IOException("upload failed");
        }));

        assertEquals("retry", guard.execute(artifact, () -> "retry"));
    }

    @Test
    void releasesPermitAfterRuntimeException() throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);
        Path artifact = artifact("runtime.bin", 1);

        assertThrows(IllegalStateException.class, () -> guard.execute(artifact, () -> {
            throw new IllegalStateException("upload failed");
        }));

        assertEquals("retry", guard.execute(artifact, () -> "retry"));
    }

    @Test
    void releasesPermitAfterBusinessException() throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, 0, 4);
        Path artifact = artifact("business.bin", 1);

        assertThrows(BusinessException.class, () -> guard.execute(artifact, () -> {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "upload failed");
        }));

        assertEquals("retry", guard.execute(artifact, () -> "retry"));
    }

    @Test
    void concurrentSuppliersNeverExceedConfiguredLimit() throws Exception {
        int limit = 2;
        ResumeUploadAdmissionGuard guard = guard(limit, 2_000, 4);
        Path artifact = artifact("concurrent.bin", 1);
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return guard.execute(artifact, () -> {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        try {
                            Thread.sleep(40);
                            return "uploaded";
                        } finally {
                            active.decrementAndGet();
                        }
                    });
                }));
            }

            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("uploaded", future.get(3, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(limit, maximum.get());
    }

    private void assertSaturationReturnsTooManyRequests(long timeoutMillis) throws Exception {
        ResumeUploadAdmissionGuard guard = guard(1, timeoutMillis, 4);
        Path artifact = artifact("saturated.bin", 1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<String> holder = executor.submit(() -> guard.execute(artifact, () -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return "holder";
        }));
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            BusinessException error = assertThrows(BusinessException.class,
                    () -> guard.execute(artifact, () -> "rejected"));

            assertEquals(ErrorCode.RESUME_UPLOAD_BUSY.getCode(), error.getCode());
            assertTrue(error.getMessage().toLowerCase().contains("retry"));
        } finally {
            release.countDown();
            assertEquals("holder", holder.get(1, TimeUnit.SECONDS));
            executor.shutdownNow();
        }
    }

    private ResumeUploadAdmissionGuard guard(int concurrency, long timeoutMillis, long maxBytes) {
        ResumeExportProperties properties = new ResumeExportProperties();
        properties.setMaxConcurrentUploads(concurrency);
        properties.setUploadAcquireTimeoutMillis(timeoutMillis);
        properties.setMaxArtifactBytes(maxBytes);
        return new ResumeUploadAdmissionGuard(properties);
    }

    private Path artifact(String name, int size) throws IOException {
        return Files.write(tempDir.resolve(name), new byte[size]);
    }

    private void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException ex) {
            Assumptions.abort("Symbolic links are not supported: " + ex.getClass().getSimpleName());
        } catch (IOException ex) {
            Assumptions.abort("Symbolic link creation is unavailable: " + ex.getClass().getSimpleName());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private record InterruptedResult(Integer code, boolean interrupted) {
    }
}
