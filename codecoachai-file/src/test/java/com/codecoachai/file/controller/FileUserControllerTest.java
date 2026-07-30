package com.codecoachai.file.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.oss.service.StsTokenService;
import com.codecoachai.file.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

class FileUserControllerTest {

    @Test
    void uploadRejectsInternalArchiveBizType() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StsTokenService> stsTokenServiceProvider = mock(ObjectProvider.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        FileUserController controller = new FileUserController(stsTokenServiceProvider, fileStorageService);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "application-package.zip",
                "application/zip",
                new byte[] {0x50, 0x4B, 0x03, 0x04});

        assertThrows(BusinessException.class,
                () -> controller.upload(file, "APPLICATION_PACKAGE_ARCHIVE"));
        verifyNoInteractions(fileStorageService);
    }
}
