-- V8 stage four: campaign pulse aggregate, immutable snapshots,
-- and source audit records.

SET @v4_089_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_campaign_pulse` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `current_snapshot_id` BIGINT NULL,
    `snapshot_version` INT NOT NULL DEFAULT 0,
    `last_generated_at` DATETIME NULL,
    `generation_claim_token` VARCHAR(64) NULL,
    `generation_claim_fingerprint`
        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `generation_claimed_at` DATETIME NULL,
    `lock_version` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_campaign_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `campaign_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_campaign_pulse_live_campaign`
        (`user_id`, `live_campaign_id`),
    KEY `idx_campaign_pulse_generation_claim`
        (`user_id`, `generation_claimed_at`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V8 career campaign pulse aggregate';

CREATE TABLE IF NOT EXISTS `career_campaign_pulse_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `pulse_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `snapshot_version` INT NOT NULL,
    `data_cutoff_at` DATETIME NOT NULL,
    `input_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `generation_fingerprint`
        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `idempotency_key_hash`
        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `idempotency_payload_hash`
        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `facts_json` MEDIUMTEXT NOT NULL,
    `metrics_json` MEDIUMTEXT NOT NULL,
    `changes_json` MEDIUMTEXT NOT NULL,
    `drift_signals_json` MEDIUMTEXT NOT NULL,
    `limits_json` MEDIUMTEXT NOT NULL,
    `action_seeds_json` MEDIUMTEXT NOT NULL,
    `narrative_json` MEDIUMTEXT NOT NULL,
    `confidence_level` VARCHAR(16) NOT NULL,
    `fallback` TINYINT NOT NULL DEFAULT 0,
    `ai_call_log_id` BIGINT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_campaign_pulse_snapshot_version`
        (`pulse_id`, `snapshot_version`),
    UNIQUE KEY `uk_career_campaign_pulse_snapshot_input`
        (`pulse_id`, `input_hash`),
    UNIQUE KEY `uk_career_campaign_pulse_snapshot_fingerprint`
        (`pulse_id`, `generation_fingerprint`),
    UNIQUE KEY `uk_career_campaign_pulse_snapshot_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_campaign_pulse_snapshot_history`
        (`user_id`, `campaign_id`, `deleted`, `snapshot_version`, `id`),
    KEY `idx_campaign_pulse_snapshot_cutoff`
        (`user_id`, `campaign_id`, `deleted`, `data_cutoff_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Immutable V8 career campaign pulse snapshot';

CREATE TABLE IF NOT EXISTS `career_campaign_pulse_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `source_type` VARCHAR(64) NOT NULL,
    `source_id` BIGINT NULL,
    `source_version` INT NULL,
    `source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `application_id` BIGINT NULL,
    `campaign_id` BIGINT NOT NULL,
    `observed_at` DATETIME NULL,
    `field_path` VARCHAR(255) NULL,
    `safe_summary` VARCHAR(500) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_campaign_pulse_source_snapshot`
        (`user_id`, `snapshot_id`, `deleted`, `id`),
    KEY `idx_campaign_pulse_source_lookup`
        (`user_id`, `campaign_id`, `source_type`, `source_id`, `deleted`, `id`),
    KEY `idx_campaign_pulse_source_application`
        (`user_id`, `application_id`, `deleted`, `observed_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V8 career campaign pulse source audit';

SET @v4_089_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_089_schema_name
              AND table_name = 'career_campaign_pulse')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_089_schema_name
                      AND table_name = 'career_campaign_pulse'
                      AND column_name = 'live_campaign_id'),
    'ALTER TABLE `career_campaign_pulse`
       ADD COLUMN `live_campaign_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `campaign_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_089_stmt FROM @v4_089_sql;
EXECUTE v4_089_stmt;
DEALLOCATE PREPARE v4_089_stmt;

