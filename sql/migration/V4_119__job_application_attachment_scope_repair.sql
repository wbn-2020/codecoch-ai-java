-- Repair databases where the attachment table was created from the legacy
-- init schema before application-scoped attachments were supported.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'job_application_attachment'
          AND column_name = 'package_id'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `job_application_attachment`
       MODIFY COLUMN `package_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'job_application_attachment'
      AND index_name = 'uk_job_application_attachment_live_file'
);
SET @sql = IF(
    @index_columns IS NULL,
    'ALTER TABLE `job_application_attachment`
       ADD UNIQUE KEY `uk_job_application_attachment_live_file` (`active_file_id`)',
    IF(
        @index_columns <> 'active_file_id',
        'ALTER TABLE `job_application_attachment`
           DROP INDEX `uk_job_application_attachment_live_file`,
           ADD UNIQUE KEY `uk_job_application_attachment_live_file` (`active_file_id`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'job_application_attachment'
      AND index_name = 'idx_job_application_attachment_application'
);
SET @sql = IF(
    @index_columns IS NULL,
    'ALTER TABLE `job_application_attachment`
       ADD KEY `idx_job_application_attachment_application`
         (`user_id`, `application_id`, `deleted`, `sort_order`, `id`)',
    IF(
        @index_columns <> 'user_id,application_id,deleted,sort_order,id',
        'ALTER TABLE `job_application_attachment`
           DROP INDEX `idx_job_application_attachment_application`,
           ADD KEY `idx_job_application_attachment_application`
             (`user_id`, `application_id`, `deleted`, `sort_order`, `id`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_application_attachment'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = @schema_name
          AND table_name = 'job_application_attachment'
          AND constraint_name = 'chk_job_application_attachment_scope'
          AND constraint_type = 'CHECK'
    ),
    'ALTER TABLE `job_application_attachment`
       ADD CONSTRAINT `chk_job_application_attachment_scope`
       CHECK (`package_id` IS NOT NULL OR `application_id` IS NOT NULL)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
