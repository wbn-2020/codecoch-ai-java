-- CodeCoachAI V3-BE-2: resume to target job match report backend foundation.
-- Scope: create resume_job_match_report/detail and seed RESUME_JOB_MATCH prompt idempotently.

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
  'resume_job_match_report',
  'CREATE TABLE `resume_job_match_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `resume_id` BIGINT NOT NULL COMMENT ''resume id'',
    `target_job_id` BIGINT NOT NULL COMMENT ''target job id'',
    `jd_analysis_id` BIGINT NOT NULL COMMENT ''job description analysis id'',
    `overall_score` INT DEFAULT NULL COMMENT ''overall match score'',
    `tech_stack_score` INT DEFAULT NULL COMMENT ''tech stack score'',
    `project_experience_score` INT DEFAULT NULL COMMENT ''project experience score'',
    `business_fit_score` INT DEFAULT NULL COMMENT ''business fit score'',
    `communication_score` INT DEFAULT NULL COMMENT ''communication score'',
    `strengths_json` LONGTEXT DEFAULT NULL COMMENT ''strengths JSON'',
    `gaps_json` LONGTEXT DEFAULT NULL COMMENT ''gaps JSON'',
    `resume_risks_json` LONGTEXT DEFAULT NULL COMMENT ''resume risks JSON'',
    `optimization_suggestions_json` LONGTEXT DEFAULT NULL COMMENT ''optimization suggestions JSON'',
    `recommended_learning_topics_json` LONGTEXT DEFAULT NULL COMMENT ''recommended learning topics JSON'',
    `recommended_interview_topics_json` LONGTEXT DEFAULT NULL COMMENT ''recommended interview topics JSON'',
    `summary` TEXT DEFAULT NULL COMMENT ''match summary'',
    `raw_result_json` LONGTEXT DEFAULT NULL COMMENT ''raw AI result JSON'',
    `status` VARCHAR(32) NOT NULL DEFAULT ''PROCESSING'' COMMENT ''match report status'',
    `error_message` VARCHAR(1000) DEFAULT NULL COMMENT ''error message'',
    `ai_call_log_id` BIGINT DEFAULT NULL COMMENT ''ai_call_log id'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 resume job match report'''
);

