-- V9 stage two: evidence usage result root and immutable result snapshots.
-- Forward-only and idempotent.

SET @v4_092_schema_name = DATABASE();

SET @v4_092_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_092_schema_name
           AND table_name = 'career_evidence_usage_result'
    ),
    'CREATE TABLE `career_evidence_usage_result` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `user_id` BIGINT NOT NULL,
       `usage_id` BIGINT NOT NULL,
       `application_id` BIGINT NOT NULL,
       `event_type` VARCHAR(40) NOT NULL,
       `event_id` BIGINT NULL,
       `event_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `current_snapshot_id` BIGINT NULL,
       `snapshot_version` INT NOT NULL DEFAULT 0,
       `status` VARCHAR(24) NOT NULL DEFAULT ''RECORDED'',
       `lock_version` INT NOT NULL DEFAULT 0,
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
       `deleted` TINYINT NOT NULL DEFAULT 0,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_evidence_usage_result_event` (`user_id`, `usage_id`, `event_key_hash`),
       KEY `idx_evidence_usage_result_usage` (`user_id`, `usage_id`, `status`, `updated_at`, `id`),
       KEY `idx_evidence_usage_result_application` (`user_id`, `application_id`, `status`, `updated_at`, `id`),
       KEY `idx_evidence_usage_result_snapshot` (`current_snapshot_id`, `deleted`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V9 evidence usage result root''',
    'SELECT 1'
);
PREPARE v4_092_stmt FROM @v4_092_sql;
EXECUTE v4_092_stmt;
DEALLOCATE PREPARE v4_092_stmt;

SET @v4_092_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_092_schema_name
           AND table_name = 'career_evidence_usage_result_snapshot'
    ),
    'CREATE TABLE `career_evidence_usage_result_snapshot` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `result_id` BIGINT NOT NULL,
       `user_id` BIGINT NOT NULL,
       `snapshot_version` INT NOT NULL,
       `status` VARCHAR(24) NOT NULL DEFAULT ''RECORDED'',
       `outcome_code` VARCHAR(32) NOT NULL DEFAULT ''UNKNOWN'',
       `known_facts_json` MEDIUMTEXT NOT NULL,
       `external_feedback_text` MEDIUMTEXT NULL,
       `user_interpretation_text` MEDIUMTEXT NULL,
       `unknowns_json` TEXT NOT NULL,
       `limits_json` TEXT NOT NULL,
       `source_type` VARCHAR(40) NOT NULL,
       `source_id` BIGINT NULL,
       `source_version` VARCHAR(64) NULL,
       `source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `occurred_at` DATETIME NULL,
       `confirmed_at` DATETIME NULL,
       `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `idempotency_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       `supersedes_snapshot_id` BIGINT NULL,
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_evidence_usage_result_snapshot_version` (`result_id`, `snapshot_version`),
       UNIQUE KEY `uk_evidence_usage_result_snapshot_idempotency` (`result_id`, `idempotency_key_hash`),
       KEY `idx_evidence_usage_result_snapshot_owner` (`user_id`, `result_id`, `created_at`, `id`),
       KEY `idx_evidence_usage_result_snapshot_cutoff` (`user_id`, `occurred_at`, `created_at`, `id`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V9 immutable evidence usage result snapshot''',
    'SELECT 1'
);
PREPARE v4_092_stmt FROM @v4_092_sql;
EXECUTE v4_092_stmt;
DEALLOCATE PREPARE v4_092_stmt;

SET @v4_092_sql = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_092_schema_name
           AND table_name = 'career_evidence_usage_result_snapshot'
    )
    AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @v4_092_schema_name
           AND table_name = 'career_evidence_usage_result_snapshot'
           AND column_name = 'status'
    ),
    'ALTER TABLE `career_evidence_usage_result_snapshot`
       ADD COLUMN `status` VARCHAR(24) NOT NULL DEFAULT ''RECORDED''
         AFTER `snapshot_version`',
    'SELECT 1'
);
PREPARE v4_092_stmt FROM @v4_092_sql;
EXECUTE v4_092_stmt;
DEALLOCATE PREPARE v4_092_stmt;
