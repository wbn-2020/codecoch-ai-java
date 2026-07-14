package com.codecoachai.resume.export;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeExportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Limits concurrent resume artifact uploads after validating file metadata.
 *
 * <p>This is admission control only. The multipart encoder may still call
 * {@code PathMultipartFile.getBytes()} and buffer the accepted file. Fully
 * streaming multipart upload requires a separate implementation.
 */
@Component
public class ResumeUploadAdmissionGuard {

    private static final String RETRY_MESSAGE = "Resume upload is busy; retry later";

    private final Semaphore uploadPermits;
    private final long maxArtifactBytes;
    private final long acquireTimeoutMillis;

    public ResumeUploadAdmissionGuard(ResumeExportProperties properties) {
        this.uploadPermits = new Semaphore(properties.effectiveMaxConcurrentUploads(), true);
        this.maxArtifactBytes = properties.effectiveMaxArtifactBytes();
        this.acquireTimeoutMillis = properties.effectiveUploadAcquireTimeoutMillis();
    }

    public <T> T execute(Path path, CheckedSupplier<T> supplier) throws Exception {
        return execute(path, ignored -> supplier.get());
    }

    public <T> T execute(Path path, CheckedFunction<ValidatedPath, T> supplier) throws Exception {
        ValidatedPath initial = validateArtifact(path);
        acquirePermit();
        try {
            ValidatedPath current = validateArtifact(path);
            validateSameFile(initial, current);
            return supplier.apply(current);
        } finally {
            uploadPermits.release();
        }
    }

    public <T> T execute(long size, Supplier<T> supplier) {
        validateSize(size);
        acquirePermit();
        try {
            return supplier.get();
        } finally {
            uploadPermits.release();
        }
    }

    private void acquirePermit() {
        boolean acquired;
        try {
            acquired = uploadPermits.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.UPLOAD_INTERRUPTED,
                    "Resume upload admission was interrupted; retry later");
        }
        if (!acquired) {
            throw new BusinessException(ErrorCode.RESUME_UPLOAD_BUSY, RETRY_MESSAGE);
        }
    }

    private ValidatedPath validateArtifact(Path path) {
        if (path == null) {
            throw invalidArtifact();
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw invalidArtifact();
            }
            validateSize(attributes.size());
            return new ValidatedPath(attributes.size(), attributes.fileKey(), maxArtifactBytes);
        } catch (IOException | SecurityException ex) {
            throw invalidArtifact();
        }
    }

    private void validateSize(long size) {
        if (size < 0 || size > maxArtifactBytes) {
            throw invalidArtifact();
        }
    }

    private void validateSameFile(ValidatedPath initial, ValidatedPath current) {
        if (initial.fileKey() != null && current.fileKey() != null
                && !Objects.equals(initial.fileKey(), current.fileKey())) {
            throw invalidArtifact();
        }
    }

    private BusinessException invalidArtifact() {
        return new BusinessException(
                ErrorCode.PARAM_ERROR,
                "Resume export artifact is unavailable or exceeds the upload size limit");
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface CheckedFunction<I, O> {
        O apply(I value) throws Exception;
    }

    public record ValidatedPath(long size, Object fileKey, long maxBytes) {
    }
}
