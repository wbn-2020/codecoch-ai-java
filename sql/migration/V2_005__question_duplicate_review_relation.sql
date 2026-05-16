-- CodeCoachAI V2 A8: initial question duplicate review and relation management.
-- Scope: rule-based duplicate candidates for formal questions and manually confirmed relations.

CREATE TABLE IF NOT EXISTS question_relation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_question_id BIGINT NOT NULL,
  target_question_id BIGINT NOT NULL,
  relation_type VARCHAR(32) NOT NULL,
  relation_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  reason VARCHAR(500) DEFAULT NULL,
  similarity_score DECIMAL(5,2) DEFAULT NULL,
  created_by BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_relation_pair_type (source_question_id, target_question_id, relation_type, deleted),
  KEY idx_question_relation_source (source_question_id),
  KEY idx_question_relation_target (target_question_id),
  KEY idx_question_relation_type (relation_type),
  KEY idx_question_relation_status (relation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Question relation confirmed by admin';

CREATE TABLE IF NOT EXISTS question_duplicate_review (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_question_id BIGINT NOT NULL,
  target_question_id BIGINT NOT NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  match_type VARCHAR(32) NOT NULL,
  similarity_score DECIMAL(5,2) DEFAULT NULL,
  match_reason VARCHAR(500) DEFAULT NULL,
  source_title_snapshot VARCHAR(255) DEFAULT NULL,
  target_title_snapshot VARCHAR(255) DEFAULT NULL,
  source_content_snapshot VARCHAR(500) DEFAULT NULL,
  target_content_snapshot VARCHAR(500) DEFAULT NULL,
  source_group_id BIGINT DEFAULT NULL,
  target_group_id BIGINT DEFAULT NULL,
  created_by BIGINT DEFAULT NULL,
  reviewed_by BIGINT DEFAULT NULL,
  reviewed_at DATETIME DEFAULT NULL,
  ignored_reason VARCHAR(500) DEFAULT NULL,
  relation_id BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_duplicate_pair (source_question_id, target_question_id, deleted),
  KEY idx_question_duplicate_status (review_status, created_at),
  KEY idx_question_duplicate_source (source_question_id),
  KEY idx_question_duplicate_target (target_question_id),
  KEY idx_question_duplicate_match (match_type),
  KEY idx_question_duplicate_relation (relation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Question duplicate review candidate';
