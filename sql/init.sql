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

CREATE TABLE IF NOT EXISTS prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  scene VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  template_name VARCHAR(128) DEFAULT NULL,
  content TEXT NOT NULL,
  template_content TEXT,
  variables TEXT,
  version VARCHAR(32) DEFAULT 'v1',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_prompt_scene (scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL,
  scene VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) DEFAULT NULL,
  prompt_template_id BIGINT DEFAULT NULL,
  request_prompt TEXT,
  response_content MEDIUMTEXT,
  business_id VARCHAR(128) DEFAULT NULL,
  request_body TEXT,
  response_body TEXT,
  elapsed_ms BIGINT DEFAULT 0,
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
    industry_code VARCHAR(64) NOT NULL COMMENT '行业编码',
    industry_name VARCHAR(100) NOT NULL COMMENT '行业名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '行业说明',
    target_positions VARCHAR(500) DEFAULT NULL COMMENT '适用岗位',
    core_business_scenarios LONGTEXT DEFAULT NULL COMMENT '核心业务场景 JSON',
    key_technical_points LONGTEXT DEFAULT NULL COMMENT '关键技术关注点 JSON',
    common_question_directions LONGTEXT DEFAULT NULL COMMENT '常见追问方向 JSON',
    risk_points LONGTEXT DEFAULT NULL COMMENT '常见风险点 JSON',
    prompt_context LONGTEXT DEFAULT NULL COMMENT '注入 AI Prompt 的行业上下文',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_industry_code (industry_code),
    KEY idx_enabled_sort (enabled, sort_order),
    KEY idx_deleted (deleted)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业模板';

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
  KEY idx_study_plan_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS study_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  stage_no INT NOT NULL DEFAULT 1,
  stage_title VARCHAR(128) DEFAULT NULL,
  task_order INT NOT NULL DEFAULT 0,
  knowledge_point VARCHAR(128) DEFAULT NULL,
  task_title VARCHAR(200) NOT NULL,
  task_description TEXT,
  task_type VARCHAR(64) NOT NULL DEFAULT 'KNOWLEDGE_REVIEW',
  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  estimated_hours INT DEFAULT NULL,
  task_status VARCHAR(32) NOT NULL DEFAULT 'TODO',
  related_question_ids_json TEXT,
  related_tags_json TEXT,
  resources_json TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_study_task_plan (plan_id, stage_no, task_order),
  KEY idx_study_task_user_status (user_id, task_status)
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
  (1, NULL, 'Java 基础', 1, 1, 1),
  (2, 1, '集合框架', 2, 2, 1),
  (3, 1, '并发编程', 3, 3, 1),
  (4, 1, 'JVM', 4, 4, 1),
  (5, NULL, 'Spring Boot', 5, 5, 1),
  (6, NULL, 'MySQL', 6, 6, 1),
  (7, NULL, 'Redis', 7, 7, 1),
  (8, NULL, '微服务', 8, 8, 1),
  (9, NULL, '设计模式', 9, 9, 1),
  (10, NULL, '项目场景', 10, 10, 1)
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), sort = VALUES(sort), sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO question_tag (id, tag_name, status)
VALUES
  (1, 'HashMap 原理', 1),
  (2, 'JVM', 1),
  (3, '线程池', 1),
  (4, 'MySQL 索引', 1),
  (5, 'Redis 缓存', 1),
  (6, 'Spring 事务', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO question_group (id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
VALUES
  (1, 'HashMap 原理', 'HashMap 的 put/get 和扩容流程是什么？', 'HashMap 通过数组加链表/红黑树存储元素，根据 key 的 hash 定位桶位，冲突时追加到链表或树节点，元素数量超过阈值后扩容并重新分布。', 'HashMap、哈希冲突、扩容', 'MEDIUM', 'HashMap 存储、扩容和冲突处理', 2, 1),
  (2, 'JVM GC', 'Full GC 常见原因有哪些？如何排查？', '常见原因包括老年代空间不足、元空间压力、显式 System.gc、对象晋升失败等，排查时结合 GC 日志、堆转储、对象分配速率和监控曲线定位。', 'JVM、GC、排查', 'MEDIUM', 'JVM 内存和垃圾回收', 4, 1),
  (3, '线程池参数', '线程池核心参数如何配置？', '线程池参数需要结合 CPU/IO 类型、队列长度、响应时间和降级策略配置，重点关注 corePoolSize、maximumPoolSize、queue、keepAliveTime 和拒绝策略。', '线程池、队列、拒绝策略', 'MEDIUM', '线程池参数和拒绝策略', 3, 1),
  (4, 'MySQL 索引', 'MySQL 索引失效有哪些常见场景？', '常见场景包括左模糊匹配、索引列参与函数计算、隐式类型转换、联合索引不满足最左前缀、选择性过低等，需要结合 explain 验证。', '索引、Explain、最左前缀', 'MEDIUM', '索引结构和查询优化', 6, 1),
  (5, 'Redis 缓存一致性', '如何处理 Redis 缓存和数据库一致性？', 'V1 推荐说明 cache-aside 模式，写数据库后删除缓存，配合 TTL、失败重试和业务幂等处理。复杂 MQ 补偿属于后续版本扩展。', 'Redis、缓存一致性', 'HARD', '缓存穿透、击穿和一致性', 7, 1)
ON DUPLICATE KEY UPDATE canonical_title = VALUES(canonical_title), canonical_answer = VALUES(canonical_answer), main_knowledge_point = VALUES(main_knowledge_point), difficulty = VALUES(difficulty), description = VALUES(description), status = VALUES(status);

INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type, experience_level, is_high_frequency, status)
VALUES
  (1, 'HashMap 的 put/get 和扩容流程是什么？', '请说明 HashMap put、get、哈希冲突处理以及扩容迁移过程。', 'HashMap 通过 hash 定位数组下标，冲突时使用链表或红黑树组织节点，put 时判断是否需要 treeify 或 resize，resize 会扩容数组并迁移节点。', '回答应覆盖 hash、桶位、链表/红黑树、负载因子、扩容迁移。', 2, 1, 'MEDIUM', 'SHORT_ANSWER', 'JUNIOR', 1, 1),
  (2, 'Full GC 常见原因有哪些？如何排查？', '请结合线上排查思路说明 Full GC 的常见原因。', 'Full GC 可能由老年代压力、元空间压力、显式 System.gc、晋升失败等触发。排查时看 GC 日志、堆 dump、对象分配速率、监控曲线和最近发布变更。', '回答应体现监控、日志、dump 和变更排查闭环。', 4, 2, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (3, '线程池核心参数如何配置？', '请说明 corePoolSize、maximumPoolSize、队列、keepAliveTime 和拒绝策略的配置依据。', '配置线程池要先判断 CPU 密集还是 IO 密集，结合吞吐、延迟、队列容量和降级策略设计，生产环境应使用有界队列并监控活跃线程、队列长度和拒绝次数。', '回答应包含有界队列、拒绝策略和监控指标。', 3, 3, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (4, 'MySQL 索引失效有哪些常见场景？', '请列举索引失效场景，并说明如何用 explain 验证。', '常见场景包括左模糊匹配、索引列函数计算、隐式类型转换、联合索引不满足最左前缀、范围查询后字段无法继续有效利用等，需要通过 explain 观察 type、key、rows、Extra。', '回答应覆盖最左前缀和 explain 关键字段。', 6, 4, 'MEDIUM', 'SHORT_ANSWER', 'MID', 1, 1),
  (5, '如何处理 Redis 缓存和数据库一致性？', '请说明 cache-aside 模式下读写流程和边界风险。', '常见方案是先更新数据库再删除缓存，配合 TTL、失败重试、幂等和监控。高并发下要说明并发读写、删除失败、热点 key 等风险。V1 不要求引入 MQ。', '回答应体现一致性边界、失败重试和 V1 不引入 MQ 的取舍。', 7, 5, 'HARD', 'SHORT_ANSWER', 'MID', 1, 1)
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
  (1, 'INTERVIEW_QUESTION_GENERATE', '技术面试问题生成', '技术面试问题生成', '你是资深 Java 面试官。请基于当前阶段生成一个干净的中文技术面试问题。当前阶段：{{stageName}}。阶段类型：{{stageType}}。阶段重点：{{focusPoints}}。目标岗位：{{targetPosition}}。难度：{{difficulty}}。题库候选题：{{questionContent}}。历史摘要：{{historySummary}}。要求：只能围绕当前阶段重点提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}', '你是资深 Java 面试官。请基于当前阶段生成一个干净的中文技术面试问题。当前阶段：{{stageName}}。阶段类型：{{stageType}}。阶段重点：{{focusPoints}}。目标岗位：{{targetPosition}}。难度：{{difficulty}}。题库候选题：{{questionContent}}。历史摘要：{{historySummary}}。要求：只能围绕当前阶段重点提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}', 'targetPosition,experienceLevel,industryDirection,difficulty,interviewerStyle,currentStage,stageName,stageType,focusPoints,questionContent,historySummary', 'v1', 1),
  (2, 'PROJECT_DEEP_DIVE_QUESTION', '项目深挖问题生成', '项目深挖问题生成', '你是资深 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}}、当前阶段 {{stageName}} 和阶段重点 {{focusPoints}} 生成一个中文项目深挖问题。只能围绕当前项目阶段提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}', '你是资深 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}}、当前阶段 {{stageName}} 和阶段重点 {{focusPoints}} 生成一个中文项目深挖问题。只能围绕当前项目阶段提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定：{"questionContent":"问题内容"}', 'resumeContent,projectContent,currentStage,stageName,stageType,focusPoints,historySummary', 'v1', 1),
  (3, 'INTERVIEW_ANSWER_EVALUATE', '回答评分点评', '回答评分点评', '你是资深 Java 面试官。请一次性完成评分、点评、流程决策，并在需要时生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。当前追问次数：{{followUpCount}}。最大追问次数：{{maxFollowUpCount}}。要求：score 必须是 0-100 整数；nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE、FINISH；followUpCount >= maxFollowUpCount 时禁止 FOLLOW_UP；FOLLOW_UP 时 followUpQuestion 必须紧扣原始主问题和候选人回答，且必须是 Java 技术面试追问；不允许出现“假设原问题”“如果你有具体问题请提供”“由于没有上下文”等话术。只输出 JSON：{"score":80,"comment":"中文点评","nextAction":"FOLLOW_UP","followUpQuestion":"追问内容","followUpReason":"追问原因","knowledgePoints":"相关知识点"}', '你是资深 Java 面试官。请一次性完成评分、点评、流程决策，并在需要时生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。当前追问次数：{{followUpCount}}。最大追问次数：{{maxFollowUpCount}}。要求：score 必须是 0-100 整数；nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE、FINISH；followUpCount >= maxFollowUpCount 时禁止 FOLLOW_UP；FOLLOW_UP 时 followUpQuestion 必须紧扣原始主问题和候选人回答，且必须是 Java 技术面试追问；不允许出现“假设原问题”“如果你有具体问题请提供”“由于没有上下文”等话术。只输出 JSON：{"score":80,"comment":"中文点评","nextAction":"FOLLOW_UP","followUpQuestion":"追问内容","followUpReason":"追问原因","knowledgePoints":"相关知识点"}', 'rootQuestionContent,currentQuestionContent,questionContent,referenceAnswer,userAnswer,currentStage,stageName,historySummary,followUpCount,maxFollowUpCount,knowledgePoints', 'v1', 1),
  (4, 'INTERVIEW_FOLLOW_UP_GENERATE', '动态追问生成', '动态追问生成', '你是资深 Java 面试官。请基于以下上下文生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。AI 评分点评：{{aiComment}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。追问必须紧扣原始主问题和候选人回答，不能换题；必须指出候选人回答中具体缺失或错误的点；只生成一个更深入的问题；不要重复原问题；不要编造“假设原问题”；不要说“请提供具体问题”；不允许跳到团队协作、用户增长、市场运营等非 Java 技术面试主题。只返回 JSON：{"followUpQuestion":"追问内容","reason":"追问原因","relatedToOriginalQuestion":true}', '你是资深 Java 面试官。请基于以下上下文生成一个追问。原始主问题：{{rootQuestionContent}}。当前问题：{{currentQuestionContent}}。参考答案：{{referenceAnswer}}。候选人回答：{{userAnswer}}。AI 评分点评：{{aiComment}}。当前阶段：{{stageName}}。历史摘要：{{historySummary}}。追问必须紧扣原始主问题和候选人回答，不能换题；必须指出候选人回答中具体缺失或错误的点；只生成一个更深入的问题；不要重复原问题；不要编造“假设原问题”；不要说“请提供具体问题”；不允许跳到团队协作、用户增长、市场运营等非 Java 技术面试主题。只返回 JSON：{"followUpQuestion":"追问内容","reason":"追问原因","relatedToOriginalQuestion":true}', 'rootQuestionContent,currentQuestionContent,questionContent,referenceAnswer,userAnswer,aiComment,currentStage,stageName,historySummary,followUpCount,maxFollowUpCount,knowledgePoints', 'v1', 1),
  (5, 'INTERVIEW_REPORT_GENERATE', '面试报告生成', '面试报告生成', '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。字段固定：{"totalScore":82,"summary":"总分来源说明","strengths":[],"weakPoints":[],"mainProblems":[],"projectProblems":[],"reviewSuggestions":[],"recommendedQuestions":[],"qaReview":[],"stageScores":{},"reportContent":"报告正文"}', '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。字段固定：{"totalScore":82,"summary":"总分来源说明","strengths":[],"weakPoints":[],"mainProblems":[],"projectProblems":[],"reviewSuggestions":[],"recommendedQuestions":[],"qaReview":[],"stageScores":{},"reportContent":"报告正文"}', 'historySummary,targetPosition,experienceLevel,industryDirection,difficulty,resumeContent,projectContent', 'v1', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), template_name = VALUES(template_name), content = VALUES(content), template_content = VALUES(template_content), variables = VALUES(variables), version = VALUES(version), status = VALUES(status);

UPDATE prompt_template
SET name = CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
    template_name = CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
    content = 'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only one JSON object with planTitle, planSummary, durationDays and stages.',
    template_content = 'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only JSON object, no Markdown, no code fences.',
    variables = 'targetPosition,industryDirection,experienceLevel,expectedDurationDays,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements',
    version = 'v2-a9',
    status = 1
WHERE scene = 'LEARNING_PLAN_GENERATE';

INSERT INTO prompt_template (scene, name, template_name, content, template_content, variables, version, status)
SELECT 'LEARNING_PLAN_GENERATE',
       CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
       CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
       'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only one JSON object with planTitle, planSummary, durationDays and stages.',
       'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only JSON object, no Markdown, no code fences.',
       'targetPosition,industryDirection,experienceLevel,expectedDurationDays,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements',
       'v2-a9',
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'LEARNING_PLAN_GENERATE'
);

INSERT INTO industry_template (
  industry_code, industry_name, description, target_positions,
  core_business_scenarios, key_technical_points, common_question_directions, risk_points,
  prompt_context, enabled, sort_order
) VALUES
  ('ECOMMERCE', '电商', '面向交易、商品、营销和履约链路的后端场景。', 'Java 后端,电商后端,交易系统开发',
   '["订单","库存","支付","秒杀","营销","售后","商品搜索"]', '["高并发扣减","缓存一致性","支付幂等","订单状态机","搜索性能"]',
   '["如何保证库存一致性","支付回调如何幂等","秒杀链路如何削峰"]', '["超卖","重复支付","缓存击穿","订单状态不一致"]',
   '电商场景关注订单、库存、支付、营销和售后链路，提问时优先考察一致性、幂等、高并发和异常补偿。', 1, 10),
  ('FINANCE_PAYMENT', '金融支付', '面向支付、账务、风控和对账的后端场景。', 'Java 后端,支付系统开发,金融科技后端',
   '["支付回调","账务一致性","风控","对账","退款"]', '["幂等设计","分布式事务取舍","资金安全","审计日志","异常补偿"]',
   '["支付回调乱序如何处理","账务差异如何排查","风控规则如何落地"]', '["重复入账","资损","回调丢失","对账遗漏"]',
   '金融支付场景关注资金安全、幂等、对账、风控和审计，提问时必须区分真实经历和假设迁移能力。', 1, 20),
  ('ONLINE_EDUCATION', '在线教育', '面向课程、排课、直播和学习进度的业务场景。', 'Java 后端,在线教育后端,教学平台开发',
   '["课程","排课","直播","作业","学习进度"]', '["排课冲突","直播状态","学习记录一致性","内容权限","异步任务"]',
   '["如何设计课程进度","排课冲突如何校验","直播回放如何关联"]', '["进度丢失","权限越权","排课冲突","消息延迟"]',
   '在线教育场景关注课程、排课、直播、作业和学习进度，提问时关注状态流转、权限和学习数据一致性。', 1, 30),
  ('SAAS', '企业 SaaS', '面向多租户、组织、权限和订阅计费的企业服务场景。', 'Java 后端,SaaS 后端,企业应用开发',
   '["租户隔离","权限","订阅计费","组织架构","审计"]', '["多租户隔离","RBAC","数据权限","计费状态","审计日志"]',
   '["租户数据如何隔离","权限模型如何设计","订阅到期如何处理"]', '["租户越权","权限膨胀","计费状态不一致","审计缺失"]',
   'SaaS 场景关注租户隔离、组织权限、订阅计费和审计，提问时关注权限边界和数据隔离。', 1, 40),
  ('CONTENT_COMMUNITY', '内容社区', '面向内容生产、互动、审核和推荐的社区场景。', 'Java 后端,社区后端,内容平台开发',
   '["内容审核","推荐","评论","点赞","反作弊"]', '["审核流","计数一致性","Feed 流","反作弊","热点内容缓存"]',
   '["点赞计数如何保证","内容审核如何异步化","评论反作弊如何设计"]', '["刷赞","违规内容漏审","热点缓存失效","计数不一致"]',
   '内容社区场景关注内容审核、互动计数、推荐和反作弊，提问时关注异步化、热点和一致性。', 1, 50),
  ('ERP_LOGISTICS', '物流 / ERP', '面向库存、仓储、调度和订单流转的业务场景。', 'Java 后端,ERP 后端,物流系统开发',
   '["库存","仓储","调度","订单流转","出入库"]', '["库存流水","状态机","调度策略","批量导入导出","权限边界"]',
   '["库存流水如何设计","订单流转如何保证一致","调度失败如何补偿"]', '["库存账实不符","状态回退混乱","批处理失败","越权操作"]',
   '物流 ERP 场景关注库存、仓储、调度和订单流转，提问时关注状态机、流水、批处理和补偿。', 1, 60),
  ('GENERAL_BACKEND', '通用后台', '面向管理后台和通用业务系统的后端场景。', 'Java 后端,后台开发,管理系统开发',
   '["RBAC","审计日志","数据权限","导入导出","配置管理"]', '["权限模型","操作审计","数据范围","批量任务","接口幂等"]',
   '["RBAC 如何设计","数据权限如何落地","导入失败如何处理"]', '["权限越权","审计缺失","批量任务阻塞","数据污染"]',
   '通用后台场景关注 RBAC、审计日志、数据权限和导入导出，适合作为默认行业迁移能力考察。', 1, 70)
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
