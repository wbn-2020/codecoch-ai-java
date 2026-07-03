SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_report' AND column_name = 'rubric_scores'),
    'ALTER TABLE `interview_report` ADD COLUMN `rubric_scores` TEXT DEFAULT NULL COMMENT ''rubric dimension score json'' AFTER `qa_review`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_report' AND column_name = 'follow_up_tree'),
    'ALTER TABLE `interview_report` ADD COLUMN `follow_up_tree` TEXT DEFAULT NULL COMMENT ''follow-up trace tree json'' AFTER `rubric_scores`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_report' AND column_name = 'advice_evidence'),
    'ALTER TABLE `interview_report` ADD COLUMN `advice_evidence` TEXT DEFAULT NULL COMMENT ''AI advice evidence json'' AFTER `follow_up_tree`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_report')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_report' AND column_name = 'ability_profile_updates'),
    'ALTER TABLE `interview_report` ADD COLUMN `ability_profile_updates` TEXT DEFAULT NULL COMMENT ''ability profile update candidates json'' AFTER `advice_evidence`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_message')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_message' AND index_name = 'idx_interview_message_session_parent'),
    'ALTER TABLE `interview_message` ADD INDEX `idx_interview_message_session_parent` (`session_id`, `parent_message_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_message')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_message' AND index_name = 'idx_interview_message_session_stage_type'),
    'ALTER TABLE `interview_message` ADD INDEX `idx_interview_message_session_stage_type` (`session_id`, `stage_id`, `message_type`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
