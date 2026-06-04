package com.codecoachai.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_result_feedback")
public class AiResultFeedback extends BaseEntity {

    private Long userId;
    private String scene;
    private String bizType;
    private Long bizId;
    private Long aiCallLogId;
    private String feedbackType;
    private Integer rating;
    private String comment;
    private String pagePath;
}