-- Pulse rows and snapshots are immutable evidence. Refuse to infer a winner
-- when a partial draft already contains dirty or duplicate business keys.
DROP TEMPORARY TABLE IF EXISTS `_v4_089_uniqueness_guard`;
CREATE TEMPORARY TABLE `_v4_089_uniqueness_guard` (
    `root_dirty_row_count` BIGINT NOT NULL,
    `root_duplicate_group_count` BIGINT NOT NULL,
    `snapshot_dirty_row_count` BIGINT NOT NULL,
    `version_duplicate_group_count` BIGINT NOT NULL,
    `input_duplicate_group_count` BIGINT NOT NULL,
    `fingerprint_duplicate_group_count` BIGINT NOT NULL,
    `idempotency_duplicate_group_count` BIGINT NOT NULL,
    `source_dirty_row_count` BIGINT NOT NULL,
    CONSTRAINT `chk_v4_089_no_dirty_root`
        CHECK (`root_dirty_row_count` = 0),
    CONSTRAINT `chk_v4_089_no_duplicate_root`
        CHECK (`root_duplicate_group_count` = 0),
    CONSTRAINT `chk_v4_089_no_dirty_snapshot`
        CHECK (`snapshot_dirty_row_count` = 0),
    CONSTRAINT `chk_v4_089_no_duplicate_version`
        CHECK (`version_duplicate_group_count` = 0),
    CONSTRAINT `chk_v4_089_no_duplicate_input`
        CHECK (`input_duplicate_group_count` = 0),
    CONSTRAINT `chk_v4_089_no_duplicate_fingerprint`
        CHECK (`fingerprint_duplicate_group_count` = 0),
    CONSTRAINT `chk_v4_089_no_duplicate_idempotency`
        CHECK (`idempotency_duplicate_group_count` = 0),
    CONSTRAINT `chk_v4_089_no_dirty_source`
        CHECK (`source_dirty_row_count` = 0)
);
INSERT INTO `_v4_089_uniqueness_guard` (
    `root_dirty_row_count`,
    `root_duplicate_group_count`,
    `snapshot_dirty_row_count`,
    `version_duplicate_group_count`,
    `input_duplicate_group_count`,
    `fingerprint_duplicate_group_count`,
    `idempotency_duplicate_group_count`,
    `source_dirty_row_count`
)
SELECT
    (
        SELECT COUNT(*)
        FROM `career_campaign_pulse`
        WHERE `user_id` IS NULL
           OR `campaign_id` IS NULL
           OR `snapshot_version` IS NULL
           OR `snapshot_version` < 0
           OR `lock_version` IS NULL
           OR `lock_version` < 1
           OR `deleted` IS NULL
           OR `deleted` NOT IN (0, 1)
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT `user_id`, `campaign_id`
            FROM `career_campaign_pulse`
            WHERE `deleted` = 0
            GROUP BY `user_id`, `campaign_id`
            HAVING COUNT(*) > 1
        ) duplicate_group
    ),
    (
        SELECT COUNT(*)
        FROM `career_campaign_pulse_snapshot`
        WHERE `user_id` IS NULL
           OR `pulse_id` IS NULL
           OR `campaign_id` IS NULL
           OR `snapshot_version` IS NULL
           OR `snapshot_version` < 1
           OR `input_hash` IS NULL
           OR CHAR_LENGTH(`input_hash`) <> 64
           OR `generation_fingerprint` IS NULL
           OR CHAR_LENGTH(`generation_fingerprint`) <> 64
           OR `idempotency_key_hash` IS NULL
           OR CHAR_LENGTH(`idempotency_key_hash`) <> 64
           OR `idempotency_payload_hash` IS NULL
           OR CHAR_LENGTH(`idempotency_payload_hash`) <> 64
           OR `deleted` IS NULL
           OR `deleted` NOT IN (0, 1)
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT `pulse_id`, `snapshot_version`
            FROM `career_campaign_pulse_snapshot`
            GROUP BY `pulse_id`, `snapshot_version`
            HAVING COUNT(*) > 1
        ) duplicate_group
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT `pulse_id`, `input_hash`
            FROM `career_campaign_pulse_snapshot`
            GROUP BY `pulse_id`, `input_hash`
            HAVING COUNT(*) > 1
        ) duplicate_group
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT `pulse_id`, `generation_fingerprint`
            FROM `career_campaign_pulse_snapshot`
            GROUP BY `pulse_id`, `generation_fingerprint`
            HAVING COUNT(*) > 1
        ) duplicate_group
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT `user_id`, `idempotency_key_hash`
            FROM `career_campaign_pulse_snapshot`
            GROUP BY `user_id`, `idempotency_key_hash`
            HAVING COUNT(*) > 1
        ) duplicate_group
    ),
    (
        SELECT COUNT(*)
        FROM `career_campaign_pulse_source`
        WHERE `user_id` IS NULL
           OR `snapshot_id` IS NULL
           OR `campaign_id` IS NULL
           OR (`source_hash` IS NOT NULL
               AND CHAR_LENGTH(`source_hash`) <> 64)
           OR `deleted` IS NULL
           OR `deleted` NOT IN (0, 1)
    );
