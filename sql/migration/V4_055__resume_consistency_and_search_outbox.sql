SET @schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS `resume_search_sync_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `resume_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `operation` VARCHAR(16) NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    `retry_count` INT NOT NULL DEFAULT 0,
    `next_retry_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_error` VARCHAR(500) DEFAULT NULL,
    `locked_at` DATETIME DEFAULT NULL,
    `locked_by` VARCHAR(64) DEFAULT NULL,
    `delivered_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_resume_search_outbox_retry` (`deleted`, `status`, `next_retry_at`, `id`),
    KEY `idx_resume_search_outbox_resume` (`resume_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reliable resume search synchronization outbox';

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'resume_version'),
    'UPDATE `resume_version` current_version
       JOIN (
         SELECT resume_id, MAX(id) AS keep_id
           FROM `resume_version`
          WHERE deleted = 0 AND current_flag = 1
          GROUP BY resume_id
         HAVING COUNT(*) > 1
       ) duplicate_current ON duplicate_current.resume_id = current_version.resume_id
        SET current_version.current_flag = 0
      WHERE current_version.deleted = 0
        AND current_version.current_flag = 1
        AND current_version.id <> duplicate_current.keep_id',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'resume_version')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name
                     AND table_name = 'resume_version'
                     AND column_name = 'active_current_resume_id'),
    'ALTER TABLE `resume_version`
       ADD COLUMN `active_current_resume_id` BIGINT
       GENERATED ALWAYS AS (
         CASE WHEN `deleted` = 0 AND `current_flag` = 1 THEN `resume_id` ELSE NULL END
       ) STORED',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @schema_name
             AND table_name = 'resume_version'
             AND column_name = 'active_current_resume_id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name
                     AND table_name = 'resume_version'
                     AND index_name = 'uk_resume_version_one_current'),
    'ALTER TABLE `resume_version`
       ADD UNIQUE KEY `uk_resume_version_one_current` (`active_current_resume_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'resume')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name
                     AND table_name = 'resume'
                     AND index_name = 'idx_resume_user_list'),
    'ALTER TABLE `resume`
       ADD INDEX `idx_resume_user_list` (`user_id`, `deleted`, `is_default`, `updated_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'resume_project')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name
                     AND table_name = 'resume_project'
                     AND index_name = 'idx_resume_project_resume_live'),
    'ALTER TABLE `resume_project`
       ADD INDEX `idx_resume_project_resume_live` (`resume_id`, `deleted`, `sort_order`, `sort`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'job_application')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name
                     AND table_name = 'job_application'
                     AND index_name = 'idx_job_application_user_list'),
    'ALTER TABLE `job_application`
       ADD INDEX `idx_job_application_user_list` (`user_id`, `deleted`, `status`, `updated_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'job_application')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name
                     AND table_name = 'job_application'
                     AND index_name = 'idx_job_application_reminder'),
    'ALTER TABLE `job_application`
       ADD INDEX `idx_job_application_reminder`
       (`user_id`, `deleted`, `status`, `next_follow_up_at`, `updated_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
