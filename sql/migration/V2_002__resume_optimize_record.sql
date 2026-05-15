-- CodeCoachAI V2 A5: resume optimization record.
-- Scope: persist user resume optimization requests, AI results, and failure status.

CREATE TABLE IF NOT EXISTS resume_optimize_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resume_id BIGINT NOT NULL,
  target_position VARCHAR(100) DEFAULT NULL,
  experience_years INT DEFAULT NULL,
  industry_direction VARCHAR(100) DEFAULT NULL,
  request_json LONGTEXT DEFAULT NULL,
  result_json LONGTEXT DEFAULT NULL,
  optimize_status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
  error_message VARCHAR(1000) DEFAULT NULL,
  ai_call_log_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_resume (user_id, resume_id),
  KEY idx_user_status (user_id, optimize_status),
  KEY idx_resume_created (resume_id, created_at),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
