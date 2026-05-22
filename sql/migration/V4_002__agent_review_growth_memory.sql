CREATE TABLE IF NOT EXISTS agent_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_job_id BIGINT NULL,
  review_date DATE NOT NULL,
  summary VARCHAR(1000) NULL,
  done_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  todo_count INT NOT NULL DEFAULT 0,
  completion_rate DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  readiness_score INT NOT NULL DEFAULT 0,
  next_actions_json TEXT NULL,
  review_json TEXT NULL,
  agent_run_id BIGINT NULL,
  ai_call_log_id BIGINT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_agent_review_user_date (user_id, review_date),
  KEY idx_agent_review_target_job (target_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 Agent daily review';

CREATE TABLE IF NOT EXISTS skill_growth_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  snapshot_date DATE NOT NULL,
  skill_code VARCHAR(100) NULL,
  skill_name VARCHAR(100) NOT NULL,
  score INT NOT NULL DEFAULT 0,
  task_count INT NOT NULL DEFAULT 0,
  done_count INT NOT NULL DEFAULT 0,
  source_type VARCHAR(50) NULL,
  source_id BIGINT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_skill_growth_user_date (user_id, snapshot_date),
  KEY idx_skill_growth_skill (skill_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 skill growth snapshot';

CREATE TABLE IF NOT EXISTS readiness_score_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_job_id BIGINT NULL,
  score_date DATE NOT NULL,
  score INT NOT NULL DEFAULT 0,
  task_completion_rate DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  agent_success_rate DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  evidence_json TEXT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_readiness_user_date (user_id, score_date),
  KEY idx_readiness_target_job (target_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 readiness score record';

CREATE TABLE IF NOT EXISTS agent_memory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  memory_type VARCHAR(50) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  source_type VARCHAR(50) NULL,
  source_id BIGINT NULL,
  confidence DECIMAL(5,2) NOT NULL DEFAULT 0.80,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_agent_memory_user_enabled (user_id, enabled),
  KEY idx_agent_memory_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 controllable agent memory';
