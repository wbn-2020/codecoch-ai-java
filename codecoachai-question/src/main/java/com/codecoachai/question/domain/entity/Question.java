package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question")
public class Question extends BaseEntity {

    private String title;
    private String content;
    private String referenceAnswer;
    private String analysis;
    private Long categoryId;
    private Long groupId;
    private String difficulty;
    private String questionType;
    private String experienceLevel;
    private Integer isHighFrequency;
    private Integer status;
    /** 归一化标题（用于去重检测） */
    private String normalizedTitle;
    /** 审核状态：PENDING / APPROVED / REJECTED */
    private String auditStatus;
    /** 来源类型：MANUAL / AI_GENERATED / IMPORT */
    private String sourceType;
    /** 是否推荐 */
    private Integer isRecommended;
}
