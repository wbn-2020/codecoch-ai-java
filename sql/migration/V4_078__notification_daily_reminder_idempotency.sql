-- Make daily reminder creation atomic across concurrent notification queries.
-- The migration is idempotent and preserves historical notification rows.

SET @v4_078_schema_name = DATABASE();

SET @v4_078_notification_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @v4_078_schema_name
      AND table_name = 'notification'
);

SET @v4_078_reminder_date_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @v4_078_schema_name
      AND table_name = 'notification'
      AND column_name = 'reminder_date'
);

SET @v4_078_sql = IF(
    @v4_078_notification_table_exists = 1
        AND @v4_078_reminder_date_exists = 0,
    'ALTER TABLE `notification`
         ADD COLUMN `reminder_date` DATE NULL
         COMMENT ''Business date used to deduplicate daily reminders''
         AFTER `biz_id`',
    'SELECT 1'
);
PREPARE v4_078_stmt FROM @v4_078_sql;
EXECUTE v4_078_stmt;
DEALLOCATE PREPARE v4_078_stmt;

-- Keep all historical rows visible. When an old race produced duplicates, only
-- the earliest non-null row participates in the new uniqueness constraint.
DROP TEMPORARY TABLE IF EXISTS `v4_078_notification_duplicate_ids`;
CREATE TEMPORARY TABLE `v4_078_notification_duplicate_ids` (
    `id` BIGINT NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB;

SET @v4_078_sql = IF(
    @v4_078_notification_table_exists = 1,
    'INSERT INTO `v4_078_notification_duplicate_ids` (`id`)
     SELECT n.`id`
     FROM `notification` n
     JOIN `notification` earlier
       ON earlier.`deleted` = 0
      AND earlier.`user_id` = n.`user_id`
      AND earlier.`type` = n.`type`
      AND earlier.`biz_type` = n.`biz_type`
      AND earlier.`biz_id` = n.`biz_id`
      AND earlier.`reminder_date` = n.`reminder_date`
      AND earlier.`id` < n.`id`
     WHERE n.`deleted` = 0
       AND n.`reminder_date` IS NOT NULL
       AND n.`biz_type` IS NOT NULL
       AND n.`biz_id` IS NOT NULL
     GROUP BY n.`id`',
    'SELECT 1'
);
PREPARE v4_078_stmt FROM @v4_078_sql;
EXECUTE v4_078_stmt;
DEALLOCATE PREPARE v4_078_stmt;

SET @v4_078_sql = IF(
    @v4_078_notification_table_exists = 1,
    'UPDATE `notification` n
       JOIN `v4_078_notification_duplicate_ids` d
         ON d.`id` = n.`id`
        SET n.`reminder_date` = NULL
      WHERE n.`deleted` = 0',
    'SELECT 1'
);
PREPARE v4_078_stmt FROM @v4_078_sql;
EXECUTE v4_078_stmt;
DEALLOCATE PREPARE v4_078_stmt;

DROP TEMPORARY TABLE IF EXISTS `v4_078_notification_duplicate_ids`;

-- Existing reminder rows were previously deduplicated by created_at day.
-- Backfill only one canonical row for each business identity and day. The
-- anti-join keeps the migration safe to execute again after the unique key
-- already exists: historical duplicate rows remain visible with a NULL date.
DROP TEMPORARY TABLE IF EXISTS `v4_078_notification_backfill_candidates`;
CREATE TEMPORARY TABLE `v4_078_notification_backfill_candidates` (
    `id` BIGINT NOT NULL,
    `reminder_date` DATE NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB;

SET @v4_078_sql = IF(
    @v4_078_notification_table_exists = 1,
    'INSERT INTO `v4_078_notification_backfill_candidates` (
         `id`, `reminder_date`
     )
     SELECT
         MIN(planned.`id`),
         planned.`business_date`
     FROM (
         SELECT
             n.`id`,
             n.`user_id`,
             n.`type`,
             n.`biz_type`,
             n.`biz_id`,
             CASE
                 WHEN n.`type` = ''AGENT_REMINDER''
                     THEN DATE_SUB(DATE(n.`created_at`), INTERVAL 1 DAY)
                 ELSE DATE(n.`created_at`)
             END AS `business_date`
         FROM `notification` n
         WHERE n.`reminder_date` IS NULL
           AND n.`deleted` = 0
           AND n.`user_id` > 0
           AND n.`biz_type` IS NOT NULL
           AND n.`biz_id` IS NOT NULL
           AND n.`type` IN (
               ''AGENT_REMINDER'',
               ''APPLICATION_FOLLOW_UP_REMINDER'',
               ''CALENDAR_REMINDER''
           )
     ) planned
     LEFT JOIN `notification` current_notification
       ON current_notification.`deleted` = 0
      AND current_notification.`user_id` = planned.`user_id`
      AND current_notification.`type` = planned.`type`
      AND current_notification.`biz_type` = planned.`biz_type`
      AND current_notification.`biz_id` = planned.`biz_id`
      AND current_notification.`reminder_date` = planned.`business_date`
     WHERE 1 = 1
       AND current_notification.`id` IS NULL
     GROUP BY
         planned.`user_id`,
         planned.`type`,
         planned.`biz_type`,
         planned.`biz_id`,
         planned.`business_date`',
    'SELECT 1'
);
PREPARE v4_078_stmt FROM @v4_078_sql;
EXECUTE v4_078_stmt;
DEALLOCATE PREPARE v4_078_stmt;

SET @v4_078_sql = IF(
    @v4_078_notification_table_exists = 1,
    'UPDATE `notification` n
       JOIN `v4_078_notification_backfill_candidates` c
         ON c.`id` = n.`id`
        SET n.`reminder_date` = c.`reminder_date`',
    'SELECT 1'
);
PREPARE v4_078_stmt FROM @v4_078_sql;
EXECUTE v4_078_stmt;
DEALLOCATE PREPARE v4_078_stmt;

DROP TEMPORARY TABLE IF EXISTS `v4_078_notification_backfill_candidates`;

SET @v4_078_live_date_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @v4_078_schema_name
      AND table_name = 'notification'
      AND column_name = 'live_reminder_date'
);

SET @v4_078_sql = IF(
    @v4_078_notification_table_exists = 1
        AND @v4_078_live_date_exists = 0,
    'ALTER TABLE `notification`
         ADD COLUMN `live_reminder_date` DATE
         GENERATED ALWAYS AS (
             CASE WHEN `deleted` = 0 THEN `reminder_date` ELSE NULL END
         ) STORED
         COMMENT ''Active reminder date used by the daily uniqueness key''',
    'SELECT 1'
);
PREPARE v4_078_stmt FROM @v4_078_sql;
EXECUTE v4_078_stmt;
DEALLOCATE PREPARE v4_078_stmt;

SET @v4_078_unique_key_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @v4_078_schema_name
      AND table_name = 'notification'
      AND index_name = 'uk_notification_daily_reminder'
);

SET @v4_078_sql = IF(
    @v4_078_notification_table_exists = 1
        AND @v4_078_unique_key_exists = 0,
    'ALTER TABLE `notification`
         ADD UNIQUE KEY `uk_notification_daily_reminder` (
             `user_id`,
             `type`,
             `biz_type`,
             `biz_id`,
             `live_reminder_date`
         )',
    'SELECT 1'
);
PREPARE v4_078_stmt FROM @v4_078_sql;
EXECUTE v4_078_stmt;
DEALLOCATE PREPARE v4_078_stmt;
