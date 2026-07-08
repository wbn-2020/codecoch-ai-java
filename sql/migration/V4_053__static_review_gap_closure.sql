SET @schema_name = DATABASE();

-- Re-run the ai_model_config default-provider guard only when the target table/columns exist.
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'ai_model_config')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'provider')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'default_model')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'deleted')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'sort_order')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'updated_at')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'id'),
    'UPDATE `ai_model_config` target
       JOIN (
         SELECT id
           FROM (
             SELECT loser.id
               FROM `ai_model_config` loser
               JOIN `ai_model_config` winner
                 ON winner.provider = loser.provider
                AND winner.deleted = 0
                AND winner.default_model = 1
                AND (
                     COALESCE(winner.sort_order, 0) < COALESCE(loser.sort_order, 0)
                  OR (COALESCE(winner.sort_order, 0) = COALESCE(loser.sort_order, 0)
                      AND COALESCE(winner.updated_at, ''1970-01-01'') > COALESCE(loser.updated_at, ''1970-01-01''))
                  OR (COALESCE(winner.sort_order, 0) = COALESCE(loser.sort_order, 0)
                      AND COALESCE(winner.updated_at, ''1970-01-01'') = COALESCE(loser.updated_at, ''1970-01-01'')
                      AND winner.id > loser.id)
                )
              WHERE loser.deleted = 0
                AND loser.default_model = 1
           ) duplicate_defaults
       ) rows_to_clear ON rows_to_clear.id = target.id
        SET target.default_model = 0',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'ai_model_config')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'provider')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'default_model')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'deleted')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'active_default_provider'),
    'ALTER TABLE `ai_model_config`
       ADD COLUMN `active_default_provider` VARCHAR(64)
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 AND `default_model` = 1 THEN `provider` ELSE NULL END) STORED
       COMMENT ''Unique guard for one active default model per provider''',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'ai_model_config')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'active_default_provider')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND index_name = 'uk_ai_model_one_default_provider'),
    'ALTER TABLE `ai_model_config` ADD UNIQUE KEY `uk_ai_model_one_default_provider` (`active_default_provider`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Interview V5 package/JD/resume-version binding for Trace Cockpit and future querying.
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'application_package_id'),
    'ALTER TABLE `interview_session` ADD COLUMN `application_package_id` BIGINT DEFAULT NULL COMMENT ''persistent job application package id'' AFTER `application_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'resume_version_id'),
    'ALTER TABLE `interview_session` ADD COLUMN `resume_version_id` BIGINT DEFAULT NULL COMMENT ''resume version used by interview'' AFTER `resume_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'jd_analysis_id'),
    'ALTER TABLE `interview_session` ADD COLUMN `jd_analysis_id` BIGINT DEFAULT NULL COMMENT ''job description analysis used by interview'' AFTER `target_job_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'training_context_summary')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'application_package_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'jd_analysis_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'resume_version_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'deleted'),
    'UPDATE `interview_session`
        SET `application_package_id` = CASE
              WHEN `application_package_id` IS NULL
               AND JSON_UNQUOTE(JSON_EXTRACT(`training_context_summary`, ''$.applicationContext.applicationPackageId'')) REGEXP ''^[0-9]+$''
              THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(`training_context_summary`, ''$.applicationContext.applicationPackageId'')) AS UNSIGNED)
              ELSE `application_package_id` END,
            `jd_analysis_id` = CASE
              WHEN `jd_analysis_id` IS NULL
               AND JSON_UNQUOTE(JSON_EXTRACT(`training_context_summary`, ''$.applicationContext.jdAnalysisId'')) REGEXP ''^[0-9]+$''
              THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(`training_context_summary`, ''$.applicationContext.jdAnalysisId'')) AS UNSIGNED)
              ELSE `jd_analysis_id` END,
            `resume_version_id` = CASE
              WHEN `resume_version_id` IS NULL
               AND JSON_UNQUOTE(JSON_EXTRACT(`training_context_summary`, ''$.applicationContext.resumeVersionId'')) REGEXP ''^[0-9]+$''
              THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(`training_context_summary`, ''$.applicationContext.resumeVersionId'')) AS UNSIGNED)
              ELSE `resume_version_id` END
      WHERE `deleted` = 0
        AND `training_context_summary` IS NOT NULL
        AND JSON_VALID(`training_context_summary`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'application_package_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'deleted')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'created_at')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_session' AND index_name = 'idx_interview_session_package'),
    'ALTER TABLE `interview_session` ADD INDEX `idx_interview_session_package` (`application_package_id`, `deleted`, `created_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'jd_analysis_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'resume_version_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'deleted')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'created_at')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_session' AND index_name = 'idx_interview_session_jd_resume'),
    'ALTER TABLE `interview_session` ADD INDEX `idx_interview_session_jd_resume` (`jd_analysis_id`, `resume_version_id`, `deleted`, `created_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'async_task')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'async_task' AND column_name = 'trace_id')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'async_task' AND column_name = 'deleted')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'async_task' AND column_name = 'created_at')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'async_task' AND column_name = 'id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'async_task' AND index_name = 'idx_async_task_trace_deleted_created'),
    'ALTER TABLE `async_task` ADD INDEX `idx_async_task_trace_deleted_created` (`trace_id`, `deleted`, `created_at`, `id`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'job_application_package')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'job_application_package' AND column_name = 'package_status')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'job_application_package' AND column_name = 'deleted')
    AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'job_application_package' AND column_name = 'updated_at'),
    'UPDATE `job_application_package`
        SET `package_status` = ''READY'', `updated_at` = NOW()
      WHERE `deleted` = 0 AND `package_status` = ''AVAILABLE''',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
