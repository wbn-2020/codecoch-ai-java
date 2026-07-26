package com.codecoachai.common.security.filter;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.internal.TrustedRequestVerifier;
import com.codecoachai.common.security.internal.TrustedRequestVerifier.FailureReason;
import com.codecoachai.common.security.internal.TrustedRequestVerifier.VerificationException;
import com.codecoachai.common.security.internal.TrustedServiceNames;
import com.codecoachai.common.security.util.HeaderUserContextReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginUserContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginUserContextFilter.class);
    private static final String JSON_UTF8 = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";
    private static final String GATEWAY_SERVICE_NAME = "codecoachai-gateway";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrustedRequestVerifier trustedRequestVerifier;

    public LoginUserContextFilter(TrustedRequestVerifier trustedRequestVerifier) {
        this.trustedRequestVerifier = trustedRequestVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String userId = request.getHeader(HeaderConstants.USER_ID);
            if (!StringUtils.hasText(userId)) {
                LoginUserContext.setLoginUser(null);
                filterChain.doFilter(request, response);
                return;
            }

            String signer = request.getHeader(HeaderConstants.USER_CONTEXT_SIGNER);
            if (!TrustedServiceNames.contains(signer)) {
                throw new VerificationException(FailureReason.INVALID_SIGNATURE);
            }
            String timestamp = request.getHeader(HeaderConstants.USER_CONTEXT_TIMESTAMP);
            String nonce = request.getHeader(HeaderConstants.USER_CONTEXT_NONCE);
            String signature = request.getHeader(HeaderConstants.USER_CONTEXT_SIGNATURE_V2);
            String bodySha256 = request.getHeader(HeaderConstants.INTERNAL_BODY_SHA256);
            String path = InternalSignatureUtils.normalizeRequestPath(
                    request.getRequestURI(), request.getContextPath());
            String payload = InternalSignatureUtils.userContextPayloadV2(
                    request.getMethod(),
                    path,
                    request.getQueryString(),
                    timestamp,
                    nonce,
                    signer,
                    bodySha256,
                    userId,
                    request.getHeader(HeaderConstants.USERNAME),
                    request.getHeader(HeaderConstants.ROLES));
            boolean allowUnsignedStreamingBody = GATEWAY_SERVICE_NAME.equals(signer)
                    || TrustedRequestVerifier.isMultipartRequest(request);
            HttpServletRequest verifiedRequest = trustedRequestVerifier.verify(
                    request,
                    timestamp,
                    nonce,
                    signature,
                    "user-context:" + signer,
                    payload,
                    bodySha256,
                    allowUnsignedStreamingBody);

            LoginUserContext.setLoginUser(HeaderUserContextReader.readTrusted(verifiedRequest));
            filterChain.doFilter(verifiedRequest, response);
        } catch (VerificationException ex) {
            log.warn("Reject trusted user context: path={}, reason={}", request.getRequestURI(), ex.reason());
            if (ex.reason() == FailureReason.REPLAY_STORE_UNAVAILABLE) {
                writeUnavailable(response);
            } else {
                writeForbidden(response);
            }
        } catch (IllegalStateException ex) {
            log.warn("Reject trusted user context: path={}, reason=INVALID_CONTEXT", request.getRequestURI());
            writeForbidden(response);
        } finally {
            LoginUserContext.clear();
        }
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(JSON_UTF8);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.FORBIDDEN)));
    }

    private void writeUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(JSON_UTF8);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(50300, "内部认证暂不可用，请稍后重试")));
    }
}
