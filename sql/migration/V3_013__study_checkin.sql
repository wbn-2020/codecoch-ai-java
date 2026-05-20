-- ============================================================
-- V3_013: 学习打卡表
-- ============================================================

CREATE TABLE IF NOT EXISTS `study_checkin` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `plan_id` BIGINT DEFAULT NULL COMMENT '关联学习计划',
    `checkin_date` DATE NOT NULL COMMENT '打卡日期',
    `completed_tasks` INT NOT NULL DEFAULT 0 COMMENT '当日完成任务数',
    `study_minutes` INT NOT NULL DEFAULT 0 COMMENT '学习时长（分钟）',
    `note` VARCHAR(255) DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `checkin_date`),
    KEY `idx_checkin_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习打卡记录';
