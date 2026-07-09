package com.codecoachai.interview.service.impl;

import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.interview.config.InterviewAsrProperties;
import com.codecoachai.interview.domain.dto.AsrRequest;
import com.codecoachai.interview.domain.vo.AsrResult;
import com.codecoachai.interview.feign.FileFeignClient;
import com.codecoachai.interview.feign.vo.InnerFileInfoVO;
import com.codecoachai.interview.service.AsrService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "codecoachai.interview.asr", name = "enabled", havingValue = "true")
public class HttpAsrService implements AsrService {

    private static final String ERROR_CODE_UNCONFIGURED = "ASR_PROVIDER_UNCONFIGURED";
    private static final String ERROR_CODE_DOWNLOAD_FAILED = "ASR_AUDIO_DOWNLOAD_FAILED";
    private static final String ERROR_CODE_AUDIO_TOO_LARGE = "ASR_AUDIO_TOO_LARGE";
    private static final String ERROR_CODE_PROVIDER_FAILED = "ASR_PROVIDER_FAILED";
    private static final String ERROR_CODE_EMPTY_TRANSCRIPT = "ASR_EMPTY_TRANSCRIPT";
    private static final String FALLBACK_REQUIRED = "ASR provider failed; manual transcript confirmation is required.";

    private final InterviewAsrProperties properties;
    private final FileFeignClient fileFeignClient;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    @Override
    public AsrResult transcribe(AsrRequest request) {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            return failed(ERROR_CODE_UNCONFIGURED,
                    "ASR endpoint is not configured; manual transcript confirmation is required.", null);
        }
        if (request == null || request.getFileId() == null || request.getUserId() == null) {
            return failed("ASR_REQUEST_INVALID",
                    "ASR request misses audio file identity; manual transcript confirmation is required.", null);
        }

