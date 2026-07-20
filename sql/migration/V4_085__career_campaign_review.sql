-- V7 stage 7: immutable campaign review snapshots and governed memory candidates.

SET @v4_085_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_campaign_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `current_snapshot_id` BIGINT NULL,
    `review_status` VARCHAR(24) NOT NULL DEFAULT 'NOT_GENERATED',
    `snapshot_version` INT NOT NULL DEFAULT 0,
    `generation_claim_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `generation_claim_token` VARCHAR(64) NULL,
    `generation_claim_idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `generation_claim_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
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
    UNIQUE KEY `uk_career_campaign_review_live_campaign` (`live_campaign_id`),
    KEY `idx_career_campaign_review_generation`
        (`user_id`, `review_status`, `generation_claimed_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 campaign review aggregate';

CREATE TABLE IF NOT EXISTS `career_campaign_review_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `review_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `snapshot_version` INT NOT NULL,
    `data_cutoff_at` DATETIME NOT NULL,
    `input_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `generation_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `idempotency_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `facts_json` MEDIUMTEXT NOT NULL,
    `coverage_json` TEXT NOT NULL,
    `limits_json` TEXT NOT NULL,
    `signals_json` MEDIUMTEXT NOT NULL,
    `memory_candidates_json` MEDIUMTEXT NOT NULL,
    `experiment_candidates_json` MEDIUMTEXT NOT NULL,
    `next_cycle_actions_json` MEDIUMTEXT NOT NULL,
    `summary` VARCHAR(2000) NULL,
    `confidence_level` VARCHAR(16) NOT NULL,
    `result_source` VARCHAR(24) NOT NULL DEFAULT 'RULE',
    `fallback` TINYINT NOT NULL DEFAULT 0,
    `fallback_reason` VARCHAR(500) NULL,
    `ai_call_log_id` BIGINT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_campaign_review_snapshot_version`
        (`review_id`, `snapshot_version`),
    UNIQUE KEY `uk_career_campaign_review_snapshot_input`
        (`review_id`, `input_hash`),
    UNIQUE KEY `uk_career_campaign_review_snapshot_generation`
        (`review_id`, `generation_fingerprint`),
    UNIQUE KEY `uk_career_campaign_review_snapshot_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_campaign_review_snapshot_cutoff`
        (`user_id`, `campaign_id`, `deleted`, `data_cutoff_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable campaign review snapshot';

CREATE TABLE IF NOT EXISTS `career_campaign_review_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `source_type` VARCHAR(64) NOT NULL,
    `source_id` BIGINT NULL,
    `source_time` DATETIME NULL,
    `source_updated_at` DATETIME NULL,
    `inclusion_status` VARCHAR(16) NOT NULL,
    `exclude_reason` VARCHAR(64) NULL,
    `source_hash` VARCHAR(80) NULL,
    `safe_summary` VARCHAR(500) NULL,
    `metadata_json` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_career_campaign_review_source_snapshot`
        (`snapshot_id`, `inclusion_status`, `source_type`, `deleted`),
    KEY `idx_career_campaign_review_source_lookup`
        (`user_id`, `source_type`, `source_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Campaign review source audit';

CREATE TABLE IF NOT EXISTS `career_campaign_review_memory_candidate` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `review_id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `candidate_key` VARCHAR(128) NOT NULL,
    `semantic_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `content` VARCHAR(2000) NOT NULL,
    `source_ref` VARCHAR(255) NULL,
    `confidence_level` VARCHAR(16) NOT NULL DEFAULT 'LOW',
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    `validity_days` INT NULL,
    `expires_at` DATETIME NULL,
    `confirmed_at` DATETIME NULL,
    `decision_idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_semantic_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `semantic_hash` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_campaign_review_memory_candidate_key`
        (`review_id`, `candidate_key`),
    UNIQUE KEY `uk_campaign_review_memory_live_semantic`
        (`user_id`, `live_semantic_hash`),
    KEY `idx_campaign_review_memory_status`
        (`user_id`, `status`, `deleted`, `expires_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Governed campaign review memory candidate';

SET @v4_085_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_085_schema_name
              AND table_name = 'career_campaign_review_memory_candidate')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_085_schema_name
                      AND table_name = 'career_campaign_review_memory_candidate'
                      AND column_name = 'decision_idempotency_key_hash'),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `decision_idempotency_key_hash`
         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `confirmed_at`',
    'SELECT 1'
);
PREPARE v4_085_stmt FROM @v4_085_sql;
EXECUTE v4_085_stmt;
DEALLOCATE PREPARE v4_085_stmt;

SET @v4_085_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_085_schema_name
              AND table_name = 'career_campaign_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_085_schema_name
                      AND table_name = 'career_campaign_review'
                      AND column_name = 'live_campaign_id'),
    'ALTER TABLE `career_campaign_review`
       ADD COLUMN `live_campaign_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `campaign_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_085_stmt FROM @v4_085_sql;
EXECUTE v4_085_stmt;
DEALLOCATE PREPARE v4_085_stmt;

SET @v4_085_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_085_schema_name
                  AND table_name = 'career_campaign_review'
                  AND index_name = 'uk_career_campaign_review_live_campaign'),
    'ALTER TABLE `career_campaign_review`
       ADD UNIQUE KEY `uk_career_campaign_review_live_campaign`
         (`live_campaign_id`)',
    'SELECT 1'
);
PREPARE v4_085_stmt FROM @v4_085_sql;
EXECUTE v4_085_stmt;
DEALLOCATE PREPARE v4_085_stmt;
