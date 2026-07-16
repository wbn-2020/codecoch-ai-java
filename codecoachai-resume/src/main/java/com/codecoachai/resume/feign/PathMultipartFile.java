package com.codecoachai.resume.feign;

import java.io.File;
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
 * <p>The resume file Feign client uses the common streaming multipart encoder
 * and consumes {@link #getInputStream()} directly. {@link #getBytes()} remains
 * for {@link MultipartFile} compatibility and uses one exact-size array.
 */
public final class PathMultipartFile implements MultipartFile {

    public static final long HARD_MAX_BYTES = 10L * 1024L * 1024L;

    private final Path path;
    private final String filename;
    private final String contentType;
    private final long maxBytes;
    private final Object expectedFileKey;

    public PathMultipartFile(Path path, String filename, String contentType) {
        this(path, filename, contentType, HARD_MAX_BYTES, null);
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
        if (maxBytes < 0 || maxBytes > HARD_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "maxBytes must be between 0 and " + HARD_MAX_BYTES);
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
        int size = Math.toIntExact(attributes.size());
        byte[] content = new byte[size];
        try (InputStream input = openInputStream()) {
            int offset = 0;
            while (offset < content.length) {
                int read = input.read(content, offset, content.length - offset);
                if (read < 0) {
                    throw new IOException("Multipart source file ended before its declared size");
                }
                if (read == 0) {
                    int single = input.read();
                    if (single < 0) {
                        throw new IOException("Multipart source file ended before its declared size");
                    }
                    content[offset++] = (byte) single;
                    continue;
                }
                offset += read;
            }
            if (input.read() != -1) {
                throw new IOException("Multipart source file grew beyond its declared size");
            }
            return content;
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