        try {
            AudioPayload audio = loadAudio(request);
            ResponseEntity<String> response = callProvider(request, audio);
            return parseProviderResponse(response);
        } catch (AudioDownloadException ex) {
            log.warn("HTTP ASR audio download failed fileId={} traceId={} reason={}",
                    request.getFileId(), request.getTraceId(), safeReason(ex.getMessage(), "audio download failed"));
            return failed(ERROR_CODE_DOWNLOAD_FAILED, ex.getMessage(), null);
        } catch (AudioTooLargeException ex) {
            return failed(ERROR_CODE_AUDIO_TOO_LARGE, ex.getMessage(), null);
        } catch (RuntimeException | IOException ex) {
            log.warn("HTTP ASR provider invocation failed fileId={} traceId={} failureType={} reason={}",
                    request.getFileId(), request.getTraceId(), ex.getClass().getSimpleName(),
                    safeReason(ex.getMessage(), "provider invocation failed"));
            return failed(ERROR_CODE_PROVIDER_FAILED, FALLBACK_REQUIRED, ex.getMessage());
        }
    }

    private AudioPayload loadAudio(AsrRequest request) throws IOException {
        InnerFileInfoVO file;
        try {
            file = FeignResultUtils.unwrap(fileFeignClient.detail(
                    request.getFileId(), request.getUserId(), request.getBizType()));
        } catch (RuntimeException ex) {
            throw new AudioDownloadException("Audio file metadata query failed; manual transcript confirmation is required.", ex);
        }
        if (file == null) {
            throw new AudioDownloadException("Audio file is unavailable; manual transcript confirmation is required.");
        }
        long maxAudioBytes = Math.max(1L, properties.getMaxAudioBytes());
        if (file.getFileSize() != null && file.getFileSize() > maxAudioBytes) {
            throw new AudioTooLargeException("Audio file exceeds ASR size limit; manual transcript confirmation is required.");
        }

        ResponseEntity<Resource> response;
        try {
            response = fileFeignClient.download(request.getFileId(), request.getUserId(), request.getBizType());
        } catch (RuntimeException ex) {
            throw new AudioDownloadException("Audio file download failed; manual transcript confirmation is required.", ex);
        }
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new AudioDownloadException("Audio file download failed; manual transcript confirmation is required.");
        }
        byte[] bytes;
        try (InputStream inputStream = response.getBody().getInputStream()) {
            bytes = readLimited(inputStream, maxAudioBytes);
        } catch (IOException ex) {
            throw new AudioDownloadException("Audio file read failed; manual transcript confirmation is required.", ex);
        }
        if (bytes.length == 0) {
            throw new AudioDownloadException("Audio file is empty; manual transcript confirmation is required.");
        }
        return new AudioPayload(
                bytes,
                firstText(file.getOriginalFilename(), file.getStoredFilename(), "interview-audio-" + request.getFileId()),
                firstText(file.getMimeType(), request.getMimeType(), MediaType.APPLICATION_OCTET_STREAM_VALUE));
    }

    private ResponseEntity<String> callProvider(AsrRequest request, AudioPayload audio) {
        HttpHeaders headers = providerHeaders();
        HttpEntity<?> entity;
        if (InterviewAsrProperties.RequestMode.JSON.equals(properties.getRequestMode())) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            entity = new HttpEntity<>(jsonBody(request, audio), headers);
        } else {
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            entity = new HttpEntity<>(multipartBody(request, audio), headers);
        }
        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(normalizeTimeout(properties.getConnectTimeout(), Duration.ofSeconds(5)))
                .setReadTimeout(normalizeTimeout(properties.getReadTimeout(), Duration.ofSeconds(60)))
                .build();
        return restTemplate.exchange(URI.create(properties.getEndpoint()), HttpMethod.POST, entity, String.class);
    }

    private HttpHeaders providerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (properties.getHeaders() != null) {
            properties.getHeaders().forEach((name, value) -> {
                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                    headers.set(name.trim(), value.trim());
                }
            });
        }
        if (StringUtils.hasText(properties.getApiKey()) && StringUtils.hasText(properties.getAuthHeader())) {
            String credential = properties.getApiKey().trim();
            if (StringUtils.hasText(properties.getAuthScheme())) {
                credential = properties.getAuthScheme().trim() + " " + credential;
            }
            headers.set(properties.getAuthHeader().trim(), credential);
        }
        return headers;
    }

    private MultiValueMap<String, Object> multipartBody(AsrRequest request, AudioPayload audio) {
        InterviewAsrProperties.Multipart multipart = properties.getMultipart();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(nonBlank(multipart.getFileField(), "file"), audioResource(audio));
        addIfPresent(body, multipart.getLanguageField(), request.getLanguage());
        addIfPresent(body, multipart.getSceneField(), request.getScene());
        addIfPresent(body, multipart.getRequestIdField(), request.getRequestId());
        addIfPresent(body, multipart.getTraceIdField(), request.getTraceId());
        addIfPresent(body, multipart.getModelField(), properties.getModel());
        return body;
    }

    private Map<String, Object> jsonBody(AsrRequest request, AudioPayload audio) {
        InterviewAsrProperties.Json json = properties.getJson();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(nonBlank(json.getAudioField(), "audioBase64"), Base64.getEncoder().encodeToString(audio.bytes()));
        putIfPresent(body, json.getMimeTypeField(), audio.mimeType());
        putIfPresent(body, json.getLanguageField(), request.getLanguage());
        putIfPresent(body, json.getSceneField(), request.getScene());
        putIfPresent(body, json.getRequestIdField(), request.getRequestId());
        putIfPresent(body, json.getTraceIdField(), request.getTraceId());
        putIfPresent(body, json.getModelField(), properties.getModel());
        return body;
    }

    private ByteArrayResource audioResource(AudioPayload audio) {
        return new ByteArrayResource(audio.bytes()) {
            @Override
            public String getFilename() {
                return audio.filename();
            }
        };
    }

    private AsrResult parseProviderResponse(ResponseEntity<String> response) throws IOException {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            return failed(ERROR_CODE_PROVIDER_FAILED, FALLBACK_REQUIRED, "Non-2xx ASR provider response");
        }
        String body = response.getBody();
        if (!StringUtils.hasText(body)) {
            return failed(ERROR_CODE_EMPTY_TRANSCRIPT,
                    "ASR provider returned an empty response; manual transcript confirmation is required.", null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException ex) {
            String plainText = body.trim();
            if (StringUtils.hasText(plainText)) {
                return success(plainText, null, null, null);
            }
            throw ex;
        }

        String transcript = firstText(
                textAt(root, "transcript"),
                textAt(root, "text"),
                textAt(root, "result.transcript"),
                textAt(root, "result.text"),
                textAt(root, "data.transcript"),
                textAt(root, "data.text"));
        String providerStatus = firstText(textAt(root, "status"), textAt(root, "code"), textAt(root, "data.status"));
        String errorMessage = firstText(
                textAt(root, "errorMessage"),
                textAt(root, "message"),
                textAt(root, "error.message"),
                textAt(root, "data.errorMessage"));
        if (!StringUtils.hasText(transcript)) {
            return failed(ERROR_CODE_EMPTY_TRANSCRIPT,
                    firstText(errorMessage, "ASR provider returned no transcript; manual transcript confirmation is required."),
                    providerStatus);
        }
        return success(
                transcript.trim(),
                decimalAt(root, "confidence", "score", "result.confidence", "result.score", "data.confidence", "data.score"),
                firstText(textAt(root, "language"), textAt(root, "data.language")),
                firstText(textAt(root, "model"), textAt(root, "data.model"), properties.getModel()));
    }

    private AsrResult success(String transcript, BigDecimal confidence, String language, String model) {
        return AsrResult.builder()
                .status(AsrResult.STATUS_SUCCESS)
                .transcript(transcript)
                .confidence(confidence)
                .language(language)
                .provider(properties.getProvider())
                .model(model)
                .fallback(Boolean.FALSE)
                .build();
    }

    private AsrResult failed(String errorCode, String fallbackReason, String detail) {
        return AsrResult.builder()
                .status(ERROR_CODE_UNCONFIGURED.equals(errorCode) ? AsrResult.STATUS_UNAVAILABLE : AsrResult.STATUS_FAILED)
                .provider(properties.getProvider())
                .model(properties.getModel())
                .errorCode(errorCode)
                .errorMessage(firstText(detail, fallbackReason))
                .fallback(Boolean.TRUE)
                .fallbackReason(fallbackReason)
                .build();
    }

    private static byte[] readLimited(InputStream inputStream, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new AudioTooLargeException("Audio file exceeds ASR size limit; manual transcript confirmation is required.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Duration normalizeTimeout(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private static void addIfPresent(MultiValueMap<String, Object> body, String key, String value) {
        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
            body.add(key.trim(), value.trim());
        }
    }

    private static void putIfPresent(Map<String, Object> body, String key, String value) {
        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
            body.put(key.trim(), value.trim());
        }
    }

    private static String textAt(JsonNode root, String path) {
        JsonNode node = root;
        for (String part : path.split("\\.")) {
            if (node == null || node.isMissingNode() || !node.has(part)) {
                return null;
            }
            node = node.get(part);
        }
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            String text = node.asText();
            return StringUtils.hasText(text) ? text : null;
        }
        return null;
    }

    private static BigDecimal decimalAt(JsonNode root, String... paths) {
        for (String path : paths) {
            String value = textAt(root, path);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                return new BigDecimal(value.trim());
            } catch (NumberFormatException ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static String firstText(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static String nonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String safeReason(String value, String fallback) {
        String reason = firstText(value, fallback);
        if (reason == null) {
            return fallback;
        }
        return reason.length() > 240 ? reason.substring(0, 240) : reason;
    }

    private record AudioPayload(byte[] bytes, String filename, String mimeType) {
    }

    private static class AudioTooLargeException extends RuntimeException {
        AudioTooLargeException(String message) {
            super(message);
        }
    }

    private static class AudioDownloadException extends RuntimeException {
        AudioDownloadException(String message) {
            super(message);
        }

        AudioDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
