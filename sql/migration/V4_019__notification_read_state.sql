CREATE TABLE IF NOT EXISTS notification_read (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notification_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_notification_read_user_notice (user_id, notification_id),
  KEY idx_notification_read_notice (notification_id),
  KEY idx_notification_read_user_read_at (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Broadcast notification per-user read state';
