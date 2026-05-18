CREATE DATABASE IF NOT EXISTS codecoachai_v1
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE codecoachai_v1;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  username VARCHAR(64) NOT NULL COMMENT 'login username',
  password VARCHAR(255) NOT NULL COMMENT 'encrypted password hash',
  nickname VARCHAR(64) DEFAULT NULL COMMENT 'nickname',
  avatar VARCHAR(500) DEFAULT NULL COMMENT 'avatar url',
  email VARCHAR(128) DEFAULT NULL COMMENT 'email',
  phone VARCHAR(32) DEFAULT NULL COMMENT 'phone',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  last_login_time DATETIME DEFAULT NULL COMMENT 'last login time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system user';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  role_code VARCHAR(64) NOT NULL COMMENT 'role code',
  role_name VARCHAR(64) NOT NULL COMMENT 'role name',
  description VARCHAR(255) DEFAULT NULL COMMENT 'role description',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (role_code),
  KEY idx_sys_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system role';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  role_id BIGINT NOT NULL COMMENT 'role id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_role_user_role (user_id, role_id),
  KEY idx_sys_user_role_user_id (user_id),
  KEY idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system user role';

INSERT INTO sys_role (id, role_code, role_name, description, status)
VALUES
  (1, 'USER', 'User', 'Default user role', 1),
  (2, 'ADMIN', 'Admin', 'Default admin role', 1)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  description = VALUES(description),
  status = VALUES(status);

INSERT INTO sys_user (id, username, password, nickname, email, status)
VALUES
  (1, 'admin', '$2a$10$OuTN8naVk6kfkcyMNiSf.eO3rCVpGr2j7RL.iQvHkM6H/AJoFVtHG', 'System Admin', 'admin@codecoachai.local', 1)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  email = VALUES(email),
  status = VALUES(status);

INSERT INTO sys_user_role (user_id, role_id)
VALUES
  (1, 2)
ON DUPLICATE KEY UPDATE
  role_id = VALUES(role_id);

