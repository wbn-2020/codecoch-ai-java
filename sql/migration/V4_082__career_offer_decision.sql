-- V7 stage 4: immutable Offer terms, deterministic comparison and decision.

SET @v4_082_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_offer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `current_version_id` BIGINT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `lock_version` INT NOT NULL DEFAULT 1,
    `next_version_no` INT NOT NULL DEFAULT 1,
    `decision_deadline` DATETIME NULL,
    `finalized_at` DATETIME NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_application_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_offer_live_application` (`live_application_id`),
    UNIQUE KEY `uk_career_offer_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_offer_deadline`
        (`user_id`, `deleted`, `status`, `decision_deadline`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 Offer aggregate';

CREATE TABLE IF NOT EXISTS `career_offer_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `offer_id` BIGINT NOT NULL,
    `version_no` INT NOT NULL,
    `campaign_id_at_creation` BIGINT NULL,
    `currency` VARCHAR(16) NULL,
    `annual_base_salary` DECIMAL(19,4) NULL,
    `annual_bonus` DECIMAL(19,4) NULL,
    `sign_on_bonus` DECIMAL(19,4) NULL,
    `annual_equity_value` DECIMAL(19,4) NULL,
    `other_annual_compensation` DECIMAL(19,4) NULL,
    `paid_leave_days` INT NULL,
    `location` VARCHAR(160) NULL,
    `work_mode` VARCHAR(32) NULL,
    `start_date` DATE NULL,
    `decision_deadline` DATETIME NULL,
    `terms_json` JSON NULL,
    `note` VARCHAR(2000) NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_offer_version_no` (`offer_id`, `version_no`),
    UNIQUE KEY `uk_career_offer_version_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_offer_version_offer`
        (`user_id`, `offer_id`, `deleted`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable Offer terms version';

CREATE TABLE IF NOT EXISTS `career_offer_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `offer_id` BIGINT NOT NULL,
    `version_id` BIGINT NULL,
    `event_type` VARCHAR(48) NOT NULL,
    `previous_status` VARCHAR(24) NULL,
    `current_status` VARCHAR(24) NULL,
    `occurred_at` DATETIME NOT NULL,
    `summary` VARCHAR(1000) NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_offer_event_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_offer_event_timeline`
        (`user_id`, `offer_id`, `deleted`, `occurred_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offer status and terms event';

CREATE TABLE IF NOT EXISTS `career_offer_decision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `current_snapshot_id` BIGINT NULL,
    `selected_offer_id` BIGINT NULL,
    `outcome` VARCHAR(24) NULL,
    `lock_version` INT NOT NULL DEFAULT 1,
    `confirmed_at` DATETIME NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_campaign_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `campaign_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_offer_decision_live_campaign` (`live_campaign_id`),
    UNIQUE KEY `uk_career_offer_decision_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_offer_decision_user_status`
        (`user_id`, `status`, `deleted`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offer decision aggregate';

CREATE TABLE IF NOT EXISTS `career_offer_decision_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `decision_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `snapshot_no` INT NOT NULL,
    `comparison_currency` VARCHAR(16) NULL,
    `comparable` TINYINT NOT NULL DEFAULT 0,
    `weights_json` JSON NULL,
    `rule_result_json` JSON NULL,
    `missing_items_json` JSON NULL,
    `limitations_json` JSON NULL,
    `exchange_rates_json` JSON NULL,
    `exchange_rate_source` VARCHAR(255) NULL,
    `exchange_rate_date` DATE NULL,
    `ai_explanation` TEXT NULL,
    `ai_call_log_id` BIGINT NULL,
    `fallback` TINYINT NOT NULL DEFAULT 0,
    `fallback_reason` VARCHAR(500) NULL,
    `input_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `generation_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_offer_decision_snapshot_no`
        (`decision_id`, `snapshot_no`),
    UNIQUE KEY `uk_career_offer_decision_snapshot_fingerprint`
        (`decision_id`, `generation_fingerprint`),
    KEY `idx_career_offer_decision_snapshot_user`
        (`user_id`, `decision_id`, `deleted`, `snapshot_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable Offer comparison snapshot';

CREATE TABLE IF NOT EXISTS `career_offer_decision_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `offer_id` BIGINT NOT NULL,
    `offer_version_id` BIGINT NOT NULL,
    `comparable_annual_value` DECIMAL(19,4) NULL,
    `weighted_score` DECIMAL(19,6) NULL,
    `rank_no` INT NULL,
    `rule_result_json` JSON NULL,
    `missing_items_json` JSON NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_offer_decision_item_offer`
        (`snapshot_id`, `offer_id`),
    KEY `idx_career_offer_decision_item_rank`
        (`snapshot_id`, `rank_no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offer comparison item';

-- Repair the two active aggregate guards if a previous partial run created
-- the tables without their generated columns or indexes.
SET @v4_082_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_082_schema_name
              AND table_name = 'career_offer')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_082_schema_name
                      AND table_name = 'career_offer'
                      AND column_name = 'live_application_id'),
    'ALTER TABLE `career_offer`
       ADD COLUMN `live_application_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_082_stmt FROM @v4_082_sql;
EXECUTE v4_082_stmt;
DEALLOCATE PREPARE v4_082_stmt;

SET @v4_082_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_082_schema_name
              AND table_name = 'career_offer_decision')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_082_schema_name
                      AND table_name = 'career_offer_decision'
                      AND column_name = 'live_campaign_id'),
    'ALTER TABLE `career_offer_decision`
       ADD COLUMN `live_campaign_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `campaign_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_082_stmt FROM @v4_082_sql;
EXECUTE v4_082_stmt;
DEALLOCATE PREPARE v4_082_stmt;

SET @v4_082_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_082_schema_name
                  AND table_name = 'career_offer'
                  AND index_name = 'uk_career_offer_live_application'),
    'ALTER TABLE `career_offer`
       ADD UNIQUE KEY `uk_career_offer_live_application` (`live_application_id`)',
    'SELECT 1'
);
PREPARE v4_082_stmt FROM @v4_082_sql;
EXECUTE v4_082_stmt;
DEALLOCATE PREPARE v4_082_stmt;

SET @v4_082_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_082_schema_name
                  AND table_name = 'career_offer_decision'
                  AND index_name = 'uk_career_offer_decision_live_campaign'),
    'ALTER TABLE `career_offer_decision`
       ADD UNIQUE KEY `uk_career_offer_decision_live_campaign` (`live_campaign_id`)',
    'SELECT 1'
);
PREPARE v4_082_stmt FROM @v4_082_sql;
EXECUTE v4_082_stmt;
DEALLOCATE PREPARE v4_082_stmt;
