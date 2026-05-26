-- V4_007: slow SQL observation table and admin permission seed.
-- Idempotent: creates missing table and inserts missing sys_menu / ADMIN bindings only.

CREATE TABLE IF NOT EXISTS `slow_sql_log` (
  `id`                BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `mapper_id`         VARCHAR(255)          DEFAULT NULL COMMENT 'MyBatis mapper statement id',
  `sql_command_type`  VARCHAR(32)           DEFAULT NULL COMMENT 'SELECT / INSERT / UPDATE / DELETE',
  `sql_text`          MEDIUMTEXT            DEFAULT NULL COMMENT 'Normalized SQL with placeholders',
  `parameter_summary` VARCHAR(1000)         DEFAULT NULL COMMENT 'Non-sensitive parameter summary',
  `database_name`     VARCHAR(128)          DEFAULT NULL COMMENT 'MyBatis environment id',
  `cost_ms`           BIGINT(20)   NOT NULL                COMMENT 'Execution cost in milliseconds',
  `threshold_ms`      BIGINT(20)   NOT NULL                COMMENT 'Threshold when captured',
  `result_size`       INT                   DEFAULT NULL COMMENT 'Result list size or affected rows',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`           TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_slow_sql_created` (`created_at`),
  KEY `idx_slow_sql_cost` (`cost_ms`, `created_at`),
  KEY `idx_slow_sql_mapper` (`mapper_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='慢 SQL 执行日志';

SET @admin_root_id := (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id ASC LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Slow SQL Log', 'MENU', '/admin/slow-sql-logs', 'admin:audit:slow-sql-log', 205, 1, 1, 'Slow SQL observation log'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:audit:slow-sql-log' AND `deleted` = 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
JOIN `sys_menu` m ON m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.permission_code = 'admin:audit:slow-sql-log'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );
