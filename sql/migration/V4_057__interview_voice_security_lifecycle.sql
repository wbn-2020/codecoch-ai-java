SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission'
                     AND column_name = 'file_delete_status'),
    'ALTER TABLE `interview_voice_submission`
        ADD COLUMN `file_delete_status` VARCHAR(32) NOT NULL DEFAULT ''RETAINED''
        COMMENT ''RETAINED/DELETE_PENDING/DELETED/DELETE_FAILED'' AFTER `fallback_reason`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission'
                     AND column_name = 'file_delete_reason'),
    'ALTER TABLE `interview_voice_submission`
        ADD COLUMN `file_delete_reason` VARCHAR(64) DEFAULT NULL
        COMMENT ''voice cleanup reason'' AFTER `file_delete_status`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission'
                     AND column_name = 'file_delete_requested_at'),
    'ALTER TABLE `interview_voice_submission`
        ADD COLUMN `file_delete_requested_at` DATETIME DEFAULT NULL
        COMMENT ''physical deletion request time'' AFTER `file_delete_reason`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission'
                     AND column_name = 'file_deleted_at'),
    'ALTER TABLE `interview_voice_submission`
        ADD COLUMN `file_deleted_at` DATETIME DEFAULT NULL
        COMMENT ''physical deletion completion time'' AFTER `file_delete_requested_at`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission'
                     AND column_name = 'file_delete_error'),
    'ALTER TABLE `interview_voice_submission`
        ADD COLUMN `file_delete_error` VARCHAR(500) DEFAULT NULL
        COMMENT ''safe physical deletion failure summary'' AFTER `file_deleted_at`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_transcript')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = @schema_name AND table_name = 'interview_transcript'
                     AND column_name = 'answer_source'),
    'ALTER TABLE `interview_transcript`
        ADD COLUMN `answer_source` VARCHAR(64) DEFAULT NULL
        COMMENT ''TEXT/VOICE_TRANSCRIPT/MANUAL_TRANSCRIPT and WITH_TEXT variants''
        AFTER `trace_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission'
                     AND index_name = 'idx_ivs_file_delete_status'),
    'ALTER TABLE `interview_voice_submission`
        ADD INDEX `idx_ivs_file_delete_status`
        (`file_delete_status`, `deleted`, `file_delete_requested_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND EXISTS(SELECT 1 FROM information_schema.columns
               WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission'
                 AND column_name = 'file_delete_status'),
    'UPDATE `interview_voice_submission`
        SET `file_delete_status` = ''DELETE_FAILED'',
            `file_delete_reason` = COALESCE(`file_delete_reason`, ''LEGACY_DISCARD_RETRY_REQUIRED''),
            `file_delete_error` = COALESCE(`file_delete_error`, ''legacy discard has no verified physical deletion record'')
      WHERE `deleted` = 0
        AND `voice_status` IN (''DISCARDED'', ''STALE'')
        AND `file_delete_status` = ''RETAINED''',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
