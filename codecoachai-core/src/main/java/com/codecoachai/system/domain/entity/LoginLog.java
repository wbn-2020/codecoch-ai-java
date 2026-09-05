package com.codecoachai.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("login_log")
public class LoginLog {
    private Long id;
    private Long userId;
    private String username;
    private String loginType;
    private String loginStatus;
    private String ip;
    private String userAgent;
    private String failReason;
    private String traceId;
    private LocalDateTime loginTime;
    private LocalDateTime createdAt;
}