CREATE TABLE IF NOT EXISTS question_category (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT DEFAULT NULL,
  category_name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_category_name (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_group (
  id BIGINT NOT NULL AUTO_INCREMENT,
  group_name VARCHAR(128) NOT NULL,
  canonical_title VARCHAR(255) DEFAULT NULL,
  canonical_answer TEXT,
  main_knowledge_point VARCHAR(255) DEFAULT NULL,
  difficulty VARCHAR(32) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL,
  category_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_tag (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tag_name VARCHAR(64) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_tag_name (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT,
  reference_answer TEXT,
  analysis TEXT,
  category_id BIGINT DEFAULT NULL,
  group_id BIGINT DEFAULT NULL,
  difficulty VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  question_type VARCHAR(32) NOT NULL DEFAULT 'SHORT_ANSWER',
  experience_level VARCHAR(64) DEFAULT NULL,
  is_high_frequency TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_question_category (category_id),
  KEY idx_question_group (group_id),
  KEY idx_question_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_tag_relation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_tag_relation (question_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_review (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id VARCHAR(64) NOT NULL,
  created_by BIGINT NOT NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  ai_call_log_id BIGINT DEFAULT NULL,
  target_position VARCHAR(128) DEFAULT NULL,
  technology_stack VARCHAR(255) DEFAULT NULL,
  knowledge_point VARCHAR(255) DEFAULT NULL,
  question_type VARCHAR(32) NOT NULL,
  difficulty VARCHAR(32) NOT NULL,
  experience_years INT DEFAULT NULL,
  raw_ai_result_json LONGTEXT DEFAULT NULL,
  question_title VARCHAR(255) NOT NULL,
  question_content TEXT DEFAULT NULL,
  reference_answer TEXT DEFAULT NULL,
  analysis TEXT DEFAULT NULL,
  follow_up_questions_json LONGTEXT DEFAULT NULL,
  tag_suggestions_json LONGTEXT DEFAULT NULL,
  category_suggestion VARCHAR(128) DEFAULT NULL,
  group_suggestion VARCHAR(255) DEFAULT NULL,
  category_id BIGINT DEFAULT NULL,
  group_id BIGINT DEFAULT NULL,
  tag_ids_json VARCHAR(1000) DEFAULT NULL,
  edited_content_json LONGTEXT DEFAULT NULL,
  reject_reason VARCHAR(500) DEFAULT NULL,
  approved_question_id BIGINT DEFAULT NULL,
  reviewer_id BIGINT DEFAULT NULL,
  reviewed_at DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_question_review_batch (batch_id),
  KEY idx_question_review_status (review_status),
  KEY idx_question_review_creator (created_by, created_at),
  KEY idx_question_review_filter (review_status, question_type, difficulty),
  KEY idx_question_review_approved_question (approved_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS user_question_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_content TEXT,
  mastery_status VARCHAR(32) DEFAULT 'UNKNOWN',
  wrong TINYINT NOT NULL DEFAULT 0,
  favorite TINYINT NOT NULL DEFAULT 0,
  last_answer_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_question_record (user_id, question_id),
  KEY idx_user_question_wrong (user_id, wrong),
  KEY idx_user_question_favorite (user_id, favorite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_content TEXT NOT NULL,
  answer_duration_seconds INT DEFAULT NULL,
  source VARCHAR(64) NOT NULL DEFAULT 'QUESTION_BANK',
  review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  score INT DEFAULT NULL,
  level VARCHAR(32) DEFAULT NULL,
  mastery_status VARCHAR(32) DEFAULT NULL,
  ai_comment TEXT,
  suggestions TEXT,
  knowledge_points TEXT,
  strengths TEXT,
  weaknesses TEXT,
  improvement_suggestions TEXT,
  reference_comparison TEXT,
  knowledge_gaps TEXT,
  suggested_follow_ups TEXT,
  reference_answer_snapshot TEXT,
  question_snapshot_json LONGTEXT,
  review_json LONGTEXT,
  reference_answer TEXT,
  ai_call_log_id BIGINT DEFAULT NULL,
  error_message VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_practice_user_question (user_id, question_id),
  KEY idx_practice_user_status (user_id, review_status),
  KEY idx_practice_ai_log (ai_call_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resume (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  resume_name VARCHAR(128) DEFAULT NULL,
  real_name VARCHAR(64) DEFAULT NULL,
  target_position VARCHAR(128) DEFAULT NULL,
  skill_stack TEXT,
  work_experience TEXT,
  education_experience TEXT,
  email VARCHAR(128) DEFAULT NULL,
  phone VARCHAR(32) DEFAULT NULL,
  summary TEXT,
  is_default TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  source_resume_id BIGINT DEFAULT NULL,
  source_optimize_record_id BIGINT DEFAULT NULL,
  applied_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume_user (user_id),
  KEY idx_resume_source_resume (source_resume_id),
  KEY idx_resume_source_optimize_record (source_optimize_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resume_project (
  id BIGINT NOT NULL AUTO_INCREMENT,
  resume_id BIGINT NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  project_period VARCHAR(128) DEFAULT NULL,
  project_background TEXT,
  role VARCHAR(64) DEFAULT NULL,
  tech_stack VARCHAR(255) DEFAULT NULL,
  responsibility TEXT,
  core_features TEXT,
  technical_difficulties TEXT,
  optimization_results TEXT,
  description TEXT,
  highlights TEXT,
  sort INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume_project_resume (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS target_job (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  job_title VARCHAR(128) NOT NULL COMMENT 'target job title',
  company_name VARCHAR(128) DEFAULT NULL COMMENT 'target company name',
  job_level VARCHAR(64) DEFAULT NULL COMMENT 'job level',
  jd_text LONGTEXT DEFAULT NULL COMMENT 'job description text',
  jd_source VARCHAR(64) DEFAULT NULL COMMENT 'JD source',
  current_flag TINYINT NOT NULL DEFAULT 0 COMMENT '1 current target job',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active, 0 disabled',
  parse_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PARSED' COMMENT 'JD parse status',
  parse_error_message VARCHAR(1000) DEFAULT NULL COMMENT 'JD parse error message',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  KEY idx_target_job_user (user_id),
  KEY idx_target_job_current (user_id, current_flag, deleted),
  KEY idx_target_job_status (status, deleted),
  KEY idx_target_job_parse_status (parse_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 target job';

CREATE TABLE IF NOT EXISTS job_description_analysis (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  target_job_id BIGINT NOT NULL COMMENT 'target_job id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  job_title VARCHAR(128) DEFAULT NULL COMMENT 'job title snapshot',
  company_name VARCHAR(128) DEFAULT NULL COMMENT 'company name snapshot',
  job_level VARCHAR(64) DEFAULT NULL COMMENT 'job level snapshot',
  responsibilities_json LONGTEXT DEFAULT NULL COMMENT 'responsibilities JSON',
  required_skills_json LONGTEXT DEFAULT NULL COMMENT 'required skills JSON',
  bonus_skills_json LONGTEXT DEFAULT NULL COMMENT 'bonus skills JSON',
  tech_keywords_json LONGTEXT DEFAULT NULL COMMENT 'technical keywords JSON',
  business_keywords_json LONGTEXT DEFAULT NULL COMMENT 'business keywords JSON',
  experience_requirement TEXT DEFAULT NULL COMMENT 'experience requirement',
  project_experience_requirement TEXT DEFAULT NULL COMMENT 'project experience requirement',
  interview_focus_json LONGTEXT DEFAULT NULL COMMENT 'interview focus JSON',
  skill_weights_json LONGTEXT DEFAULT NULL COMMENT 'skill weights JSON',
  summary TEXT DEFAULT NULL COMMENT 'analysis summary',
  raw_result_json LONGTEXT DEFAULT NULL COMMENT 'raw AI result JSON',
  ai_call_log_id BIGINT DEFAULT NULL COMMENT 'ai_call_log id',
  parse_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PARSED' COMMENT 'JD parse status',
  parse_error_message VARCHAR(1000) DEFAULT NULL COMMENT 'JD parse error message',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  KEY idx_jd_analysis_target_job (target_job_id, deleted),
  KEY idx_jd_analysis_user (user_id, deleted),
  KEY idx_jd_analysis_parse_status (parse_status, deleted),
  KEY idx_jd_analysis_ai_log (ai_call_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 JD analysis result';

CREATE TABLE IF NOT EXISTS resume_job_match_report (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  resume_id BIGINT NOT NULL COMMENT 'resume id',
  target_job_id BIGINT NOT NULL COMMENT 'target job id',
  jd_analysis_id BIGINT NOT NULL COMMENT 'job description analysis id',
  overall_score INT DEFAULT NULL COMMENT 'overall match score',
  tech_stack_score INT DEFAULT NULL COMMENT 'tech stack score',
  project_experience_score INT DEFAULT NULL COMMENT 'project experience score',
  business_fit_score INT DEFAULT NULL COMMENT 'business fit score',
  communication_score INT DEFAULT NULL COMMENT 'communication score',
  strengths_json LONGTEXT DEFAULT NULL COMMENT 'strengths JSON',
  gaps_json LONGTEXT DEFAULT NULL COMMENT 'gaps JSON',
  resume_risks_json LONGTEXT DEFAULT NULL COMMENT 'resume risks JSON',
  optimization_suggestions_json LONGTEXT DEFAULT NULL COMMENT 'optimization suggestions JSON',
  recommended_learning_topics_json LONGTEXT DEFAULT NULL COMMENT 'recommended learning topics JSON',
  recommended_interview_topics_json LONGTEXT DEFAULT NULL COMMENT 'recommended interview topics JSON',
  summary TEXT DEFAULT NULL COMMENT 'match summary',
  raw_result_json LONGTEXT DEFAULT NULL COMMENT 'raw AI result JSON',
  status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT 'match report status',
  error_message VARCHAR(1000) DEFAULT NULL COMMENT 'error message',
  ai_call_log_id BIGINT DEFAULT NULL COMMENT 'ai_call_log id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  KEY idx_resume_match_user (user_id),
  KEY idx_resume_match_resume (resume_id, deleted),
  KEY idx_resume_match_target_job (target_job_id, deleted),
  KEY idx_resume_match_status (status, deleted),
  KEY idx_resume_match_user_resume_job (user_id, resume_id, target_job_id, deleted),
  KEY idx_resume_match_ai_log (ai_call_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 resume job match report';

CREATE TABLE IF NOT EXISTS resume_job_match_detail (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  report_id BIGINT NOT NULL COMMENT 'resume_job_match_report id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  dimension VARCHAR(64) DEFAULT NULL COMMENT 'match dimension',
  skill_name VARCHAR(128) DEFAULT NULL COMMENT 'skill name',
  match_level VARCHAR(32) DEFAULT NULL COMMENT 'match level',
  score INT DEFAULT NULL COMMENT 'detail score',
  evidence TEXT DEFAULT NULL COMMENT 'resume or JD evidence',
  gap_description TEXT DEFAULT NULL COMMENT 'gap description',
  suggestion TEXT DEFAULT NULL COMMENT 'improvement suggestion',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  KEY idx_resume_match_detail_report (report_id, deleted),
  KEY idx_resume_match_detail_user (user_id, deleted),
  KEY idx_resume_match_detail_dimension (dimension, deleted),
  KEY idx_resume_match_detail_skill (skill_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 resume job match detail';

CREATE TABLE IF NOT EXISTS skill_profile (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  target_job_id BIGINT NOT NULL COMMENT 'target job id',
  match_report_id BIGINT NOT NULL COMMENT 'resume_job_match_report id',
  profile_name VARCHAR(128) DEFAULT NULL COMMENT 'profile name',
  overall_level INT DEFAULT NULL COMMENT 'overall level 1-5',
  overall_score INT DEFAULT NULL COMMENT 'overall score 0-100',
  summary TEXT DEFAULT NULL COMMENT 'profile summary',
  source_type VARCHAR(64) NOT NULL DEFAULT 'RESUME_JOB_MATCH' COMMENT 'source type',
  source_biz_id BIGINT DEFAULT NULL COMMENT 'source business id',
  status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT 'profile status',
  raw_result_json LONGTEXT DEFAULT NULL COMMENT 'raw AI result JSON',
  ai_call_log_id BIGINT DEFAULT NULL COMMENT 'ai_call_log id',
  error_message VARCHAR(1000) DEFAULT NULL COMMENT 'error message',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  KEY idx_skill_profile_user (user_id),
  KEY idx_skill_profile_target_job (target_job_id, deleted),
  KEY idx_skill_profile_match_report (match_report_id, deleted),
  KEY idx_skill_profile_status (status, deleted),
  KEY idx_skill_profile_user_job_status (user_id, target_job_id, status, deleted),
  KEY idx_skill_profile_ai_log (ai_call_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 skill profile';

CREATE TABLE IF NOT EXISTS skill_gap_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  profile_id BIGINT NOT NULL COMMENT 'skill_profile id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  target_job_id BIGINT NOT NULL COMMENT 'target job id',
  skill_name VARCHAR(128) NOT NULL COMMENT 'skill name',
  category VARCHAR(64) DEFAULT NULL COMMENT 'skill category',
  target_level INT DEFAULT NULL COMMENT 'target level',
  current_level INT DEFAULT NULL COMMENT 'current level',
  gap_level INT DEFAULT NULL COMMENT 'gap level',
  confidence DECIMAL(5,4) DEFAULT NULL COMMENT 'confidence 0-1',
  severity VARCHAR(32) DEFAULT NULL COMMENT 'gap severity',
  evidence_sources_json LONGTEXT DEFAULT NULL COMMENT 'evidence sources JSON',
  gap_description TEXT DEFAULT NULL COMMENT 'gap description',
  recommended_actions_json LONGTEXT DEFAULT NULL COMMENT 'recommended actions JSON',
  priority INT DEFAULT NULL COMMENT 'priority order',
  source_type VARCHAR(64) NOT NULL DEFAULT 'RESUME_JOB_MATCH' COMMENT 'source type',
  source_biz_id BIGINT DEFAULT NULL COMMENT 'source business id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  KEY idx_skill_gap_profile (profile_id, deleted),
  KEY idx_skill_gap_user (user_id, deleted),
  KEY idx_skill_gap_target_job (target_job_id, deleted),
  KEY idx_skill_gap_skill (skill_name, deleted),
  KEY idx_skill_gap_severity (severity, deleted),
  KEY idx_skill_gap_profile_priority (profile_id, priority, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 skill gap item';

CREATE TABLE IF NOT EXISTS prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  scene VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  template_name VARCHAR(128) DEFAULT NULL,
  description VARCHAR(500) DEFAULT NULL,
  content LONGTEXT NOT NULL,
  template_content LONGTEXT,
  variables TEXT,
  version VARCHAR(32) DEFAULT 'v1',
  active_version_id BIGINT DEFAULT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_prompt_scene (scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prompt_template_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) NOT NULL,
  version_code VARCHAR(64) NOT NULL,
  version_name VARCHAR(128) DEFAULT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT DEFAULT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  is_active TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT DEFAULT NULL,
  activated_by BIGINT DEFAULT NULL,
  activated_at DATETIME DEFAULT NULL,
  change_log VARCHAR(1000) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prompt_template_version (template_id, version_code),
  KEY idx_prompt_version_scene (scene, status, is_active),
  KEY idx_prompt_version_template (template_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL,
  scene VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) DEFAULT NULL,
  prompt_template_id BIGINT DEFAULT NULL,
  prompt_template_version_id BIGINT DEFAULT NULL,
  prompt_version VARCHAR(64) DEFAULT NULL,
  request_id VARCHAR(64) DEFAULT NULL,
  trace_id VARCHAR(128) DEFAULT NULL,
  input_variables_json LONGTEXT DEFAULT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  prompt_hash VARCHAR(64) DEFAULT NULL,
  response_format VARCHAR(64) DEFAULT NULL,
  request_prompt LONGTEXT,
  response_content MEDIUMTEXT,
  business_id VARCHAR(128) DEFAULT NULL,
  request_body LONGTEXT,
  response_body LONGTEXT,
  elapsed_ms BIGINT DEFAULT 0,
  cost_millis BIGINT DEFAULT 0,
  success TINYINT DEFAULT NULL,
  prompt_tokens INT DEFAULT NULL,
  completion_tokens INT DEFAULT NULL,
  total_tokens INT DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  error_message VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_ai_call_scene (scene),
  KEY idx_ai_call_business (business_id),
  KEY idx_ai_call_prompt_version (prompt_template_version_id),
  KEY idx_ai_call_trace (trace_id),
  KEY idx_ai_call_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resume_id BIGINT DEFAULT NULL,
    interview_mode VARCHAR(64) DEFAULT NULL,
  mode VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    target_position VARCHAR(128) DEFAULT NULL,
    experience_level VARCHAR(64) DEFAULT NULL,
    industry_template_id BIGINT DEFAULT NULL,
    industry_direction VARCHAR(128) DEFAULT NULL,
    industry_context LONGTEXT DEFAULT NULL,
    difficulty VARCHAR(32) DEFAULT NULL,
  interviewer_style VARCHAR(64) DEFAULT NULL,
  based_on_resume TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  report_status VARCHAR(32) NOT NULL,
  current_stage_id BIGINT DEFAULT NULL,
  current_question_id BIGINT DEFAULT NULL,
  current_question_group_id BIGINT DEFAULT NULL,
  answered_question_count INT NOT NULL DEFAULT 0,
  max_question_count INT NOT NULL DEFAULT 5,
  current_follow_up_count INT NOT NULL DEFAULT 0,
  total_score INT DEFAULT NULL,
  start_time DATETIME DEFAULT NULL,
  end_time DATETIME DEFAULT NULL,
  failure_reason VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_interview_user (user_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS industry_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    industry_code VARCHAR(64) NOT NULL COMMENT '琛屼笟缂栫爜',
    industry_name VARCHAR(100) NOT NULL COMMENT '琛屼笟鍚嶇О',
    description VARCHAR(500) DEFAULT NULL COMMENT '琛屼笟璇存槑',
    target_positions VARCHAR(500) DEFAULT NULL COMMENT '閫傜敤宀椾綅',
    core_business_scenarios LONGTEXT DEFAULT NULL COMMENT '鏍稿績涓氬姟鍦烘櫙 JSON',
    key_technical_points LONGTEXT DEFAULT NULL COMMENT '鍏抽敭鎶€鏈叧娉ㄧ偣 JSON',
    common_question_directions LONGTEXT DEFAULT NULL COMMENT '甯歌杩介棶鏂瑰悜 JSON',
    risk_points LONGTEXT DEFAULT NULL COMMENT '甯歌椋庨櫓鐐?JSON',
    prompt_context LONGTEXT DEFAULT NULL COMMENT '娉ㄥ叆 AI Prompt 鐨勮涓氫笂涓嬫枃',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '鏄惁鍚敤',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '鎺掑簭',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '鏄惁鍒犻櫎',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_industry_code (industry_code),
    KEY idx_enabled_sort (enabled, sort_order),
    KEY idx_deleted (deleted)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='琛屼笟妯℃澘';

CREATE TABLE IF NOT EXISTS interview_stage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  stage_type VARCHAR(64) NOT NULL,
  stage_name VARCHAR(128) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  stage_order INT NOT NULL DEFAULT 0,
  expected_question_count INT NOT NULL DEFAULT 1,
  asked_question_count INT NOT NULL DEFAULT 0,
  focus_points TEXT,
  based_on_resume TINYINT NOT NULL DEFAULT 0,
  allow_follow_up TINYINT NOT NULL DEFAULT 1,
  max_follow_up_count INT NOT NULL DEFAULT 2,
  status VARCHAR(32) NOT NULL,
  score INT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_interview_stage_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  stage_id BIGINT DEFAULT NULL,
  question_id BIGINT DEFAULT NULL,
  question_group_id BIGINT DEFAULT NULL,
  parent_message_id BIGINT DEFAULT NULL,
  role VARCHAR(32) NOT NULL,
  message_type VARCHAR(32) NOT NULL,
  content TEXT,
  question_content TEXT,
  user_answer TEXT,
  ai_comment TEXT,
  ai_score INT DEFAULT NULL,
  is_follow_up TINYINT NOT NULL DEFAULT 0,
  follow_up_count INT NOT NULL DEFAULT 0,
  follow_up_reason VARCHAR(500) DEFAULT NULL,
  knowledge_points TEXT,
  score INT DEFAULT NULL,
  comment TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_interview_message_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  user_id BIGINT DEFAULT NULL,
  status VARCHAR(32) NOT NULL,
  total_score INT DEFAULT NULL,
  stage_scores TEXT,
  weak_points TEXT,
  summary TEXT,
  strengths TEXT,
  weaknesses TEXT,
  main_problems TEXT,
  project_problems TEXT,
  review_suggestions TEXT,
  recommended_questions TEXT,
  qa_review TEXT,
  report_content TEXT,
  generated_at DATETIME DEFAULT NULL,
  suggestions TEXT,
  failure_reason VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_interview_report_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS study_plan (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT DEFAULT NULL,
  target_job_id BIGINT DEFAULT NULL,
  skill_profile_id BIGINT DEFAULT NULL,
  match_report_id BIGINT DEFAULT NULL,
  report_id BIGINT DEFAULT NULL,
  session_id BIGINT DEFAULT NULL,
  resume_id BIGINT DEFAULT NULL,
  optimize_record_id BIGINT DEFAULT NULL,
  target_position VARCHAR(128) DEFAULT NULL,
  industry_direction VARCHAR(128) DEFAULT NULL,
  plan_title VARCHAR(200) DEFAULT NULL,
  plan_summary TEXT,
  plan_status VARCHAR(32) NOT NULL DEFAULT 'GENERATING',
  duration_days INT DEFAULT NULL,
  daily_minutes INT DEFAULT NULL,
  start_date DATE DEFAULT NULL,
  ai_call_log_id BIGINT DEFAULT NULL,
  request_json LONGTEXT,
  result_json LONGTEXT,
  failure_reason VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_study_plan_user_status (user_id, plan_status, created_at),
  KEY idx_study_plan_report (report_id),
  KEY idx_study_plan_session (session_id),
  KEY idx_study_plan_source (source_type, source_id),
  KEY idx_study_plan_target_job (target_job_id, deleted),
  KEY idx_study_plan_skill_profile (skill_profile_id, deleted),
  KEY idx_study_plan_match_report (match_report_id, deleted),
  KEY idx_study_plan_user_source (user_id, source_type, source_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS study_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  target_job_id BIGINT DEFAULT NULL,
  skill_profile_id BIGINT DEFAULT NULL,
  skill_gap_item_id BIGINT DEFAULT NULL,
  source_type VARCHAR(64) DEFAULT NULL,
  source_biz_id BIGINT DEFAULT NULL,
  stage_no INT NOT NULL DEFAULT 1,
  planned_date DATE DEFAULT NULL,
  stage_title VARCHAR(128) DEFAULT NULL,
  task_order INT NOT NULL DEFAULT 0,
  knowledge_point VARCHAR(128) DEFAULT NULL,
  task_title VARCHAR(200) NOT NULL,
  task_description TEXT,
  task_type VARCHAR(64) NOT NULL DEFAULT 'KNOWLEDGE_REVIEW',
  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  estimated_hours INT DEFAULT NULL,
  estimated_minutes INT DEFAULT NULL,
  acceptance_criteria VARCHAR(500) DEFAULT NULL,
  task_status VARCHAR(32) NOT NULL DEFAULT 'TODO',
  related_question_ids_json TEXT,
  related_tags_json TEXT,
  resources_json TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_study_task_plan (plan_id, stage_no, task_order),
  KEY idx_study_task_user_status (user_id, task_status),
  KEY idx_study_task_user_planned_date (user_id, planned_date, task_status),
  KEY idx_study_task_plan_planned_date (plan_id, planned_date, task_order),
  KEY idx_study_task_skill_gap (skill_gap_item_id, deleted),
  KEY idx_study_task_profile (skill_profile_id, deleted),
  KEY idx_study_task_target_job (target_job_id, deleted),
  KEY idx_study_task_source (source_type, source_biz_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS study_plan_skill_relation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  study_plan_id BIGINT NOT NULL COMMENT 'study_plan id',
  study_task_id BIGINT DEFAULT NULL COMMENT 'study_task id',
  target_job_id BIGINT DEFAULT NULL COMMENT 'target_job id',
  skill_profile_id BIGINT NOT NULL COMMENT 'skill_profile id',
  skill_gap_item_id BIGINT NOT NULL COMMENT 'skill_gap_item id',
  source_type VARCHAR(64) NOT NULL COMMENT 'source type',
  source_biz_id BIGINT DEFAULT NULL COMMENT 'source business id',
  priority INT DEFAULT NULL COMMENT 'relation priority',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  KEY idx_spsr_user (user_id, deleted),
  KEY idx_spsr_plan (study_plan_id, deleted),
  KEY idx_spsr_task (study_task_id, deleted),
  KEY idx_spsr_target_job (target_job_id, deleted),
  KEY idx_spsr_profile (skill_profile_id, deleted),
  KEY idx_spsr_gap (skill_gap_item_id, deleted),
  KEY idx_spsr_source (source_type, source_biz_id, deleted),
  KEY idx_spsr_plan_gap (study_plan_id, skill_gap_item_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  config_key VARCHAR(128) NOT NULL,
  config_value VARCHAR(500) NOT NULL,
  value_type VARCHAR(32) DEFAULT 'STRING',
  description VARCHAR(255) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO question_category (id, parent_id, category_name, sort, sort_order, status)
VALUES
  (1, NULL, 'Java Basics', 1, 1, 1),
  (2, 1, 'Collections', 2, 2, 1),
  (3, 1, 'Concurrency', 3, 3, 1),
  (4, 1, 'JVM', 4, 4, 1),
  (5, NULL, 'Spring Boot', 5, 5, 1),
  (6, NULL, 'MySQL', 6, 6, 1),
  (7, NULL, 'Redis', 7, 7, 1),
  (8, NULL, 'Microservices', 8, 8, 1),
  (9, NULL, 'Design Patterns', 9, 9, 1),
  (10, NULL, 'Project Scenario', 10, 10, 1)
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), sort = VALUES(sort), sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO question_tag (id, tag_name, status)
VALUES
  (1, 'HashMap', 1),
  (2, 'JVM', 1),
  (3, 'ThreadPool', 1),
  (4, 'MySQL Index', 1),
  (5, 'Redis Cache', 1),
  (6, 'Spring Transaction', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
VALUES
  (1, 'HashMap', 'What are the put/get and resize flows of HashMap?', 'HashMap locates buckets by hash, resolves collisions with list or tree nodes, and resizes when the threshold is exceeded.', 'HashMap collision and resize', 'MEDIUM', 'HashMap storage, resize, and collision handling', 2, 1),
  (2, 'JVM GC', 'What are common Full GC causes and how do you troubleshoot them?', 'Common causes include old generation pressure, metaspace pressure, explicit System.gc, and promotion failure. Troubleshooting uses GC logs, heap dumps, allocation rate, metrics, and recent changes.', 'JVM GC troubleshooting', 'MEDIUM', 'JVM memory and garbage collection', 4, 1),
  (3, 'ThreadPool', 'How do you configure thread pool core parameters?', 'Thread pool sizing depends on CPU or IO workload, latency, queue capacity, rejection policy, and monitoring.', 'Thread pool parameters', 'MEDIUM', 'Thread pool sizing and rejection policy', 3, 1),
  (4, 'MySQL Index', 'What are common MySQL index invalidation cases?', 'Common cases include leading wildcard, function on indexed column, implicit type conversion, and violating the leftmost prefix rule. Verify with EXPLAIN.', 'MySQL index and EXPLAIN', 'MEDIUM', 'Index usage and query optimization', 6, 1),
  (5, 'Redis Cache Consistency', 'How do you handle consistency between Redis cache and database?', 'A common cache-aside flow updates the database and then deletes cache, with TTL, retry, idempotency, and monitoring. More complex compensation belongs to later versions.', 'Redis cache consistency', 'HARD', 'Cache consistency and failure handling', 7, 1)
ON DUPLICATE KEY UPDATE canonical_title = VALUES(canonical_title), canonical_answer = VALUES(canonical_answer), main_knowledge_point = VALUES(main_knowledge_point), difficulty = VALUES(difficulty), description = VALUES(description), status = VALUES(status);

INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  (1, 'What are the put/get and resize flows of HashMap?', 'Explain HashMap put, get, collision handling, and resize migration.', 'HashMap locates array buckets by hash. Collisions are stored as list or tree nodes. put may treeify or resize. resize expands the array and migrates nodes.', 'Cover hash, bucket index, list/tree, load factor, and resize migration.', 2, 1, 'MEDIUM', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (2, 'What are common Full GC causes and how do you troubleshoot them?', 'Explain common Full GC causes with an online troubleshooting approach.', 'Full GC may be caused by old generation pressure, metaspace pressure, explicit System.gc, or promotion failure. Troubleshooting uses GC logs, heap dump, allocation rate, metrics, and recent releases.', 'A good answer includes metrics, logs, dumps, and change review.', 4, 2, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3, 'How do you configure thread pool core parameters?', 'Explain corePoolSize, maximumPoolSize, queue, keepAliveTime, and rejection policy.', 'Decide CPU-bound or IO-bound first, then combine throughput, latency, queue capacity, and fallback strategy. Use bounded queues and monitor active threads, queue length, and rejection count.', 'Mention bounded queue, rejection policy, and monitoring.', 3, 3, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4, 'What are common MySQL index invalidation cases?', 'List index invalidation cases and explain how to verify with EXPLAIN.', 'Common cases include leading wildcard, function or calculation on indexed column, implicit type conversion, not satisfying leftmost prefix, and range query limiting subsequent columns. Verify with EXPLAIN type, key, rows, and Extra.', 'Cover leftmost prefix and EXPLAIN fields.', 6, 4, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5, 'How do you handle consistency between Redis cache and database?', 'Explain read/write flow and boundary risks in cache-aside mode.', 'Usually update database first and then delete cache, with TTL, retry, idempotency, and monitoring. Discuss concurrent read/write, delete failure, and hot key risks. V1/V2 baseline does not require MQ.', 'Mention consistency boundary, retry, and no extra MQ by default.', 7, 5, 'HARD', 'SHORT_ANSWER', 'MID', 1, 1)
ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), reference_answer = VALUES(reference_answer), analysis = VALUES(analysis), question_type = VALUES(question_type), experience_level = VALUES(experience_level), is_high_frequency = VALUES(is_high_frequency), status = VALUES(status);

INSERT INTO question_tag_relation (question_id, tag_id)
VALUES
  (1, 1),
  (2, 2),
  (3, 3),
  (4, 4),
  (5, 5)
ON DUPLICATE KEY UPDATE tag_id = VALUES(tag_id);

INSERT INTO prompt_template (id, scene, name, template_name, content, template_content, variables, version, status)
VALUES
  (1, 'INTERVIEW_QUESTION_GENERATE', 'Interview Question Generate', 'Interview Question Generate', 'Generate one Java interview question. Output JSON only: {"questionContent":"content"}.', 'Generate one Java interview question. Output JSON only: {"questionContent":"content"}.', 'targetPosition,experienceLevel,industryDirection,difficulty,interviewerStyle,currentStage,stageName,stageType,focusPoints,questionContent,historySummary', 'v1', 1),
  (2, 'PROJECT_DEEP_DIVE_QUESTION', 'Project Deep Dive Question', 'Project Deep Dive Question', 'Generate one project deep-dive interview question. Output JSON only: {"questionContent":"content"}.', 'Generate one project deep-dive interview question. Output JSON only: {"questionContent":"content"}.', 'resumeContent,projectContent,currentStage,stageName,stageType,focusPoints,historySummary', 'v1', 1),
  (3, 'INTERVIEW_ANSWER_EVALUATE', 'Interview Answer Evaluate', 'Interview Answer Evaluate', 'Evaluate an interview answer. Output JSON only with score, comment, nextAction, followUpQuestion, followUpReason, and knowledgePoints.', 'Evaluate an interview answer. Output JSON only with score, comment, nextAction, followUpQuestion, followUpReason, and knowledgePoints.', 'rootQuestionContent,currentQuestionContent,questionContent,referenceAnswer,userAnswer,currentStage,stageName,historySummary,followUpCount,maxFollowUpCount,knowledgePoints', 'v1', 1),
  (4, 'INTERVIEW_FOLLOW_UP_GENERATE', 'Interview Follow Up Generate', 'Interview Follow Up Generate', 'Generate one follow-up question. Output JSON only with followUpQuestion, reason, and relatedToOriginalQuestion.', 'Generate one follow-up question. Output JSON only with followUpQuestion, reason, and relatedToOriginalQuestion.', 'rootQuestionContent,currentQuestionContent,questionContent,referenceAnswer,userAnswer,aiComment,currentStage,stageName,historySummary,followUpCount,maxFollowUpCount,knowledgePoints', 'v1', 1),
  (5, 'INTERVIEW_REPORT_GENERATE', 'Interview Report Generate', 'Interview Report Generate', 'Generate a structured interview report. Output JSON only with totalScore, summary, strengths, weakPoints, suggestions, and reportContent.', 'Generate a structured interview report. Output JSON only with totalScore, summary, strengths, weakPoints, suggestions, and reportContent.', 'historySummary,targetPosition,experienceLevel,industryDirection,difficulty,resumeContent,projectContent', 'v1', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), template_name = VALUES(template_name), content = VALUES(content), template_content = VALUES(template_content), variables = VALUES(variables), version = VALUES(version), status = VALUES(status);

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'AI_QUESTION_GENERATE', 'AI Question Generate', 'AI Question Generate', 'V2 AI question generation prompt', 'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.', 'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.', 'targetPosition,technologyStack,knowledgePoint,questionType,difficulty,experienceYears,count,generateReferenceAnswer,generateFollowUps,generateTagSuggestions,generateCategorySuggestion,extraRequirements', 'v2-a7', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'AI_QUESTION_GENERATE');

UPDATE prompt_template
SET name = 'AI Question Generate',
    template_name = 'AI Question Generate',
    content = 'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.',
    template_content = 'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.',
    variables = 'targetPosition,technologyStack,knowledgePoint,questionType,difficulty,experienceYears,count,generateReferenceAnswer,generateFollowUps,generateTagSuggestions,generateCategorySuggestion,extraRequirements',
    version = 'v2-a7',
    status = 1,
    enabled = 1
WHERE scene = 'AI_QUESTION_GENERATE';

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'LEARNING_PLAN_GENERATE', 'Learning Plan Generate', 'Learning Plan Generate', 'V2 learning plan generation prompt', 'Generate a practical study plan. Output JSON only with planTitle, planSummary, durationDays, and stages.', 'Generate a practical study plan. Output JSON only with planTitle, planSummary, durationDays, and stages.', 'targetPosition,industryDirection,experienceLevel,expectedDurationDays,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements', 'v2-a9', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'LEARNING_PLAN_GENERATE');

UPDATE prompt_template
SET name = 'Learning Plan Generate',
    template_name = 'Learning Plan Generate',
    content = 'Generate a practical study plan. Output JSON only with planTitle, planSummary, durationDays, and stages.',
    template_content = 'Generate a practical study plan. Output JSON only with planTitle, planSummary, durationDays, and stages.',
    variables = 'targetPosition,industryDirection,experienceLevel,expectedDurationDays,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements',
    version = 'v2-a9',
    status = 1,
    enabled = 1
WHERE scene = 'LEARNING_PLAN_GENERATE';

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'PRACTICE_ANSWER_REVIEW', 'Question Answer AI Review', 'Question Answer AI Review', 'P0-4 short-answer practice AI review prompt', 'You are a senior Java backend interview coach. Review the user short-answer practice response using questionTitle, questionContent, referenceAnswer, analysis, userAnswer, difficulty, knowledgePoint, and answerDurationSeconds. Output one JSON object only with score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, and suggestedFollowUps.', 'You are a senior Java backend interview coach. Review the user short-answer practice response using questionTitle, questionContent, referenceAnswer, analysis, userAnswer, difficulty, knowledgePoint, and answerDurationSeconds. Output one JSON object only with score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, and suggestedFollowUps.', 'recordId,userId,questionId,questionTitle,questionContent,questionType,difficulty,technologyStack,knowledgePoint,referenceAnswer,analysis,userAnswer,answerDurationSeconds,targetPosition,experienceLevel', 'v2-p0-4-practice-review', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'PRACTICE_ANSWER_REVIEW');

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'JOB_DESCRIPTION_PARSE', 'Job Description Parse', 'Job Description Parse', 'V3 target job JD structured parsing prompt', 'You are a senior Java backend career coach. Parse this job description into structured JSON. Input: jobTitle={{jobTitle}}, companyName={{companyName}}, jobLevel={{jobLevel}}, userTargetDirection={{userTargetDirection}}, jdText={{jdText}}. Output only one JSON object with jobTitle, companyName, jobLevel, responsibilities, requiredSkills, bonusSkills, techStackKeywords, businessKeywords, experienceRequirement, projectExperienceRequirement, interviewFocusPoints, skillWeights, and summary. requiredSkills items should contain name, category, requiredLevel, weight, and evidence.', 'You are a senior Java backend career coach. Parse this job description into structured JSON. Input: jobTitle={{jobTitle}}, companyName={{companyName}}, jobLevel={{jobLevel}}, userTargetDirection={{userTargetDirection}}, jdText={{jdText}}. Output only one JSON object with jobTitle, companyName, jobLevel, responsibilities, requiredSkills, bonusSkills, techStackKeywords, businessKeywords, experienceRequirement, projectExperienceRequirement, interviewFocusPoints, skillWeights, and summary. requiredSkills items should contain name, category, requiredLevel, weight, and evidence.', 'targetJobId,userId,jobTitle,companyName,jobLevel,jdText,jdSource,userTargetDirection', 'v3-be-1', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'JOB_DESCRIPTION_PARSE');

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'RESUME_JOB_MATCH', 'Resume Job Match', 'Resume Job Match', 'V3 resume to target job match analysis prompt', 'You are a senior Java backend career coach. Generate a resume-to-target-job match report. Inputs include resumeAnalysisJson, resumeSnapshotJson, jobDescriptionAnalysisJson, targetJobJson, and userExperienceYears. Output only one JSON object with overallScore, dimensionScores, strengths, gaps, resumeRisks, optimizationSuggestions, recommendedLearningTopics, recommendedInterviewTopics, and summary.', 'You are a senior Java backend career coach. Generate a resume-to-target-job match report. Inputs include resumeAnalysisJson, resumeSnapshotJson, jobDescriptionAnalysisJson, targetJobJson, and userExperienceYears. Output only one JSON object with overallScore, dimensionScores, strengths, gaps, resumeRisks, optimizationSuggestions, recommendedLearningTopics, recommendedInterviewTopics, and summary.', 'reportId,userId,resumeId,targetJobId,jdAnalysisId,resumeAnalysisJson,resumeSnapshotJson,jobDescriptionAnalysisJson,targetJobJson,userExperienceYears', 'v3-be-2', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'RESUME_JOB_MATCH');

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'SKILL_GAP_ANALYZE', 'Skill Gap Analyze', 'Skill Gap Analyze', 'V3 skill profile generation prompt', 'You are a senior Java backend career coach. Generate a target-job skill profile from resume-job match evidence. Output only one JSON object with profileSummary, overallLevel, overallScore, skillGaps, nextPrioritySkills, and nextActions. skillGaps items must contain skillName, category, targetLevel, currentLevel, gapLevel, confidence, severity, evidenceSources, gapDescription, recommendedActions, and priority.', 'You are a senior Java backend career coach. Generate a target-job skill profile from resume-job match evidence. Output only one JSON object with profileSummary, overallLevel, overallScore, skillGaps, nextPrioritySkills, and nextActions. skillGaps items must contain skillName, category, targetLevel, currentLevel, gapLevel, confidence, severity, evidenceSources, gapDescription, recommendedActions, and priority.', 'profileId,matchReportId,userId,resumeId,targetJobId,jdAnalysisId,targetJobJson,jobDescriptionAnalysisJson,matchReportJson,matchDetailsJson,gapsJson,recommendedLearningTopicsJson,recommendedInterviewTopicsJson,resumeAnalysisJson,resumeSnapshotJson', 'v3-be-3', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'SKILL_GAP_ANALYZE');

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'TARGETED_STUDY_PLAN_GENERATE', 'Targeted Study Plan Generate', 'Targeted Study Plan Generate', 'V3 gap-driven study plan generation prompt', 'You are a senior Java backend career coach. Generate a gap-driven study plan from targetJobJson, skillProfileJson, skillGapsJson, availableDays, dailyMinutes, startDate, and existingStudyPlansJson. Output only one JSON object with planTitle, planSummary, durationDays, and stages. Each item must contain dayOffset, skillName, sourceGapId, taskTitle, taskDescription, taskType, priority, estimatedMinutes, acceptance, relatedTags, and resources.', 'You are a senior Java backend career coach. Generate a gap-driven study plan from targetJobJson, skillProfileJson, skillGapsJson, availableDays, dailyMinutes, startDate, and existingStudyPlansJson. Output only one JSON object with planTitle, planSummary, durationDays, and stages. Each item must contain dayOffset, skillName, sourceGapId, taskTitle, taskDescription, taskType, priority, estimatedMinutes, acceptance, relatedTags, and resources. Do not output Markdown, code fences, explanations, or invented candidate experience.', 'learningPlanId,userId,targetJobId,skillProfileId,matchReportId,targetJobJson,skillProfileJson,skillGapsJson,availableDays,dailyMinutes,startDate,existingStudyPlansJson,planTitle', 'v3-be-4', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'TARGETED_STUDY_PLAN_GENERATE');

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       COALESCE(NULLIF(p.version, ''), 'v1'),
       p.name,
       COALESCE(NULLIF(p.template_content, ''), p.content),
       p.variables,
       'ACTIVE',
       1,
       NOW(),
       'Initialized from prompt_template'
FROM prompt_template p
WHERE p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = COALESCE(NULLIF(p.version, ''), 'v1')
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.enabled = p.status
WHERE p.deleted = 0
  AND p.active_version_id IS NULL;

INSERT INTO industry_template (
  industry_code, industry_name, description, target_positions,
  core_business_scenarios, key_technical_points, common_question_directions, risk_points,
  prompt_context, enabled, sort_order
) VALUES
  ('ECOMMERCE', 'E-Commerce', 'Backend scenarios for product, order, inventory, payment, and marketing.', 'Java Backend,E-Commerce Backend', '["Order","Inventory","Payment","Promotion"]', '["High concurrency","Cache consistency","Payment idempotency"]', '["Inventory consistency","Payment callback idempotency","Promotion traffic spike"]', '["Oversell","Duplicate payment","Cache penetration"]', 'Focus on order, inventory, payment, idempotency, consistency, and high concurrency.', 1, 10),
  ('FINANCE_PAYMENT', 'Finance Payment', 'Backend scenarios for payment, accounting, risk control, and reconciliation.', 'Java Backend,Payment Backend,FinTech Backend', '["Payment Callback","Reconciliation","Risk Control"]', '["Idempotency","Audit log","Fund safety"]', '["Callback disorder","Accounting mismatch","Risk rule design"]', '["Duplicate booking","Fund loss","Audit gap"]', 'Focus on fund safety, idempotency, reconciliation, risk control, and audit.', 1, 20),
  ('ONLINE_EDUCATION', 'Online Education', 'Backend scenarios for courses, scheduling, live class, homework, and learning progress.', 'Java Backend,Education Platform Backend', '["Course","Schedule","Live Class","Learning Progress"]', '["Schedule conflict","Live status","Progress consistency"]', '["Progress design","Schedule conflict validation","Replay relation"]', '["Progress loss","Permission bypass","Message delay"]', 'Focus on course progress, scheduling, permissions, and learning data consistency.', 1, 30),
  ('SAAS', 'Enterprise SaaS', 'Backend scenarios for tenants, organizations, permissions, subscription, and audit.', 'Java Backend,SaaS Backend', '["Tenant Isolation","RBAC","Subscription","Audit"]', '["Tenant isolation","RBAC","Data permission","Billing status"]', '["Tenant data isolation","Permission model","Subscription expiration"]', '["Tenant data leak","Permission expansion","Audit gap"]', 'Focus on tenant isolation, organization permissions, subscription billing, and audit.', 1, 40),
  ('CONTENT_COMMUNITY', 'Content Community', 'Backend scenarios for content, moderation, interaction, and recommendation.', 'Java Backend,Community Backend,Content Platform Backend', '["Moderation","Feed","Comment","Like"]', '["Moderation workflow","Counter consistency","Hot content cache"]', '["Like count consistency","Async moderation","Comment anti-abuse"]', '["Fake engagement","Moderation miss","Hot cache failure"]', 'Focus on moderation, interaction counts, feed, hot content, and consistency.', 1, 50),
  ('ERP_LOGISTICS', 'ERP Logistics', 'Backend scenarios for inventory, warehouse, dispatching, and order flow.', 'Java Backend,ERP Backend,Logistics Backend', '["Inventory","Warehouse","Dispatch","Order Flow"]', '["Inventory ledger","State machine","Batch import/export"]', '["Inventory ledger design","Order state consistency","Dispatch failure compensation"]', '["Inventory mismatch","State rollback","Permission bypass"]', 'Focus on inventory ledger, state machine, batch processing, and compensation.', 1, 60),
  ('GENERAL_BACKEND', 'General Backend', 'General management backend scenarios.', 'Java Backend,Admin Backend', '["RBAC","Audit Log","Data Permission","Import Export"]', '["Permission model","Audit","Data scope","Batch task"]', '["RBAC design","Data permission","Import failure handling"]', '["Permission bypass","Audit gap","Batch task blocking"]', 'Focus on RBAC, audit logs, data permissions, and import/export tasks.', 1, 70)
ON DUPLICATE KEY UPDATE
  industry_name = VALUES(industry_name),
  description = VALUES(description),
  target_positions = VALUES(target_positions),
  core_business_scenarios = VALUES(core_business_scenarios),
  key_technical_points = VALUES(key_technical_points),
  common_question_directions = VALUES(common_question_directions),
  risk_points = VALUES(risk_points),
  prompt_context = VALUES(prompt_context),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);
INSERT INTO system_config (id, config_key, config_value, value_type, description, status)
VALUES
  (1, 'interview.max_follow_up_count', '2', 'NUMBER', 'Maximum follow-up count per main question', 1),
  (2, 'interview.max_question_count', '5', 'NUMBER', 'Default maximum questions per interview', 1),
  (3, 'ai.mock.enabled', 'true', 'BOOLEAN', 'Use mock AI implementation in V1', 1),
  (4, 'ai.timeout.seconds', '30', 'NUMBER', 'AI timeout seconds', 1)
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  value_type = VALUES(value_type),
  description = VALUES(description),
  status = VALUES(status);
