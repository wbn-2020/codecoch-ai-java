package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class JobApplicationArchiveDTO {
    private Integer expectedLockVersion;
    private String idempotencyKey;
    private String reason;
}
