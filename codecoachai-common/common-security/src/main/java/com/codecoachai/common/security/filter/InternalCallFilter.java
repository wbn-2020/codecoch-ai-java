package com.codecoachai.common.security.filter;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import com.codecoachai.common.security.internal.TrustedRequestVerifier;
import com.codecoachai.common.security.internal.TrustedRequestVerifier.FailureReason;
import com.codecoachai.common.security.internal.TrustedRequestVerifier.VerificationException;
import com.codecoachai.common.security.internal.TrustedServiceNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalCallFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalCallFilter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InternalAuthProperties internalAuthProperties;
    private final TrustedRequestVerifier trustedRequestVerifier;

    public InternalCallFilter(
            InternalAuthProperties internalAuthProperties,
            TrustedRequestVerifier trustedRequestVerifier) {
        this.internalAuthProperties = internalAuthProperties;
        this.trustedRequestVerifier = trustedRequestVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = normalizeRequestPath(request);
        if (!isInternalPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String internalCall = request.getHeader(HeaderConstants.INTERNAL_CALL);
        String serviceName = request.getHeader(HeaderConstants.SERVICE_NAME);
        if (!"true".equalsIgnoreCase(internalCall)) {
            log.warn("Reject internal request: invalid internal flag, path={}, serviceName={}", path, serviceName);
            writeForbidden(response);
            return;
        }
        if (!StringUtils.hasText(serviceName)) {
            log.warn("Reject internal request: missing service name, path={}", path);
            writeForbidden(response);
            return;
        }
        if (!TrustedServiceNames.contains(serviceName)) {
            log.warn("Reject internal request: service not allowed, path={}, serviceName={}", path, serviceName);
            writeForbidden(response);
            return;
        }

        if (!internalAuthProperties.isEnabled()) {
            log.warn("Reject internal request: internal auth is disabled, path={}, serviceName={}", path, serviceName);
            writeForbidden(response);
            return;
        }

        try {
            HttpServletRequest verifiedRequest = verifySignature(request, path, serviceName);
            filterChain.doFilter(verifiedRequest, response);
        } catch (VerificationException ex) {
            log.warn("Reject internal request: verification failed, path={}, serviceName={}, reason={}",
                    path, serviceName, ex.reason());
            if (ex.reason() == FailureReason.REPLAY_STORE_UNAVAILABLE) {
                writeUnavailable(response);
            } else {
                writeForbidden(response);
            }
        }
    }

    private HttpServletRequest verifySignature(HttpServletRequest request, String path, String serviceName) {
        if (!StringUtils.hasText(internalAuthProperties.getSecret())) {
            log.warn("Reject internal request: internal secret not configured, path={}, serviceName={}", path,
                    serviceName);
            throw new VerificationException(FailureReason.INVALID_SIGNATURE);
        }

        String timestamp = request.getHeader(HeaderConstants.INTERNAL_TIMESTAMP);
        String nonce = request.getHeader(HeaderConstants.INTERNAL_NONCE);
        String signature = request.getHeader(HeaderConstants.INTERNAL_SIGNATURE_V2);
        String bodySha256 = request.getHeader(HeaderConstants.INTERNAL_BODY_SHA256);
        String payload = InternalSignatureUtils.internalRequestPayloadV2(
                request.getMethod(),
                path,
                request.getQueryString(),
                timestamp,
                nonce,
                serviceName,
                bodySha256);
        return trustedRequestVerifier.verify(
                request,
                timestamp,
                nonce,
                signature,
                "internal-request:" + serviceName,
                payload,
                bodySha256,
                TrustedRequestVerifier.isMultipartRequest(request));
    }

    private String normalizeRequestPath(HttpServletRequest request) {
        return InternalSignatureUtils.normalizeRequestPath(request.getRequestURI(), request.getContextPath());
    }

    private boolean isInternalPath(String path) {
        return "/inner".equals(path) || path.startsWith("/inner/");
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.FORBIDDEN)));
    }

    private void writeUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(50300, "内部认证暂不可用，请稍后重试")));
    }
}
