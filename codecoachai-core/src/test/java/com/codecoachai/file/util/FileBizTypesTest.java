package com.codecoachai.file.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
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

    @Test
    void generatedArchiveTypesOnlyAllowZipAndRemainInternalOnly() {
        List<String> globalExtensions = List.of("pdf", "zip");

        assertTrue(FileBizTypes.isExtensionAllowed("APPLICATION_PACKAGE_ARCHIVE", "zip", globalExtensions));
        assertTrue(FileBizTypes.isExtensionAllowed("CAREER_CAMPAIGN_ARCHIVE", "zip", globalExtensions));
        assertFalse(FileBizTypes.isExtensionAllowed("APPLICATION_PACKAGE_ARCHIVE", "pdf", globalExtensions));
        assertFalse(FileBizTypes.isExtensionAllowed("RESUME", "zip", globalExtensions));
        assertFalse(FileBizTypes.isExtensionAllowed("ATTACHMENT", "zip", globalExtensions));
        assertThrows(BusinessException.class,
                () -> FileBizTypes.requireUserAllowed("APPLICATION_PACKAGE_ARCHIVE"));
        assertThrows(BusinessException.class,
                () -> FileBizTypes.requireUserAllowed("CAREER_CAMPAIGN_ARCHIVE"));
    }
}
