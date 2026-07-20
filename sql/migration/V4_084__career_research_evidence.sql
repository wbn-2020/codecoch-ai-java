-- V7 stage 6: versioned user-provided research evidence and immutable snapshots.

SET @v4_084_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_research_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `official_url` VARCHAR(1000) NULL,
    `external_ref` VARCHAR(255) NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    `current_version_id` BIGINT NULL,
    `lock_version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_career_research_source_application`
        (`user_id`, `application_id`, `status`, `deleted`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 user-owned research source';

CREATE TABLE IF NOT EXISTS `career_research_source_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `source_id` BIGINT NOT NULL,
    `version_token` VARCHAR(64) NOT NULL,
    `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `content_summary` VARCHAR(2000) NULL,
    `content_text` MEDIUMTEXT NOT NULL,
    `captured_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_research_source_version_token`
        (`source_id`, `version_token`),
    UNIQUE KEY `uk_career_research_source_content_hash`
        (`source_id`, `content_hash`),
    KEY `idx_career_research_source_version_user`
        (`user_id`, `source_id`, `deleted`, `captured_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable research source content version';

CREATE TABLE IF NOT EXISTS `career_research_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `current_snapshot_id` BIGINT NULL,
    `generation_status` VARCHAR(24) NOT NULL DEFAULT 'IDLE',
    `generation_claim_token` VARCHAR(64) NULL,
    `generation_claimed_at` DATETIME NULL,
    `lock_version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_application_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_research_report_live_application`
        (`live_application_id`),
    KEY `idx_career_research_report_generation`
        (`user_id`, `generation_status`, `generation_claimed_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Research generation aggregate';

CREATE TABLE IF NOT EXISTS `career_research_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `report_id` BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `source_set_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `generation_claim_token` VARCHAR(64) NOT NULL,
    `snapshot_json` MEDIUMTEXT NOT NULL,
    `confidence_level` VARCHAR(16) NOT NULL DEFAULT 'LOW',
    `fallback` VARCHAR(64) NULL,
    `ai_call_log_id` BIGINT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_research_snapshot_claim`
        (`report_id`, `generation_claim_token`),
    UNIQUE KEY `uk_career_research_snapshot_source_set`
        (`report_id`, `source_set_hash`),
    KEY `idx_career_research_snapshot_application`
        (`user_id`, `application_id`, `deleted`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable research snapshot';

CREATE TABLE IF NOT EXISTS `career_research_snapshot_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `source_id` BIGINT NOT NULL,
    `source_version_id` BIGINT NOT NULL,
    `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_research_snapshot_source`
        (`snapshot_id`, `source_version_id`),
    KEY `idx_career_research_snapshot_source_lookup`
        (`user_id`, `source_id`, `source_version_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Research snapshot source audit';

SET @v4_084_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_084_schema_name
              AND table_name = 'career_research_report')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_084_schema_name
                      AND table_name = 'career_research_report'
                      AND column_name = 'live_application_id'),
    'ALTER TABLE `career_research_report`
       ADD COLUMN `live_application_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_084_stmt FROM @v4_084_sql;
EXECUTE v4_084_stmt;
DEALLOCATE PREPARE v4_084_stmt;

SET @v4_084_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_084_schema_name
                  AND table_name = 'career_research_report'
                  AND index_name = 'uk_career_research_report_live_application'),
    'ALTER TABLE `career_research_report`
       ADD UNIQUE KEY `uk_career_research_report_live_application`
         (`live_application_id`)',
    'SELECT 1'
);
PREPARE v4_084_stmt FROM @v4_084_sql;
EXECUTE v4_084_stmt;
DEALLOCATE PREPARE v4_084_stmt;
