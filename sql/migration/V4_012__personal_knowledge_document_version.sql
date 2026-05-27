-- V4_012: Personal knowledge document edit history.
-- Stores read-only snapshots before document content is replaced and chunks are rebuilt.

CREATE TABLE IF NOT EXISTS `personal_knowledge_document_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_id` BIGINT NOT NULL COMMENT 'Owner user id',
  `document_id` BIGINT NOT NULL COMMENT 'Knowledge document id',
  `version_no` INT NOT NULL COMMENT 'Monotonic version number per document',
  `title` VARCHAR(200) NOT NULL COMMENT 'Snapshot title',
  `document_type` VARCHAR(64) DEFAULT NULL COMMENT 'Snapshot document type',
  `content` MEDIUMTEXT COMMENT 'Snapshot content',
  `content_hash` CHAR(64) DEFAULT NULL COMMENT 'Snapshot content hash',
  `chunk_count` INT DEFAULT 0 COMMENT 'Chunk count before edit',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pkdv_document_version` (`document_id`, `version_no`),
  KEY `idx_pkdv_user_document` (`user_id`, `document_id`),
  KEY `idx_pkdv_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Personal knowledge document edit history';
