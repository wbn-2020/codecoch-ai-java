package com.codecoachai.common.feign.multipart;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Prepares multipart metadata without materializing file content in a byte array.
 *
 * <p>Feign 13 request bodies are byte-array backed. For multipart requests that
 * contain a {@link MultipartFile}, this encoder places only a private marker in
 * the request and hands the file metadata to {@link StreamingMultipartClient}.
 */
public final class StreamingMultipartEncoder implements Encoder {

    static final String MARKER_HEADER = "X-CodeCoachAI-Streaming-Multipart";
    static final long DEFAULT_MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_PARTS = 32;
    private static final int MAX_TEXT_PART_BYTES = 64 * 1024;

    private final Encoder delegate;
    private final long maxFileBytes;

    public StreamingMultipartEncoder(Encoder delegate, long maxFileBytes) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxFileBytes < 1 || maxFileBytes > DEFAULT_MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "maxFileBytes must be between 1 and " + DEFAULT_MAX_FILE_BYTES);
        }
        this.maxFileBytes = maxFileBytes;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        if (!isMultipart(template)) {
            delegate.encode(object, bodyType, template);
            return;
        }

        List<Part> parts = parts(object);
        if (parts.stream().noneMatch(FilePart.class::isInstance)) {
            delegate.encode(object, bodyType, template);
            return;
        }
        validateAggregateFileSize(parts);

        String token = UUID.randomUUID().toString();
        String boundary = "----CodeCoachAI" + token.replace("-", "");
        String serviceId = template.feignTarget() == null ? null : template.feignTarget().name();
        PreparedMultipart prepared = new PreparedMultipart(token, boundary, serviceId, List.copyOf(parts));
        MultipartLeaseRegistry.Registration registration = MultipartLeaseRegistry.register(prepared);
        try {
            template.header(HttpHeaders.CONTENT_TYPE, Collections.emptyList());
            template.header(
                    HttpHeaders.CONTENT_TYPE,
                    MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary);
            template.header(MARKER_HEADER, token);
            template.body(registration.tokenBody(), null);
        } catch (RuntimeException ex) {
            MultipartLeaseRegistry.release(token);
            throw ex;
        }
    }

    private boolean isMultipart(RequestTemplate template) {
        return template.headers().entrySet().stream()
                .filter(entry -> HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));
    }

    private List<Part> parts(Object object) {
        List<Part> result = new ArrayList<>();
        if (object instanceof MultipartFile file) {
            addFilePart(result, partName(file.getName()), file);
            return result;
        }
        if (!(object instanceof Map<?, ?> values)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !StringUtils.hasText(name)) {
                throw new EncodeException("Multipart part name must be a non-empty string");
            }
            addValue(result, partName(name), entry.getValue());
        }
        return result;
    }

    private void addValue(List<Part> parts, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof MultipartFile file) {
            addFilePart(parts, name, file);
            return;
        }
        if (value.getClass().isArray()) {
            if (value instanceof byte[] || value instanceof char[]) {
                throw new EncodeException("Binary multipart values must be provided as MultipartFile");
            }
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                addValue(parts, name, Array.get(value, index));
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addValue(parts, name, item);
            }
            return;
        }
        if (isScalar(value)) {
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_TEXT_PART_BYTES) {
                throw new EncodeException("Multipart text part exceeds the configured size limit");
            }
            addPart(parts, new TextPart(name, bytes));
            return;
        }
        throw new EncodeException(
                "Unsupported streaming multipart part type: " + value.getClass().getName());
    }

    private void addFilePart(List<Part> parts, String name, MultipartFile file) {
        long size;
        try {
            size = file.getSize();
        } catch (RuntimeException ex) {
            throw new EncodeException("Unable to inspect multipart file size", ex);
        }
        if (size < 0 || size > maxFileBytes) {
            throw new EncodeException("Multipart file exceeds the configured 10 MiB size limit");
        }
        String filename = safeFilename(file.getOriginalFilename(), file.getName());
        String contentType = safeContentType(file.getContentType());
        addPart(parts, new FilePart(name, filename, contentType, size, file));
    }

    private void addPart(List<Part> parts, Part part) {
        if (parts.size() >= MAX_PARTS) {
            throw new EncodeException("Multipart request contains too many parts");
        }
        parts.add(part);
    }

    private void validateAggregateFileSize(List<Part> parts) {
        long total = 0L;
        for (Part part : parts) {
            if (part instanceof FilePart filePart) {
                try {
                    total = Math.addExact(total, filePart.size());
                } catch (ArithmeticException ex) {
                    throw new EncodeException("Multipart file size exceeds the configured limit", ex);
                }
                if (total > maxFileBytes) {
                    throw new EncodeException("Multipart files exceed the configured 10 MiB size limit");
                }
            }
        }
    }

    private boolean isScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
    }

    private String partName(String value) {
        if (!StringUtils.hasText(value) || hasHeaderControl(value)) {
            throw new EncodeException("Multipart part name is invalid");
        }
        return value;
    }

    private String safeFilename(String originalFilename, String fallback) {
        String candidate = StringUtils.hasText(originalFilename) ? originalFilename : fallback;
        if (!StringUtils.hasText(candidate) || hasHeaderControl(candidate)) {
            throw new EncodeException("Multipart filename is invalid");
        }
        String normalized = candidate.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(filename) || ".".equals(filename) || "..".equals(filename)) {
            throw new EncodeException("Multipart filename is invalid");
        }
        return filename;
    }

    private String safeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        if (hasHeaderControl(contentType)) {
            throw new EncodeException("Multipart content type is invalid");
        }
        try {
            return MediaType.parseMediaType(contentType).toString();
        } catch (IllegalArgumentException ex) {
            throw new EncodeException("Multipart content type is invalid", ex);
        }
    }

    private boolean hasHeaderControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\r' || current == '\n' || current == '\0') {
                return true;
            }
        }
        return false;
    }

    sealed interface Part permits FilePart, TextPart {
        String name();
    }

    record FilePart(
            String name,
            String filename,
            String contentType,
            long size,
            MultipartFile file) implements Part {
    }

    record TextPart(String name, byte[] value) implements Part {
    }

    record PreparedMultipart(
            String token,
            String boundary,
            String serviceId,
            List<Part> parts) {
    }
}
