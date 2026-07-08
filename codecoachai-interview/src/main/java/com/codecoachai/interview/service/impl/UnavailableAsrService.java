package com.codecoachai.interview.service.impl;

import com.codecoachai.interview.domain.dto.AsrRequest;
import com.codecoachai.interview.domain.vo.AsrResult;
import com.codecoachai.interview.service.AsrService;
import org.springframework.stereotype.Service;

@Service
public class UnavailableAsrService implements AsrService {

    private static final String PROVIDER = "UNCONFIGURED";
    private static final String ERROR_CODE = "ASR_UNAVAILABLE";
    private static final String FALLBACK_REASON = "ASR provider is not configured; manual transcript confirmation is required.";

    @Override
    public AsrResult transcribe(AsrRequest request) {
        return AsrResult.builder()
                .status(AsrResult.STATUS_UNAVAILABLE)
                .provider(PROVIDER)
                .errorCode(ERROR_CODE)
                .errorMessage(FALLBACK_REASON)
                .fallback(Boolean.TRUE)
                .fallbackReason(FALLBACK_REASON)
                .build();
    }
}
