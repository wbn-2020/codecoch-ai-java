-- V8 stage two: durable campaign action decisions with idempotent replay.
-- Forward-only and idempotent. Only one active decision may own a semantic source.

SET @v4_088_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_campaign_action_decision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `semantic_key` VARCHAR(255) NOT NULL,
    `source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `action_type` VARCHAR(48) NOT NULL,
    `decision_status` VARCHAR(24) NOT NULL,
    `snoozed_until` DATETIME NULL,
    `reason` VARCHAR(1000) NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `decided_at` DATETIME NOT NULL,
    `active_guard` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_semantic_source` VARCHAR(320)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (
            CASE
                WHEN `deleted` = 0 AND `active_guard` = 1
                THEN CONCAT(`semantic_key`, '#', `source_hash`)
                ELSE NULL
            END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_campaign_action_decision_idempotency`
        (`user_id`, `idempotency_key_hash`),
    UNIQUE KEY `uk_career_campaign_action_decision_live_source`
        (`user_id`, `campaign_id`, `live_semantic_source`),
    KEY `idx_campaign_action_decision_campaign`
        (`user_id`, `campaign_id`, `deleted`, `decided_at`, `id`),
    KEY `idx_campaign_action_decision_status`
        (`user_id`, `campaign_id`, `decision_status`, `deleted`, `snoozed_until`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V8 career campaign action decision';

SET @v4_088_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_088_schema_name
              AND table_name = 'career_campaign_action_decision')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_088_schema_name
                      AND table_name = 'career_campaign_action_decision'
                      AND column_name = 'live_semantic_source'),
    'ALTER TABLE `career_campaign_action_decision`
       ADD COLUMN `live_semantic_source` VARCHAR(320)
         CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
         GENERATED ALWAYS AS (
           CASE
             WHEN `deleted` = 0 AND `active_guard` = 1
             THEN CONCAT(`semantic_key`, ''#'', `source_hash`)
             ELSE NULL
           END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_088_stmt FROM @v4_088_sql;
EXECUTE v4_088_stmt;
DEALLOCATE PREPARE v4_088_stmt;

-- Preserve every historical decision. Conflicts must be reviewed rather than
-- silently selecting a winner before the business unique keys are repaired.
DROP TEMPORARY TABLE IF EXISTS `_v4_088_uniqueness_guard`;
CREATE TEMPORARY TABLE `_v4_088_uniqueness_guard` (
    `dirty_row_count` BIGINT NOT NULL,
    `live_duplicate_group_count` BIGINT NOT NULL,
    `idempotency_duplicate_group_count` BIGINT NOT NULL,
    CONSTRAINT `chk_v4_088_no_dirty_decision`
        CHECK (`dirty_row_count` = 0),
    CONSTRAINT `chk_v4_088_no_live_duplicate`
        CHECK (`live_duplicate_group_count` = 0),
    CONSTRAINT `chk_v4_088_no_idempotency_duplicate`
        CHECK (`idempotency_duplicate_group_count` = 0)
);
INSERT INTO `_v4_088_uniqueness_guard` (
    `dirty_row_count`,
    `live_duplicate_group_count`,
    `idempotency_duplicate_group_count`
)
SELECT
    (
        SELECT COUNT(*)
        FROM `career_campaign_action_decision`
        WHERE `user_id` IS NULL
           OR `campaign_id` IS NULL
           OR `semantic_key` IS NULL
           OR `semantic_key` = ''
           OR `source_hash` IS NULL
           OR CHAR_LENGTH(`source_hash`) <> 64
           OR `idempotency_key_hash` IS NULL
           OR CHAR_LENGTH(`idempotency_key_hash`) <> 64
           OR `payload_hash` IS NULL
           OR CHAR_LENGTH(`payload_hash`) <> 64
           OR `active_guard` IS NULL
           OR `active_guard` NOT IN (0, 1)
           OR `deleted` IS NULL
           OR `deleted` NOT IN (0, 1)
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT
                `user_id`,
                `campaign_id`,
                `semantic_key` COLLATE utf8mb4_bin,
                `source_hash`
            FROM `career_campaign_action_decision`
            WHERE `deleted` = 0
              AND `active_guard` = 1
            GROUP BY
                `user_id`,
                `campaign_id`,
                `semantic_key` COLLATE utf8mb4_bin,
                `source_hash`
            HAVING COUNT(*) > 1
        ) duplicate_group
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT `user_id`, `idempotency_key_hash`
            FROM `career_campaign_action_decision`
            GROUP BY `user_id`, `idempotency_key_hash`
            HAVING COUNT(*) > 1
        ) duplicate_group
    );
DROP TEMPORARY TABLE `_v4_088_uniqueness_guard`;

SET @v4_088_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_088_schema_name
                  AND table_name = 'career_campaign_action_decision'
                  AND index_name = 'uk_career_campaign_action_decision_idempotency'),
    'ALTER TABLE `career_campaign_action_decision`
       ADD UNIQUE KEY `uk_career_campaign_action_decision_idempotency`
         (`user_id`, `idempotency_key_hash`)',
    'SELECT 1'
);
PREPARE v4_088_stmt FROM @v4_088_sql;
EXECUTE v4_088_stmt;
DEALLOCATE PREPARE v4_088_stmt;

SET @v4_088_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_088_schema_name
                  AND table_name = 'career_campaign_action_decision'
                  AND index_name = 'uk_career_campaign_action_decision_live_source'),
    'ALTER TABLE `career_campaign_action_decision`
       ADD UNIQUE KEY `uk_career_campaign_action_decision_live_source`
         (`user_id`, `campaign_id`, `live_semantic_source`)',
    'SELECT 1'
);
PREPARE v4_088_stmt FROM @v4_088_sql;
EXECUTE v4_088_stmt;
DEALLOCATE PREPARE v4_088_stmt;
