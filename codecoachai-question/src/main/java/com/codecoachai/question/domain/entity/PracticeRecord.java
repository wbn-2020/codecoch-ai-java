package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("practice_record")
public class PracticeRecord extends BaseEntity {

    private Long userId;
    private Long questionId;
    private String answerContent;
    private String reviewStatus;
    private Integer score;
    private String masteryStatus;
    private String aiComment;
    private String suggestions;
    private String knowledgePoints;
    private String referenceAnswer;
    private Long aiCallLogId;
    private String errorMessage;
}
