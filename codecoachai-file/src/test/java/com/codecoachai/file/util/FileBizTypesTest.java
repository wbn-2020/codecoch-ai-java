package com.codecoachai.file.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FileBizTypesTest {

    @Test
    void interviewVoiceAllowlistCannotBeExpandedByGlobalExtensions() {
        List<String> globalExtensions = List.of("png", "webm");

        assertFalse(FileBizTypes.isExtensionAllowed("INTERVIEW_VOICE", "png", globalExtensions));
        assertTrue(FileBizTypes.isExtensionAllowed("INTERVIEW_VOICE", "webm", globalExtensions));
        assertFalse(FileBizTypes.isExtensionAllowed("ATTACHMENT", "webm", globalExtensions));
    }
}