CALL create_table_if_not_exists(
  'resume_job_match_detail',
  'CREATE TABLE `resume_job_match_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary id'',
    `report_id` BIGINT NOT NULL COMMENT ''resume_job_match_report id'',
    `user_id` BIGINT NOT NULL COMMENT ''user id'',
    `dimension` VARCHAR(64) DEFAULT NULL COMMENT ''match dimension'',
    `skill_name` VARCHAR(128) DEFAULT NULL COMMENT ''skill name'',
    `match_level` VARCHAR(32) DEFAULT NULL COMMENT ''match level'',
    `score` INT DEFAULT NULL COMMENT ''detail score'',
    `evidence` TEXT DEFAULT NULL COMMENT ''resume or JD evidence'',
    `gap_description` TEXT DEFAULT NULL COMMENT ''gap description'',
    `suggestion` TEXT DEFAULT NULL COMMENT ''improvement suggestion'',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time'',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time'',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted'',
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''V3 resume job match detail'''
);

CALL add_column_if_not_exists('resume_job_match_report', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'id');
CALL add_column_if_not_exists('resume_job_match_report', 'resume_id', 'BIGINT NOT NULL COMMENT ''resume id''', 'user_id');
CALL add_column_if_not_exists('resume_job_match_report', 'target_job_id', 'BIGINT NOT NULL COMMENT ''target job id''', 'resume_id');
CALL add_column_if_not_exists('resume_job_match_report', 'jd_analysis_id', 'BIGINT NOT NULL COMMENT ''job description analysis id''', 'target_job_id');
CALL add_column_if_not_exists('resume_job_match_report', 'overall_score', 'INT DEFAULT NULL COMMENT ''overall match score''', 'jd_analysis_id');
CALL add_column_if_not_exists('resume_job_match_report', 'tech_stack_score', 'INT DEFAULT NULL COMMENT ''tech stack score''', 'overall_score');
CALL add_column_if_not_exists('resume_job_match_report', 'project_experience_score', 'INT DEFAULT NULL COMMENT ''project experience score''', 'tech_stack_score');
CALL add_column_if_not_exists('resume_job_match_report', 'business_fit_score', 'INT DEFAULT NULL COMMENT ''business fit score''', 'project_experience_score');
CALL add_column_if_not_exists('resume_job_match_report', 'communication_score', 'INT DEFAULT NULL COMMENT ''communication score''', 'business_fit_score');
CALL add_column_if_not_exists('resume_job_match_report', 'strengths_json', 'LONGTEXT DEFAULT NULL COMMENT ''strengths JSON''', 'communication_score');
CALL add_column_if_not_exists('resume_job_match_report', 'gaps_json', 'LONGTEXT DEFAULT NULL COMMENT ''gaps JSON''', 'strengths_json');
CALL add_column_if_not_exists('resume_job_match_report', 'resume_risks_json', 'LONGTEXT DEFAULT NULL COMMENT ''resume risks JSON''', 'gaps_json');
CALL add_column_if_not_exists('resume_job_match_report', 'optimization_suggestions_json', 'LONGTEXT DEFAULT NULL COMMENT ''optimization suggestions JSON''', 'resume_risks_json');
CALL add_column_if_not_exists('resume_job_match_report', 'recommended_learning_topics_json', 'LONGTEXT DEFAULT NULL COMMENT ''recommended learning topics JSON''', 'optimization_suggestions_json');
CALL add_column_if_not_exists('resume_job_match_report', 'recommended_interview_topics_json', 'LONGTEXT DEFAULT NULL COMMENT ''recommended interview topics JSON''', 'recommended_learning_topics_json');
CALL add_column_if_not_exists('resume_job_match_report', 'summary', 'TEXT DEFAULT NULL COMMENT ''match summary''', 'recommended_interview_topics_json');
CALL add_column_if_not_exists('resume_job_match_report', 'raw_result_json', 'LONGTEXT DEFAULT NULL COMMENT ''raw AI result JSON''', 'summary');
CALL add_column_if_not_exists('resume_job_match_report', 'status', 'VARCHAR(32) NOT NULL DEFAULT ''PROCESSING'' COMMENT ''match report status''', 'raw_result_json');
CALL add_column_if_not_exists('resume_job_match_report', 'error_message', 'VARCHAR(1000) DEFAULT NULL COMMENT ''error message''', 'status');
CALL add_column_if_not_exists('resume_job_match_report', 'ai_call_log_id', 'BIGINT DEFAULT NULL COMMENT ''ai_call_log id''', 'error_message');
CALL add_column_if_not_exists('resume_job_match_report', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'ai_call_log_id');
CALL add_column_if_not_exists('resume_job_match_report', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('resume_job_match_report', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_column_if_not_exists('resume_job_match_detail', 'report_id', 'BIGINT NOT NULL COMMENT ''resume_job_match_report id''', 'id');
CALL add_column_if_not_exists('resume_job_match_detail', 'user_id', 'BIGINT NOT NULL COMMENT ''user id''', 'report_id');
CALL add_column_if_not_exists('resume_job_match_detail', 'dimension', 'VARCHAR(64) DEFAULT NULL COMMENT ''match dimension''', 'user_id');
CALL add_column_if_not_exists('resume_job_match_detail', 'skill_name', 'VARCHAR(128) DEFAULT NULL COMMENT ''skill name''', 'dimension');
CALL add_column_if_not_exists('resume_job_match_detail', 'match_level', 'VARCHAR(32) DEFAULT NULL COMMENT ''match level''', 'skill_name');
CALL add_column_if_not_exists('resume_job_match_detail', 'score', 'INT DEFAULT NULL COMMENT ''detail score''', 'match_level');
CALL add_column_if_not_exists('resume_job_match_detail', 'evidence', 'TEXT DEFAULT NULL COMMENT ''resume or JD evidence''', 'score');
CALL add_column_if_not_exists('resume_job_match_detail', 'gap_description', 'TEXT DEFAULT NULL COMMENT ''gap description''', 'evidence');
CALL add_column_if_not_exists('resume_job_match_detail', 'suggestion', 'TEXT DEFAULT NULL COMMENT ''improvement suggestion''', 'gap_description');
CALL add_column_if_not_exists('resume_job_match_detail', 'created_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''created time''', 'suggestion');
CALL add_column_if_not_exists('resume_job_match_detail', 'updated_at', 'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''updated time''', 'created_at');
CALL add_column_if_not_exists('resume_job_match_detail', 'deleted', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''0 active, 1 deleted''', 'updated_at');

CALL add_index_if_not_exists('resume_job_match_report', 'idx_resume_match_user', 'KEY `idx_resume_match_user` (`user_id`)');
CALL add_index_if_not_exists('resume_job_match_report', 'idx_resume_match_resume', 'KEY `idx_resume_match_resume` (`resume_id`, `deleted`)');
CALL add_index_if_not_exists('resume_job_match_report', 'idx_resume_match_target_job', 'KEY `idx_resume_match_target_job` (`target_job_id`, `deleted`)');
CALL add_index_if_not_exists('resume_job_match_report', 'idx_resume_match_status', 'KEY `idx_resume_match_status` (`status`, `deleted`)');
CALL add_index_if_not_exists('resume_job_match_report', 'idx_resume_match_user_resume_job', 'KEY `idx_resume_match_user_resume_job` (`user_id`, `resume_id`, `target_job_id`, `deleted`)');
CALL add_index_if_not_exists('resume_job_match_report', 'idx_resume_match_ai_log', 'KEY `idx_resume_match_ai_log` (`ai_call_log_id`)');

CALL add_index_if_not_exists('resume_job_match_detail', 'idx_resume_match_detail_report', 'KEY `idx_resume_match_detail_report` (`report_id`, `deleted`)');
CALL add_index_if_not_exists('resume_job_match_detail', 'idx_resume_match_detail_user', 'KEY `idx_resume_match_detail_user` (`user_id`, `deleted`)');
CALL add_index_if_not_exists('resume_job_match_detail', 'idx_resume_match_detail_dimension', 'KEY `idx_resume_match_detail_dimension` (`dimension`, `deleted`)');
CALL add_index_if_not_exists('resume_job_match_detail', 'idx_resume_match_detail_skill', 'KEY `idx_resume_match_detail_skill` (`skill_name`, `deleted`)');

INSERT INTO prompt_template (
  scene, name, template_name, description, content, template_content, variables, version, enabled, status
)
SELECT 'RESUME_JOB_MATCH',
       'Resume Job Match',
       'Resume Job Match',
       'V3 resume to target job match analysis prompt',
       'You are a senior Java backend career coach. Generate a resume-to-target-job match report. Inputs include resumeAnalysisJson, resumeSnapshotJson, jobDescriptionAnalysisJson, targetJobJson, and userExperienceYears. Output only one JSON object with overallScore, dimensionScores, strengths, gaps, resumeRisks, optimizationSuggestions, recommendedLearningTopics, recommendedInterviewTopics, and summary.',
       'You are a senior Java backend career coach. Generate a resume-to-target-job match report. Inputs include resumeAnalysisJson, resumeSnapshotJson, jobDescriptionAnalysisJson, targetJobJson, and userExperienceYears. Output only one JSON object with overallScore, dimensionScores, strengths, gaps, resumeRisks, optimizationSuggestions, recommendedLearningTopics, recommendedInterviewTopics, and summary.',
       'reportId,userId,resumeId,targetJobId,jdAnalysisId,resumeAnalysisJson,resumeSnapshotJson,jobDescriptionAnalysisJson,targetJobJson,userExperienceYears',
       'v3-be-2',
       1,
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'RESUME_JOB_MATCH' AND deleted = 0
);

UPDATE prompt_template
SET name = 'Resume Job Match',
    template_name = 'Resume Job Match',
    description = 'V3 resume to target job match analysis prompt',
    variables = 'reportId,userId,resumeId,targetJobId,jdAnalysisId,resumeAnalysisJson,resumeSnapshotJson,jobDescriptionAnalysisJson,targetJobJson,userExperienceYears',
    version = 'v3-be-2',
    enabled = 1,
    status = 1
WHERE scene = 'RESUME_JOB_MATCH'
  AND deleted = 0;

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       'v3-be-2',
       'V3-BE-2 resume job match',
       COALESCE(NULLIF(p.template_content, ''), p.content),
       JSON_OBJECT(
         'reportId', 'resume job match report id',
         'userId', 'user id',
         'resumeId', 'resume id',
         'targetJobId', 'target job id',
         'jdAnalysisId', 'JD analysis id',
         'resumeAnalysisJson', 'latest confirmed resume analysis JSON',
         'resumeSnapshotJson', 'resume and projects snapshot JSON',
         'jobDescriptionAnalysisJson', 'structured JD analysis JSON',
         'targetJobJson', 'target job snapshot JSON',
         'userExperienceYears', 'user experience years or work experience text'
       ),
       'ACTIVE',
       1,
       NOW(),
       'V3-BE-2 initial RESUME_JOB_MATCH prompt'
FROM prompt_template p
WHERE p.scene = 'RESUME_JOB_MATCH'
  AND p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = 'v3-be-2'
 AND v.deleted = 0
SET p.active_version_id = v.id
WHERE p.scene = 'RESUME_JOB_MATCH'
  AND p.deleted = 0
  AND p.active_version_id IS NULL;

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
