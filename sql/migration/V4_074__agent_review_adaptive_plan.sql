-- Persist user-governed review suggestions and deterministic plan changes.

SET @schema_name = DATABASE();

-- agent_review compatibility fields
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_review'
                      AND column_name = 'target_scope_key'),
    'ALTER TABLE `agent_review`
       ADD COLUMN `target_scope_key` VARCHAR(64) NOT NULL DEFAULT ''ALL''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_review'
                      AND column_name = 'review_version'),
    'ALTER TABLE `agent_review`
       ADD COLUMN `review_version` INT NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_review'
                      AND column_name = 'source_snapshot_hash'),
    'ALTER TABLE `agent_review`
       ADD COLUMN `source_snapshot_hash` CHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_review')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'review_type')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'target_scope_key')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'deleted')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_review'
                      AND column_name = 'live_daily_scope_key'),
    'ALTER TABLE `agent_review`
       ADD COLUMN `live_daily_scope_key` VARCHAR(64)
         GENERATED ALWAYS AS (
           CASE
             WHEN `deleted` = 0 AND `review_type` = ''DAILY'' THEN `target_scope_key`
             ELSE NULL
           END
         ) STORED',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_review')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'target_job_id')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'target_scope_key'),
    'UPDATE `agent_review`
        SET `target_scope_key` = CASE
          WHEN `target_job_id` IS NULL THEN ''ALL''
          ELSE CONCAT(''JOB:'', `target_job_id`)
        END
      WHERE `target_scope_key` IS NULL
         OR `target_scope_key` = ''''
         OR (`target_scope_key` = ''ALL'' AND `target_job_id` IS NOT NULL)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Never discard conflicting historical reviews automatically. Stop the migration
-- and require an operator to compare duplicate rows and repair their references.
DROP TEMPORARY TABLE IF EXISTS `_v4_074_daily_review_guard`;
CREATE TEMPORARY TABLE `_v4_074_daily_review_guard` (
    `duplicate_group_count` BIGINT NOT NULL,
    CONSTRAINT `chk_v4_074_no_duplicate_daily_review`
        CHECK (`duplicate_group_count` = 0)
);
INSERT INTO `_v4_074_daily_review_guard` (`duplicate_group_count`)
SELECT COUNT(*)
FROM (
    SELECT `user_id`, `review_date`, `target_scope_key`
    FROM `agent_review`
    WHERE `deleted` = 0
      AND `review_type` = 'DAILY'
    GROUP BY `user_id`, `review_date`, `target_scope_key`
    HAVING COUNT(*) > 1
) duplicate_group;
DROP TEMPORARY TABLE `_v4_074_daily_review_guard`;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review'
      AND index_name = 'uk_agent_review_live_daily'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review'
      AND index_name = 'uk_agent_review_live_daily'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_review')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'user_id')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'review_date')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review'
                  AND column_name = 'live_daily_scope_key')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'user_id,review_date,live_daily_scope_key'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_review`
         ADD UNIQUE KEY `uk_agent_review_live_daily`
           (`user_id`, `review_date`, `live_daily_scope_key`)',
      'ALTER TABLE `agent_review`
         DROP INDEX `uk_agent_review_live_daily`,
         ADD UNIQUE KEY `uk_agent_review_live_daily`
           (`user_id`, `review_date`, `live_daily_scope_key`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Decision suggestions and persisted previews
CREATE TABLE IF NOT EXISTS `agent_review_plan_suggestion` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `review_id` BIGINT NOT NULL,
    `review_version` INT NOT NULL DEFAULT 1,
    `suggestion_key` VARCHAR(96) NOT NULL,
    `suggestion_fingerprint` CHAR(64) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `content` VARCHAR(1000) NOT NULL,
    `reason` VARCHAR(1000) NULL,
    `intent_type` VARCHAR(48) NOT NULL,
    `target_scope` VARCHAR(32) NOT NULL,
    `intent_json` TEXT NULL,
    `evidence_json` TEXT NULL,
    `confidence_level` VARCHAR(16) NOT NULL DEFAULT 'LOW',
    `fallback` TINYINT NOT NULL DEFAULT 0,
    `decision_status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    `decision_version` INT NOT NULL DEFAULT 1,
    `decided_at` DATETIME NULL,
    `ignored_reason` VARCHAR(500) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_arps_review_suggestion` (`review_id`, `suggestion_key`),
    KEY `idx_arps_user_review_status`
        (`user_id`, `review_id`, `decision_status`, `deleted`),
    KEY `idx_arps_user_fingerprint`
        (`user_id`, `suggestion_fingerprint`, `decision_status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-governed review plan suggestions';

CREATE TABLE IF NOT EXISTS `agent_review_plan_decision_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `review_id` BIGINT NOT NULL,
    `decision_request_key_hash` CHAR(64) NOT NULL,
    `decision_payload_hash` CHAR(64) NOT NULL,
    `request_id` VARCHAR(128) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_arpdr_user_request` (`user_id`, `decision_request_key_hash`),
    KEY `idx_arpdr_user_review` (`user_id`, `review_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Atomic idempotency records for review suggestion decisions';

CREATE TABLE IF NOT EXISTS `agent_plan_change_set` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `review_id` BIGINT NOT NULL,
    `review_version` INT NOT NULL DEFAULT 1,
    `target_job_id` BIGINT NULL,
    `target_scope_key` VARCHAR(64) NOT NULL DEFAULT 'ALL',
    `target_date` DATE NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PREVIEW_READY',
    `selection_hash` CHAR(64) NOT NULL,
    `source_snapshot_hash` CHAR(64) NULL,
    `base_daily_run_id` BIGINT NULL,
    `base_daily_status` VARCHAR(32) NULL,
    `base_daily_task_hash` CHAR(64) NULL,
    `base_week_plan_id` BIGINT NULL,
    `base_week_snapshot_version` INT NULL,
    `base_week_item_hash` CHAR(64) NULL,
    `preview_version` INT NOT NULL DEFAULT 1,
    `preview_hash` CHAR(64) NOT NULL,
    `preview_summary_json` TEXT NULL,
    `result_source` VARCHAR(24) NOT NULL DEFAULT 'RULE',
    `fallback` TINYINT NOT NULL DEFAULT 0,
    `preview_request_key_hash` CHAR(64) NOT NULL,
    `preview_payload_hash` CHAR(64) NOT NULL,
    `confirm_request_key_hash` CHAR(64) NULL,
    `confirm_payload_hash` CHAR(64) NULL,
    `lock_version` INT NOT NULL DEFAULT 1,
    `expires_at` DATETIME NOT NULL,
    `confirmed_at` DATETIME NULL,
    `applied_at` DATETIME NULL,
    `failure_code` VARCHAR(64) NULL,
    `failure_message` VARCHAR(500) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_apcs_preview_request`
        (`user_id`, `preview_request_key_hash`),
    UNIQUE KEY `uk_apcs_confirm_request`
        (`user_id`, `confirm_request_key_hash`),
    KEY `idx_apcs_user_review` (`user_id`, `review_id`, `deleted`, `created_at`),
    KEY `idx_apcs_user_date_status`
        (`user_id`, `target_date`, `status`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Review plan change preview and execution state';

CREATE TABLE IF NOT EXISTS `agent_plan_change_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `change_set_id` BIGINT NOT NULL,
    `suggestion_id` BIGINT NOT NULL,
    `item_key` VARCHAR(96) NOT NULL,
    `change_type` VARCHAR(48) NOT NULL,
    `target_date` DATE NOT NULL,
    `source_task_id` BIGINT NULL,
    `base_daily_run_id` BIGINT NULL,
    `before_json` TEXT NULL,
    `after_json` TEXT NULL,
    `daily_impact_json` TEXT NULL,
    `week_impact_json` TEXT NULL,
    `validation_status` VARCHAR(24) NOT NULL DEFAULT 'PASS',
    `warning_codes_json` TEXT NULL,
    `confidence_level` VARCHAR(16) NOT NULL DEFAULT 'LOW',
    `fallback` TINYINT NOT NULL DEFAULT 0,
    `apply_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `applied_run_id` BIGINT NULL,
    `applied_task_id` BIGINT NULL,
    `applied_week_plan_id` BIGINT NULL,
    `applied_week_plan_item_id` BIGINT NULL,
    `apply_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_apci_set_item` (`change_set_id`, `item_key`),
    KEY `idx_apci_user_set` (`user_id`, `change_set_id`, `deleted`),
    KEY `idx_apci_apply_status` (`user_id`, `apply_status`, `target_date`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Deterministic review-confirmed plan change items';

-- agent_task fields are deliberately checked one by one for partial-migration recovery.
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_task'
                      AND column_name = 'plan_change_item_id'),
    'ALTER TABLE `agent_task`
       ADD COLUMN `plan_change_item_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_task'
                      AND column_name = 'plan_origin_type'),
    'ALTER TABLE `agent_task`
       ADD COLUMN `plan_origin_type` VARCHAR(32) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_task'
                      AND column_name = 'plan_origin_id'),
    'ALTER TABLE `agent_task`
       ADD COLUMN `plan_origin_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_task'
                      AND column_name = 'user_confirmed'),
    'ALTER TABLE `agent_task`
       ADD COLUMN `user_confirmed` TINYINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_task'
                  AND column_name = 'deleted')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_task'
                  AND column_name = 'plan_change_item_id')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_task'
                      AND column_name = 'live_plan_change_item_id'),
    'ALTER TABLE `agent_task`
       ADD COLUMN `live_plan_change_item_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `plan_change_item_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review_plan_decision_request'
      AND index_name = 'uk_arpdr_user_request'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review_plan_decision_request'
      AND index_name = 'uk_arpdr_user_request'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_review_plan_decision_request'
              AND column_name = 'user_id')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name
                  AND table_name = 'agent_review_plan_decision_request'
                  AND column_name = 'decision_request_key_hash')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'user_id,decision_request_key_hash'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_review_plan_decision_request`
         ADD UNIQUE KEY `uk_arpdr_user_request`
           (`user_id`, `decision_request_key_hash`)',
      'ALTER TABLE `agent_review_plan_decision_request`
         DROP INDEX `uk_arpdr_user_request`,
         ADD UNIQUE KEY `uk_arpdr_user_request`
           (`user_id`, `decision_request_key_hash`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_task'
      AND index_name = 'uk_agent_task_live_plan_change_item'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_task'
      AND index_name = 'uk_agent_task_live_plan_change_item'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_task'
                  AND column_name = 'live_plan_change_item_id')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'live_plan_change_item_id'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_task`
         ADD UNIQUE KEY `uk_agent_task_live_plan_change_item`
           (`live_plan_change_item_id`)',
      'ALTER TABLE `agent_task`
         DROP INDEX `uk_agent_task_live_plan_change_item`,
         ADD UNIQUE KEY `uk_agent_task_live_plan_change_item`
           (`live_plan_change_item_id`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Strict audit idempotency keys
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_plan_adjustment')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_plan_adjustment'
                      AND column_name = 'event_key'),
    'ALTER TABLE `agent_plan_adjustment`
       ADD COLUMN `event_key` CHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_adjustment'
      AND index_name = 'uk_apa_event_key'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_adjustment'
      AND index_name = 'uk_apa_event_key'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_plan_adjustment')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_plan_adjustment'
                  AND column_name = 'event_key')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'event_key'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_plan_adjustment`
         ADD UNIQUE KEY `uk_apa_event_key` (`event_key`)',
      'ALTER TABLE `agent_plan_adjustment`
         DROP INDEX `uk_apa_event_key`,
         ADD UNIQUE KEY `uk_apa_event_key` (`event_key`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_plan_influence')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @schema_name AND table_name = 'agent_plan_influence'
                      AND column_name = 'reference_key'),
    'ALTER TABLE `agent_plan_influence`
       ADD COLUMN `reference_key` CHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_influence'
      AND index_name = 'uk_api_reference_key'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_influence'
      AND index_name = 'uk_api_reference_key'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @schema_name AND table_name = 'agent_plan_influence')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_plan_influence'
                  AND column_name = 'reference_key')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'reference_key'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_plan_influence`
         ADD UNIQUE KEY `uk_api_reference_key` (`reference_key`)',
      'ALTER TABLE `agent_plan_influence`
         DROP INDEX `uk_api_reference_key`,
         ADD UNIQUE KEY `uk_api_reference_key` (`reference_key`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Repair unique indexes if a partial run created the same names with different definitions.
SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review_plan_suggestion'
      AND index_name = 'uk_arps_review_suggestion'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review_plan_suggestion'
      AND index_name = 'uk_arps_review_suggestion'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name AND table_name = 'agent_review_plan_suggestion'
              AND column_name = 'review_id')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_review_plan_suggestion'
                  AND column_name = 'suggestion_key')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'review_id,suggestion_key'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_review_plan_suggestion`
         ADD UNIQUE KEY `uk_arps_review_suggestion` (`review_id`, `suggestion_key`)',
      'ALTER TABLE `agent_review_plan_suggestion`
         DROP INDEX `uk_arps_review_suggestion`,
         ADD UNIQUE KEY `uk_arps_review_suggestion` (`review_id`, `suggestion_key`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_change_set'
      AND index_name = 'uk_apcs_preview_request'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_change_set'
      AND index_name = 'uk_apcs_preview_request'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name AND table_name = 'agent_plan_change_set'
              AND column_name = 'user_id')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_plan_change_set'
                  AND column_name = 'preview_request_key_hash')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'user_id,preview_request_key_hash'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_plan_change_set`
         ADD UNIQUE KEY `uk_apcs_preview_request`
           (`user_id`, `preview_request_key_hash`)',
      'ALTER TABLE `agent_plan_change_set`
         DROP INDEX `uk_apcs_preview_request`,
         ADD UNIQUE KEY `uk_apcs_preview_request`
           (`user_id`, `preview_request_key_hash`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_change_set'
      AND index_name = 'uk_apcs_confirm_request'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_change_set'
      AND index_name = 'uk_apcs_confirm_request'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name AND table_name = 'agent_plan_change_set'
              AND column_name = 'user_id')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_plan_change_set'
                  AND column_name = 'confirm_request_key_hash')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'user_id,confirm_request_key_hash'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_plan_change_set`
         ADD UNIQUE KEY `uk_apcs_confirm_request`
           (`user_id`, `confirm_request_key_hash`)',
      'ALTER TABLE `agent_plan_change_set`
         DROP INDEX `uk_apcs_confirm_request`,
         ADD UNIQUE KEY `uk_apcs_confirm_request`
           (`user_id`, `confirm_request_key_hash`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_change_item'
      AND index_name = 'uk_apci_set_item'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_plan_change_item'
      AND index_name = 'uk_apci_set_item'
);
SET @sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name AND table_name = 'agent_plan_change_item'
              AND column_name = 'change_set_id')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @schema_name AND table_name = 'agent_plan_change_item'
                  AND column_name = 'item_key')
    AND (
      @index_columns IS NULL
      OR @index_columns <> 'change_set_id,item_key'
      OR @index_non_unique <> 0
    ),
    IF(
      @index_columns IS NULL,
      'ALTER TABLE `agent_plan_change_item`
         ADD UNIQUE KEY `uk_apci_set_item` (`change_set_id`, `item_key`)',
      'ALTER TABLE `agent_plan_change_item`
         DROP INDEX `uk_apci_set_item`,
         ADD UNIQUE KEY `uk_apci_set_item` (`change_set_id`, `item_key`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
