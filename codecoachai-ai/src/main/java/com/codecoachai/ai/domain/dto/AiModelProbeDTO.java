package com.codecoachai.ai.domain.dto;

import lombok.Data;

/**
 * 管理端模型测活请求。
 *
 * <p>测试语句只用于本次供应商调用，不会写入调用日志，避免管理员误填业务敏感内容后被持久化。</p>
 */
@Data
public class AiModelProbeDTO {

    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
    private String prompt;
}
