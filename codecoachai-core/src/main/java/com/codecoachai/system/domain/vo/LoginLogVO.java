package com.codecoachai.system.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LoginLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String loginType;
    private String loginStatus;
    private String ip;
    private String ipMasked;
    private String maskedIp;
    private String userAgent;
    private String userAgentSummary;
    private String failReason;
    private String traceId;
    private String traceIdShort;
    private String shortTraceId;
    private LocalDateTime loginTime;
    private LocalDateTime createdAt;
}
