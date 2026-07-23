-- V7 stage 3: real recruiting interview processes and rounds.

SET @v4_081_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_interview_process` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    `current_round_no` INT NOT NULL DEFAULT 0,
    `outcome` VARCHAR(24) NULL,
    `lock_version` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_application_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_interview_process_live_application`
        (`live_application_id`),
    KEY `idx_career_interview_process_user`
        (`user_id`, `application_id`, `deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 real recruiting interview process';

CREATE TABLE IF NOT EXISTS `career_interview_round` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `process_id` BIGINT NOT NULL,
    `round_no` INT NOT NULL,
    `round_type` VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    `title` VARCHAR(200) NOT NULL,
    `timezone` VARCHAR(64) NOT NULL,
    `scheduled_starts_at_utc` DATETIME NULL,
    `scheduled_ends_at_utc` DATETIME NULL,
    `calendar_event_id` BIGINT NULL,
    `preparation_source_hash` VARCHAR(80) NULL,
    `rescheduled_from_round_id` BIGINT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    `result_summary` VARCHAR(2000) NULL,
    `next_step` VARCHAR(1000) NULL,
    `lock_version` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_round_no` INT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `round_no` ELSE NULL END
        ) STORED,
    `live_calendar_event_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `calendar_event_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_interview_round_live_no`
        (`process_id`, `live_round_no`),
    UNIQUE KEY `uk_career_interview_round_live_calendar`
        (`live_calendar_event_id`),
    KEY `idx_career_interview_round_schedule`
        (`process_id`, `deleted`, `status`, `scheduled_starts_at_utc`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 real recruiting interview round';

CREATE TABLE IF NOT EXISTS `career_interview_round_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `process_id` BIGINT NOT NULL,
    `round_id` BIGINT NOT NULL,
    `event_type` VARCHAR(48) NOT NULL,
    `previous_status` VARCHAR(24) NULL,
    `current_status` VARCHAR(24) NULL,
    `payload_json` TEXT NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `occurred_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_interview_round_event_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_interview_round_event_timeline`
        (`user_id`, `round_id`, `deleted`, `occurred_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 real interview round event';

-- Repair generated guard columns and indexes when a partial migration created
-- a table without all keys.
SET @v4_081_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_081_schema_name
              AND table_name = 'career_interview_process')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_081_schema_name
                      AND table_name = 'career_interview_process'
                      AND column_name = 'live_application_id'),
    'ALTER TABLE `career_interview_process`
       ADD COLUMN `live_application_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_081_stmt FROM @v4_081_sql;
EXECUTE v4_081_stmt;
DEALLOCATE PREPARE v4_081_stmt;

SET @v4_081_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_081_schema_name
              AND table_name = 'career_interview_round')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_081_schema_name
                      AND table_name = 'career_interview_round'
                      AND column_name = 'live_round_no'),
    'ALTER TABLE `career_interview_round`
       ADD COLUMN `live_round_no` INT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `round_no` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_081_stmt FROM @v4_081_sql;
EXECUTE v4_081_stmt;
DEALLOCATE PREPARE v4_081_stmt;

SET @v4_081_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_081_schema_name
              AND table_name = 'career_interview_round')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_081_schema_name
                      AND table_name = 'career_interview_round'
                      AND column_name = 'live_calendar_event_id'),
    'ALTER TABLE `career_interview_round`
       ADD COLUMN `live_calendar_event_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `calendar_event_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_081_stmt FROM @v4_081_sql;
EXECUTE v4_081_stmt;
DEALLOCATE PREPARE v4_081_stmt;

SET @v4_081_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_081_schema_name
                  AND table_name = 'career_interview_process'
                  AND index_name = 'uk_career_interview_process_live_application'),
    'ALTER TABLE `career_interview_process`
       ADD UNIQUE KEY `uk_career_interview_process_live_application`
         (`live_application_id`)',
    'SELECT 1'
);
PREPARE v4_081_stmt FROM @v4_081_sql;
EXECUTE v4_081_stmt;
DEALLOCATE PREPARE v4_081_stmt;

SET @v4_081_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_081_schema_name
                  AND table_name = 'career_interview_round'
                  AND index_name = 'uk_career_interview_round_live_no'),
    'ALTER TABLE `career_interview_round`
       ADD UNIQUE KEY `uk_career_interview_round_live_no`
         (`process_id`, `live_round_no`)',
    'SELECT 1'
);
PREPARE v4_081_stmt FROM @v4_081_sql;
EXECUTE v4_081_stmt;
DEALLOCATE PREPARE v4_081_stmt;

SET @v4_081_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_081_schema_name
                  AND table_name = 'career_interview_round'
                  AND index_name = 'uk_career_interview_round_live_calendar'),
    'ALTER TABLE `career_interview_round`
       ADD UNIQUE KEY `uk_career_interview_round_live_calendar`
         (`live_calendar_event_id`)',
    'SELECT 1'
);
PREPARE v4_081_stmt FROM @v4_081_sql;
EXECUTE v4_081_stmt;
DEALLOCATE PREPARE v4_081_stmt;
