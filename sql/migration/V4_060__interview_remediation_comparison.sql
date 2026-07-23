SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_report'
                     AND column_name = 'rubric_version'),
    'ALTER TABLE `interview_report`
       ADD COLUMN `rubric_version` VARCHAR(64) DEFAULT ''INTERVIEW_RUBRIC_V1''
       COMMENT ''Stable scoring rubric version used by this report''
       AFTER `ability_profile_updates`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 'interview_report'
             AND column_name = 'rubric_version'),
    'UPDATE `interview_report`
        SET `rubric_version` = ''INTERVIEW_RUBRIC_V1''
      WHERE (`rubric_version` IS NULL OR `rubric_version` = '''')
        AND `status` = ''GENERATED''',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_session'
                     AND column_name = 'source_report_id'),
    'ALTER TABLE `interview_session`
       ADD COLUMN `source_report_id` BIGINT DEFAULT NULL
       COMMENT ''Source interview report for remediation practice''
       AFTER `training_context_summary`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_session'
                     AND column_name = 'source_requirement_ids'),
    'ALTER TABLE `interview_session`
       ADD COLUMN `source_requirement_ids` TEXT DEFAULT NULL
       COMMENT ''JSON requirement ids selected from the source report''
       AFTER `source_report_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_session'
                     AND column_name = 'practice_purpose'),
    'ALTER TABLE `interview_session`
       ADD COLUMN `practice_purpose` VARCHAR(500) DEFAULT NULL
       COMMENT ''User supplied remediation practice purpose''
       AFTER `source_requirement_ids`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_session'
                     AND column_name = 'remediation_strength'),
    'ALTER TABLE `interview_session`
       ADD COLUMN `remediation_strength` VARCHAR(16) DEFAULT NULL
       COMMENT ''NORMAL or STRONG remediation''
       AFTER `practice_purpose`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name AND table_name = 'interview_session'
             AND column_name = 'source_report_id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name AND table_name = 'interview_session'
                     AND index_name = 'idx_interview_session_source_report'),
    'ALTER TABLE `interview_session`
       ADD INDEX `idx_interview_session_source_report`
       (`user_id`, `source_report_id`, `deleted`, `created_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `interview_remediation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `source_report_id` BIGINT NOT NULL,
    `source_session_id` BIGINT NOT NULL,
    `target_session_id` BIGINT DEFAULT NULL,
    `target_job_id` BIGINT DEFAULT NULL,
    `source_requirement_ids` TEXT NOT NULL COMMENT 'Normalized JSON requirement ids',
    `practice_purpose` VARCHAR(500) NOT NULL,
    `remediation_strength` VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    `rubric_version` VARCHAR(64) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATING',
    `idempotency_key` VARCHAR(64) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_interview_remediation_user_token` (`user_id`, `idempotency_key`),
    UNIQUE KEY `uk_interview_remediation_target_session` (`target_session_id`),
    KEY `idx_interview_remediation_source` (`user_id`, `source_report_id`, `deleted`, `created_at`, `id`),
    KEY `idx_interview_remediation_target_job` (`user_id`, `target_job_id`, `deleted`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Interview report one-click remediation requests';

CREATE TABLE IF NOT EXISTS `interview_comparison` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `target_job_id` BIGINT DEFAULT NULL,
    `report_ids` TEXT NOT NULL COMMENT 'Normalized JSON report ids',
    `report_key` CHAR(64) NOT NULL COMMENT 'SHA-256 of normalized report ids',
    `rubric_version` VARCHAR(64) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL,
    `reason_codes` TEXT DEFAULT NULL COMMENT 'JSON unavailable reason codes',
    `result_json` LONGTEXT NOT NULL COMMENT 'Comparison result snapshot',
    `idempotency_key` VARCHAR(64) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_interview_comparison_user_token` (`user_id`, `idempotency_key`),
    KEY `idx_interview_comparison_user_created` (`user_id`, `deleted`, `created_at`, `id`),
    KEY `idx_interview_comparison_report_key` (`user_id`, `report_key`, `deleted`, `created_at`, `id`),
    KEY `idx_interview_comparison_target_job` (`user_id`, `target_job_id`, `deleted`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Same-job multi-round interview comparison snapshots';

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_comparison')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name AND table_name = 'interview_comparison'
                     AND index_name = 'idx_interview_comparison_user_created'),
    'ALTER TABLE `interview_comparison`
       ADD INDEX `idx_interview_comparison_user_created`
       (`user_id`, `deleted`, `created_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
