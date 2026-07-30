-- V9 stage one: immutable evidence versions, application package snapshots, and usage facts.
-- Forward-only and idempotent.

SET @v4_091_schema_name = DATABASE();

SET @v4_091_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_091_schema_name
           AND table_name = 'project_evidence_version'
    ),
    'CREATE TABLE `project_evidence_version` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `project_evidence_id` BIGINT NOT NULL,
       `user_id` BIGINT NOT NULL,
       `version_no` INT NOT NULL,
       `snapshot_json` MEDIUMTEXT NOT NULL,
       `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `source_type` VARCHAR(32) NOT NULL DEFAULT ''MANUAL'',
       `source_id` BIGINT NULL,
       `confirmed_at` DATETIME NULL,
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       `deleted` TINYINT NOT NULL DEFAULT 0,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_project_evidence_version_no` (`project_evidence_id`, `version_no`),
       UNIQUE KEY `uk_project_evidence_version_content` (`project_evidence_id`, `content_hash`),
       KEY `idx_project_evidence_version_owner` (`user_id`, `project_evidence_id`, `created_at`, `id`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V9 immutable project evidence version''',
    'SELECT 1'
);
PREPARE v4_091_stmt FROM @v4_091_sql;
EXECUTE v4_091_stmt;
DEALLOCATE PREPARE v4_091_stmt;

SET @v4_091_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_091_schema_name
           AND table_name = 'job_application_package_snapshot'
    ),
    'CREATE TABLE `job_application_package_snapshot` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `package_id` BIGINT NOT NULL,
       `user_id` BIGINT NOT NULL,
       `snapshot_version` INT NOT NULL,
       `snapshot_json` MEDIUMTEXT NOT NULL,
       `checklist_json` MEDIUMTEXT NULL,
       `actions_json` MEDIUMTEXT NULL,
       `project_evidence_ids_json` TEXT NULL,
       `resume_version_id` BIGINT NULL,
       `match_report_id` BIGINT NULL,
       `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `captured_at` DATETIME NOT NULL,
       `capture_source` VARCHAR(32) NOT NULL DEFAULT ''SAVE'',
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       `deleted` TINYINT NOT NULL DEFAULT 0,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_application_package_snapshot_version` (`package_id`, `snapshot_version`),
       UNIQUE KEY `uk_application_package_snapshot_content` (`package_id`, `content_hash`),
       KEY `idx_application_package_snapshot_owner` (`user_id`, `package_id`, `captured_at`, `id`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V9 immutable application package snapshot''',
    'SELECT 1'
);
PREPARE v4_091_stmt FROM @v4_091_sql;
EXECUTE v4_091_stmt;
DEALLOCATE PREPARE v4_091_stmt;

SET @v4_091_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_091_schema_name
           AND table_name = 'career_evidence_usage'
    ),
    'CREATE TABLE `career_evidence_usage` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `user_id` BIGINT NOT NULL,
       `campaign_id` BIGINT NULL,
       `application_id` BIGINT NOT NULL,
       `target_job_id` BIGINT NULL,
       `asset_type` VARCHAR(48) NOT NULL,
       `asset_id` BIGINT NOT NULL,
       `asset_version` VARCHAR(64) NOT NULL,
       `package_snapshot_id` BIGINT NULL,
       `source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `usage_scene` VARCHAR(32) NOT NULL,
       `used_at` DATETIME NOT NULL,
       `hypothesis_id` BIGINT NULL,
       `variant_id` BIGINT NULL,
       `assignment_id` BIGINT NULL,
       `usage_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `idempotency_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `status` VARCHAR(24) NOT NULL DEFAULT ''CAPTURED'',
       `stale` TINYINT NOT NULL DEFAULT 0,
       `stale_reason` VARCHAR(500) NULL,
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
       `deleted` TINYINT NOT NULL DEFAULT 0,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_career_evidence_usage_fact` (`user_id`, `usage_key_hash`),
       UNIQUE KEY `uk_career_evidence_usage_idempotency` (`user_id`, `idempotency_key_hash`),
       KEY `idx_career_evidence_usage_application` (`user_id`, `application_id`, `status`, `used_at`, `id`),
       KEY `idx_career_evidence_usage_campaign` (`user_id`, `campaign_id`, `used_at`, `id`),
       KEY `idx_career_evidence_usage_asset` (`user_id`, `asset_type`, `asset_id`, `asset_version`, `id`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V9 immutable evidence usage fact''',
    'SELECT 1'
);
PREPARE v4_091_stmt FROM @v4_091_sql;
EXECUTE v4_091_stmt;
DEALLOCATE PREPARE v4_091_stmt;

SET @v4_091_sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_091_schema_name
           AND table_name = 'job_application_package'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @v4_091_schema_name
           AND table_name = 'job_application_package'
           AND column_name = 'current_snapshot_id'
    ),
    'ALTER TABLE `job_application_package`
       ADD COLUMN `current_snapshot_id` BIGINT NULL AFTER `snapshot_version`',
    'SELECT 1'
);
PREPARE v4_091_stmt FROM @v4_091_sql;
EXECUTE v4_091_stmt;
DEALLOCATE PREPARE v4_091_stmt;

SET @v4_091_sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_091_schema_name
           AND table_name = 'job_application_package'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = @v4_091_schema_name
           AND table_name = 'job_application_package'
           AND index_name = 'idx_job_application_package_current_snapshot'
    ),
    'ALTER TABLE `job_application_package`
       ADD KEY `idx_job_application_package_current_snapshot` (`current_snapshot_id`, `deleted`)',
    'SELECT 1'
);
PREPARE v4_091_stmt FROM @v4_091_sql;
EXECUTE v4_091_stmt;
DEALLOCATE PREPARE v4_091_stmt;
