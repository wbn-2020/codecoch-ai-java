package com.codecoachai.common.security.internal;

import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

public final class TrustedRequestVerifier {

    private static final String NONCE_KEY_PREFIX = "codecoachai:internal:v2:nonce:";
    private static final String CACHED_BODY_ATTRIBUTE =
            TrustedRequestVerifier.class.getName() + ".cachedBody";
    private static final Pattern NONCE_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final InternalAuthProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final LongSupplier currentTimeMillis;

    public TrustedRequestVerifier(
            InternalAuthProperties properties,
            StringRedisTemplate stringRedisTemplate) {
        this(properties, stringRedisTemplate, System::currentTimeMillis);
    }

    TrustedRequestVerifier(
            InternalAuthProperties properties,
            StringRedisTemplate stringRedisTemplate,
            LongSupplier currentTimeMillis) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.currentTimeMillis = currentTimeMillis;
    }

    public HttpServletRequest verify(
            HttpServletRequest request,
            String timestamp,
            String nonce,
            String signature,
            String nonceScope,
            String canonicalPayload,
            String declaredBodySha256,
            boolean allowUnsignedStreamingBody) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getSecret())) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }
        TimestampWindow timestampWindow = validateTimestamp(timestamp);
        if (!StringUtils.hasText(nonce) || !NONCE_PATTERN.matcher(nonce).matches()) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }
        if (!StringUtils.hasText(signature)
                || !StringUtils.hasText(nonceScope)
                || !StringUtils.hasText(canonicalPayload)
                || !StringUtils.hasText(declaredBodySha256)) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }

        String expectedSignature =
                InternalSignatureUtils.hmacSha256Hex(properties.getSecret(), canonicalPayload);
        if (!InternalSignatureUtils.constantTimeEquals(expectedSignature, signature)) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }

        HttpServletRequest verifiedRequest =
                verifyBody(request, declaredBodySha256, allowUnsignedStreamingBody);
        claimNonce(nonceScope, nonce, timestampWindow.replayTtl());
        return verifiedRequest;
    }

    public static boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return StringUtils.hasText(contentType)
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private TimestampWindow validateTimestamp(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long now = currentTimeMillis.getAsLong();
            long allowedSkewMillis = Math.multiplyExact(properties.getAllowedClockSkewSeconds(), 1000L);
            long ageMillis = Math.subtractExact(now, requestTime);
            if (ageMillis < -allowedSkewMillis || ageMillis > allowedSkewMillis) {
                throw new VerificationException(FailureReason.INVALID_SIGNATURE);
            }

            long configuredTtlMillis = Math.multiplyExact(properties.getNonceTtlSeconds(), 1000L);
            long remainingValidityMillis = Math.addExact(
                    Math.subtractExact(allowedSkewMillis, ageMillis),
                    1000L);
            long replayTtlMillis = Math.max(configuredTtlMillis, remainingValidityMillis);
            return new TimestampWindow(Duration.ofMillis(replayTtlMillis));
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }
    }

    private HttpServletRequest verifyBody(
            HttpServletRequest request,
            String declaredBodySha256,
            boolean allowUnsignedStreamingBody) {
        if (InternalSignatureUtils.STREAMING_BODY_SHA256.equals(declaredBodySha256)) {
            if (!allowUnsignedStreamingBody) {
                throw new VerificationException(FailureReason.INVALID_SIGNATURE);
            }
            return request;
        }
        if (!SHA256_PATTERN.matcher(declaredBodySha256).matches()) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }

        byte[] body = cachedBody(request);
        if (body == null) {
            body = readBody(request);
            request.setAttribute(CACHED_BODY_ATTRIBUTE, body);
        }
        String actualBodySha256 = InternalSignatureUtils.sha256Hex(body);
        if (!InternalSignatureUtils.constantTimeEquals(actualBodySha256, declaredBodySha256)) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }
        return request instanceof RepeatableBodyRequestWrapper
                ? request
                : new RepeatableBodyRequestWrapper(request, body);
    }

    private byte[] cachedBody(HttpServletRequest request) {
        Object cached = request.getAttribute(CACHED_BODY_ATTRIBUTE);
        return cached instanceof byte[] bytes ? bytes : null;
    }

    private byte[] readBody(HttpServletRequest request) {
        long maxSignedBodyBytes = properties.getMaxSignedBodyBytes();
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxSignedBodyBytes) {
            throw new VerificationException(FailureReason.BODY_TOO_LARGE);
        }
        try {
            int readLimit = Math.toIntExact(maxSignedBodyBytes + 1L);
            byte[] body = request.getInputStream().readNBytes(readLimit);
            if (body.length > maxSignedBodyBytes) {
                throw new VerificationException(FailureReason.BODY_TOO_LARGE);
            }
            return body;
        } catch (IOException | ArithmeticException ex) {
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }
    }

    private void claimNonce(String nonceScope, String nonce, Duration replayTtl) {
        String keyMaterial = nonceScope + "\n" + nonce;
        String key = NONCE_KEY_PREFIX
                + InternalSignatureUtils.sha256Hex(keyMaterial.getBytes(StandardCharsets.UTF_8));
        try {
            Boolean inserted = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", replayTtl);
            if (!Boolean.TRUE.equals(inserted)) {
                throw new VerificationException(FailureReason.REPLAY_DETECTED);
            }
        } catch (VerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VerificationException(FailureReason.REPLAY_STORE_UNAVAILABLE);
        }
    }

    public enum FailureReason {
        INVALID_SIGNATURE,
        REPLAY_DETECTED,
        REPLAY_STORE_UNAVAILABLE,
        BODY_TOO_LARGE
    }

    public static final class VerificationException extends RuntimeException {

        private final FailureReason reason;

        public VerificationException(FailureReason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public FailureReason reason() {
            return reason;
        }
    }

    private record TimestampWindow(Duration replayTtl) {
    }

    private static final class RepeatableBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        private RepeatableBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new RepeatableServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = StringUtils.hasText(encoding)
                    ? Charset.forName(encoding)
                    : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class RepeatableServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private RepeatableServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return input.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener == null) {
                throw new IllegalArgumentException("readListener must not be null");
            }
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
            } catch (IOException ex) {
                readListener.onError(ex);
            }
        }
    }
}
