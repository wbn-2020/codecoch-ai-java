-- V8 stage seven: owner-scoped career campaign archive export records.
-- Forward-only and idempotent. The CREATE TABLE statement is atomic in MySQL.

SET @v4_090_schema_name = DATABASE();

SET @v4_090_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_090_schema_name
           AND table_name = 'career_campaign_archive_export'
    ),
    'CREATE TABLE `career_campaign_archive_export` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `user_id` BIGINT NOT NULL,
       `campaign_id` BIGINT NOT NULL,
       `data_cutoff_at` DATETIME NOT NULL,
       `export_format` VARCHAR(16) NOT NULL DEFAULT ''ZIP'',
       `status` VARCHAR(24) NOT NULL DEFAULT ''GENERATING'',
       `source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `manifest_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
       `file_id` BIGINT DEFAULT NULL,
       `file_size` BIGINT DEFAULT NULL,
       `error_code` VARCHAR(64) DEFAULT NULL,
       `error_message` VARCHAR(1000) DEFAULT NULL,
       `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
       `deleted` TINYINT NOT NULL DEFAULT 0,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_campaign_archive_export_source` (
         `user_id`, `campaign_id`, `data_cutoff_at`, `export_format`, `source_hash`
       ),
       UNIQUE KEY `uk_campaign_archive_export_idempotency` (
         `user_id`, `idempotency_key_hash`
       ),
       KEY `idx_campaign_archive_export_campaign` (
         `user_id`, `campaign_id`, `deleted`, `created_at`, `id`
       ),
       KEY `idx_campaign_archive_export_status` (
         `user_id`, `status`, `deleted`, `updated_at`, `id`
       )
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V8 career campaign archive export''',
    'SELECT 1'
);
PREPARE v4_090_stmt FROM @v4_090_sql;
EXECUTE v4_090_stmt;
DEALLOCATE PREPARE v4_090_stmt;
