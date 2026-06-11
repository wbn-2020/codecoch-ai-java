-- V4_025: notification delivery status for admin operations dashboard.
-- Idempotent: only adds missing columns and indexes.

SET @db_name := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = @db_name AND table_name = 'notification' AND column_name = 'send_status'
    ),
    'SELECT 1',
    'ALTER TABLE `notification` ADD COLUMN `send_status` VARCHAR(32) NOT NULL DEFAULT ''SUCCESS'' COMMENT ''SUCCESS / FAILED / UNKNOWN'' AFTER `read_at`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = @db_name AND table_name = 'notification' AND column_name = 'send_error'
    ),
    'SELECT 1',
    'ALTER TABLE `notification` ADD COLUMN `send_error` VARCHAR(1000) DEFAULT NULL COMMENT ''Notification delivery failure reason'' AFTER `send_status`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = @db_name AND table_name = 'notification' AND column_name = 'sent_at'
    ),
    'SELECT 1',
    'ALTER TABLE `notification` ADD COLUMN `sent_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''Notification write/delivery time'' AFTER `send_error`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = @db_name AND table_name = 'notification' AND index_name = 'idx_notification_send_status'
    ),
    'SELECT 1',
    'ALTER TABLE `notification` ADD KEY `idx_notification_send_status` (`send_status`, `created_at`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
