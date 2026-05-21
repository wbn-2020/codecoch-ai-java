CREATE TABLE IF NOT EXISTS resume_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resume_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  version_name VARCHAR(100) NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  source_type VARCHAR(50) NULL,
  source_id BIGINT NULL,
  current_flag TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_resume_version_no (resume_id, version_no, deleted),
  KEY idx_resume_version_user_resume (user_id, resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 resume version snapshot';

CREATE TABLE IF NOT EXISTS job_application (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_job_id BIGINT NULL,
  resume_version_id BIGINT NULL,
  company_name VARCHAR(120) NULL,
  job_title VARCHAR(120) NOT NULL,
  source VARCHAR(120) NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'SAVED',
  applied_at DATETIME NULL,
  next_follow_up_at DATETIME NULL,
  note VARCHAR(1000) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  KEY idx_job_application_user_status (user_id, status),
  KEY idx_job_application_target_job (target_job_id),
  KEY idx_job_application_resume_version (resume_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 personal job application progress';
