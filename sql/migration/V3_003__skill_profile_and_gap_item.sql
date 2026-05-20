-- CodeCoachAI V3-BE-3: skill profile and skill gap backend foundation.
-- Scope: create skill_profile/skill_gap_item and seed SKILL_GAP_ANALYZE prompt idempotently.

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

CALL create_table_if_not_exists(
  'skill_profile',
  'CREATE TABLE `skill_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `target_job_id` BIGINT NOT NULL COMMENT ''target job id'',
    `match_report_id` BIGINT NOT NULL COMMENT ''resume_job_match_report id'',
    `profile_name` VARCHAR(128) DEFAULT NULL COMMENT ''profile name'',
    `overall_level` INT DEFAULT NULL COMMENT ''overall level 1-5'',
    `overall_score` INT DEFAULT NULL COMMENT ''overall score 0-100'',
    `summary` TEXT DEFAULT NULL COMMENT ''profile summary'',
    `source_type` VARCHAR(64) NOT NULL DEFAULT ''RESUME_JOB_MATCH'' COMMENT ''source type'',
    `source_biz_id` BIGINT DEFAULT NULL COMMENT ''source business id'',
    `status` VARCHAR(32) NOT NULL DEFAULT ''PROCESSING'' COMMENT ''profile status'',
    `raw_result_json` LONGTEXT DEFAULT NULL COMMENT ''raw AI result JSON'',
    `ai_call_log_id` BIGINT DEFAULT NULL COMMENT ''ai_call_log id'',
    `error_message` VARCHAR(1000) DEFAULT NULL COMMENT ''error message'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 skill profile'''
);

CALL create_table_if_not_exists(
  'skill_gap_item',
  'CREATE TABLE `skill_gap_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `profile_id` BIGINT NOT NULL COMMENT ''skill_profile id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `target_job_id` BIGINT NOT NULL COMMENT ''target job id'',
    `skill_name` VARCHAR(128) NOT NULL COMMENT ''skill name'',
    `category` VARCHAR(64) DEFAULT NULL COMMENT ''skill category'',
    `target_level` INT DEFAULT NULL COMMENT ''target level'',
    `current_level` INT DEFAULT NULL COMMENT ''current level'',
    `gap_level` INT DEFAULT NULL COMMENT ''gap level'',
    `confidence` DECIMAL(5,4) DEFAULT NULL COMMENT ''confidence 0-1'',
    `severity` VARCHAR(32) DEFAULT NULL COMMENT ''gap severity'',
    `evidence_sources_json` LONGTEXT DEFAULT NULL COMMENT ''evidence sources JSON'',
    `gap_description` TEXT DEFAULT NULL COMMENT ''gap description'',
    `recommended_actions_json` LONGTEXT DEFAULT NULL COMMENT ''recommended actions JSON'',
    `priority` INT DEFAULT NULL COMMENT ''priority order'',
    `source_type` VARCHAR(64) NOT NULL DEFAULT ''RESUME_JOB_MATCH'' COMMENT ''source type'',
    `source_biz_id` BIGINT DEFAULT NULL COMMENT ''source business id'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 skill gap item'''
);

