package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.feign.vo.InnerFileInfoVO;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InterviewVoiceAudioValidatorTest {

    @Test
    void acceptsMatchingServerMetadataAndNormalizesMime() {
        InnerFileInfoVO file = audioFile("webm", "audio/webm;codecs=opus", 1024L);

        String mimeType = InterviewVoiceAudioValidator.validateFile(
                file, "audio/webm;codecs=opus", 2048L);

        assertEquals("audio/webm", mimeType);
    }

    @Test
    void rejectsImageMetadataEvenWhenBizTypeIsVoice() {
        InnerFileInfoVO file = audioFile("png", "image/png", 1024L);
        file.setOriginalFilename("recording.png");
        file.setStoredFilename("stored.png");

        assertThrows(BusinessException.class,
                () -> InterviewVoiceAudioValidator.validateFile(file, "image/png", 2048L));
    }

    @Test
    void rejectsDurationOverServerLimit() {
        assertThrows(BusinessException.class,
                () -> InterviewVoiceAudioValidator.validateDuration(120_001L, Duration.ofMinutes(2)));
    }

    static InnerFileInfoVO audioFile(String extension, String mimeType, long size) {
        InnerFileInfoVO file = new InnerFileInfoVO();
        file.setId(9L);
        file.setUserId(7L);
        file.setBizType("INTERVIEW_VOICE");
        file.setStatus("AVAILABLE");
        file.setOriginalFilename("recording." + extension);
        file.setStoredFilename("stored." + extension);
        file.setFileExt(extension);
        file.setMimeType(mimeType);
        file.setFileSize(size);
        return file;
    }
}
