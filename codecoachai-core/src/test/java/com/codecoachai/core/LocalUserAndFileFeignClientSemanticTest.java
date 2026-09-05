package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.auth.domain.dto.InnerCreateUserDTO;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.core.local.LocalFileFeignClient;
import com.codecoachai.core.local.LocalResultMapper;
import com.codecoachai.core.local.LocalUserFeignClient;
import com.codecoachai.file.controller.InnerFileController;
import com.codecoachai.file.service.FileStorageService;
import com.codecoachai.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class LocalUserAndFileFeignClientSemanticTest {

    private final LocalResultMapper resultMapper = new LocalResultMapper(
            new ObjectMapper(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void userBusinessExceptionBecomesFailureResult() {
        UserService userService = mock(UserService.class);
        when(userService.getInnerUserByUsername("missing"))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND, "user missing"));
        LocalUserFeignClient client = new LocalUserFeignClient(userService, resultMapper);

        Result<?> result = client.getByUsername("missing");

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), result.getCode());
        assertEquals("user missing", result.getMessage());
    }

    @Test
    void createUserRunsTheControllersValidDtoContractBeforeServiceInvocation() {
        UserService userService = mock(UserService.class);
        LocalUserFeignClient client = new LocalUserFeignClient(userService, resultMapper);
        InnerCreateUserDTO dto = new InnerCreateUserDTO();
        dto.setUsername("bad");
        dto.setPasswordHash("");

        Result<?> result = client.createUser(dto);

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("username"));
        assertTrue(result.getMessage().contains("passwordHash"));
        verifyNoInteractions(userService);
    }

    @Test
    void missingRequiredUserParameterFailsBeforeServiceInvocation() {
        UserService userService = mock(UserService.class);
        LocalUserFeignClient client = new LocalUserFeignClient(userService, resultMapper);

        Result<?> result = client.getByUsername(null);

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), result.getCode());
        assertEquals("username不能为空", result.getMessage());
        verifyNoInteractions(userService);
    }

    @Test
    void fileBusinessExceptionBecomesFailureResult() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        InnerFileController controller = mock(InnerFileController.class);
        when(controller.detail(7L, 9L, "INTERVIEW"))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "file bizType mismatch"));
        LocalFileFeignClient client =
                new LocalFileFeignClient(fileStorageService, controller, resultMapper);

        Result<?> result = client.detail(7L, 9L, "INTERVIEW");

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), result.getCode());
        assertEquals("file bizType mismatch", result.getMessage());
    }

    @Test
    void missingRequiredUploadPartFailsBeforeServiceInvocation() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        InnerFileController controller = mock(InnerFileController.class);
        LocalFileFeignClient client =
                new LocalFileFeignClient(fileStorageService, controller, resultMapper);

        Result<?> result = client.upload(null, "RESUME", 9L);

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), result.getCode());
        assertEquals("file不能为空", result.getMessage());
        verifyNoInteractions(fileStorageService, controller);
    }

    @Test
    void downloadKeepsItsNonResultContractButStillChecksMvcParameters() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        InnerFileController controller = mock(InnerFileController.class);
        LocalFileFeignClient client =
                new LocalFileFeignClient(fileStorageService, controller, resultMapper);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> client.download(null, 9L, "RESUME"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("id不能为空", exception.getMessage());
        verifyNoInteractions(fileStorageService, controller);
    }
}
