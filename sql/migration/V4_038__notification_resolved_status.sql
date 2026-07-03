-- V4_038: distinguish notification read state from business resolved state.
-- Compatible migration: nullable/default columns and a non-unique lookup index.

SET @schema_name = DATABASE();

SET @notification_resolved_status_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'notification'
    AND column_name = 'resolved_status'
);
SET @sql = IF(
  @notification_resolved_status_column_exists = 0,
  'ALTER TABLE `notification` ADD COLUMN `resolved_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''Business resolved status: 0 unresolved, 1 resolved'' AFTER `read_at`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @notification_resolved_at_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'notification'
    AND column_name = 'resolved_at'
);
SET @sql = IF(
  @notification_resolved_at_column_exists = 0,
  'ALTER TABLE `notification` ADD COLUMN `resolved_at` DATETIME NULL COMMENT ''Business resolved time'' AFTER `resolved_status`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @notification_resolved_reason_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'notification'
    AND column_name = 'resolved_reason'
);
SET @sql = IF(
  @notification_resolved_reason_column_exists = 0,
  'ALTER TABLE `notification` ADD COLUMN `resolved_reason` VARCHAR(64) NULL COMMENT ''Business resolved reason'' AFTER `resolved_at`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_notification_user_type_biz_resolved_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'notification'
    AND index_name = 'idx_notification_user_type_biz_resolved'
);
SET @sql = IF(
  @idx_notification_user_type_biz_resolved_exists = 0,
  'ALTER TABLE `notification` ADD INDEX `idx_notification_user_type_biz_resolved` (`user_id`, `type`, `biz_type`, `biz_id`, `resolved_status`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
