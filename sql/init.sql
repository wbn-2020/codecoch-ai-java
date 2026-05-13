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
  category_name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
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

CREATE TABLE IF NOT EXISTS resume (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  real_name VARCHAR(64) DEFAULT NULL,
  email VARCHAR(128) DEFAULT NULL,
  phone VARCHAR(32) DEFAULT NULL,
  summary TEXT,
  is_default TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resume_project (
  id BIGINT NOT NULL AUTO_INCREMENT,
  resume_id BIGINT NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  role VARCHAR(64) DEFAULT NULL,
  tech_stack VARCHAR(255) DEFAULT NULL,
  description TEXT,
  highlights TEXT,
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_resume_project_resume (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  scene VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_prompt_scene (scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  scene VARCHAR(64) NOT NULL,
  request_body TEXT,
  response_body TEXT,
  cost_millis BIGINT DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  error_message VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_ai_call_scene (scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resume_id BIGINT DEFAULT NULL,
  mode VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  report_status VARCHAR(32) NOT NULL,
  current_stage_id BIGINT DEFAULT NULL,
  current_question_id BIGINT DEFAULT NULL,
  current_question_group_id BIGINT DEFAULT NULL,
  answered_question_count INT NOT NULL DEFAULT 0,
  max_question_count INT NOT NULL DEFAULT 5,
  current_follow_up_count INT NOT NULL DEFAULT 0,
  failure_reason VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_interview_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_stage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  stage_type VARCHAR(64) NOT NULL,
  stage_name VARCHAR(128) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
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
  role VARCHAR(32) NOT NULL,
  message_type VARCHAR(32) NOT NULL,
  content TEXT,
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
  status VARCHAR(32) NOT NULL,
  total_score INT DEFAULT NULL,
  summary TEXT,
  strengths TEXT,
  weaknesses TEXT,
  suggestions TEXT,
  failure_reason VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_interview_report_session (session_id)
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

INSERT INTO question_category (id, category_name, sort, status)
VALUES
  (1, 'Java Basics', 1, 1),
  (2, 'Collections', 2, 1),
  (3, 'Concurrency', 3, 1),
  (4, 'JVM', 4, 1),
  (5, 'Spring Boot', 5, 1),
  (6, 'MySQL', 6, 1),
  (7, 'Redis', 7, 1),
  (8, 'Microservices', 8, 1),
  (9, 'Design Patterns', 9, 1),
  (10, 'Project Scenarios', 10, 1)
ON DUPLICATE KEY UPDATE sort = VALUES(sort), status = VALUES(status);

INSERT INTO question_tag (id, tag_name, status)
VALUES
  (1, 'HashMap', 1),
  (2, 'JVM', 1),
  (3, 'ThreadPool', 1),
  (4, 'MySQL Index', 1),
  (5, 'Redis Cache', 1),
  (6, 'Spring Transaction', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO question_group (id, group_name, description, category_id, status)
VALUES
  (1, 'HashMap Principle', 'HashMap storage and resize questions', 2, 1),
  (2, 'JVM GC', 'JVM memory and garbage collection', 4, 1),
  (3, 'Thread Pool', 'Thread pool parameters and rejection policy', 3, 1),
  (4, 'MySQL Index', 'Index structure and query optimization', 6, 1),
  (5, 'Redis Cache Consistency', 'Cache penetration, breakdown, and consistency', 7, 1)
ON DUPLICATE KEY UPDATE description = VALUES(description), status = VALUES(status);

INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, status)
VALUES
  (1, 'How does HashMap work?', 'Explain HashMap put/get and resize in Java.', 'HashMap uses array plus linked list or tree nodes. It hashes keys, locates buckets, handles collisions, and resizes when load factor is exceeded.', 'Mention hash, bucket, collision, treeify, resize.', 2, 1, 'MEDIUM', 1),
  (2, 'What causes full GC?', 'Explain common reasons for full GC and how to analyze it.', 'Full GC may be caused by old generation pressure, metaspace pressure, explicit System.gc, or allocation failure.', 'Mention GC logs, heap dump, allocation rate.', 4, 2, 'MEDIUM', 1),
  (3, 'How do you configure a thread pool?', 'Explain corePoolSize, maximumPoolSize, queue, keepAliveTime, and rejection policy.', 'Thread pool sizing depends on CPU or IO workload, queue choice, latency, and backpressure strategy.', 'Mention bounded queue and monitoring.', 3, 3, 'MEDIUM', 1),
  (4, 'Why can MySQL index fail?', 'List common cases where MySQL indexes are not used effectively.', 'Leading wildcard, function on indexed column, implicit conversion, low selectivity, and wrong composite index order can hurt index usage.', 'Mention explain and composite index leftmost prefix.', 6, 4, 'MEDIUM', 1),
  (5, 'How to handle Redis cache consistency?', 'Describe common cache consistency patterns.', 'Common patterns include cache-aside, delete cache after DB write, delayed double delete, TTL, and MQ retry in advanced cases.', 'V1 should discuss synchronous cache-aside without MQ.', 7, 5, 'HARD', 1)
ON DUPLICATE KEY UPDATE title = VALUES(title), status = VALUES(status);

INSERT INTO question_tag_relation (question_id, tag_id)
VALUES
  (1, 1),
  (2, 2),
  (3, 3),
  (4, 4),
  (5, 5)
ON DUPLICATE KEY UPDATE tag_id = VALUES(tag_id);

INSERT INTO prompt_template (id, scene, name, content, status)
VALUES
  (1, 'INTERVIEW_QUESTION_GENERATE', 'Technical question mock', 'Generate an interview question for {{questionTitle}}.', 1),
  (2, 'PROJECT_DEEP_DIVE_GENERATE', 'Project deep dive mock', 'Generate a project deep dive question from resume context.', 1),
  (3, 'INTERVIEW_ANSWER_EVALUATE', 'Answer evaluation mock', 'Evaluate answer and return score/comment/nextAction.', 1),
  (4, 'INTERVIEW_FOLLOW_UP_GENERATE', 'Follow-up mock', 'Generate one follow-up question.', 1),
  (5, 'INTERVIEW_REPORT_GENERATE', 'Report mock', 'Generate structured interview report.', 1)
ON DUPLICATE KEY UPDATE content = VALUES(content), status = VALUES(status);

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