DROP TEMPORARY TABLE `_v4_089_uniqueness_guard`;

SET @v4_089_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_089_schema_name
                  AND table_name = 'career_campaign_pulse'
                  AND index_name = 'uk_career_campaign_pulse_live_campaign'),
    'ALTER TABLE `career_campaign_pulse`
       ADD UNIQUE KEY `uk_career_campaign_pulse_live_campaign`
         (`user_id`, `live_campaign_id`)',
    'SELECT 1'
);
PREPARE v4_089_stmt FROM @v4_089_sql;
EXECUTE v4_089_stmt;
DEALLOCATE PREPARE v4_089_stmt;

SET @v4_089_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_089_schema_name
                  AND table_name = 'career_campaign_pulse_snapshot'
                  AND index_name = 'uk_career_campaign_pulse_snapshot_version'),
    'ALTER TABLE `career_campaign_pulse_snapshot`
       ADD UNIQUE KEY `uk_career_campaign_pulse_snapshot_version`
         (`pulse_id`, `snapshot_version`)',
    'SELECT 1'
);
PREPARE v4_089_stmt FROM @v4_089_sql;
EXECUTE v4_089_stmt;
DEALLOCATE PREPARE v4_089_stmt;

SET @v4_089_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_089_schema_name
                  AND table_name = 'career_campaign_pulse_snapshot'
                  AND index_name = 'uk_career_campaign_pulse_snapshot_input'),
    'ALTER TABLE `career_campaign_pulse_snapshot`
       ADD UNIQUE KEY `uk_career_campaign_pulse_snapshot_input`
         (`pulse_id`, `input_hash`)',
    'SELECT 1'
);
PREPARE v4_089_stmt FROM @v4_089_sql;
EXECUTE v4_089_stmt;
DEALLOCATE PREPARE v4_089_stmt;

SET @v4_089_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_089_schema_name
                  AND table_name = 'career_campaign_pulse_snapshot'
                  AND index_name = 'uk_career_campaign_pulse_snapshot_fingerprint'),
    'ALTER TABLE `career_campaign_pulse_snapshot`
       ADD UNIQUE KEY `uk_career_campaign_pulse_snapshot_fingerprint`
         (`pulse_id`, `generation_fingerprint`)',
    'SELECT 1'
);
PREPARE v4_089_stmt FROM @v4_089_sql;
EXECUTE v4_089_stmt;
DEALLOCATE PREPARE v4_089_stmt;

SET @v4_089_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_089_schema_name
                  AND table_name = 'career_campaign_pulse_snapshot'
                  AND index_name = 'uk_career_campaign_pulse_snapshot_idempotency'),
    'ALTER TABLE `career_campaign_pulse_snapshot`
       ADD UNIQUE KEY `uk_career_campaign_pulse_snapshot_idempotency`
         (`user_id`, `idempotency_key_hash`)',
    'SELECT 1'
);
PREPARE v4_089_stmt FROM @v4_089_sql;
EXECUTE v4_089_stmt;
DEALLOCATE PREPARE v4_089_stmt;
