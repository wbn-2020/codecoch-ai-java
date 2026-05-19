package com.codecoachai.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("operation_log")
public class OperationLog {
    private Long id;
    private String traceId;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String method;
    private String requestUri;
    private String requestArgs;
    private String response;
    private String status;
    private String errorMsg;
    private String ip;
    private String userAgent;
    private Long costMs;
    private LocalDateTime createdAt;
}
