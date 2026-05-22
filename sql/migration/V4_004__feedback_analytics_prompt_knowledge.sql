DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //

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

DELIMITER ;

CREATE TABLE IF NOT EXISTS agent_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  agent_task_id BIGINT NULL,
  agent_run_id BIGINT NULL,
  feedback_type VARCHAR(40) NOT NULL,
  comment VARCHAR(1000) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_agent_feedback_user (user_id),
  KEY idx_agent_feedback_task (agent_task_id),
  KEY idx_agent_feedback_run (agent_run_id),
  KEY idx_agent_feedback_type (feedback_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 Agent suggestion feedback';

CREATE TABLE IF NOT EXISTS analytics_metric_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  metric_code VARCHAR(100) NOT NULL,
  metric_name VARCHAR(120) NOT NULL,
  category VARCHAR(60) NOT NULL,
  definition VARCHAR(1000) NULL,
  data_source VARCHAR(200) NULL,
  refresh_frequency VARCHAR(60) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_metric_code (metric_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 analytics metric dictionary';

CREATE TABLE IF NOT EXISTS analytics_job_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(100) NOT NULL,
  job_name VARCHAR(120) NOT NULL,
  status VARCHAR(40) NOT NULL,
  stat_date DATE NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  duration_ms BIGINT NULL,
  error_message VARCHAR(1000) NULL,
  output_json TEXT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_analytics_job_code (job_code),
  KEY idx_analytics_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 analytics job execution log';

CREATE TABLE IF NOT EXISTS prompt_regression_case (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_name VARCHAR(120) NOT NULL,
  prompt_type VARCHAR(100) NOT NULL,
  input_json TEXT NOT NULL,
  expected_schema_json TEXT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_prompt_regression_type (prompt_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 prompt regression case';

CREATE TABLE IF NOT EXISTS prompt_regression_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_id BIGINT NOT NULL,
  prompt_version_id BIGINT NULL,
  status VARCHAR(40) NOT NULL,
  output_json TEXT NULL,
  score INT NULL,
  error_message VARCHAR(1000) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_prompt_regression_case (case_id),
  KEY idx_prompt_regression_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 prompt regression result';

CREATE TABLE IF NOT EXISTS personal_knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  document_type VARCHAR(50) NOT NULL DEFAULT 'NOTE',
  content MEDIUMTEXT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'INDEXED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_personal_doc_user (user_id),
  FULLTEXT KEY ft_personal_doc_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 personal knowledge document';

CREATE TABLE IF NOT EXISTS personal_knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  document_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  source_ref VARCHAR(200) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_personal_chunk_doc (document_id),
  FULLTEXT KEY ft_personal_chunk_content (content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 personal knowledge chunk';

CREATE TABLE IF NOT EXISTS resume_suggestion_adoption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resume_id BIGINT NULL,
  optimize_record_id BIGINT NULL,
  resume_version_id BIGINT NULL,
  suggestion_type VARCHAR(80) NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'ADOPTED',
  note VARCHAR(1000) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_resume_adoption_user (user_id),
  KEY idx_resume_adoption_resume (resume_id),
  KEY idx_resume_adoption_record (optimize_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 resume suggestion adoption';

CREATE TABLE IF NOT EXISTS job_application_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  event_time DATETIME NOT NULL,
  summary VARCHAR(1000) NULL,
  review_json TEXT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_application_event_app (application_id),
  KEY idx_application_event_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 real interview/application event';

CALL add_column_if_not_exists('target_job', 'priority', 'INT NOT NULL DEFAULT 50', 'status');
CALL add_column_if_not_exists('target_job', 'preparation_status', 'VARCHAR(40) NOT NULL DEFAULT ''PREPARING''', 'priority');

INSERT INTO analytics_metric_definition(metric_code, metric_name, category, definition, data_source, refresh_frequency)
SELECT 'agent_success_rate','Agent success rate','AGENT','SUCCESS run / total run','agent_run','REALTIME'
WHERE NOT EXISTS (SELECT 1 FROM analytics_metric_definition WHERE metric_code='agent_success_rate' AND deleted=0);

INSERT INTO analytics_metric_definition(metric_code, metric_name, category, definition, data_source, refresh_frequency)
SELECT 'task_adoption_rate','Task adoption rate','AGENT','ADOPT feedback / generated task','agent_feedback,agent_task','REALTIME'
WHERE NOT EXISTS (SELECT 1 FROM analytics_metric_definition WHERE metric_code='task_adoption_rate' AND deleted=0);

INSERT INTO analytics_metric_definition(metric_code, metric_name, category, definition, data_source, refresh_frequency)
SELECT 'ai_token_cost','AI token cost','AI_OPS','Token usage and estimated cost','ai_call_log','REALTIME'
WHERE NOT EXISTS (SELECT 1 FROM analytics_metric_definition WHERE metric_code='ai_token_cost' AND deleted=0);

INSERT INTO analytics_metric_definition(metric_code, metric_name, category, definition, data_source, refresh_frequency)
SELECT 'prompt_regression_pass_rate','Prompt regression pass rate','AI_OPS','SUCCESS prompt regression result / total result','prompt_regression_result','MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM analytics_metric_definition WHERE metric_code='prompt_regression_pass_rate' AND deleted=0);

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
