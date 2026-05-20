-- CodeCoachAI V3-BE-5: gap-driven question recommendation backend.
-- Scope: create question recommendation batch/item tables and seed TARGETED_QUESTION_RECOMMEND prompt idempotently.

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

DELIMITER //

CREATE PROCEDURE create_table_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN table_definition_value LONGTEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
  ) THEN
    SET @create_sql = table_definition_value;
    PREPARE create_stmt FROM @create_sql;
    EXECUTE create_stmt;
    DEALLOCATE PREPARE create_stmt;
  END IF;
END//

CREATE PROCEDURE add_column_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT,
  IN after_column_value VARCHAR(64)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @alter_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ',
      column_definition_value,
      IF(after_column_value IS NULL OR after_column_value = '', '', CONCAT(' AFTER `', after_column_value, '`'))
    );
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

CREATE PROCEDURE add_index_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN index_name_value VARCHAR(64),
  IN index_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND index_name = index_name_value
  ) THEN
    SET @alter_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

DELIMITER ;

CALL create_table_if_not_exists(
  'question_recommendation_batch',
  'CREATE TABLE `question_recommendation_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `source_type` VARCHAR(64) NOT NULL COMMENT ''recommendation source type'',
    `source_id` BIGINT DEFAULT NULL COMMENT ''source business id'',
    `job_target_id` BIGINT DEFAULT NULL COMMENT ''target_job id'',
    `match_report_id` BIGINT DEFAULT NULL COMMENT ''resume_job_match_report id'',
    `skill_profile_id` BIGINT DEFAULT NULL COMMENT ''skill_profile id'',
    `study_plan_id` BIGINT DEFAULT NULL COMMENT ''study_plan id'',
    `strategy` VARCHAR(64) NOT NULL DEFAULT ''GAP_PRIORITY'' COMMENT ''recommendation strategy'',
    `question_count` INT NOT NULL DEFAULT 0 COMMENT ''recommended question count'',
    `status` VARCHAR(32) NOT NULL DEFAULT ''GENERATING'' COMMENT ''batch status'',
    `ai_call_log_id` BIGINT DEFAULT NULL COMMENT ''ai_call_log id'',
    `request_json` LONGTEXT DEFAULT NULL COMMENT ''request snapshot JSON'',
    `result_json` LONGTEXT DEFAULT NULL COMMENT ''AI result JSON'',
    `error_message` VARCHAR(1000) DEFAULT NULL COMMENT ''error message'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 question recommendation batch'''
);

CALL create_table_if_not_exists(
  'question_recommendation_item',
  'CREATE TABLE `question_recommendation_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `batch_id` BIGINT NOT NULL COMMENT ''question_recommendation_batch id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `question_id` BIGINT DEFAULT NULL COMMENT ''existing question id'',
    `question_title` VARCHAR(255) NOT NULL COMMENT ''question title'',
    `question_content` LONGTEXT DEFAULT NULL COMMENT ''question content'',
    `question_type` VARCHAR(64) DEFAULT NULL COMMENT ''question type'',
    `difficulty` VARCHAR(32) DEFAULT NULL COMMENT ''question difficulty'',
    `skill_code` VARCHAR(64) DEFAULT NULL COMMENT ''skill code'',
    `skill_name` VARCHAR(128) DEFAULT NULL COMMENT ''skill name'',
    `gap_severity` VARCHAR(32) DEFAULT NULL COMMENT ''gap severity'',
    `recommend_reason` VARCHAR(1000) DEFAULT NULL COMMENT ''recommend reason'',
    `answer_hint` LONGTEXT DEFAULT NULL COMMENT ''answer hint'',
    `evaluate_points` LONGTEXT DEFAULT NULL COMMENT ''evaluate points'',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT ''sort order'',
    `practice_status` VARCHAR(32) NOT NULL DEFAULT ''UNPRACTICED'' COMMENT ''practice status'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 question recommendation item'''
);

