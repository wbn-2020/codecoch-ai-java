package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.config.InterviewAsrProperties;
import com.codecoachai.interview.domain.dto.AsrRequest;
import com.codecoachai.interview.domain.vo.AsrResult;
import com.codecoachai.interview.feign.FileFeignClient;
import com.codecoachai.interview.feign.vo.InnerFileInfoVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class HttpAsrServiceTest {

    @Mock
    private FileFeignClient fileFeignClient;
    @Mock
    private RestTemplateBuilder restTemplateBuilder;
    @Mock
    private RestTemplate restTemplate;

    private InterviewAsrProperties properties;
    private HttpAsrService service;

    @BeforeEach
    void setUp() {
        properties = new InterviewAsrProperties();
        properties.setEndpoint("https://asr.example.test/transcribe");
        properties.setMaxAudioBytes(1024L);
        properties.setMaxAudioDuration(Duration.ofMinutes(2));
        lenient().when(restTemplateBuilder.connectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        lenient().when(restTemplateBuilder.readTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        lenient().when(restTemplateBuilder.build()).thenReturn(restTemplate);
        service = new HttpAsrService(
                properties, fileFeignClient, new ObjectMapper(), restTemplateBuilder);
    }

    @Test
    void multipartUsesStreamingResourceInsteadOfByteArrayResource() {
        byte[] webm = new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4};
        InnerFileInfoVO file = InterviewVoiceAudioValidatorTest.audioFile("webm", "audio/webm", webm.length);
        when(fileFeignClient.detail(9L, 7L, "INTERVIEW_VOICE")).thenReturn(Result.success(file));
        when(fileFeignClient.download(9L, 7L, "INTERVIEW_VOICE"))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource(webm)));
        when(restTemplate.exchange(
                any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"text\":\"hello\"}"));

        AsrResult result = service.transcribe(request());

        assertEquals(AsrResult.STATUS_SUCCESS, result.getStatus());
        ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                any(URI.class), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        MultiValueMap<String, Object> body =
                (MultiValueMap<String, Object>) entityCaptor.getValue().getBody();
        Object audioPart = body.getFirst("file");
        assertInstanceOf(InputStreamResource.class, audioPart);
    }

    @Test
    void jsonBase64UsesIndependentSmallerLimitBeforeDownload() {
        properties.setRequestMode(InterviewAsrProperties.RequestMode.JSON);
        properties.getJson().setMaxAudioBytes(4L);
        InnerFileInfoVO file = InterviewVoiceAudioValidatorTest.audioFile("webm", "audio/webm", 8L);
        when(fileFeignClient.detail(9L, 7L, "INTERVIEW_VOICE")).thenReturn(Result.success(file));

        AsrResult result = service.transcribe(request());

        assertEquals("ASR_AUDIO_TOO_LARGE", result.getErrorCode(), result.getFallbackReason());
        verify(fileFeignClient, never()).download(any(), any(), any());
    }

    private AsrRequest request() {
        return AsrRequest.builder()
                .userId(7L)
                .sessionId(3L)
                .voiceSubmissionId(5L)
                .fileId(9L)
                .bizType("INTERVIEW_VOICE")
                .mimeType("audio/webm")
                .audioDurationMs(10_000L)
                .language("zh-CN")
                .build();
    }
}
