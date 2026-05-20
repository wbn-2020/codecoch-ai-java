package com.codecoachai.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知表 notification。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    /** 接收用户 ID（0 表示系统公告所有人） */
    private Long userId;

    /** 通知类型：SYSTEM / TASK_DONE / TASK_FAILED / REVIEW_RESULT / SECURITY */
    private String type;

    private String title;

    private String content;

    /** 关联业务类型 */
    private String bizType;

    /** 关联业务 ID（点击跳转用） */
    private String bizId;

    /** 0=未读 1=已读 */
    private Integer readStatus;

    private LocalDateTime readAt;
}
