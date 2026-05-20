package com.codecoachai.common.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历解析任务负载（生产方/消费方共用）。
 * Topic: codecoachai-resume  Tag: parse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParsePayload {

    /** 简历 ID */
    private Long resumeId;

    /** 关联的文件 ID */
    private Long fileId;

    /** OSS Key（或本地相对路径） */
    private String ossKey;

    /** 文件 MIME 类型 */
    private String mimeType;

    /** 触发用户 ID */
    private Long userId;

    /** 解析模式（默认 deep） */
    private String mode;
}
