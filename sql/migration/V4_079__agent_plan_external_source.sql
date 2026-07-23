-- V7 stage 1: reuse the existing review-plan proposal and change-set chain
-- for weekly reports and interview preparation actions.
--
-- This migration deliberately does not create a second action-inbox state
-- machine. Every ALTER is guarded so a partially applied run can continue.

SET @v4_079_schema_name = DATABASE();

-- Existing daily-review rows remain valid. External proposals have no review
-- root, therefore these two legacy columns must be nullable.
SET @v4_079_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'review_id'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       MODIFY COLUMN `review_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'review_version'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       MODIFY COLUMN `review_version` INT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_suggestion')
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'source_type'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT ''DAILY_REVIEW''
         COMMENT ''DAILY_REVIEW/WEEKLY_REPORT/INTERVIEW_PREPARATION''',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_suggestion')
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'source_id'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       ADD COLUMN `source_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_suggestion')
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'source_version'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       ADD COLUMN `source_version` INT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_suggestion')
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'source_snapshot_hash'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       ADD COLUMN `source_snapshot_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_suggestion')
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'source_item_key'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       ADD COLUMN `source_item_key` VARCHAR(128) NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_suggestion')
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_suggestion'
          AND column_name = 'live_source_fingerprint'
    ),
    'ALTER TABLE `agent_review_plan_suggestion`
       ADD COLUMN `live_source_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `suggestion_fingerprint` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

UPDATE `agent_review_plan_suggestion`
SET `source_type` = 'DAILY_REVIEW'
WHERE `source_type` IS NULL OR `source_type` = '';

SET @v4_079_index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @v4_079_schema_name
      AND table_name = 'agent_review_plan_suggestion'
      AND index_name = 'uk_arps_live_source_fingerprint'
);
SET @v4_079_index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @v4_079_schema_name
      AND table_name = 'agent_review_plan_suggestion'
      AND index_name = 'uk_arps_live_source_fingerprint'
);
SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_suggestion'
              AND column_name = 'live_source_fingerprint')
    AND (
        @v4_079_index_columns IS NULL
        OR @v4_079_index_columns <> 'user_id,source_type,source_id,source_version,live_source_fingerprint'
        OR @v4_079_index_non_unique <> 0
    ),
    IF(
        @v4_079_index_columns IS NULL,
        'ALTER TABLE `agent_review_plan_suggestion`
           ADD UNIQUE KEY `uk_arps_live_source_fingerprint`
             (`user_id`, `source_type`, `source_id`, `source_version`, `live_source_fingerprint`)',
        'ALTER TABLE `agent_review_plan_suggestion`
           DROP INDEX `uk_arps_live_source_fingerprint`,
           ADD UNIQUE KEY `uk_arps_live_source_fingerprint`
             (`user_id`, `source_type`, `source_id`, `source_version`, `live_source_fingerprint`)'
    ),
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

-- Change sets are no longer required to have a daily-review root.
SET @v4_079_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_plan_change_set'
          AND column_name = 'review_id'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `agent_plan_change_set`
       MODIFY COLUMN `review_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_plan_change_set'
          AND column_name = 'review_version'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `agent_plan_change_set`
       MODIFY COLUMN `review_version` INT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_plan_change_set')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_plan_change_set'
                      AND column_name = 'source_type'),
    'ALTER TABLE `agent_plan_change_set`
       ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT ''DAILY_REVIEW''',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_plan_change_set')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_plan_change_set'
                      AND column_name = 'source_id'),
    'ALTER TABLE `agent_plan_change_set`
       ADD COLUMN `source_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_plan_change_set')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_plan_change_set'
                      AND column_name = 'source_version'),
    'ALTER TABLE `agent_plan_change_set`
       ADD COLUMN `source_version` INT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_plan_change_set')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_plan_change_set'
                      AND column_name = 'source_context_hash'),
    'ALTER TABLE `agent_plan_change_set`
       ADD COLUMN `source_context_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_plan_change_set')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_plan_change_set'
                      AND index_name = 'idx_apcs_user_source'),
    'ALTER TABLE `agent_plan_change_set`
       ADD KEY `idx_apcs_user_source`
         (`user_id`, `source_type`, `source_id`, `source_version`, `deleted`)',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

-- External items may not have a review suggestion row.
SET @v4_079_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_plan_change_item'
          AND column_name = 'suggestion_id'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `agent_plan_change_item`
       MODIFY COLUMN `suggestion_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_plan_change_item')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_plan_change_item'
                      AND column_name = 'source_item_key'),
    'ALTER TABLE `agent_plan_change_item`
       ADD COLUMN `source_item_key` VARCHAR(128) NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

-- Decision requests are also reused by external sources.
SET @v4_079_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @v4_079_schema_name
          AND table_name = 'agent_review_plan_decision_request'
          AND column_name = 'review_id'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `agent_review_plan_decision_request`
       MODIFY COLUMN `review_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_decision_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_review_plan_decision_request'
                      AND column_name = 'source_type'),
    'ALTER TABLE `agent_review_plan_decision_request`
       ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT ''DAILY_REVIEW''',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;

SET @v4_079_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_079_schema_name
              AND table_name = 'agent_review_plan_decision_request')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_079_schema_name
                      AND table_name = 'agent_review_plan_decision_request'
                      AND column_name = 'source_id'),
    'ALTER TABLE `agent_review_plan_decision_request`
       ADD COLUMN `source_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_079_stmt FROM @v4_079_sql;
EXECUTE v4_079_stmt;
DEALLOCATE PREPARE v4_079_stmt;
