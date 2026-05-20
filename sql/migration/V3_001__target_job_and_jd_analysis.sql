-- CodeCoachAI V3-BE-1: target job and JD analysis backend foundation.
-- Scope: create target_job and job_description_analysis idempotently without changing V1/V2 tables.

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
  'target_job',
  'CREATE TABLE `target_job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `job_title` VARCHAR(128) NOT NULL COMMENT ''target job title'',
    `company_name` VARCHAR(128) DEFAULT NULL COMMENT ''target company name'',
    `job_level` VARCHAR(64) DEFAULT NULL COMMENT ''job level'',
    `jd_text` LONGTEXT DEFAULT NULL COMMENT ''job description text'',
    `jd_source` VARCHAR(64) DEFAULT NULL COMMENT ''JD source'',
    `current_flag` TINYINT NOT NULL DEFAULT 0 COMMENT ''1 current target job'',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''1 active, 0 disabled'',
    `parse_status` VARCHAR(32) NOT NULL DEFAULT ''NOT_PARSED'' COMMENT ''JD parse status'',
    `parse_error_message` VARCHAR(1000) DEFAULT NULL COMMENT ''JD parse error message'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 target job'''
);

CALL create_table_if_not_exists(
  'job_description_analysis',
  'CREATE TABLE `job_description_analysis` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `target_job_id` BIGINT NOT NULL COMMENT ''target_job id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `job_title` VARCHAR(128) DEFAULT NULL COMMENT ''job title snapshot'',
    `company_name` VARCHAR(128) DEFAULT NULL COMMENT ''company name snapshot'',
    `job_level` VARCHAR(64) DEFAULT NULL COMMENT ''job level snapshot'',
    `responsibilities_json` LONGTEXT DEFAULT NULL COMMENT ''responsibilities JSON'',
    `required_skills_json` LONGTEXT DEFAULT NULL COMMENT ''required skills JSON'',
    `bonus_skills_json` LONGTEXT DEFAULT NULL COMMENT ''bonus skills JSON'',
    `tech_keywords_json` LONGTEXT DEFAULT NULL COMMENT ''technical keywords JSON'',
    `business_keywords_json` LONGTEXT DEFAULT NULL COMMENT ''business keywords JSON'',
    `experience_requirement` TEXT DEFAULT NULL COMMENT ''experience requirement'',
    `project_experience_requirement` TEXT DEFAULT NULL COMMENT ''project experience requirement'',
    `interview_focus_json` LONGTEXT DEFAULT NULL COMMENT ''interview focus JSON'',
    `skill_weights_json` LONGTEXT DEFAULT NULL COMMENT ''skill weights JSON'',
    `summary` TEXT DEFAULT NULL COMMENT ''analysis summary'',
    `raw_result_json` LONGTEXT DEFAULT NULL COMMENT ''raw AI result JSON'',
    `ai_call_log_id` BIGINT DEFAULT NULL COMMENT ''ai_call_log id'',
    `parse_status` VARCHAR(32) NOT NULL DEFAULT ''NOT_PARSED'' COMMENT ''JD parse status'',
    `parse_error_message` VARCHAR(1000) DEFAULT NULL COMMENT ''JD parse error message'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 JD analysis result'''
);

