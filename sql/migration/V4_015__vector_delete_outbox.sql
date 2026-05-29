-- V4_015: Durable retry queue for vector point deletes.

CREATE TABLE IF NOT EXISTS `vector_delete_outbox` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `collection_name` VARCHAR(128) NOT NULL COMMENT 'Vector collection name',
  `point_id` VARCHAR(128) NOT NULL COMMENT 'Vector point id to delete',
  `biz_type` VARCHAR(64) DEFAULT NULL COMMENT 'Source business object type',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Delete status: PENDING, DONE, FAILED',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'Delete retry count',
  `last_error` VARCHAR(512) DEFAULT NULL COMMENT 'Last delete error',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  UNIQUE KEY `uk_vector_delete_point` (`collection_name`, `point_id`),
  KEY `idx_vector_delete_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vector point delete outbox';
