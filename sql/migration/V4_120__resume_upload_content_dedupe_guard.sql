-- Add the persisted content hash and an atomic user-scoped upload guard.
-- The guard serializes same-content uploads before physical storage is written.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'file_info'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'file_info'
          AND column_name = 'content_sha256'
    ),
    'ALTER TABLE `file_info`
       ADD COLUMN `content_sha256`
         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
       COMMENT ''SHA-256 of the stored file content''
       AFTER `storage_path`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'file_info'
          AND column_name = 'content_sha256'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'file_info'
          AND index_name = 'idx_file_info_resume_content'
    ),
    'ALTER TABLE `file_info`
       ADD KEY `idx_file_info_resume_content`
         (`user_id`, `biz_type`, `content_sha256`, `status`, `deleted`, `id`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS resume_upload_dedupe_guard (
  user_id BIGINT NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  file_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, content_sha256),
  KEY idx_resume_upload_guard_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Atomic user-scoped guard for duplicate resume uploads';

INSERT INTO resume_upload_dedupe_guard (
  user_id, content_sha256, file_id, created_at, updated_at
)
SELECT
  existing.user_id,
  existing.content_sha256,
  MAX(existing.id),
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM file_info existing
WHERE existing.biz_type = 'RESUME'
  AND existing.content_sha256 IS NOT NULL
  AND existing.status = 'AVAILABLE'
  AND existing.deleted = 0
GROUP BY existing.user_id, existing.content_sha256
ON DUPLICATE KEY UPDATE
  file_id = VALUES(file_id),
  updated_at = CURRENT_TIMESTAMP;
