package com.codecoachai.file.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codecoachai.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileUploadValidatorTest {

    @Test
    void rejectsPngContentDisguisedAsWebm() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recording.webm",
                "audio/webm",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        assertThrows(BusinessException.class,
                () -> FileUploadValidator.validateContent(file, "INTERVIEW_VOICE", "webm"));
    }

    @Test
    void rejectsGenericMimeForInterviewVoice() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recording.webm",
                "application/octet-stream",
                new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});

        assertThrows(BusinessException.class,
                () -> FileUploadValidator.validateContent(file, "INTERVIEW_VOICE", "webm"));
    }

    @Test
    void acceptsMatchingVoiceMimeAndHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recording.webm",
                "audio/webm;codecs=opus",
                new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});

        assertDoesNotThrow(() -> FileUploadValidator.validateContent(file, "INTERVIEW_VOICE", "webm"));
    }
}
