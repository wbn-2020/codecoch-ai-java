package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习打卡记录。每天每用户最多一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_checkin")
public class StudyCheckin extends BaseEntity {

    private Long userId;
    private Long planId;
    private LocalDate checkinDate;
    /** 当日完成任务数 */
    private Integer completedTasks;
    /** 当日学习时长（分钟） */
    private Integer studyMinutes;
    /** 备注 */
    private String note;
}
