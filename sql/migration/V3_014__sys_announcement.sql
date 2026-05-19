-- ============================================================
-- V3_014: 系统公告表
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_announcement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL COMMENT '公告标题',
    `content` TEXT NOT NULL COMMENT '公告内容',
    `type` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/URGENT/MAINTENANCE',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已下线',
    `target_users` VARCHAR(255) DEFAULT NULL COMMENT '目标用户（ALL/角色编码/用户ID列表）',
    `created_by` BIGINT DEFAULT NULL,
    `published_at` DATETIME DEFAULT NULL,
    `expired_at` DATETIME DEFAULT NULL COMMENT '过期时间',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ann_status` (`status`),
    KEY `idx_ann_published` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告';
