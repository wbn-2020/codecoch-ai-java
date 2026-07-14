package com.codecoachai.common.web.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsDomainConflictsToHttp409WithDistinctBusinessCodes() {
        assertBusinessResponse(ErrorCode.STALE_SOURCE_VERSION, HttpStatus.CONFLICT);
        assertBusinessResponse(ErrorCode.RESOURCE_RELATION_CONFLICT, HttpStatus.CONFLICT);
    }

    @Test
    void mapsSemanticValidationAndNotFoundToTheirHttpStatuses() {
        assertBusinessResponse(ErrorCode.SEMANTIC_VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY);
        assertBusinessResponse(ErrorCode.SNAPSHOT_NOT_COMPARABLE, HttpStatus.UNPROCESSABLE_ENTITY);
        assertBusinessResponse(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void keepsLegacyTooManyRequestsHttpContract() {
        assertBusinessResponse(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.OK);
    }

    @Test
    void mapsResumeUploadBusyToHttp429() {
        assertBusinessResponse(ErrorCode.RESUME_UPLOAD_BUSY, HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void mapsInterruptedUploadAdmissionToHttp503() {
        assertBusinessResponse(ErrorCode.UPLOAD_INTERRUPTED, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void restoresDomainHttpStatusWhenBusinessExceptionWasRebuiltFromIntegerCode() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.STALE_SOURCE_VERSION.getCode(), "stale"));

        assertErrorResponse(response, ErrorCode.STALE_SOURCE_VERSION, HttpStatus.CONFLICT);
    }

    @Test
    void keepsLegacyBusinessParameterErrorsInTheResponseEnvelope() {
        assertNull(ErrorCode.PARAM_ERROR.getHttpStatus());
        assertBusinessResponse(ErrorCode.PARAM_ERROR, HttpStatus.OK);
    }

    @Test
    void declaresAuthorizationHttpStatusesInErrorCodeMetadata() {
        assertEquals(HttpStatus.UNAUTHORIZED.value(), ErrorCode.UNAUTHORIZED.getHttpStatus());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), ErrorCode.TOKEN_INVALID.getHttpStatus());
        assertEquals(HttpStatus.FORBIDDEN.value(), ErrorCode.FORBIDDEN.getHttpStatus());
    }

    @Test
    void mapsAuthorizationFailuresToTheirDeclaredHttpStatuses() {
        assertBusinessResponse(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        assertBusinessResponse(ErrorCode.TOKEN_INVALID, HttpStatus.UNAUTHORIZED);
        assertBusinessResponse(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    @Test
    void mapsUnsupportedHttpMethodTo405() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("PATCH", Set.of("GET", "POST"));

        ResponseEntity<Result<Void>> response = handler.handleMethodNotSupported(exception);
        assertErrorResponse(
                response,
                ErrorCode.PARAM_ERROR,
                HttpStatus.METHOD_NOT_ALLOWED);
        assertEquals(Set.of(HttpMethod.GET, HttpMethod.POST), response.getHeaders().getAllow());
    }

    @Test
    void mapsUnsupportedMediaTypeTo415() {
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML,
                List.of(MediaType.APPLICATION_JSON),
                HttpMethod.POST);

        assertErrorResponse(
                handler.handleMediaTypeNotSupported(exception),
                ErrorCode.PARAM_ERROR,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void mapsMethodArgumentTypeMismatchTo400() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "not-a-number",
                Long.class,
                "id",
                null,
                new NumberFormatException("not-a-number"));

        assertErrorResponse(
                handler.handleMethodArgumentTypeMismatch(exception),
                ErrorCode.PARAM_ERROR,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsMalformedOrMissingBodyTo400() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "malformed JSON",
                new MockHttpInputMessage("{".getBytes()));

        assertErrorResponse(
                handler.handleMessageNotReadable(exception),
                ErrorCode.PARAM_ERROR,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsMissingRequiredHeaderTo400() {
        ServletRequestBindingException exception =
                new ServletRequestBindingException("Missing request header X-Request-Id");

        assertErrorResponse(
                handler.handleRequestBinding(exception),
                ErrorCode.PARAM_ERROR,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void sanitizeMasksCommonSecretsAndPii() throws Exception {
        Method sanitize = GlobalExceptionHandler.class.getDeclaredMethod("sanitize", String.class);
        sanitize.setAccessible(true);

        String result = (String) sanitize.invoke(handler,
                "email=alice@example.com phone=13812345678 token=abc.def Authorization: Bearer sk-live-123 apiKey=secret-key password=p@ss");

        assertFalse(result.contains("alice@example.com"));
        assertFalse(result.contains("13812345678"));
        assertFalse(result.contains("abc.def"));
        assertFalse(result.contains("sk-live-123"));
        assertFalse(result.contains("secret-key"));
        assertFalse(result.contains("p@ss"));
        assertTrue(result.contains("***@***"));
        assertTrue(result.contains("1**********"));
    }

    private void assertBusinessResponse(ErrorCode errorCode, HttpStatus expectedStatus) {
        ResponseEntity<Result<Void>> response =
                handler.handleBusinessException(new BusinessException(errorCode));

        assertErrorResponse(response, errorCode, expectedStatus);
    }

    private void assertErrorResponse(
            ResponseEntity<Result<Void>> response,
            ErrorCode errorCode,
            HttpStatus expectedStatus) {
        assertEquals(expectedStatus, response.getStatusCode());
        assertEquals(errorCode.getCode(), response.getBody().getCode());
    }
}
