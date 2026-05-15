-- CodeCoachAI V2 A1: file service and resume upload baseline.
-- Scope: create file metadata and resume analysis record tables only.
-- This migration does not parse file text, call AI, or create confirmed resumes.

CREATE TABLE IF NOT EXISTS file_info (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  stored_filename VARCHAR(255) NOT NULL,
  file_ext VARCHAR(16) NOT NULL,
  mime_type VARCHAR(128) DEFAULT NULL,
  file_size BIGINT NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_file_info_user (user_id),
  KEY idx_file_info_biz_type (biz_type),
  KEY idx_file_info_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resume_analysis_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resume_id BIGINT DEFAULT NULL,
  file_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'FILE_UPLOAD',
  parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  raw_text MEDIUMTEXT DEFAULT NULL,
  structured_json MEDIUMTEXT DEFAULT NULL,
  error_message VARCHAR(1000) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume_analysis_user (user_id),
  KEY idx_resume_analysis_file (file_id),
  KEY idx_resume_analysis_status (parse_status),
  KEY idx_resume_analysis_resume (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
