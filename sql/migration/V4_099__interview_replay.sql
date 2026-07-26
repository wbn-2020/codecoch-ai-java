-- V12 block C: same-configuration interview replay requests.
-- A replay clones the source session's full creation config (including the scenario binding,
-- which the remediation flow historically dropped) so the new round stays rubric-comparable.
-- Forward-only and idempotent. The CREATE TABLE statement is atomic in MySQL.

SET @v4_099_schema_name = DATABASE();

SET @v4_099_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_099_schema_name
           AND table_name = 'interview_replay'
    ),
    'CREATE TABLE `interview_replay` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `user_id` BIGINT NOT NULL,
       `source_session_id` BIGINT NOT NULL,
       `source_report_id` BIGINT NOT NULL,
       `target_session_id` BIGINT DEFAULT NULL,
       `target_job_id` BIGINT DEFAULT NULL,
       `scenario_version_id` BIGINT DEFAULT NULL,
       `rubric_version` VARCHAR(64) DEFAULT NULL,
       `status` VARCHAR(32) NOT NULL DEFAULT ''CREATING'',
       `idempotency_key` VARCHAR(64) NOT NULL,
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
       `deleted` TINYINT NOT NULL DEFAULT 0,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_interview_replay_user_token` (`user_id`, `idempotency_key`),
       UNIQUE KEY `uk_interview_replay_target_session` (`target_session_id`),
       KEY `idx_interview_replay_source` (`user_id`, `source_session_id`, `deleted`, `created_at`, `id`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V12 same-configuration interview replay requests''',
    'SELECT 1'
);
PREPARE v4_099_stmt FROM @v4_099_sql;
EXECUTE v4_099_stmt;
DEALLOCATE PREPARE v4_099_stmt;
