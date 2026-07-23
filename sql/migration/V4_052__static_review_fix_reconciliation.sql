SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'study_task')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'study_task' AND column_name = 'task_status'),
    'UPDATE `study_task` SET `task_status` = ''TODO'', `updated_at` = NOW() WHERE `deleted` = 0 AND `task_status` = ''PENDING''',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_transcript')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_transcript' AND index_name = 'idx_it_submission_latest'),
    'ALTER TABLE `interview_transcript` ADD INDEX `idx_it_submission_latest` (`voice_submission_id`, `deleted`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission' AND index_name = 'idx_ivs_trace_created'),
    'ALTER TABLE `interview_voice_submission` ADD INDEX `idx_ivs_trace_created` (`trace_id`, `deleted`, `created_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_voice_submission' AND index_name = 'idx_ivs_session_created'),
    'ALTER TABLE `interview_voice_submission` ADD INDEX `idx_ivs_session_created` (`session_id`, `deleted`, `created_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'question_recommendation_batch')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'question_recommendation_batch' AND index_name = 'idx_qrb_retention_cleanup'),
    'ALTER TABLE `question_recommendation_batch` ADD INDEX `idx_qrb_retention_cleanup` (`deleted`, `status`, `updated_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'question_recommendation_batch')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'question_recommendation_batch' AND index_name = 'idx_qrb_legacy_minimize'),
    'ALTER TABLE `question_recommendation_batch` ADD INDEX `idx_qrb_legacy_minimize` (`deleted`, `updated_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
