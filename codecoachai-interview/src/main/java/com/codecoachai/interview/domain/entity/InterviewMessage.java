package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_message")
public class InterviewMessage extends BaseEntity {

    private Long sessionId;
    private Long stageId;
    private Long questionId;
    private Long questionGroupId;
    private String role;
    private String messageType;
    private String content;
    private Integer score;
    private String comment;
}
