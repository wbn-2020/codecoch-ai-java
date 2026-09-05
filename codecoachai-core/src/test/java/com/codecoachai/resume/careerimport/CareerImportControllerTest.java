package com.codecoachai.resume.careerimport;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class CareerImportControllerTest {

    @Mock
    private CareerImportService careerImportService;
    @Mock
    private MultipartFile file;

    private CareerImportController controller;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("import-user").build());
        controller = new CareerImportController(careerImportService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsOversizedUploadBeforeReadingMultipartBytes() throws IOException {
        when(file.getSize()).thenReturn(2L * 1024 * 1024 + 1);
        when(file.isEmpty()).thenReturn(false);

        assertThrows(BusinessException.class, () ->
                controller.previewCsv(file, "Asia/Shanghai", null, null));

        verify(file, never()).getBytes();
    }

    @Test
    void rejectsImportWhenCurrentBytesDoNotMatchPreviewHash() throws IOException {
        byte[] content = "company_name,job_title\nAcme,Backend".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(file.getSize()).thenReturn((long) content.length);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(content);

        assertThrows(BusinessException.class, () ->
                controller.importCsv(
                        file,
                        "Asia/Shanghai",
                        "SKIP",
                        null,
                        CareerImportContentHash.sha256(content),
                        "0".repeat(64)));

        verify(careerImportService, never()).importCsv(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