CALL add_column_if_not_exists('question_recommendation_batch', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'id');
CALL add_column_if_not_exists('question_recommendation_batch', 'source_type', 'VARCHAR(64) NOT NULL COMMENT ''recommendation source type''', 'user_id');
CALL add_column_if_not_exists('question_recommendation_batch', 'source_id', 'BIGINT DEFAULT NULL COMMENT ''source business id''', 'source_type');
CALL add_column_if_not_exists('question_recommendation_batch', 'job_target_id', 'BIGINT DEFAULT NULL COMMENT ''target_job id''', 'source_id');
CALL add_column_if_not_exists('question_recommendation_batch', 'match_report_id', 'BIGINT DEFAULT NULL COMMENT ''resume_job_match_report id''', 'job_target_id');
CALL add_column_if_not_exists('question_recommendation_batch', 'skill_profile_id', 'BIGINT DEFAULT NULL COMMENT ''skill_profile id''', 'match_report_id');
CALL add_column_if_not_exists('question_recommendation_batch', 'study_plan_id', 'BIGINT DEFAULT NULL COMMENT ''study_plan id''', 'skill_profile_id');
CALL add_column_if_not_exists('question_recommendation_batch', 'strategy', 'VARCHAR(64) NOT NULL DEFAULT ''GAP_PRIORITY'' COMMENT ''recommendation strategy''', 'study_plan_id');
CALL add_column_if_not_exists('question_recommendation_batch', 'question_count', 'INT NOT NULL DEFAULT 0 COMMENT ''recommended question count''', 'strategy');
CALL add_column_if_not_exists('question_recommendation_batch', 'status', 'VARCHAR(32) NOT NULL DEFAULT ''GENERATING'' COMMENT ''batch status''', 'question_count');
CALL add_column_if_not_exists('question_recommendation_batch', 'ai_call_log_id', 'BIGINT DEFAULT NULL COMMENT ''ai_call_log id''', 'status');
CALL add_column_if_not_exists('question_recommendation_batch', 'request_json', 'LONGTEXT DEFAULT NULL COMMENT ''request snapshot JSON''', 'ai_call_log_id');
CALL add_column_if_not_exists('question_recommendation_batch', 'result_json', 'LONGTEXT DEFAULT NULL COMMENT ''AI result JSON''', 'request_json');
CALL add_column_if_not_exists('question_recommendation_batch', 'error_message', 'VARCHAR(1000) DEFAULT NULL COMMENT ''error message''', 'result_json');
CALL add_column_if_not_exists('question_recommendation_batch', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'error_message');
CALL add_column_if_not_exists('question_recommendation_batch', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('question_recommendation_batch', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_column_if_not_exists('question_recommendation_item', 'batch_id', 'BIGINT NOT NULL COMMENT ''question_recommendation_batch id''', 'id');
CALL add_column_if_not_exists('question_recommendation_item', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'batch_id');
CALL add_column_if_not_exists('question_recommendation_item', 'question_id', 'BIGINT DEFAULT NULL COMMENT ''existing question id''', 'user_id');
CALL add_column_if_not_exists('question_recommendation_item', 'question_title', 'VARCHAR(255) NOT NULL COMMENT ''question title''', 'question_id');
CALL add_column_if_not_exists('question_recommendation_item', 'question_content', 'LONGTEXT DEFAULT NULL COMMENT ''question content''', 'question_title');
CALL add_column_if_not_exists('question_recommendation_item', 'question_type', 'VARCHAR(64) DEFAULT NULL COMMENT ''question type''', 'question_content');
CALL add_column_if_not_exists('question_recommendation_item', 'difficulty', 'VARCHAR(32) DEFAULT NULL COMMENT ''question difficulty''', 'question_type');
CALL add_column_if_not_exists('question_recommendation_item', 'skill_code', 'VARCHAR(64) DEFAULT NULL COMMENT ''skill code''', 'difficulty');
CALL add_column_if_not_exists('question_recommendation_item', 'skill_name', 'VARCHAR(128) DEFAULT NULL COMMENT ''skill name''', 'skill_code');
CALL add_column_if_not_exists('question_recommendation_item', 'gap_severity', 'VARCHAR(32) DEFAULT NULL COMMENT ''gap severity''', 'skill_name');
CALL add_column_if_not_exists('question_recommendation_item', 'recommend_reason', 'VARCHAR(1000) DEFAULT NULL COMMENT ''recommend reason''', 'gap_severity');
CALL add_column_if_not_exists('question_recommendation_item', 'answer_hint', 'LONGTEXT DEFAULT NULL COMMENT ''answer hint''', 'recommend_reason');
CALL add_column_if_not_exists('question_recommendation_item', 'evaluate_points', 'LONGTEXT DEFAULT NULL COMMENT ''evaluate points''', 'answer_hint');
CALL add_column_if_not_exists('question_recommendation_item', 'sort_order', 'INT NOT NULL DEFAULT 0 COMMENT ''sort order''', 'evaluate_points');
CALL add_column_if_not_exists('question_recommendation_item', 'practice_status', 'VARCHAR(32) NOT NULL DEFAULT ''UNPRACTICED'' COMMENT ''practice status''', 'sort_order');
CALL add_column_if_not_exists('question_recommendation_item', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'practice_status');
CALL add_column_if_not_exists('question_recommendation_item', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('question_recommendation_item', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_user', 'KEY `idx_qrb_user` (`user_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_source', 'KEY `idx_qrb_source` (`source_type`, `source_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_target_job', 'KEY `idx_qrb_target_job` (`job_target_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_match_report', 'KEY `idx_qrb_match_report` (`match_report_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_skill_profile', 'KEY `idx_qrb_skill_profile` (`skill_profile_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_study_plan', 'KEY `idx_qrb_study_plan` (`study_plan_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_status', 'KEY `idx_qrb_status` (`status`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_batch', 'idx_qrb_ai_log', 'KEY `idx_qrb_ai_log` (`ai_call_log_id`)');

CALL add_index_if_not_exists('question_recommendation_item', 'idx_qri_batch', 'KEY `idx_qri_batch` (`batch_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_item', 'idx_qri_user', 'KEY `idx_qri_user` (`user_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_item', 'idx_qri_question', 'KEY `idx_qri_question` (`question_id`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_item', 'idx_qri_skill', 'KEY `idx_qri_skill` (`skill_name`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_item', 'idx_qri_practice_status', 'KEY `idx_qri_practice_status` (`practice_status`, `deleted`)');
CALL add_index_if_not_exists('question_recommendation_item', 'idx_qri_batch_order', 'KEY `idx_qri_batch_order` (`batch_id`, `sort_order`, `deleted`)');

INSERT INTO prompt_template (
  scene, name, template_name, description, content, template_content, variables, version, enabled, status
)
SELECT 'TARGETED_QUESTION_RECOMMEND',
       'Targeted Question Recommend',
       'Targeted Question Recommend',
       'V3 gap-driven question recommendation prompt',
       'You are a senior Java backend interview training coach. Generate target-job question recommendations from targetJobJson, matchReportJson, skillProfileJson, skillGapsJson, studyPlanJson and studyTasksJson. Output only one JSON object with questions array. Each item must contain title, content, questionType, difficulty, skillName, gapSeverity, recommendReason, answerHint and evaluatePoints.',
       'You are a senior Java backend interview training coach. Generate target-job question recommendations from targetJobJson, matchReportJson, skillProfileJson, skillGapsJson, studyPlanJson and studyTasksJson. Output only one JSON object with questions array. Each item must contain title, content, questionType, difficulty, skillName, gapSeverity, recommendReason, answerHint and evaluatePoints. Do not output Markdown, code fences, explanations, or invented candidate experience.',
       'batchId,userId,sourceType,sourceId,targetJobId,matchReportId,skillProfileId,studyPlanId,strategy,questionCount,difficultyPreference,targetJobJson,matchReportJson,skillProfileJson,skillGapsJson,studyPlanJson,studyTasksJson',
       'v3-be-5',
       1,
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'TARGETED_QUESTION_RECOMMEND' AND deleted = 0
);

UPDATE prompt_template
SET name = 'Targeted Question Recommend',
    template_name = 'Targeted Question Recommend',
    description = 'V3 gap-driven question recommendation prompt',
    variables = 'batchId,userId,sourceType,sourceId,targetJobId,matchReportId,skillProfileId,studyPlanId,strategy,questionCount,difficultyPreference,targetJobJson,matchReportJson,skillProfileJson,skillGapsJson,studyPlanJson,studyTasksJson',
    version = 'v3-be-5',
    enabled = 1,
    status = 1
WHERE scene = 'TARGETED_QUESTION_RECOMMEND'
  AND deleted = 0;

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       'v3-be-5',
       'V3-BE-5 targeted question recommendation',
       COALESCE(NULLIF(p.template_content, ''), p.content),
       JSON_OBJECT(
         'batchId', 'recommendation batch id',
         'userId', 'user id',
         'sourceType', 'JD_GAP / RESUME_JOB_MATCH / STUDY_PLAN',
         'targetJobJson', 'target job snapshot',
         'matchReportJson', 'resume job match snapshot',
         'skillProfileJson', 'skill profile snapshot',
         'skillGapsJson', 'selected skill gaps',
         'studyPlanJson', 'study plan snapshot',
         'studyTasksJson', 'study task snapshots',
         'questionCount', 'question count',
         'difficultyPreference', 'difficulty preference'
       ),
       'ACTIVE',
       1,
       NOW(),
       'V3-BE-5 initial TARGETED_QUESTION_RECOMMEND prompt'
FROM prompt_template p
WHERE p.scene = 'TARGETED_QUESTION_RECOMMEND'
  AND p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = 'v3-be-5'
 AND v.deleted = 0
SET p.active_version_id = v.id
WHERE p.scene = 'TARGETED_QUESTION_RECOMMEND'
  AND p.deleted = 0
  AND p.active_version_id IS NULL;

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
