CREATE TABLE IF NOT EXISTS job_application_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  package_id BIGINT DEFAULT NULL,
  application_id BIGINT DEFAULT NULL,
  file_id BIGINT NOT NULL,
  attachment_type VARCHAR(32) NOT NULL DEFAULT 'OTHER',
  display_name VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) DEFAULT NULL,
  file_size BIGINT DEFAULT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  active_file_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 THEN file_id ELSE NULL END
    ) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_application_attachment_live_file (active_file_id),
  KEY idx_job_application_attachment_owner (
    user_id, package_id, deleted, sort_order, id
  ),
  KEY idx_job_application_attachment_application (
    user_id, application_id, deleted, sort_order, id
  ),
  KEY idx_job_application_attachment_file (
    file_id, deleted
  ),
  CONSTRAINT chk_job_application_attachment_scope
    CHECK (package_id IS NOT NULL OR application_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Owned file attachments associated with a job application package';
