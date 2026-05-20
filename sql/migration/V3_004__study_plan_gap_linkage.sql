-- CodeCoachAI V3-BE-4: gap-driven study plan backend.
-- Scope: link study plans/tasks to skill_gap_item and seed TARGETED_STUDY_PLAN_GENERATE prompt idempotently.

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

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

CALL add_column_if_not_exists('study_plan', 'target_job_id', 'BIGINT DEFAULT NULL COMMENT ''V3 target_job id''', 'source_id');
CALL add_column_if_not_exists('study_plan', 'skill_profile_id', 'BIGINT DEFAULT NULL COMMENT ''V3 skill_profile id''', 'target_job_id');
CALL add_column_if_not_exists('study_plan', 'match_report_id', 'BIGINT DEFAULT NULL COMMENT ''V3 resume_job_match_report id''', 'skill_profile_id');
CALL add_column_if_not_exists('study_plan', 'daily_minutes', 'INT DEFAULT NULL COMMENT ''daily study minutes''', 'duration_days');
CALL add_column_if_not_exists('study_plan', 'start_date', 'DATE DEFAULT NULL COMMENT ''study plan start date''', 'daily_minutes');

CALL add_column_if_not_exists('study_task', 'target_job_id', 'BIGINT DEFAULT NULL COMMENT ''V3 target_job id''', 'user_id');
CALL add_column_if_not_exists('study_task', 'skill_profile_id', 'BIGINT DEFAULT NULL COMMENT ''V3 skill_profile id''', 'target_job_id');
CALL add_column_if_not_exists('study_task', 'skill_gap_item_id', 'BIGINT DEFAULT NULL COMMENT ''V3 skill_gap_item id''', 'skill_profile_id');
CALL add_column_if_not_exists('study_task', 'source_type', 'VARCHAR(64) DEFAULT NULL COMMENT ''task source type''', 'skill_gap_item_id');
CALL add_column_if_not_exists('study_task', 'source_biz_id', 'BIGINT DEFAULT NULL COMMENT ''task source business id''', 'source_type');
CALL add_column_if_not_exists('study_task', 'estimated_minutes', 'INT DEFAULT NULL COMMENT ''estimated minutes''', 'estimated_hours');
CALL add_column_if_not_exists('study_task', 'acceptance_criteria', 'VARCHAR(500) DEFAULT NULL COMMENT ''task acceptance criteria''', 'estimated_minutes');

CALL create_table_if_not_exists(
  'study_plan_skill_relation',
  'CREATE TABLE `study_plan_skill_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `study_plan_id` BIGINT NOT NULL COMMENT ''study_plan id'',
    `study_task_id` BIGINT DEFAULT NULL COMMENT ''study_task id'',
    `target_job_id` BIGINT DEFAULT NULL COMMENT ''target_job id'',
    `skill_profile_id` BIGINT NOT NULL COMMENT ''skill_profile id'',
    `skill_gap_item_id` BIGINT NOT NULL COMMENT ''skill_gap_item id'',
    `source_type` VARCHAR(64) NOT NULL COMMENT ''source type'',
    `source_biz_id` BIGINT DEFAULT NULL COMMENT ''source business id'',
    `priority` INT DEFAULT NULL COMMENT ''relation priority'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 study plan skill gap relation'''
);

