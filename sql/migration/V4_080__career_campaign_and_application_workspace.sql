-- V7 stage 2: career campaigns and application lifecycle/workspace fields.
-- Forward-only, idempotent schema repair. Historical applications are not
-- assigned to a fabricated campaign.

SET @v4_080_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_campaign` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(160) NOT NULL,
    `goal` VARCHAR(2000) NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `started_at` DATETIME NULL,
    `completed_at` DATETIME NULL,
    `archived_at` DATETIME NULL,
    `lock_version` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_active_user_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 AND `status` = 'ACTIVE'
                 THEN `user_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_campaign_live_active_user` (`live_active_user_id`),
    KEY `idx_career_campaign_user_status`
        (`user_id`, `status`, `deleted`, `updated_at`),
    KEY `idx_career_campaign_user_time`
        (`user_id`, `started_at`, `completed_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 user career campaign';

CREATE TABLE IF NOT EXISTS `career_campaign_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `event_type` VARCHAR(48) NOT NULL,
    `summary` VARCHAR(1000) NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `occurred_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_campaign_event_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_campaign_event_timeline`
        (`user_id`, `campaign_id`, `deleted`, `occurred_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 career campaign event';

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application'
                  AND column_name = 'campaign_id'),
    'ALTER TABLE `job_application` ADD COLUMN `campaign_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application'
                  AND column_name = 'stage_changed_at'),
    'ALTER TABLE `job_application` ADD COLUMN `stage_changed_at` DATETIME NULL',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application'
                  AND column_name = 'priority_level'),
    'ALTER TABLE `job_application`
       ADD COLUMN `priority_level` VARCHAR(16) NULL',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application'
                  AND column_name = 'opportunity_outcome'),
    'ALTER TABLE `job_application`
       ADD COLUMN `opportunity_outcome` VARCHAR(24) NULL',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application'
                  AND column_name = 'lock_version'),
    'ALTER TABLE `job_application`
       ADD COLUMN `lock_version` INT NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

UPDATE `job_application`
SET `lock_version` = 1
WHERE `lock_version` IS NULL OR `lock_version` < 1;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application'
                  AND index_name = 'idx_job_application_user_campaign'),
    'ALTER TABLE `job_application`
       ADD KEY `idx_job_application_user_campaign`
         (`user_id`, `campaign_id`, `deleted`, `status`, `stage_changed_at`, `id`)',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application'
                  AND index_name = 'idx_job_application_user_stage'),
    'ALTER TABLE `job_application`
       ADD KEY `idx_job_application_user_stage`
         (`user_id`, `deleted`, `status`, `stage_changed_at`, `id`)',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application_event'
                  AND column_name = 'idempotency_key_hash'),
    'ALTER TABLE `job_application_event`
       ADD COLUMN `idempotency_key_hash` CHAR(64)
         CHARACTER SET ascii COLLATE ascii_bin NULL',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application_event'
                  AND column_name = 'live_idempotency_key_hash'),
    'ALTER TABLE `job_application_event`
       ADD COLUMN `live_idempotency_key_hash` CHAR(64)
         CHARACTER SET ascii COLLATE ascii_bin
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `idempotency_key_hash` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application_event'
                  AND index_name = 'uk_job_application_event_idempotency'),
    'ALTER TABLE `job_application_event`
       ADD UNIQUE KEY `uk_job_application_event_idempotency`
         (`user_id`, `application_id`, `live_idempotency_key_hash`)',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;

SET @v4_080_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_080_schema_name
                  AND table_name = 'job_application_event'
                  AND index_name = 'idx_job_application_event_timeline'),
    'ALTER TABLE `job_application_event`
       ADD KEY `idx_job_application_event_timeline`
         (`user_id`, `application_id`, `deleted`, `event_time`, `id`)',
    'SELECT 1'
);
PREPARE v4_080_stmt FROM @v4_080_sql;
EXECUTE v4_080_stmt;
DEALLOCATE PREPARE v4_080_stmt;
