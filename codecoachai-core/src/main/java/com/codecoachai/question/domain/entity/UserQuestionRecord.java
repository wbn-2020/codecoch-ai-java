package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_question_record")
public class UserQuestionRecord extends BaseEntity {

    private Long userId;
    private Long questionId;
    private String answerContent;
    private String masteryStatus;
    private Integer wrong;
    private Integer favorite;
    private LocalDateTime lastAnswerAt;
}
