package com.codecoachai.resume.feign.dto;

import lombok.Data;

@Data
public class NotificationResolveByBizDTO {
    private Long userId;
    private String type;
    private String bizType;
    private String bizId;
    private String reason;
}
