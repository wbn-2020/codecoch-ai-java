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
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDateTime nextReviewAt;
    /** 已完成复习次数（决定档位下标） */
    private Integer reviewStage;
    /** 记忆稳定性(天)，FSRS-lite；NULL=未初始化，调度走旧档位 fallback */
    private Double memoryStability;
    /** 记忆难度 1-10，FSRS-lite；NULL=未初始化 */
    private Double memoryDifficulty;
    /** 复习成功次数（FSRS-lite，可大于 4，不受旧档位上限约束） */
    private Integer reviewReps;
    /** 复习遗忘（答错）次数 */
    private Integer reviewLapses;
}
