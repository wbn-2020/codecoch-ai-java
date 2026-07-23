-- V7 stage 5: privacy-bounded contacts and communication activity ledger.
-- No recoverable contact value or full private message body is stored.

SET @v4_083_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_contact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `display_name` VARCHAR(160) NOT NULL,
    `role_type` VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    `channel_type` VARCHAR(32) NULL,
    `masked_contact_hint` VARCHAR(160) NULL,
    `relationship_summary` VARCHAR(1000) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_career_contact_user`
        (`user_id`, `deleted`, `updated_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V7 privacy-bounded career contact';

CREATE TABLE IF NOT EXISTS `career_contact_application` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `contact_id` BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `relationship_type` VARCHAR(32) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_application_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_contact_application_live`
        (`contact_id`, `live_application_id`),
    KEY `idx_career_contact_application_lookup`
        (`user_id`, `application_id`, `deleted`, `contact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career contact to application relation';

CREATE TABLE IF NOT EXISTS `career_activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `application_id` BIGINT NOT NULL,
    `contact_id` BIGINT NULL,
    `activity_type` VARCHAR(48) NOT NULL,
    `channel_type` VARCHAR(32) NULL,
    `subject` VARCHAR(255) NULL,
    `summary` VARCHAR(2000) NOT NULL,
    `occurred_at` DATETIME NULL,
    `next_follow_up_at` DATETIME NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_activity_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_activity_timeline`
        (`user_id`, `application_id`, `deleted`, `occurred_at`, `id`),
    KEY `idx_career_activity_follow_up`
        (`user_id`, `deleted`, `status`, `next_follow_up_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Privacy-bounded career activity';

CREATE TABLE IF NOT EXISTS `career_activity_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `activity_id` BIGINT NOT NULL,
    `event_type` VARCHAR(48) NOT NULL,
    `event_time` DATETIME NOT NULL,
    `idempotency_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_activity_event_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_career_activity_event_timeline`
        (`user_id`, `activity_id`, `deleted`, `event_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career activity event';

CREATE TABLE IF NOT EXISTS `career_interview_round_contact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `interview_round_id` BIGINT NOT NULL,
    `contact_id` BIGINT NOT NULL,
    `relationship_type` VARCHAR(32) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_contact_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `contact_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_round_contact_live`
        (`interview_round_id`, `live_contact_id`),
    KEY `idx_career_round_contact_lookup`
        (`user_id`, `contact_id`, `deleted`, `interview_round_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Real interview round contact relation';

SET @v4_083_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_083_schema_name
              AND table_name = 'career_contact_application')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_083_schema_name
                      AND table_name = 'career_contact_application'
                      AND column_name = 'live_application_id'),
    'ALTER TABLE `career_contact_application`
       ADD COLUMN `live_application_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_083_stmt FROM @v4_083_sql;
EXECUTE v4_083_stmt;
DEALLOCATE PREPARE v4_083_stmt;

SET @v4_083_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_083_schema_name
              AND table_name = 'career_interview_round_contact')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_083_schema_name
                      AND table_name = 'career_interview_round_contact'
                      AND column_name = 'live_contact_id'),
    'ALTER TABLE `career_interview_round_contact`
       ADD COLUMN `live_contact_id` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `contact_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_083_stmt FROM @v4_083_sql;
EXECUTE v4_083_stmt;
DEALLOCATE PREPARE v4_083_stmt;

SET @v4_083_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_083_schema_name
                  AND table_name = 'career_contact_application'
                  AND index_name = 'uk_career_contact_application_live'),
    'ALTER TABLE `career_contact_application`
       ADD UNIQUE KEY `uk_career_contact_application_live`
         (`contact_id`, `live_application_id`)',
    'SELECT 1'
);
PREPARE v4_083_stmt FROM @v4_083_sql;
EXECUTE v4_083_stmt;
DEALLOCATE PREPARE v4_083_stmt;

SET @v4_083_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_083_schema_name
                  AND table_name = 'career_interview_round_contact'
                  AND index_name = 'uk_career_round_contact_live'),
    'ALTER TABLE `career_interview_round_contact`
       ADD UNIQUE KEY `uk_career_round_contact_live`
         (`interview_round_id`, `live_contact_id`)',
    'SELECT 1'
);
PREPARE v4_083_stmt FROM @v4_083_sql;
EXECUTE v4_083_stmt;
DEALLOCATE PREPARE v4_083_stmt;
