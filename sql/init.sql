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
  industry_direction VARCHAR(128) DEFAULT NULL,
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
  (1, 'INTERVIEW_QUESTION_GENERATE', '技术面试问题生成', '技术面试问题生成', '你是 Java 面试官。请基于阶段 {{currentStage}}、目标岗位 {{targetPosition}}、难度 {{difficulty}} 和题库问题 {{questionContent}} 生成一道中文面试问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', '你是 Java 面试官。请基于阶段 {{currentStage}}、目标岗位 {{targetPosition}}、难度 {{difficulty}} 和题库问题 {{questionContent}} 生成一道中文面试问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', 'targetPosition,experienceLevel,industryDirection,difficulty,interviewerStyle,currentStage,questionContent,historySummary', 'v1', 1),
  (2, 'PROJECT_DEEP_DIVE_QUESTION', '项目深挖问题生成', '项目深挖问题生成', '你是 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}} 和当前阶段 {{currentStage}} 生成一个中文项目深挖问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', '你是 Java 项目面试官。请结合简历 {{resumeContent}}、项目 {{projectContent}} 和当前阶段 {{currentStage}} 生成一个中文项目深挖问题。只返回 JSON：{\"questionContent\":\"问题内容\"}', 'resumeContent,projectContent,currentStage,historySummary', 'v1', 1),
  (3, 'INTERVIEW_ANSWER_EVALUATE', '回答评分点评', '回答评分点评', '你是 Java 面试官。请根据问题 {{questionContent}}、参考答案 {{referenceAnswer}} 和候选人回答 {{userAnswer}} 给出中文评分。只返回 JSON：{\"score\":80,\"comment\":\"点评\",\"nextAction\":\"NEXT_QUESTION\",\"knowledgePoints\":\"知识点\"}，nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE 或 FINISH。', '你是 Java 面试官。请根据问题 {{questionContent}}、参考答案 {{referenceAnswer}} 和候选人回答 {{userAnswer}} 给出中文评分。只返回 JSON：{\"score\":80,\"comment\":\"点评\",\"nextAction\":\"NEXT_QUESTION\",\"knowledgePoints\":\"知识点\"}，nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE 或 FINISH。', 'questionContent,referenceAnswer,userAnswer,currentStage,historySummary', 'v1', 1),
  (4, 'INTERVIEW_FOLLOW_UP_GENERATE', '动态追问生成', '动态追问生成', '你是 Java 面试官。请基于问题 {{questionContent}}、回答 {{userAnswer}} 和点评 {{aiComment}} 生成一个中文追问。只返回 JSON：{\"followUpQuestion\":\"追问内容\"}', '你是 Java 面试官。请基于问题 {{questionContent}}、回答 {{userAnswer}} 和点评 {{aiComment}} 生成一个中文追问。只返回 JSON：{\"followUpQuestion\":\"追问内容\"}', 'questionContent,userAnswer,currentStage,historySummary', 'v1', 1),
  (5, 'INTERVIEW_REPORT_GENERATE', '面试报告生成', '面试报告生成', '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。只返回 JSON：{\"totalScore\":82,\"summary\":\"总分来源说明\",\"strengths\":\"亮点\",\"weaknesses\":\"问题\",\"stageScores\":\"{}\",\"weakPoints\":\"[]\",\"mainProblems\":\"问题\",\"projectProblems\":\"[]\",\"reviewSuggestions\":\"建议\",\"recommendedQuestions\":\"[]\",\"qaReview\":\"[]\",\"reportContent\":\"报告正文\"}', '你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。只返回 JSON：{\"totalScore\":82,\"summary\":\"总分来源说明\",\"strengths\":\"亮点\",\"weaknesses\":\"问题\",\"stageScores\":\"{}\",\"weakPoints\":\"[]\",\"mainProblems\":\"问题\",\"projectProblems\":\"[]\",\"reviewSuggestions\":\"建议\",\"recommendedQuestions\":\"[]\",\"qaReview\":\"[]\",\"reportContent\":\"报告正文\"}', 'historySummary,targetPosition,experienceLevel,industryDirection,difficulty,resumeContent,projectContent', 'v1', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), template_name = VALUES(template_name), content = VALUES(content), template_content = VALUES(template_content), variables = VALUES(variables), version = VALUES(version), status = VALUES(status);

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