CALL add_column_if_not_exists('target_job', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'id');
CALL add_column_if_not_exists('target_job', 'job_title', 'VARCHAR(128) NOT NULL COMMENT ''target job title''', 'user_id');
CALL add_column_if_not_exists('target_job', 'company_name', 'VARCHAR(128) DEFAULT NULL COMMENT ''target company name''', 'job_title');
CALL add_column_if_not_exists('target_job', 'job_level', 'VARCHAR(64) DEFAULT NULL COMMENT ''job level''', 'company_name');
CALL add_column_if_not_exists('target_job', 'jd_text', 'LONGTEXT DEFAULT NULL COMMENT ''job description text''', 'job_level');
CALL add_column_if_not_exists('target_job', 'jd_source', 'VARCHAR(64) DEFAULT NULL COMMENT ''JD source''', 'jd_text');
CALL add_column_if_not_exists('target_job', 'current_flag', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''1 current target job''', 'jd_source');
CALL add_column_if_not_exists('target_job', 'status', 'TINYINT NOT NULL DEFAULT 1 COMMENT ''1 active, 0 disabled''', 'current_flag');
CALL add_column_if_not_exists('target_job', 'parse_status', 'VARCHAR(32) NOT NULL DEFAULT ''NOT_PARSED'' COMMENT ''JD parse status''', 'status');
CALL add_column_if_not_exists('target_job', 'parse_error_message', 'VARCHAR(1000) DEFAULT NULL COMMENT ''JD parse error message''', 'parse_status');
CALL add_column_if_not_exists('target_job', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'parse_error_message');
CALL add_column_if_not_exists('target_job', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('target_job', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_column_if_not_exists('job_description_analysis', 'target_job_id', 'BIGINT NOT NULL COMMENT ''target_job id''', 'id');
CALL add_column_if_not_exists('job_description_analysis', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'target_job_id');
CALL add_column_if_not_exists('job_description_analysis', 'job_title', 'VARCHAR(128) DEFAULT NULL COMMENT ''job title snapshot''', 'user_id');
CALL add_column_if_not_exists('job_description_analysis', 'company_name', 'VARCHAR(128) DEFAULT NULL COMMENT ''company name snapshot''', 'job_title');
CALL add_column_if_not_exists('job_description_analysis', 'job_level', 'VARCHAR(64) DEFAULT NULL COMMENT ''job level snapshot''', 'company_name');
CALL add_column_if_not_exists('job_description_analysis', 'responsibilities_json', 'LONGTEXT DEFAULT NULL COMMENT ''responsibilities JSON''', 'job_level');
CALL add_column_if_not_exists('job_description_analysis', 'required_skills_json', 'LONGTEXT DEFAULT NULL COMMENT ''required skills JSON''', 'responsibilities_json');
CALL add_column_if_not_exists('job_description_analysis', 'bonus_skills_json', 'LONGTEXT DEFAULT NULL COMMENT ''bonus skills JSON''', 'required_skills_json');
CALL add_column_if_not_exists('job_description_analysis', 'tech_keywords_json', 'LONGTEXT DEFAULT NULL COMMENT ''technical keywords JSON''', 'bonus_skills_json');
CALL add_column_if_not_exists('job_description_analysis', 'business_keywords_json', 'LONGTEXT DEFAULT NULL COMMENT ''business keywords JSON''', 'tech_keywords_json');
CALL add_column_if_not_exists('job_description_analysis', 'experience_requirement', 'TEXT DEFAULT NULL COMMENT ''experience requirement''', 'business_keywords_json');
CALL add_column_if_not_exists('job_description_analysis', 'project_experience_requirement', 'TEXT DEFAULT NULL COMMENT ''project experience requirement''', 'experience_requirement');
CALL add_column_if_not_exists('job_description_analysis', 'interview_focus_json', 'LONGTEXT DEFAULT NULL COMMENT ''interview focus JSON''', 'project_experience_requirement');
CALL add_column_if_not_exists('job_description_analysis', 'skill_weights_json', 'LONGTEXT DEFAULT NULL COMMENT ''skill weights JSON''', 'interview_focus_json');
CALL add_column_if_not_exists('job_description_analysis', 'summary', 'TEXT DEFAULT NULL COMMENT ''analysis summary''', 'skill_weights_json');
CALL add_column_if_not_exists('job_description_analysis', 'raw_result_json', 'LONGTEXT DEFAULT NULL COMMENT ''raw AI result JSON''', 'summary');
CALL add_column_if_not_exists('job_description_analysis', 'ai_call_log_id', 'BIGINT DEFAULT NULL COMMENT ''ai_call_log id''', 'raw_result_json');
CALL add_column_if_not_exists('job_description_analysis', 'parse_status', 'VARCHAR(32) NOT NULL DEFAULT ''NOT_PARSED'' COMMENT ''JD parse status''', 'ai_call_log_id');
CALL add_column_if_not_exists('job_description_analysis', 'parse_error_message', 'VARCHAR(1000) DEFAULT NULL COMMENT ''JD parse error message''', 'parse_status');
CALL add_column_if_not_exists('job_description_analysis', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'parse_error_message');
CALL add_column_if_not_exists('job_description_analysis', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('job_description_analysis', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_index_if_not_exists('target_job', 'idx_target_job_user', 'KEY `idx_target_job_user` (`user_id`)');
CALL add_index_if_not_exists('target_job', 'idx_target_job_current', 'KEY `idx_target_job_current` (`user_id`, `current_flag`, `deleted`)');
CALL add_index_if_not_exists('target_job', 'idx_target_job_status', 'KEY `idx_target_job_status` (`status`, `deleted`)');
CALL add_index_if_not_exists('target_job', 'idx_target_job_parse_status', 'KEY `idx_target_job_parse_status` (`parse_status`, `deleted`)');

CALL add_index_if_not_exists('job_description_analysis', 'idx_jd_analysis_target_job', 'KEY `idx_jd_analysis_target_job` (`target_job_id`, `deleted`)');
CALL add_index_if_not_exists('job_description_analysis', 'idx_jd_analysis_user', 'KEY `idx_jd_analysis_user` (`user_id`, `deleted`)');
CALL add_index_if_not_exists('job_description_analysis', 'idx_jd_analysis_parse_status', 'KEY `idx_jd_analysis_parse_status` (`parse_status`, `deleted`)');
CALL add_index_if_not_exists('job_description_analysis', 'idx_jd_analysis_ai_log', 'KEY `idx_jd_analysis_ai_log` (`ai_call_log_id`)');

INSERT INTO prompt_template (
  scene, name, template_name, description, content, template_content, variables, version, enabled, status
)
SELECT 'JOB_DESCRIPTION_PARSE',
       'Job Description Parse',
       'Job Description Parse',
       'V3 target job JD structured parsing prompt',
       'You are a senior Java backend career coach. Parse this job description into structured JSON. Input: jobTitle={{jobTitle}}, companyName={{companyName}}, jobLevel={{jobLevel}}, userTargetDirection={{userTargetDirection}}, jdText={{jdText}}. Output only one JSON object with jobTitle, companyName, jobLevel, responsibilities, requiredSkills, bonusSkills, techStackKeywords, businessKeywords, experienceRequirement, projectExperienceRequirement, interviewFocusPoints, skillWeights, and summary. requiredSkills items should contain name, category, requiredLevel, weight, and evidence.',
       'You are a senior Java backend career coach. Parse this job description into structured JSON. Input: jobTitle={{jobTitle}}, companyName={{companyName}}, jobLevel={{jobLevel}}, userTargetDirection={{userTargetDirection}}, jdText={{jdText}}. Output only one JSON object with jobTitle, companyName, jobLevel, responsibilities, requiredSkills, bonusSkills, techStackKeywords, businessKeywords, experienceRequirement, projectExperienceRequirement, interviewFocusPoints, skillWeights, and summary. requiredSkills items should contain name, category, requiredLevel, weight, and evidence.',
       'targetJobId,userId,jobTitle,companyName,jobLevel,jdText,jdSource,userTargetDirection',
       'v3-be-1',
       1,
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'JOB_DESCRIPTION_PARSE' AND deleted = 0
);

UPDATE prompt_template
SET name = 'Job Description Parse',
    template_name = 'Job Description Parse',
    description = 'V3 target job JD structured parsing prompt',
    variables = 'targetJobId,userId,jobTitle,companyName,jobLevel,jdText,jdSource,userTargetDirection',
    version = 'v3-be-1',
    enabled = 1,
    status = 1
WHERE scene = 'JOB_DESCRIPTION_PARSE'
  AND deleted = 0;

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       'v3-be-1',
       'V3-BE-1 JD parse',
       COALESCE(NULLIF(p.template_content, ''), p.content),
       JSON_OBJECT(
         'targetJobId', 'target job id',
         'userId', 'user id',
         'jobTitle', 'target job title',
         'companyName', 'company name',
         'jobLevel', 'job level',
         'jdText', 'job description text',
         'jdSource', 'JD source',
         'userTargetDirection', 'optional target direction'
       ),
       'ACTIVE',
       1,
       NOW(),
       'V3-BE-1 initial JOB_DESCRIPTION_PARSE prompt'
FROM prompt_template p
WHERE p.scene = 'JOB_DESCRIPTION_PARSE'
  AND p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = 'v3-be-1'
 AND v.deleted = 0
SET p.active_version_id = v.id
WHERE p.scene = 'JOB_DESCRIPTION_PARSE'
  AND p.deleted = 0
  AND p.active_version_id IS NULL;

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
