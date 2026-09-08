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
    /** 间隔复习档位（天）：1/3/7/15 */
    private Integer reviewIntervalDays;
    /** 下次复习到期时间；wrong=1 时调度 */
    private LocalDateTime nextReviewAt;
    /** 已完成复习次数（决定档位下标） */
    private Integer reviewStage;
}
