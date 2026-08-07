package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AsrResult {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_UNAVAILABLE = "ASR_UNAVAILABLE";

    private String status;
    private String transcript;
    private BigDecimal confidence;
    private String language;
    private String provider;
    private String model;
    private String errorCode;
    private String errorMessage;
    private Boolean fallback;
    private String fallbackReason;
}