CALL add_column_if_not_exists('study_plan_skill_relation', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'study_plan_id', 'BIGINT NOT NULL COMMENT ''study_plan id''', 'user_id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'study_task_id', 'BIGINT DEFAULT NULL COMMENT ''study_task id''', 'study_plan_id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'target_job_id', 'BIGINT DEFAULT NULL COMMENT ''target_job id''', 'study_task_id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'skill_profile_id', 'BIGINT NOT NULL COMMENT ''skill_profile id''', 'target_job_id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'skill_gap_item_id', 'BIGINT NOT NULL COMMENT ''skill_gap_item id''', 'skill_profile_id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'source_type', 'VARCHAR(64) NOT NULL COMMENT ''source type''', 'skill_gap_item_id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'source_biz_id', 'BIGINT DEFAULT NULL COMMENT ''source business id''', 'source_type');
CALL add_column_if_not_exists('study_plan_skill_relation', 'priority', 'INT DEFAULT NULL COMMENT ''relation priority''', 'source_biz_id');
CALL add_column_if_not_exists('study_plan_skill_relation', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'priority');
CALL add_column_if_not_exists('study_plan_skill_relation', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('study_plan_skill_relation', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_index_if_not_exists('study_plan', 'idx_study_plan_target_job', 'KEY `idx_study_plan_target_job` (`target_job_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan', 'idx_study_plan_skill_profile', 'KEY `idx_study_plan_skill_profile` (`skill_profile_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan', 'idx_study_plan_match_report', 'KEY `idx_study_plan_match_report` (`match_report_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan', 'idx_study_plan_user_source', 'KEY `idx_study_plan_user_source` (`user_id`, `source_type`, `source_id`, `deleted`)');

CALL add_index_if_not_exists('study_task', 'idx_study_task_skill_gap', 'KEY `idx_study_task_skill_gap` (`skill_gap_item_id`, `deleted`)');
CALL add_index_if_not_exists('study_task', 'idx_study_task_profile', 'KEY `idx_study_task_profile` (`skill_profile_id`, `deleted`)');
CALL add_index_if_not_exists('study_task', 'idx_study_task_target_job', 'KEY `idx_study_task_target_job` (`target_job_id`, `deleted`)');
CALL add_index_if_not_exists('study_task', 'idx_study_task_source', 'KEY `idx_study_task_source` (`source_type`, `source_biz_id`, `deleted`)');

CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_user', 'KEY `idx_spsr_user` (`user_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_plan', 'KEY `idx_spsr_plan` (`study_plan_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_task', 'KEY `idx_spsr_task` (`study_task_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_target_job', 'KEY `idx_spsr_target_job` (`target_job_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_profile', 'KEY `idx_spsr_profile` (`skill_profile_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_gap', 'KEY `idx_spsr_gap` (`skill_gap_item_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_source', 'KEY `idx_spsr_source` (`source_type`, `source_biz_id`, `deleted`)');
CALL add_index_if_not_exists('study_plan_skill_relation', 'idx_spsr_plan_gap', 'KEY `idx_spsr_plan_gap` (`study_plan_id`, `skill_gap_item_id`, `deleted`)');

INSERT INTO prompt_template (
  scene, name, template_name, description, content, template_content, variables, version, enabled, status
)
SELECT 'TARGETED_STUDY_PLAN_GENERATE',
       'Targeted Study Plan Generate',
       'Targeted Study Plan Generate',
       'V3 gap-driven study plan generation prompt',
       'You are a senior Java backend career coach. Generate a gap-driven study plan from targetJobJson, skillProfileJson, skillGapsJson, availableDays, dailyMinutes, startDate, and existingStudyPlansJson. Output only one JSON object with planTitle, planSummary, durationDays, and stages. Each item must contain dayOffset, skillName, sourceGapId, taskTitle, taskDescription, taskType, priority, estimatedMinutes, acceptance, relatedTags, and resources.',
       'You are a senior Java backend career coach. Generate a gap-driven study plan from targetJobJson, skillProfileJson, skillGapsJson, availableDays, dailyMinutes, startDate, and existingStudyPlansJson. Output only one JSON object with planTitle, planSummary, durationDays, and stages. Each item must contain dayOffset, skillName, sourceGapId, taskTitle, taskDescription, taskType, priority, estimatedMinutes, acceptance, relatedTags, and resources. Do not output Markdown, code fences, explanations, or invented candidate experience.',
       'learningPlanId,userId,targetJobId,skillProfileId,matchReportId,targetJobJson,skillProfileJson,skillGapsJson,availableDays,dailyMinutes,startDate,existingStudyPlansJson,planTitle',
       'v3-be-4',
       1,
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'TARGETED_STUDY_PLAN_GENERATE' AND deleted = 0
);

UPDATE prompt_template
SET name = 'Targeted Study Plan Generate',
    template_name = 'Targeted Study Plan Generate',
    description = 'V3 gap-driven study plan generation prompt',
    variables = 'learningPlanId,userId,targetJobId,skillProfileId,matchReportId,targetJobJson,skillProfileJson,skillGapsJson,availableDays,dailyMinutes,startDate,existingStudyPlansJson,planTitle',
    version = 'v3-be-4',
    enabled = 1,
    status = 1
WHERE scene = 'TARGETED_STUDY_PLAN_GENERATE'
  AND deleted = 0;

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       'v3-be-4',
       'V3-BE-4 targeted study plan',
       COALESCE(NULLIF(p.template_content, ''), p.content),
       JSON_OBJECT(
         'learningPlanId', 'study plan id',
         'userId', 'user id',
         'targetJobId', 'target job id',
         'skillProfileId', 'skill profile id',
         'matchReportId', 'resume job match report id',
         'targetJobJson', 'target job snapshot JSON',
         'skillProfileJson', 'skill profile summary JSON',
         'skillGapsJson', 'selected skill gap JSON',
         'availableDays', 'plan duration days',
         'dailyMinutes', 'daily learning minutes',
         'startDate', 'plan start date',
         'existingStudyPlansJson', 'existing plan summaries JSON',
         'planTitle', 'requested plan title'
       ),
       'ACTIVE',
       1,
       NOW(),
       'V3-BE-4 initial TARGETED_STUDY_PLAN_GENERATE prompt'
FROM prompt_template p
WHERE p.scene = 'TARGETED_STUDY_PLAN_GENERATE'
  AND p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = 'v3-be-4'
 AND v.deleted = 0
SET p.active_version_id = v.id
WHERE p.scene = 'TARGETED_STUDY_PLAN_GENERATE'
  AND p.deleted = 0
  AND p.active_version_id IS NULL;

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