CALL add_column_if_not_exists('skill_profile', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'id');
CALL add_column_if_not_exists('skill_profile', 'target_job_id', 'BIGINT NOT NULL COMMENT ''target job id''', 'user_id');
CALL add_column_if_not_exists('skill_profile', 'match_report_id', 'BIGINT NOT NULL COMMENT ''resume_job_match_report id''', 'target_job_id');
CALL add_column_if_not_exists('skill_profile', 'profile_name', 'VARCHAR(128) DEFAULT NULL COMMENT ''profile name''', 'match_report_id');
CALL add_column_if_not_exists('skill_profile', 'overall_level', 'INT DEFAULT NULL COMMENT ''overall level 1-5''', 'profile_name');
CALL add_column_if_not_exists('skill_profile', 'overall_score', 'INT DEFAULT NULL COMMENT ''overall score 0-100''', 'overall_level');
CALL add_column_if_not_exists('skill_profile', 'summary', 'TEXT DEFAULT NULL COMMENT ''profile summary''', 'overall_score');
CALL add_column_if_not_exists('skill_profile', 'source_type', 'VARCHAR(64) NOT NULL DEFAULT ''RESUME_JOB_MATCH'' COMMENT ''source type''', 'summary');
CALL add_column_if_not_exists('skill_profile', 'source_biz_id', 'BIGINT DEFAULT NULL COMMENT ''source business id''', 'source_type');
CALL add_column_if_not_exists('skill_profile', 'status', 'VARCHAR(32) NOT NULL DEFAULT ''PROCESSING'' COMMENT ''profile status''', 'source_biz_id');
CALL add_column_if_not_exists('skill_profile', 'raw_result_json', 'LONGTEXT DEFAULT NULL COMMENT ''raw AI result JSON''', 'status');
CALL add_column_if_not_exists('skill_profile', 'ai_call_log_id', 'BIGINT DEFAULT NULL COMMENT ''ai_call_log id''', 'raw_result_json');
CALL add_column_if_not_exists('skill_profile', 'error_message', 'VARCHAR(1000) DEFAULT NULL COMMENT ''error message''', 'ai_call_log_id');
CALL add_column_if_not_exists('skill_profile', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'error_message');
CALL add_column_if_not_exists('skill_profile', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('skill_profile', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_column_if_not_exists('skill_gap_item', 'profile_id', 'BIGINT NOT NULL COMMENT ''skill_profile id''', 'id');
CALL add_column_if_not_exists('skill_gap_item', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'profile_id');
CALL add_column_if_not_exists('skill_gap_item', 'target_job_id', 'BIGINT NOT NULL COMMENT ''target job id''', 'user_id');
CALL add_column_if_not_exists('skill_gap_item', 'skill_name', 'VARCHAR(128) NOT NULL COMMENT ''skill name''', 'target_job_id');
CALL add_column_if_not_exists('skill_gap_item', 'category', 'VARCHAR(64) DEFAULT NULL COMMENT ''skill category''', 'skill_name');
CALL add_column_if_not_exists('skill_gap_item', 'target_level', 'INT DEFAULT NULL COMMENT ''target level''', 'category');
CALL add_column_if_not_exists('skill_gap_item', 'current_level', 'INT DEFAULT NULL COMMENT ''current level''', 'target_level');
CALL add_column_if_not_exists('skill_gap_item', 'gap_level', 'INT DEFAULT NULL COMMENT ''gap level''', 'current_level');
CALL add_column_if_not_exists('skill_gap_item', 'confidence', 'DECIMAL(5,4) DEFAULT NULL COMMENT ''confidence 0-1''', 'gap_level');
CALL add_column_if_not_exists('skill_gap_item', 'severity', 'VARCHAR(32) DEFAULT NULL COMMENT ''gap severity''', 'confidence');
CALL add_column_if_not_exists('skill_gap_item', 'evidence_sources_json', 'LONGTEXT DEFAULT NULL COMMENT ''evidence sources JSON''', 'severity');
CALL add_column_if_not_exists('skill_gap_item', 'gap_description', 'TEXT DEFAULT NULL COMMENT ''gap description''', 'evidence_sources_json');
CALL add_column_if_not_exists('skill_gap_item', 'recommended_actions_json', 'LONGTEXT DEFAULT NULL COMMENT ''recommended actions JSON''', 'gap_description');
CALL add_column_if_not_exists('skill_gap_item', 'priority', 'INT DEFAULT NULL COMMENT ''priority order''', 'recommended_actions_json');
CALL add_column_if_not_exists('skill_gap_item', 'source_type', 'VARCHAR(64) NOT NULL DEFAULT ''RESUME_JOB_MATCH'' COMMENT ''source type''', 'priority');
CALL add_column_if_not_exists('skill_gap_item', 'source_biz_id', 'BIGINT DEFAULT NULL COMMENT ''source business id''', 'source_type');
CALL add_column_if_not_exists('skill_gap_item', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'source_biz_id');
CALL add_column_if_not_exists('skill_gap_item', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('skill_gap_item', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_index_if_not_exists('skill_profile', 'idx_skill_profile_user', 'KEY `idx_skill_profile_user` (`user_id`)');
CALL add_index_if_not_exists('skill_profile', 'idx_skill_profile_target_job', 'KEY `idx_skill_profile_target_job` (`target_job_id`, `deleted`)');
CALL add_index_if_not_exists('skill_profile', 'idx_skill_profile_match_report', 'KEY `idx_skill_profile_match_report` (`match_report_id`, `deleted`)');
CALL add_index_if_not_exists('skill_profile', 'idx_skill_profile_status', 'KEY `idx_skill_profile_status` (`status`, `deleted`)');
CALL add_index_if_not_exists('skill_profile', 'idx_skill_profile_user_job_status', 'KEY `idx_skill_profile_user_job_status` (`user_id`, `target_job_id`, `status`, `deleted`)');
CALL add_index_if_not_exists('skill_profile', 'idx_skill_profile_ai_log', 'KEY `idx_skill_profile_ai_log` (`ai_call_log_id`)');

CALL add_index_if_not_exists('skill_gap_item', 'idx_skill_gap_profile', 'KEY `idx_skill_gap_profile` (`profile_id`, `deleted`)');
CALL add_index_if_not_exists('skill_gap_item', 'idx_skill_gap_user', 'KEY `idx_skill_gap_user` (`user_id`, `deleted`)');
CALL add_index_if_not_exists('skill_gap_item', 'idx_skill_gap_target_job', 'KEY `idx_skill_gap_target_job` (`target_job_id`, `deleted`)');
CALL add_index_if_not_exists('skill_gap_item', 'idx_skill_gap_skill', 'KEY `idx_skill_gap_skill` (`skill_name`, `deleted`)');
CALL add_index_if_not_exists('skill_gap_item', 'idx_skill_gap_severity', 'KEY `idx_skill_gap_severity` (`severity`, `deleted`)');
CALL add_index_if_not_exists('skill_gap_item', 'idx_skill_gap_profile_priority', 'KEY `idx_skill_gap_profile_priority` (`profile_id`, `priority`, `deleted`)');

INSERT INTO prompt_template (
  scene, name, template_name, description, content, template_content, variables, version, enabled, status
)
SELECT 'SKILL_GAP_ANALYZE',
       'Skill Gap Analyze',
       'Skill Gap Analyze',
       'V3 skill profile generation prompt',
       'You are a senior Java backend career coach. Generate a target-job skill profile from resume-job match evidence. Output only one JSON object with profileSummary, overallLevel, overallScore, skillGaps, nextPrioritySkills, and nextActions. skillGaps items must contain skillName, category, targetLevel, currentLevel, gapLevel, confidence, severity, evidenceSources, gapDescription, recommendedActions, and priority.',
       'You are a senior Java backend career coach. Generate a target-job skill profile from resume-job match evidence. Output only one JSON object with profileSummary, overallLevel, overallScore, skillGaps, nextPrioritySkills, and nextActions. skillGaps items must contain skillName, category, targetLevel, currentLevel, gapLevel, confidence, severity, evidenceSources, gapDescription, recommendedActions, and priority.',
       'profileId,matchReportId,userId,resumeId,targetJobId,jdAnalysisId,targetJobJson,jobDescriptionAnalysisJson,matchReportJson,matchDetailsJson,gapsJson,recommendedLearningTopicsJson,recommendedInterviewTopicsJson,resumeAnalysisJson,resumeSnapshotJson',
       'v3-be-3',
       1,
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'SKILL_GAP_ANALYZE' AND deleted = 0
);

UPDATE prompt_template
SET name = 'Skill Gap Analyze',
    template_name = 'Skill Gap Analyze',
    description = 'V3 skill profile generation prompt',
    variables = 'profileId,matchReportId,userId,resumeId,targetJobId,jdAnalysisId,targetJobJson,jobDescriptionAnalysisJson,matchReportJson,matchDetailsJson,gapsJson,recommendedLearningTopicsJson,recommendedInterviewTopicsJson,resumeAnalysisJson,resumeSnapshotJson',
    version = 'v3-be-3',
    enabled = 1,
    status = 1
WHERE scene = 'SKILL_GAP_ANALYZE'
  AND deleted = 0;

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       'v3-be-3',
       'V3-BE-3 skill gap analyze',
       COALESCE(NULLIF(p.template_content, ''), p.content),
       JSON_OBJECT(
         'profileId', 'skill profile id',
         'matchReportId', 'resume job match report id',
         'userId', 'user id',
         'resumeId', 'resume id',
         'targetJobId', 'target job id',
         'jdAnalysisId', 'JD analysis id',
         'targetJobJson', 'target job snapshot JSON',
         'jobDescriptionAnalysisJson', 'structured JD analysis JSON',
         'matchReportJson', 'resume job match report snapshot JSON',
         'matchDetailsJson', 'resume job match detail JSON',
         'gapsJson', 'match report gaps JSON',
         'recommendedLearningTopicsJson', 'recommended learning topics JSON',
         'recommendedInterviewTopicsJson', 'recommended interview topics JSON',
         'resumeAnalysisJson', 'latest resume analysis JSON',
         'resumeSnapshotJson', 'resume and projects snapshot JSON'
       ),
       'ACTIVE',
       1,
       NOW(),
       'V3-BE-3 initial SKILL_GAP_ANALYZE prompt'
FROM prompt_template p
WHERE p.scene = 'SKILL_GAP_ANALYZE'
  AND p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = 'v3-be-3'
 AND v.deleted = 0
SET p.active_version_id = v.id
WHERE p.scene = 'SKILL_GAP_ANALYZE'
  AND p.deleted = 0
  AND p.active_version_id IS NULL;

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
