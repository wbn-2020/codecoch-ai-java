package com.codecoachai.resume.feign;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart view over a generated file with optional identity and size checks.
 *
 * <p>{@link #getBytes()} enforces the configured byte limit. The input stream
 * method prevents final-component symlink following but is not a complete
 * bounded or end-to-end streaming upload guarantee.
 */
public final class PathMultipartFile implements MultipartFile {

    private final Path path;
    private final String filename;
    private final String contentType;
    private final long maxBytes;
    private final Object expectedFileKey;

    public PathMultipartFile(Path path, String filename, String contentType) {
        this(path, filename, contentType, Long.MAX_VALUE, null);
    }

    public PathMultipartFile(
            Path path,
            String filename,
            String contentType,
            long maxBytes,
            Object expectedFileKey) {
        this.path = Objects.requireNonNull(path, "path");
        this.filename = Objects.requireNonNull(filename, "filename");
        this.contentType = contentType;
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        this.maxBytes = maxBytes;
        this.expectedFileKey = expectedFileKey;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }

    @Override
    public long getSize() {
        try {
            return validateSource().size();
        } catch (IOException ex) {
            throw new IllegalStateException("Multipart source file is unavailable", ex);
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        BasicFileAttributes attributes = validateSource();
        try (InputStream input = openInputStream()) {
            if (maxBytes == Long.MAX_VALUE) {
                return input.readAllBytes();
            }
            int initialCapacity = (int) Math.min(attributes.size(), 8192L);
            ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity);
            byte[] buffer = new byte[8192];
            long remaining = maxBytes + 1;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            if (output.size() > maxBytes) {
                throw new IOException("Multipart source file exceeds the configured size limit");
            }
            return output.toByteArray();
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        validateSource();
        return openInputStream();
    }

    @Override
    public void transferTo(File dest) throws IOException {
        try (InputStream input = getInputStream()) {
            Files.copy(input, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private BasicFileAttributes validateSource() throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Multipart source must be a regular file");
        }
        if (attributes.size() > maxBytes) {
            throw new IOException("Multipart source file exceeds the configured size limit");
        }
        if (expectedFileKey != null && attributes.fileKey() != null
                && !expectedFileKey.equals(attributes.fileKey())) {
            throw new IOException("Multipart source file was replaced");
        }
        return attributes;
    }

    private InputStream openInputStream() throws IOException {
        return Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }
}
