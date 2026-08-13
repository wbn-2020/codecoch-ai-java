-- V4_108 recoverable application archive. This does not use the deleted flag and
-- therefore preserves events, materials, and other audit references.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
          AND column_name = 'archived_at'
    ),
    'ALTER TABLE `job_application`
       ADD COLUMN `archived_at` DATETIME NULL
       COMMENT ''Recoverable user archive timestamp; distinct from logical deletion''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
          AND column_name = 'archive_reason'
    ),
    'ALTER TABLE `job_application`
       ADD COLUMN `archive_reason` VARCHAR(500) NULL
       COMMENT ''Optional user-provided archive reason for audit''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
          AND index_name = 'idx_job_application_user_archived'
    ),
    'ALTER TABLE `job_application`
       ADD KEY `idx_job_application_user_archived`
         (`user_id`, `deleted`, `archived_at`, `updated_at`, `id`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
