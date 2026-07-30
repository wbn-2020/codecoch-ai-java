-- V8 stage one: owner-scoped campaign operating profile.
-- Forward-only and idempotent. Active-row uniqueness survives soft deletion.

SET @v4_087_schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `career_campaign_operating_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `campaign_id` BIGINT NOT NULL,
    `weekly_application_target` INT NOT NULL DEFAULT 3,
    `weekly_time_budget_minutes` INT NOT NULL DEFAULT 180,
    `max_active_opportunities` INT NOT NULL DEFAULT 10,
    `stale_after_days` INT NOT NULL DEFAULT 7,
    `default_follow_up_days` INT NOT NULL DEFAULT 5,
    `focus_roles_json` MEDIUMTEXT NOT NULL,
    `focus_locations_json` MEDIUMTEXT NOT NULL,
    `focus_channels_json` MEDIUMTEXT NOT NULL,
    `timezone` VARCHAR(64) NOT NULL DEFAULT 'UTC',
    `lock_version` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `active_guard` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `campaign_id` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_career_campaign_operating_profile_live_campaign`
        (`user_id`, `active_guard`),
    KEY `idx_campaign_operating_profile_campaign`
        (`user_id`, `campaign_id`, `deleted`, `updated_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V8 career campaign operating profile';

SET @v4_087_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_087_schema_name
              AND table_name = 'career_campaign_operating_profile')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_087_schema_name
                      AND table_name = 'career_campaign_operating_profile'
                      AND column_name = 'active_guard'),
    'ALTER TABLE `career_campaign_operating_profile`
       ADD COLUMN `active_guard` BIGINT
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `campaign_id` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_087_stmt FROM @v4_087_sql;
EXECUTE v4_087_stmt;
DEALLOCATE PREPARE v4_087_stmt;

-- Never choose a winner for conflicting draft rows. Fail before adding the
-- active-row unique key so an operator can reconcile the data explicitly.
DROP TEMPORARY TABLE IF EXISTS `_v4_087_uniqueness_guard`;
CREATE TEMPORARY TABLE `_v4_087_uniqueness_guard` (
    `dirty_row_count` BIGINT NOT NULL,
    `duplicate_group_count` BIGINT NOT NULL,
    CONSTRAINT `chk_v4_087_no_dirty_profile`
        CHECK (`dirty_row_count` = 0),
    CONSTRAINT `chk_v4_087_no_duplicate_profile`
        CHECK (`duplicate_group_count` = 0)
);
INSERT INTO `_v4_087_uniqueness_guard` (
    `dirty_row_count`,
    `duplicate_group_count`
)
SELECT
    (
        SELECT COUNT(*)
        FROM `career_campaign_operating_profile`
        WHERE `user_id` IS NULL
           OR `campaign_id` IS NULL
           OR `deleted` IS NULL
           OR `deleted` NOT IN (0, 1)
    ),
    (
        SELECT COUNT(*)
        FROM (
            SELECT `user_id`, `campaign_id`
            FROM `career_campaign_operating_profile`
            WHERE `deleted` = 0
            GROUP BY `user_id`, `campaign_id`
            HAVING COUNT(*) > 1
        ) duplicate_group
    );
DROP TEMPORARY TABLE `_v4_087_uniqueness_guard`;

SET @v4_087_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @v4_087_schema_name
                  AND table_name = 'career_campaign_operating_profile'
                  AND index_name = 'uk_career_campaign_operating_profile_live_campaign'),
    'ALTER TABLE `career_campaign_operating_profile`
       ADD UNIQUE KEY `uk_career_campaign_operating_profile_live_campaign`
         (`user_id`, `active_guard`)',
    'SELECT 1'
);
PREPARE v4_087_stmt FROM @v4_087_sql;
EXECUTE v4_087_stmt;
DEALLOCATE PREPARE v4_087_stmt;
