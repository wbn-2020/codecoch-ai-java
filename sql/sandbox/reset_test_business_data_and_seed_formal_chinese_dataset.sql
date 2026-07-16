-- ============================================================================
-- Test environment business reset and formal Chinese seed data
-- ============================================================================
-- Scope:
--   * Preserve accounts, roles, permissions, system/model configuration, and
--     immutable product definitions.
--   * Physically remove all other records from codecoachai_v1.
--   * Seed one coherent fictional Chinese career journey for user001.
--
-- Safety:
--   1. Run only after a verified database backup.
--   2. Stop application containers and consumers before execution.
--   3. This script refuses to run unless the caller sets the explicit session
--      variable shown in the execution runbook.
--   4. This is intentionally a sandbox script. Do not move it into Flyway.
--
-- Required session variable:
--   SET @codecoachai_allow_test_business_reset = 'I_UNDERSTAND_TEST_RESET_20260716';
--
-- The current seed is fictional. It does not contain real contact details,
-- credentials, tokens, or externally downloadable artifact records.

SET NAMES utf8mb4;

DELIMITER //
DROP PROCEDURE IF EXISTS assert_test_business_reset_allowed//
CREATE PROCEDURE assert_test_business_reset_allowed()
BEGIN
  IF DATABASE() <> 'codecoachai_v1' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Refusing reset because the active schema is not codecoachai_v1.';
  END IF;

  IF COALESCE(@codecoachai_allow_test_business_reset, '') <> 'I_UNDERSTAND_TEST_RESET_20260716' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Explicit test-environment reset acknowledgement is required.';
  END IF;
END//
DELIMITER ;

CALL assert_test_business_reset_allowed();
DROP PROCEDURE IF EXISTS assert_test_business_reset_allowed;

SET @seed_now = '2026-07-16 10:00:00';
SET @seed_user_id = (
  SELECT id
  FROM sys_user
  WHERE username = 'user001' AND deleted = 0
  LIMIT 1
);

DELIMITER //
DROP PROCEDURE IF EXISTS assert_seed_user_exists//
CREATE PROCEDURE assert_seed_user_exists()
BEGIN
  IF @seed_user_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'The required user001 account is missing or disabled.';
  END IF;
END//
DELIMITER ;

CALL assert_seed_user_exists();
DROP PROCEDURE IF EXISTS assert_seed_user_exists;

-- Preserve identities, authorization, runtime configuration, and stable
-- definitions. Everything else is business, audit, cache, or derived data.
DELIMITER //
DROP PROCEDURE IF EXISTS clear_non_baseline_tables//
CREATE PROCEDURE clear_non_baseline_tables()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE current_table VARCHAR(128);
  DECLARE table_cursor CURSOR FOR
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_type = 'BASE TABLE'
      AND table_name NOT IN (
        'sys_user',
        'sys_role',
        'sys_user_role',
        'sys_menu',
        'sys_role_menu',
        'system_config',
        'ai_model_config',
        'prompt_template',
        'prompt_template_version',
        'industry_template',
        'interview_rubric_version',
        'interview_scenario_version',
        'resume_ats_template',
        'ability_skill_node',
        'analytics_metric_definition',
        'flyway_schema_history'
      )
    ORDER BY table_name;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN table_cursor;
  clear_loop: LOOP
    FETCH table_cursor INTO current_table;
    IF done = 1 THEN
      LEAVE clear_loop;
    END IF;

    SET @delete_sql = CONCAT(
      'DELETE FROM `',
      REPLACE(current_table, '`', '``'),
      '`'
    );
    PREPARE delete_statement FROM @delete_sql;
    EXECUTE delete_statement;
    DEALLOCATE PREPARE delete_statement;
  END LOOP;
  CLOSE table_cursor;
END//
DELIMITER ;

START TRANSACTION;

CALL clear_non_baseline_tables();

-- ============================================================================
-- Question bank
-- ============================================================================

INSERT INTO question_category (
  id, parent_id, category_name, sort, sort_order, status, deleted, created_at, updated_at
) VALUES
  (9700101, NULL, 'Java 基础与集合', 10, 10, 1, 0, @seed_now, @seed_now),
  (9700102, NULL, '并发与 JVM', 20, 20, 1, 0, @seed_now, @seed_now),
  (9700103, NULL, 'Spring 与微服务', 30, 30, 1, 0, @seed_now, @seed_now),
  (9700104, NULL, '数据存储与缓存', 40, 40, 1, 0, @seed_now, @seed_now),
  (9700105, NULL, '系统设计与项目表达', 50, 50, 1, 0, @seed_now, @seed_now);

INSERT INTO question_tag (
  id, tag_name, status, deleted, created_at, updated_at
) VALUES
  (9700201, 'HashMap', 1, 0, @seed_now, @seed_now),
  (9700202, '线程池', 1, 0, @seed_now, @seed_now),
  (9700203, 'JVM 调优', 1, 0, @seed_now, @seed_now),
  (9700204, 'Spring 事务', 1, 0, @seed_now, @seed_now),
  (9700205, 'MySQL 索引', 1, 0, @seed_now, @seed_now),
  (9700206, 'Redis 一致性', 1, 0, @seed_now, @seed_now),
  (9700207, 'RocketMQ', 1, 0, @seed_now, @seed_now),
  (9700208, '可观测性', 1, 0, @seed_now, @seed_now),
  (9700209, '幂等设计', 1, 0, @seed_now, @seed_now),
  (9700210, 'STAR 表达', 1, 0, @seed_now, @seed_now);

INSERT INTO question_group (
  id, group_name, canonical_title, canonical_answer, main_knowledge_point,
  difficulty, description, category_id, status, deleted, created_at, updated_at
) VALUES
  (9700301, '高并发集合与线程模型', 'HashMap 冲突与线程池隔离如何共同影响线上稳定性？',
   'HashMap 在高冲突场景会影响单次访问延迟；线程池未隔离会让阻塞任务耗尽共享资源。回答应分别说明数据结构复杂度、容量规划、队列边界、拒绝策略和监控闭环。',
   '集合复杂度、线程池隔离与容量规划', 'MEDIUM', '考察候选人从局部实现到运行时稳定性的推理能力。', 9700101, 1, 0, @seed_now, @seed_now),
  (9700302, 'JVM 与慢查询排障', '线上出现 Full GC 和慢 SQL 时，你如何建立排障闭环？',
   '先根据监控确认影响范围，再结合 GC 日志、堆转储、慢 SQL、EXPLAIN、发布变更和容量指标定位原因。修复后必须通过压测和生产指标验证，并补齐告警阈值。',
   'GC、慢 SQL、发布变更与验证闭环', 'HARD', '考察可观测性、假设验证和工程化复盘。', 9700102, 1, 0, @seed_now, @seed_now),
  (9700303, '微服务可靠性设计', '异步消息、缓存和数据库如何做到可恢复的一致性？',
   '以数据库提交为事实源，采用可靠事件、消费幂等、失败重试、死信补偿和可观测追踪。缓存更新需要明确失效策略和版本边界。',
   '可靠事件、幂等、补偿与缓存一致性', 'HARD', '考察跨组件一致性和失败恢复能力。', 9700103, 1, 0, @seed_now, @seed_now),
  (9700304, '项目深挖表达', '如何用 STAR 结构讲清一次稳定性治理？',
   '清晰交代背景和风险，量化目标，说明个人职责和技术决策，给出可核验结果及复盘边界，避免把团队成果全部归因到个人。',
   'STAR、量化结果与证据边界', 'MEDIUM', '考察面试表达的可信度与结构化程度。', 9700105, 1, 0, @seed_now, @seed_now);

INSERT INTO question (
  id, title, normalized_title, normalized_title_hash, content_hash,
  content, reference_answer, analysis, category_id, group_id, difficulty,
  question_type, experience_level, is_high_frequency, is_recommended,
  status, audit_status, source_type, deleted, created_at, updated_at
) VALUES
  (9700401, 'HashMap 在 Java 8 中为什么需要树化？',
   'hashmap java8 为什么树化', SHA2('hashmap java8 为什么树化', 256), SHA2('HashMap 树化条件', 256),
   '请说明链表树化的触发条件、复杂度变化，以及为什么不是默认使用红黑树。',
   '桶内冲突达到阈值且数组容量满足条件时会树化，以降低极端冲突的查询复杂度。节点少时链表维护成本低，因此不默认树化。',
   '重点观察是否能说明阈值、容量前置条件、复杂度和内存成本的取舍。',
   9700101, 9700301, 'MEDIUM', 'SHORT_ANSWER', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now),
  (9700402, '线程池参数如何根据业务负载设计？',
   '线程池参数如何根据业务负载设计', SHA2('线程池参数如何根据业务负载设计', 256), SHA2('线程池参数负载设计', 256),
   '一个异步解析任务平均耗时 800ms、峰值每秒 80 个请求，你如何设计线程数、队列和拒绝策略？',
   '先区分 CPU 或 IO 密集，基于 QPS 与平均耗时估算并发，再结合下游容量设置有界队列、拒绝降级、重试和监控。',
   '不能只背参数定义，要说清业务 SLA、峰值、下游保护和补偿方案。',
   9700102, 9700301, 'HARD', 'SCENARIO', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now),
  (9700403, 'Spring 事务在什么情况下会失效？',
   'spring 事务失效场景', SHA2('spring 事务失效场景', 256), SHA2('spring 事务失效场景', 256),
   '请结合代理、自调用、异常类型和传播行为说明常见事务失效原因。',
   '自调用绕过代理、非 public 方法、异常被吞、受检异常未配置回滚和传播级别使用不当都可能导致事务失效。',
   '回答应覆盖代理边界和真实提交行为，而不是只列举注解。',
   9700103, 9700303, 'MEDIUM', 'SHORT_ANSWER', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now),
  (9700404, 'MySQL 联合索引为什么强调最左前缀？',
   'mysql 联合索引最左前缀', SHA2('mysql 联合索引最左前缀', 256), SHA2('mysql 联合索引最左前缀', 256),
   '联合索引为 user_id、status、created_at 时，哪些查询可有效利用该索引？范围和排序有什么影响？',
   '等值条件可按最左顺序使用索引；范围条件后续列的有序利用会受限。需要结合 EXPLAIN、扫描行数和 filesort 判断。',
   '重点考察索引顺序与真实查询模式的匹配。',
   9700104, 9700302, 'MEDIUM', 'SHORT_ANSWER', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now),
  (9700405, 'Redis 与数据库如何实现最终一致？',
   'redis 与数据库最终一致', SHA2('redis 与数据库最终一致', 256), SHA2('redis 与数据库最终一致', 256),
   '请设计一个订单状态变更后的缓存更新方案，并解释失败补偿。',
   '以数据库提交为事实源，提交后删除或更新缓存；结合重试、延迟补偿、版本号和可观测告警处理失败与并发覆盖。',
   '重点关注一致性边界、失败恢复和可观测性。',
   9700104, 9700303, 'HARD', 'SCENARIO', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now),
  (9700406, 'RocketMQ 消费幂等如何落地？',
   'rocketmq 消费幂等如何落地', SHA2('rocketmq 消费幂等如何落地', 256), SHA2('rocketmq 消费幂等如何落地', 256),
   '支付结果消息可能重复投递，消费者如何避免重复扣减和错误重试？',
   '以业务唯一键建立幂等约束，记录处理状态，区分可重试与不可重试错误，并将失败转入可追踪的补偿流程。',
   '重点是数据库唯一约束、状态机与消息确认语义。',
   9700103, 9700303, 'HARD', 'SCENARIO', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now),
  (9700407, '如何排查线上频繁 Full GC？',
   '如何排查线上频繁 full gc', SHA2('如何排查线上频繁 full gc', 256), SHA2('如何排查线上频繁 full gc', 256),
   '请按发现、定位、修复和验证四步说明排查过程。',
   '通过监控确认频次和停顿，再分析 GC 日志、堆转储、对象分配和发布变更；修复后进行压测与线上指标验证。',
   '重点考察从指标到根因再到验证的闭环。',
   9700102, 9700302, 'HARD', 'SHORT_ANSWER', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now),
  (9700408, '如何讲清一次系统稳定性治理项目？',
   '如何讲清一次系统稳定性治理项目', SHA2('如何讲清一次系统稳定性治理项目', 256), SHA2('如何讲清一次系统稳定性治理项目', 256),
   '请在两分钟内介绍一次稳定性治理，要求包含背景、个人职责、关键决策、量化结果与复盘。',
   '使用 STAR 结构，明确个人边界。结果应可量化，例如接口成功率、告警数量、人工处理时长或故障恢复时间。',
   '重点观察表达是否具体、可信、可追问。',
   9700105, 9700304, 'MEDIUM', 'PROJECT', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, @seed_now, @seed_now);

INSERT INTO question_tag_relation (
  id, question_id, tag_id, deleted, created_at, updated_at
) VALUES
  (9700501, 9700401, 9700201, 0, @seed_now, @seed_now),
  (9700502, 9700402, 9700202, 0, @seed_now, @seed_now),
  (9700503, 9700403, 9700204, 0, @seed_now, @seed_now),
  (9700504, 9700404, 9700205, 0, @seed_now, @seed_now),
  (9700505, 9700405, 9700206, 0, @seed_now, @seed_now),
  (9700506, 9700406, 9700207, 0, @seed_now, @seed_now),
  (9700507, 9700406, 9700209, 0, @seed_now, @seed_now),
  (9700508, 9700407, 9700203, 0, @seed_now, @seed_now),
  (9700509, 9700407, 9700208, 0, @seed_now, @seed_now),
  (9700510, 9700408, 9700210, 0, @seed_now, @seed_now);

-- ============================================================================
-- Resume, versions, projects, and suggestion workflow
-- ============================================================================

SET @resume_summary_v1 = '5年Java后端开发经验，负责高并发业务系统建设与稳定性治理。';
SET @resume_summary_v2 = '5年Java后端开发经验，聚焦高并发服务、数据一致性与可观测性治理。';
SET @resume_summary_v3 = '5年Java后端开发经验，负责高并发业务系统建设与稳定性治理；主导订单链路可观测性改造并持续优化故障恢复效率。';
SET @summary_phrase_a = '高并发业务系统建设';
SET @summary_phrase_b = '稳定性治理';
SET @summary_phrase_a_start = LOCATE(@summary_phrase_a, @resume_summary_v3) - 1;
SET @summary_phrase_b_start = LOCATE(@summary_phrase_b, @resume_summary_v3) - 1;

INSERT INTO resume (
  id, user_id, title, resume_name, real_name, target_position, skill_stack,
  work_experience, education_experience, email, phone, summary, is_default,
  status, created_at, updated_at, deleted
) VALUES (
  9701001, @seed_user_id, '张伟｜高级 Java 后端工程师', '高级 Java 后端工程师求职简历',
  '张伟', '高级 Java 后端工程师',
  'Java、Spring Boot、MySQL、Redis、RocketMQ、Docker、Prometheus、Grafana',
  '2023.04 至今 澄明云科有限公司 高级 Java 后端工程师；2020.07 至 2023.03 星河智联科技有限公司 Java 后端工程师。',
  '2016.09 至 2020.06 华东理工大学 软件工程 本科。',
  NULL, NULL, @resume_summary_v3, 1, 1, '2026-07-15 18:00:00', @seed_now, 0
);

INSERT INTO resume_project (
  id, resume_id, project_name, project_period, project_background, role, tech_stack,
  responsibility, core_features, technical_difficulties, optimization_results,
  description, highlights, sort, sort_order, created_at, updated_at, deleted
) VALUES
  (9701101, 9701001, '学习平台稳定性治理', '2025.03 - 2026.02',
   '面向百万级学习行为的在线教育平台，核心课程进度和作业链路在高峰期存在延迟与告警噪音。',
   '后端负责人', 'Spring Boot、MySQL、Redis、RocketMQ、Prometheus、Grafana',
   '负责进度链路拆分、异步事件可靠投递、缓存治理与告警分级。',
   '学习进度状态机、可靠事件表、幂等消费、延迟补偿、链路监控。',
   '处理消息重复、缓存与数据库一致性、热点课程流量突增以及跨服务追踪。',
   '核心接口 P95 从 620ms 降至 260ms；进度写入失败率降至 0.03%；夜间人工告警处理时长降低 58%。',
   '主导稳定性专项，从问题度量到灰度验证形成闭环。',
   '可靠事件与幂等消费；Redis 热点保护；Prometheus 指标分级。', 10, 10, @seed_now, @seed_now, 0),
  (9701102, 9701001, '招聘 SaaS 候选人检索优化', '2024.04 - 2025.02',
   '企业招聘 SaaS 的候选人筛选列表在多条件组合下出现慢查询与分页抖动。',
   '核心开发', 'Java、MySQL、Elasticsearch、Redis、Docker',
   '负责查询画像、索引重构、游标分页改造和检索降级策略。',
   '筛选条件归一化、联合索引设计、异步索引同步、游标分页。',
   '平衡高选择性筛选、排序稳定性、索引同步延迟与结果一致性。',
   '核心筛选接口 P95 从 1.8s 降至 320ms；数据库扫描行数降低 87%；超时率下降 92%。',
   '以真实 SQL 画像驱动索引调整，并通过压测验证每次变更。',
   'EXPLAIN 与慢 SQL 闭环；联合索引；搜索降级。', 20, 20, @seed_now, @seed_now, 0),
  (9701103, 9701001, '订单异步可靠性改造', '2022.06 - 2024.03',
   '交易系统在支付回调高峰期存在重复消费、补偿不可追踪和人工对账压力。',
   'Java 后端工程师', 'Spring Cloud、RocketMQ、MySQL、Redis、SkyWalking',
   '参与订单状态机、消费幂等、失败补偿任务和链路追踪建设。',
   '消息去重、状态机校验、死信补偿、对账任务、TraceId 贯通。',
   '确保重复回调不产生重复扣减，并在下游异常时可审计地恢复。',
   '重复扣减线上事故归零；异常订单平均修复时长从 45 分钟降至 9 分钟。',
   '将业务唯一键、数据库约束和补偿任务组合为可恢复闭环。',
   '幂等键；可靠消息；状态机；异常补偿。', 30, 30, @seed_now, @seed_now, 0);

INSERT INTO resume_version (
  id, user_id, resume_id, version_no, version_name, snapshot_json,
  source_type, source_id, current_flag, created_at, updated_at, deleted
) VALUES
  (9701201, @seed_user_id, 9701001, 1, '初始版本',
   JSON_OBJECT(
     'title', '张伟｜高级 Java 后端工程师',
     'realName', '张伟',
     'targetPosition', '高级 Java 后端工程师',
     'skillStack', 'Java、Spring Boot、MySQL、Redis、RocketMQ、Docker、Prometheus、Grafana',
     'workExperience', '2023.04 至今 澄明云科有限公司 高级 Java 后端工程师；2020.07 至 2023.03 星河智联科技有限公司 Java 后端工程师。',
     'educationExperience', '2016.09 至 2020.06 华东理工大学 软件工程 本科。',
     'email', NULL, 'phone', NULL, 'summary', @resume_summary_v1,
     'projects', JSON_ARRAY(
       JSON_OBJECT('id', '9701101', 'projectName', '学习平台稳定性治理', 'projectPeriod', '2025.03 - 2026.02', 'role', '后端负责人', 'techStack', 'Spring Boot、MySQL、Redis、RocketMQ'),
       JSON_OBJECT('id', '9701102', 'projectName', '招聘 SaaS 候选人检索优化', 'projectPeriod', '2024.04 - 2025.02', 'role', '核心开发', 'techStack', 'Java、MySQL、Elasticsearch、Redis')
     ),
     'projectSnapshotSource', 'RESUME_PROJECT'
   ),
   'MANUAL', NULL, 0, '2026-05-18 09:30:00', '2026-05-18 09:30:00', 0),
  (9701202, @seed_user_id, 9701001, 2, '岗位定制版本',
   JSON_OBJECT(
     'title', '张伟｜高级 Java 后端工程师',
     'realName', '张伟',
     'targetPosition', '高级 Java 后端工程师',
     'skillStack', 'Java、Spring Boot、MySQL、Redis、RocketMQ、Docker、Prometheus、Grafana',
     'workExperience', '2023.04 至今 澄明云科有限公司 高级 Java 后端工程师；2020.07 至 2023.03 星河智联科技有限公司 Java 后端工程师。',
     'educationExperience', '2016.09 至 2020.06 华东理工大学 软件工程 本科。',
     'email', NULL, 'phone', NULL, 'summary', @resume_summary_v2,
     'projects', JSON_ARRAY(
       JSON_OBJECT('id', '9701101', 'projectName', '学习平台稳定性治理', 'result', '核心接口 P95 从 620ms 降至 260ms'),
       JSON_OBJECT('id', '9701102', 'projectName', '招聘 SaaS 候选人检索优化', 'result', '核心筛选接口 P95 从 1.8s 降至 320ms'),
       JSON_OBJECT('id', '9701103', 'projectName', '订单异步可靠性改造', 'result', '重复扣减线上事故归零')
     ),
     'projectSnapshotSource', 'RESUME_PROJECT'
   ),
   'JOB_TARGETING', 9701301, 0, '2026-06-19 15:00:00', '2026-06-19 15:00:00', 0),
  (9701203, @seed_user_id, 9701001, 3, '当前投递版本',
   JSON_OBJECT(
     'title', '张伟｜高级 Java 后端工程师',
     'realName', '张伟',
     'targetPosition', '高级 Java 后端工程师',
     'skillStack', 'Java、Spring Boot、MySQL、Redis、RocketMQ、Docker、Prometheus、Grafana',
     'workExperience', '2023.04 至今 澄明云科有限公司 高级 Java 后端工程师；2020.07 至 2023.03 星河智联科技有限公司 Java 后端工程师。',
     'educationExperience', '2016.09 至 2020.06 华东理工大学 软件工程 本科。',
     'email', NULL, 'phone', NULL, 'summary', @resume_summary_v3,
     'projects', JSON_ARRAY(
       JSON_OBJECT('id', '9701101', 'projectName', '学习平台稳定性治理', 'result', '核心接口 P95 从 620ms 降至 260ms，进度写入失败率降至 0.03%'),
       JSON_OBJECT('id', '9701102', 'projectName', '招聘 SaaS 候选人检索优化', 'result', '核心筛选接口 P95 从 1.8s 降至 320ms，扫描行数降低 87%'),
       JSON_OBJECT('id', '9701103', 'projectName', '订单异步可靠性改造', 'result', '重复扣减线上事故归零，异常订单平均修复时长降至 9 分钟')
     ),
     'projectSnapshotSource', 'RESUME_PROJECT'
   ),
   'SUGGESTION_BATCH', 9701501, 1, '2026-07-05 16:30:00', '2026-07-05 16:30:00', 0);

INSERT INTO resume_suggestion (
  id, user_id, resume_id, source_resume_version_id, source_type, source_id,
  source_version, section_key, section_id, field_path, anchor_start, anchor_end,
  anchor_text_hash, original_text, suggested_text, accepted_text, evidence_refs_json,
  risk_level, rationale, status, decision_version, applied_resume_version_id,
  undo_resume_version_id, decided_at, created_at, updated_at, deleted
) VALUES
  (9701501, @seed_user_id, 9701001, 9701202, 'AI', 9701301,
   'test-seed-v1', 'summary', 'summary', 'summary',
   0, CHAR_LENGTH(@resume_summary_v2), SHA2(@resume_summary_v2, 256),
   @resume_summary_v2, @resume_summary_v3, @resume_summary_v3,
   JSON_ARRAY(JSON_OBJECT('sourceType', 'PROJECT_EVIDENCE', 'sourceId', 9701701, 'sourceSummary', '学习平台稳定性治理')),
   'LOW', '将稳定性治理的具体结果写入摘要，增强与目标岗位的可验证关联。', 'ACCEPTED', 1, 9701203,
   NULL, '2026-07-05 16:30:00', '2026-07-05 15:40:00', '2026-07-05 16:30:00', 0),
  (9701502, @seed_user_id, 9701001, 9701202, 'AI', 9701301,
   'test-seed-v1', 'projects', '9701102', 'projects[1].optimizationResults',
   0, 0, SHA2('', 256),
   '', '补充联合索引设计依据和 EXPLAIN 验证过程。', NULL,
   JSON_ARRAY(JSON_OBJECT('sourceType', 'PROJECT_EVIDENCE', 'sourceId', 9701702, 'sourceSummary', '招聘 SaaS 候选人检索优化')),
   'MEDIUM', '建议存在事实依据，但当前描述已覆盖核心量化结果，因此保留为拒绝样本。', 'REJECTED', 1, NULL,
   NULL, '2026-07-05 16:40:00', '2026-07-05 15:45:00', '2026-07-05 16:40:00', 0),
  (9701503, @seed_user_id, 9701001, 9701203, 'AI', 9701301,
   'test-seed-v2', 'summary', 'summary', 'summary',
   @summary_phrase_a_start, @summary_phrase_a_start + CHAR_LENGTH(@summary_phrase_a), SHA2(@summary_phrase_a, 256),
   @summary_phrase_a, '百万级学习行为高峰下的核心链路建设与治理', NULL,
   JSON_ARRAY(JSON_OBJECT('sourceType', 'PROJECT_EVIDENCE', 'sourceId', 9701701, 'sourceSummary', '学习平台稳定性治理')),
   'LOW', '同一区块的短替换建议，用于验证批量接受后长度变化下的精确撤销。', 'PENDING', 0, NULL,
   NULL, NULL, @seed_now, @seed_now, 0),
  (9701504, @seed_user_id, 9701001, 9701203, 'AI', 9701301,
   'test-seed-v2', 'summary', 'summary', 'summary',
   @summary_phrase_b_start, @summary_phrase_b_start + CHAR_LENGTH(@summary_phrase_b), SHA2(@summary_phrase_b, 256),
   @summary_phrase_b, '稳定性与可恢复性治理闭环', NULL,
   JSON_ARRAY(JSON_OBJECT('sourceType', 'PROJECT_EVIDENCE', 'sourceId', 9701701, 'sourceSummary', '学习平台稳定性治理')),
   'LOW', '同一区块的长替换建议，用于验证批量接受后长度变化下的精确撤销。', 'PENDING', 0, NULL,
   NULL, NULL, @seed_now, @seed_now, 0);

INSERT INTO resume_suggestion_decision (
  id, user_id, suggestion_id, decision_type, from_status, to_status, decision_version,
  result_resume_version_id, idempotency_key, note, created_at, updated_at, deleted
) VALUES
  (9701601, @seed_user_id, 9701501, 'ACCEPT', 'PENDING', 'ACCEPTED', 1, 9701203,
   'seed-suggestion-accept-9701501', '已纳入当前投递版本。', '2026-07-05 16:30:00', '2026-07-05 16:30:00', 0),
  (9701602, @seed_user_id, 9701502, 'REJECT', 'PENDING', 'REJECTED', 1, NULL,
   'seed-suggestion-reject-9701502', '当前项目描述已经覆盖该建议的主要信息。', '2026-07-05 16:40:00', '2026-07-05 16:40:00', 0);

-- ============================================================================
-- Job targeting, evidence matrix, readiness, and delivery
-- ============================================================================

INSERT INTO target_job (
  id, user_id, job_title, company_name, job_level, jd_text, jd_source, current_flag,
  status, priority, preparation_status, parse_status, parse_error_message,
  created_at, updated_at, deleted
) VALUES
  (9701301, @seed_user_id, '高级 Java 后端工程师', '澄观智研有限公司', '高级',
   '岗位职责：负责高并发 Java 服务设计与核心链路稳定性治理；参与微服务架构、数据一致性和可观测性建设。任职要求：熟悉 Spring Boot、MySQL、Redis、RocketMQ，具备复杂系统设计、性能优化和故障排查经验；有 SaaS 或在线教育业务经验者优先。',
   'MANUAL', 1, 1, 100, 'PREPARING', 'PARSED', NULL, '2026-06-19 10:00:00', @seed_now, 0),
  (9701302, @seed_user_id, 'Java 平台研发工程师', '星穹企业服务有限公司', '中高级',
   '负责企业平台后台服务、权限与审计能力建设。要求熟悉 Java、Spring、MySQL、Redis、消息队列和监控体系。',
   'MANUAL', 0, 1, 80, 'PREPARING', 'PARSED', NULL, '2026-06-20 10:00:00', @seed_now, 0);

INSERT INTO job_description_analysis (
  id, target_job_id, user_id, job_title, company_name, job_level,
  responsibilities_json, required_skills_json, bonus_skills_json, tech_keywords_json,
  business_keywords_json, experience_requirement, project_experience_requirement,
  interview_focus_json, skill_weights_json, summary, raw_result_json,
  parse_status, created_at, updated_at, deleted
) VALUES
  (9701301, 9701301, @seed_user_id, '高级 Java 后端工程师', '澄观智研有限公司', '高级',
   JSON_ARRAY('高并发服务设计', '核心链路稳定性治理', '微服务架构建设', '故障复盘与持续优化'),
   JSON_ARRAY('Java', 'Spring Boot', 'MySQL', 'Redis', 'RocketMQ', '系统设计', '可观测性'),
   JSON_ARRAY('SaaS 业务经验', '在线教育业务经验'),
   JSON_ARRAY('Java', 'Spring Boot', 'MySQL', 'Redis', 'RocketMQ', 'Prometheus', 'Grafana'),
   JSON_ARRAY('高并发', '数据一致性', '稳定性', '可观测性', '微服务'),
   '5 年以上后端研发经验，有高并发系统实践。',
   '至少具备一个可量化的稳定性治理或性能优化项目。',
   JSON_ARRAY('系统设计', '消息幂等', 'MySQL 性能优化', '故障排查', '项目深挖'),
   JSON_OBJECT('Java', 1.0, 'Spring Boot', 1.0, 'MySQL', 1.0, 'Redis', 1.0, 'RocketMQ', 1.0, '可观测性', 0.9),
   '岗位聚焦高并发服务、异步可靠性和工程化可观测性。候选人项目有较强相关证据，仍需补强系统设计表达和可追问的治理复盘。',
   JSON_OBJECT('fallback', false, 'confidence', 'HIGH', 'source', 'FORMAL_TEST_SEED'),
   'PARSED', '2026-06-19 10:05:00', @seed_now, 0),
  (9701302, 9701302, @seed_user_id, 'Java 平台研发工程师', '星穹企业服务有限公司', '中高级',
   JSON_ARRAY('平台服务开发', '权限与审计能力建设'),
   JSON_ARRAY('Java', 'Spring', 'MySQL', 'Redis', '消息队列', '监控体系'),
   JSON_ARRAY('企业 SaaS 经验'),
   JSON_ARRAY('Java', 'Spring', 'MySQL', 'Redis', 'RocketMQ'),
   JSON_ARRAY('权限', '审计', '平台化'),
   '3 年以上 Java 后端经验。', '有企业平台或 SaaS 项目经验。',
   JSON_ARRAY('权限模型', '审计日志', '缓存一致性'), JSON_OBJECT('Java', 1.0, 'MySQL', 1.0),
   '平台岗位适合作为横向对照，重点关注权限和审计经验。', JSON_OBJECT('fallback', false, 'confidence', 'MEDIUM'),
   'PARSED', '2026-06-20 10:05:00', @seed_now, 0);

INSERT INTO resume_job_match_report (
  id, user_id, resume_id, resume_version_id, target_job_id, jd_analysis_id,
  overall_score, tech_stack_score, project_experience_score, business_fit_score,
  communication_score, strengths_json, gaps_json, resume_risks_json,
  optimization_suggestions_json, recommended_learning_topics_json,
  recommended_interview_topics_json, summary, raw_result_json, status,
  created_at, updated_at, deleted
) VALUES
  (9701401, @seed_user_id, 9701001, 9701202, 9701301, 9701301,
   76, 82, 79, 74, 68,
   JSON_ARRAY('Spring Boot、MySQL、Redis 与 RocketMQ 经历匹配岗位核心栈', '有稳定性治理与性能优化量化结果'),
   JSON_ARRAY('系统设计表达需要更聚焦决策与取舍', '可观测性指标口径需要准备追问证据'),
   JSON_ARRAY('部分项目亮点缺少明确的个人职责边界'),
   JSON_ARRAY('将成果与个人负责模块对应', '补充一次故障复盘的假设验证过程'),
   JSON_ARRAY('分布式一致性', '可观测性体系', '高并发系统设计'),
   JSON_ARRAY('RocketMQ 幂等', '慢 SQL 优化', '稳定性治理项目深挖'),
   '定制版本已达到岗位基础匹配，但仍应将项目决策过程和个人边界表达得更清晰。',
   JSON_OBJECT('fallback', false, 'confidence', 'HIGH'), 'SUCCESS',
   '2026-06-19 16:00:00', '2026-06-19 16:00:00', 0),
  (9701402, @seed_user_id, 9701001, 9701203, 9701301, 9701301,
   86, 90, 88, 84, 80,
   JSON_ARRAY('核心技术栈与岗位要求高度重合', '稳定性治理项目具备量化结果与可追问的技术决策'),
   JSON_ARRAY('需继续练习系统设计中的容量估算和降级取舍'),
    JSON_ARRAY('暂无高风险表述'),
    JSON_ARRAY('围绕消息幂等准备 2 个替代方案比较', '准备容量估算口径'),
    JSON_ARRAY('系统设计', '高并发缓存策略', '异常补偿与复盘'),
    JSON_ARRAY('系统设计', '消息幂等', '稳定性治理项目深挖'),
    '当前投递版本与主岗位高度匹配，建议完成针对性面试练习后再推进正式投递。',
   JSON_OBJECT('fallback', false, 'confidence', 'HIGH'), 'SUCCESS',
   '2026-07-15 18:10:00', '2026-07-15 18:10:00', 0);

INSERT INTO resume_job_match_detail (
  id, report_id, user_id, dimension, skill_name, match_level, score, evidence,
  gap_description, suggestion, created_at, updated_at, deleted
) VALUES
  (9701411, 9701402, @seed_user_id, 'TECH_STACK', 'Spring Boot', 'STRONG', 92,
   '三段项目均使用 Spring Boot，并包含事务、异步和可观测性实践。', NULL, '准备事务边界与异步上下文追问。', @seed_now, @seed_now, 0),
  (9701412, 9701402, @seed_user_id, 'TECH_STACK', 'MySQL', 'STRONG', 90,
   '候选人检索优化项目包含联合索引、EXPLAIN 与压测验证。', NULL, '准备索引选择性和范围条件的取舍。', @seed_now, @seed_now, 0),
  (9701413, 9701402, @seed_user_id, 'TECH_STACK', 'Redis', 'STRONG', 86,
   '学习平台项目包含热点保护、缓存失效和补偿实践。', NULL, '准备缓存一致性异常场景。', @seed_now, @seed_now, 0),
  (9701414, 9701402, @seed_user_id, 'TECH_STACK', 'RocketMQ', 'STRONG', 88,
   '订单可靠性改造包含消费幂等、死信补偿和状态机。', NULL, '准备重复投递与补偿幂等细节。', @seed_now, @seed_now, 0),
  (9701415, 9701402, @seed_user_id, 'COMMUNICATION', '系统设计表达', 'PARTIAL', 78,
   '已有项目结果，但容量估算和方案取舍口径仍需更结构化。', '缺少统一的容量和降级表达框架。', '完成一次系统设计限时复练。', @seed_now, @seed_now, 0);

INSERT INTO project_evidence (
  id, user_id, title, role, start_date, end_date, background, responsibility,
  tech_stack, difficulty, solution, result, reflection, completeness_score,
  completeness_status, missing_fields, source_resume_id, source_resume_project_id,
  target_job_id, deleted, created_at, updated_at
) VALUES
  (9701701, @seed_user_id, '学习平台稳定性治理', '后端负责人', '2025-03', '2026-02',
   '核心学习进度链路在晚高峰出现排队与告警噪音。', '负责链路拆分、消息可靠投递、缓存治理与告警分级。',
   'Spring Boot、MySQL、Redis、RocketMQ、Prometheus、Grafana',
   '需要在不影响存量学习记录的前提下提升高峰稳定性。',
   '用可靠事件表保证提交后投递；消费端以业务唯一键幂等；热点课程采用分级缓存和降级；通过 TraceId 串联指标。',
   '核心接口 P95 从 620ms 降至 260ms；进度写入失败率降至 0.03%；夜间人工处理时长降低 58%。',
   '后续需要补充容量模型和跨区域故障演练。', 94, 'READY', NULL, 9701001, 9701101, 9701301, 0, @seed_now, @seed_now),
  (9701702, @seed_user_id, '招聘 SaaS 候选人检索优化', '核心开发', '2024-04', '2025-02',
   '多条件筛选列表随客户数据增长出现慢查询和分页不稳定。', '负责查询画像、索引重构、游标分页和检索降级。',
   'Java、MySQL、Elasticsearch、Redis',
   '不同筛选条件的选择性差异大，且需要保证排序稳定。',
   '依据慢 SQL 画像重构联合索引，改造大偏移分页，索引同步失败时降级为数据库精确查询。',
   '核心筛选接口 P95 从 1.8s 降至 320ms；扫描行数降低 87%；超时率下降 92%。',
   '应进一步准备索引选择性和数据倾斜的追问。', 92, 'READY', NULL, 9701001, 9701102, 9701301, 0, @seed_now, @seed_now),
  (9701703, @seed_user_id, '订单异步可靠性改造', 'Java 后端工程师', '2022-06', '2024-03',
   '支付回调高峰时出现重复消费和补偿不可追踪。', '参与订单状态机、消费幂等、失败补偿与链路追踪建设。',
   'Spring Cloud、RocketMQ、MySQL、Redis、SkyWalking',
   '既要避免重复扣减，又要在下游失败时保留可审计恢复路径。',
   '使用业务唯一键与唯一索引保障幂等，明确状态机迁移，失败进入死信和定时补偿，并记录 TraceId。',
   '重复扣减线上事故归零；异常订单平均修复时长从 45 分钟降至 9 分钟。',
   '需要补充消息顺序与最终一致性边界表达。', 91, 'READY', NULL, 9701001, 9701103, 9701301, 0, @seed_now, @seed_now);

INSERT INTO project_skill_evidence (
  id, user_id, project_evidence_id, skill_name, skill_category, evidence_text,
  strength_level, jd_keyword, risk_points, source_type, confirmed, deleted,
  created_at, updated_at
) VALUES
  (9701801, @seed_user_id, 9701701, 'Spring Boot', '后端框架', '完成核心学习进度服务的链路拆分与接口治理。', 'STRONG', 'Spring Boot', NULL, 'MANUAL', 1, 0, @seed_now, @seed_now),
  (9701802, @seed_user_id, 9701701, 'Redis', '缓存', '针对热点课程设计分级缓存、失效和降级策略。', 'STRONG', 'Redis', '需要说明缓存与数据库一致性边界。', 'MANUAL', 1, 0, @seed_now, @seed_now),
  (9701803, @seed_user_id, 9701701, 'RocketMQ', '消息队列', '以可靠事件和业务唯一键实现消息投递与消费幂等。', 'STRONG', 'RocketMQ', NULL, 'MANUAL', 1, 0, @seed_now, @seed_now),
  (9701804, @seed_user_id, 9701701, '可观测性', '工程效能', '通过 Prometheus、Grafana 和 TraceId 形成告警到复盘的闭环。', 'STRONG', '可观测性', NULL, 'MANUAL', 1, 0, @seed_now, @seed_now),
  (9701805, @seed_user_id, 9701702, 'MySQL', '数据库', '依据查询画像和 EXPLAIN 重构联合索引并压测验证。', 'STRONG', 'MySQL', NULL, 'MANUAL', 1, 0, @seed_now, @seed_now),
  (9701806, @seed_user_id, 9701702, '系统设计', '架构', '在高选择性筛选、排序稳定和检索降级间完成方案取舍。', 'MEDIUM', '系统设计', '需准备容量估算细节。', 'MANUAL', 1, 0, @seed_now, @seed_now),
  (9701807, @seed_user_id, 9701703, '幂等设计', '可靠性', '用业务唯一键、数据库约束和状态机阻断重复扣减。', 'STRONG', '幂等', NULL, 'MANUAL', 1, 0, @seed_now, @seed_now),
  (9701808, @seed_user_id, 9701703, '异常补偿', '可靠性', '死信和定时补偿任务保证异常订单可追踪恢复。', 'STRONG', '故障排查', NULL, 'MANUAL', 1, 0, @seed_now, @seed_now);

INSERT INTO project_story_generation (
  id, user_id, project_evidence_id, generation_type, target_job_id, prompt_version,
  result_text, structured_result_json, input_summary_json, result_source, accepted,
  status, error_message, prompt_tokens, completion_tokens, total_tokens, deleted,
  created_at, updated_at
) VALUES
  (9701901, @seed_user_id, 9701701, 'INTERVIEW_STORY', 9701301, 'formal-seed-v1',
   '在学习平台稳定性治理中，我先用链路指标定位晚高峰排队和消息重复，再以可靠事件、幂等消费和热点缓存治理恢复核心链路。上线后通过灰度和告警复盘确认 P95 从 620ms 降到 260ms。',
   JSON_OBJECT('structure', 'STAR', 'result', 'P95 从 620ms 降至 260ms', 'fallback', false),
   JSON_OBJECT('evidenceId', 9701701, 'targetJobId', 9701301), 'LOCAL_GENERATOR', 1,
   'SUCCESS', NULL, 560, 320, 880, 0, @seed_now, @seed_now),
  (9701902, @seed_user_id, 9701702, 'INTERVIEW_STORY', 9701301, 'formal-seed-v1',
   '候选人检索优化先用慢 SQL 画像识别高成本组合，再按选择性重构联合索引并改造游标分页，最终将 P95 从 1.8 秒降至 320 毫秒。',
   JSON_OBJECT('structure', 'STAR', 'result', 'P95 从 1.8s 降至 320ms', 'fallback', false),
   JSON_OBJECT('evidenceId', 9701702, 'targetJobId', 9701301), 'LOCAL_GENERATOR', 1,
   'SUCCESS', NULL, 490, 280, 770, 0, @seed_now, @seed_now);

INSERT INTO job_requirement (
  id, user_id, target_job_id, jd_analysis_id, requirement_key, requirement_type,
  requirement_name, description, category, required_level, priority, weight,
  confidence_level, source_field, source_json, source_fallback, active_flag,
  deleted, created_at, updated_at
) VALUES
  (9702001, @seed_user_id, 9701301, 9701301, SHA2('required_skills|MUST|Java', 256), 'SKILL', 'Java', '具备扎实的 Java 服务端开发与问题定位能力。', '技术栈', '熟练', 'MUST', 1.0, 'HIGH', 'required_skills', JSON_OBJECT('keyword', 'Java'), 0, 1, 0, @seed_now, @seed_now),
  (9702002, @seed_user_id, 9701301, 9701301, SHA2('required_skills|MUST|Spring Boot', 256), 'SKILL', 'Spring Boot', '能够独立设计和维护 Spring Boot 服务。', '技术栈', '熟练', 'MUST', 1.0, 'HIGH', 'required_skills', JSON_OBJECT('keyword', 'Spring Boot'), 0, 1, 0, @seed_now, @seed_now),
  (9702003, @seed_user_id, 9701301, 9701301, SHA2('required_skills|MUST|MySQL', 256), 'SKILL', 'MySQL', '熟悉索引、事务和性能优化。', '数据存储', '熟练', 'MUST', 1.0, 'HIGH', 'required_skills', JSON_OBJECT('keyword', 'MySQL'), 0, 1, 0, @seed_now, @seed_now),
  (9702004, @seed_user_id, 9701301, 9701301, SHA2('required_skills|MUST|Redis', 256), 'SKILL', 'Redis', '理解缓存一致性、热点与失效策略。', '数据存储', '熟练', 'MUST', 0.9, 'HIGH', 'required_skills', JSON_OBJECT('keyword', 'Redis'), 0, 1, 0, @seed_now, @seed_now),
  (9702005, @seed_user_id, 9701301, 9701301, SHA2('required_skills|MUST|RocketMQ', 256), 'SKILL', 'RocketMQ', '理解消息可靠投递、幂等与补偿。', '消息队列', '熟练', 'MUST', 0.9, 'HIGH', 'required_skills', JSON_OBJECT('keyword', 'RocketMQ'), 0, 1, 0, @seed_now, @seed_now),
  (9702006, @seed_user_id, 9701301, 9701301, SHA2('responsibilities|MUST|高并发服务设计', 256), 'RESPONSIBILITY', '高并发服务设计', '能进行容量评估、限流降级和性能优化。', '系统设计', '熟练', 'MUST', 1.0, 'HIGH', 'responsibilities', JSON_OBJECT('keyword', '高并发'), 0, 1, 0, @seed_now, @seed_now),
  (9702007, @seed_user_id, 9701301, 9701301, SHA2('responsibilities|MUST|稳定性治理', 256), 'RESPONSIBILITY', '稳定性治理', '能建立指标、告警、故障复盘和恢复闭环。', '工程效能', '熟练', 'MUST', 1.0, 'HIGH', 'responsibilities', JSON_OBJECT('keyword', '稳定性'), 0, 1, 0, @seed_now, @seed_now),
  (9702008, @seed_user_id, 9701301, 9701301, SHA2('responsibilities|MUST|数据一致性', 256), 'RESPONSIBILITY', '数据一致性', '能处理异步消息、缓存和数据库间的一致性边界。', '可靠性', '熟练', 'MUST', 1.0, 'HIGH', 'responsibilities', JSON_OBJECT('keyword', '数据一致性'), 0, 1, 0, @seed_now, @seed_now),
  (9702009, @seed_user_id, 9701301, 9701301, SHA2('responsibilities|MUST|可观测性', 256), 'RESPONSIBILITY', '可观测性', '能基于指标、日志和链路追踪完成问题闭环。', '工程效能', '熟练', 'MUST', 0.8, 'HIGH', 'responsibilities', JSON_OBJECT('keyword', '可观测性'), 0, 1, 0, @seed_now, @seed_now),
  (9702010, @seed_user_id, 9701301, 9701301, SHA2('bonus_skills|NICE_TO_HAVE|SaaS 业务经验', 256), 'EXPERIENCE', 'SaaS 业务经验', '有企业服务平台经验优先。', '业务经验', '了解', 'NICE_TO_HAVE', 0.5, 'MEDIUM', 'bonus_skills', JSON_OBJECT('keyword', 'SaaS'), 0, 1, 0, @seed_now, @seed_now),
  (9702011, @seed_user_id, 9701301, 9701301, SHA2('bonus_skills|NICE_TO_HAVE|在线教育业务经验', 256), 'EXPERIENCE', '在线教育业务经验', '有在线教育业务经验优先。', '业务经验', '了解', 'NICE_TO_HAVE', 0.5, 'MEDIUM', 'bonus_skills', JSON_OBJECT('keyword', '在线教育'), 0, 1, 0, @seed_now, @seed_now),
  (9702012, @seed_user_id, 9701301, 9701301, SHA2('interview_focus|NICE_TO_HAVE|系统设计表达', 256), 'INTERVIEW', '系统设计表达', '能清晰说明容量、取舍和降级方案。', '面试表现', '熟练', 'NICE_TO_HAVE', 0.7, 'MEDIUM', 'interview_focus', JSON_OBJECT('keyword', '系统设计'), 0, 1, 0, @seed_now, @seed_now);

INSERT INTO job_requirement_evidence (
  id, user_id, target_job_id, requirement_id, project_evidence_id,
  project_skill_evidence_id, evidence_type, evidence_id, evidence_sub_id,
  title, excerpt, result_source, result_score, occurred_at, evidence_ref_key,
  match_type, coverage_level, confidence_level, evidence_source_type, confirmed,
  fallback, evidence_text, match_reason, active_flag, deleted, created_at, updated_at
) VALUES
  (9702101, @seed_user_id, 9701301, 9702001, 9701701, 9701801, 'PROJECT_SKILL_EVIDENCE', 9701701, 9701801, '学习平台稳定性治理', '完成核心学习进度服务的链路拆分与接口治理。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702001|9701801', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '完成核心学习进度服务的链路拆分与接口治理。', 'Java 服务端实践与岗位要求直接匹配。', 1, 0, @seed_now, @seed_now),
  (9702102, @seed_user_id, 9701301, 9702002, 9701701, 9701801, 'PROJECT_SKILL_EVIDENCE', 9701701, 9701801, '学习平台稳定性治理', '完成核心学习进度服务的链路拆分与接口治理。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702002|9701801', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '完成核心学习进度服务的链路拆分与接口治理。', 'Spring Boot 服务治理与岗位要求直接匹配。', 1, 0, @seed_now, @seed_now),
  (9702103, @seed_user_id, 9701301, 9702003, 9701702, 9701805, 'PROJECT_SKILL_EVIDENCE', 9701702, 9701805, '招聘 SaaS 候选人检索优化', '依据查询画像和 EXPLAIN 重构联合索引并压测验证。', 'PROJECT_EVIDENCE', 92, '2025-02-28 18:00:00', SHA2('9702003|9701805', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '依据查询画像和 EXPLAIN 重构联合索引并压测验证。', 'MySQL 性能优化证据直接匹配。', 1, 0, @seed_now, @seed_now),
  (9702104, @seed_user_id, 9701301, 9702004, 9701701, 9701802, 'PROJECT_SKILL_EVIDENCE', 9701701, 9701802, '学习平台稳定性治理', '针对热点课程设计分级缓存、失效和降级策略。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702004|9701802', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '针对热点课程设计分级缓存、失效和降级策略。', 'Redis 缓存治理与岗位要求直接匹配。', 1, 0, @seed_now, @seed_now),
  (9702105, @seed_user_id, 9701301, 9702005, 9701701, 9701803, 'PROJECT_SKILL_EVIDENCE', 9701701, 9701803, '学习平台稳定性治理', '以可靠事件和业务唯一键实现消息投递与消费幂等。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702005|9701803', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '以可靠事件和业务唯一键实现消息投递与消费幂等。', 'RocketMQ 可靠性实践与岗位要求直接匹配。', 1, 0, @seed_now, @seed_now),
  (9702106, @seed_user_id, 9701301, 9702006, 9701701, 9701801, 'PROJECT_EVIDENCE', 9701701, NULL, '学习平台稳定性治理', '核心接口 P95 从 620ms 降至 260ms。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702006|9701701', 256), 'SEMANTIC', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '核心接口 P95 从 620ms 降至 260ms。', '有高峰链路治理和量化性能结果。', 1, 0, @seed_now, @seed_now),
  (9702107, @seed_user_id, 9701301, 9702007, 9701701, 9701804, 'PROJECT_EVIDENCE', 9701701, NULL, '学习平台稳定性治理', '通过 Prometheus、Grafana 和 TraceId 形成告警到复盘的闭环。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702007|9701701', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '通过 Prometheus、Grafana 和 TraceId 形成告警到复盘的闭环。', '稳定性治理有完整指标和复盘闭环。', 1, 0, @seed_now, @seed_now),
  (9702108, @seed_user_id, 9701301, 9702008, 9701703, 9701807, 'PROJECT_SKILL_EVIDENCE', 9701703, 9701807, '订单异步可靠性改造', '用业务唯一键、数据库约束和状态机阻断重复扣减。', 'PROJECT_EVIDENCE', 91, '2024-03-31 18:00:00', SHA2('9702008|9701807', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '用业务唯一键、数据库约束和状态机阻断重复扣减。', '数据一致性证据直接匹配。', 1, 0, @seed_now, @seed_now),
  (9702109, @seed_user_id, 9701301, 9702009, 9701701, 9701804, 'PROJECT_SKILL_EVIDENCE', 9701701, 9701804, '学习平台稳定性治理', '通过 Prometheus、Grafana 和 TraceId 形成告警到复盘的闭环。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702009|9701804', 256), 'EXACT', 'STRONG', 'HIGH', 'PROJECT_EVIDENCE', 1, 0, '通过 Prometheus、Grafana 和 TraceId 形成告警到复盘的闭环。', '可观测性证据直接匹配。', 1, 0, @seed_now, @seed_now),
  (9702110, @seed_user_id, 9701301, 9702010, 9701702, 9701806, 'PROJECT_EVIDENCE', 9701702, NULL, '招聘 SaaS 候选人检索优化', '面向企业招聘 SaaS 完成检索优化与降级策略。', 'PROJECT_EVIDENCE', 92, '2025-02-28 18:00:00', SHA2('9702010|9701702', 256), 'EXACT', 'STRONG', 'MEDIUM', 'PROJECT_EVIDENCE', 1, 0, '面向企业招聘 SaaS 完成检索优化与降级策略。', '具备企业 SaaS 业务经验。', 1, 0, @seed_now, @seed_now),
  (9702111, @seed_user_id, 9701301, 9702011, 9701701, 9701804, 'PROJECT_EVIDENCE', 9701701, NULL, '学习平台稳定性治理', '面向在线学习平台的核心进度链路完成稳定性治理。', 'PROJECT_EVIDENCE', 94, '2026-02-28 18:00:00', SHA2('9702011|9701701', 256), 'EXACT', 'STRONG', 'MEDIUM', 'PROJECT_EVIDENCE', 1, 0, '面向在线学习平台的核心进度链路完成稳定性治理。', '具备在线教育业务经验。', 1, 0, @seed_now, @seed_now),
  (9702112, @seed_user_id, 9701301, 9702012, 9701702, 9701806, 'PROJECT_EVIDENCE', 9701702, NULL, '招聘 SaaS 候选人检索优化', '在高选择性筛选、排序稳定和检索降级间完成方案取舍。', 'PROJECT_EVIDENCE', 92, '2025-02-28 18:00:00', SHA2('9702012|9701702', 256), 'SEMANTIC', 'MEDIUM', 'MEDIUM', 'PROJECT_EVIDENCE', 1, 0, '在高选择性筛选、排序稳定和检索降级间完成方案取舍。', '存在系统设计取舍证据，但仍应通过复练强化表达。', 1, 0, @seed_now, @seed_now);

INSERT INTO skill_profile (
  id, user_id, target_job_id, match_report_id, profile_name, overall_level,
  overall_score, summary, source_type, source_biz_id, status, raw_result_json,
  error_message, created_at, updated_at, deleted
) VALUES (
  9702201, @seed_user_id, 9701301, 9701402, '高级 Java 后端岗位能力画像', 4, 86,
  '核心技术栈与项目经验较强，当前应集中提升系统设计表达、容量估算和异常边界说明。',
  'RESUME_JOB_MATCH', 9701402, 'SUCCESS',
  JSON_OBJECT('fallback', false, 'confidence', 'HIGH'), NULL, @seed_now, @seed_now, 0
);

INSERT INTO skill_gap_item (
  id, profile_id, user_id, target_job_id, skill_name, category, target_level,
  current_level, gap_level, confidence, severity, evidence_sources_json,
  gap_description, recommended_actions_json, priority, source_type, source_biz_id,
  created_at, updated_at, deleted
) VALUES
  (9702211, 9702201, @seed_user_id, 9701301, '系统设计表达', '面试表现', 5, 3, 2, 0.86, 'MEDIUM',
   JSON_ARRAY('招聘 SaaS 候选人检索优化', '项目深挖面试报告'),
   '需要更清晰地说明容量估算、降级边界和方案取舍。',
   JSON_ARRAY('完成一次 45 分钟系统设计复练', '将容量估算写入项目复盘'), 1, 'RESUME_JOB_MATCH', 9701402, @seed_now, @seed_now, 0),
  (9702212, 9702201, @seed_user_id, 9701301, '异常补偿边界', '可靠性', 5, 4, 1, 0.82, 'LOW',
   JSON_ARRAY('订单异步可靠性改造'),
   '具备补偿实践，但需准备消息乱序和重复补偿的明确边界。',
   JSON_ARRAY('梳理死信、重试与人工介入条件'), 2, 'RESUME_JOB_MATCH', 9701402, @seed_now, @seed_now, 0);

INSERT INTO job_readiness_snapshot (
  id, user_id, target_job_id, jd_analysis_id, snapshot_hash, policy_version,
  readiness_score, readiness_level, confidence_level, fallback, requirement_count,
  strong_count, weak_count, missing_count, must_requirement_count, must_missing_count,
  summary_json, matrix_json, dimension_json, generated_at, deleted, created_at, updated_at
) VALUES
  (9702301, @seed_user_id, 9701301, 9701301, SHA2('9701301|2026-06-23|58', 256), 'READINESS_V4',
   58, 'NEEDS_WORK', 'MEDIUM', 0, 12, 5, 3, 4, 9, 3,
   JSON_OBJECT('headline', '已建立岗位目标，但核心项目证据与面试准备仍不足。', 'trend', 'UP'),
   JSON_OBJECT('strong', 5, 'weak', 3, 'missing', 4),
   JSON_OBJECT('resume', 62, 'project', 58, 'knowledge', 48, 'interview', 52, 'delivery', 55),
   '2026-06-23 19:00:00', 0, '2026-06-23 19:00:00', '2026-06-23 19:00:00'),
  (9702302, @seed_user_id, 9701301, 9701301, SHA2('9701301|2026-07-08|64', 256), 'READINESS_V4',
   64, 'NEEDS_WORK', 'HIGH', 0, 12, 8, 2, 2, 9, 1,
   JSON_OBJECT('headline', '项目证据和简历定制已提升，系统设计表达仍需加强。', 'trend', 'UP'),
   JSON_OBJECT('strong', 8, 'weak', 2, 'missing', 2),
   JSON_OBJECT('resume', 74, 'project', 76, 'knowledge', 52, 'interview', 59, 'delivery', 61),
   '2026-07-08 20:00:00', 0, '2026-07-08 20:00:00', '2026-07-08 20:00:00'),
  (9702303, @seed_user_id, 9701301, 9701301, SHA2('9701301|2026-07-15|69', 256), 'READINESS_V4',
   69, 'NEAR_READY', 'HIGH', 0, 12, 10, 1, 1, 9, 1,
   JSON_OBJECT('headline', '当前已接近投递准备状态；建议完成系统设计复练后推进正式投递。', 'trend', 'UP'),
   JSON_OBJECT('strong', 10, 'weak', 1, 'missing', 1),
   JSON_OBJECT('resume', 84, 'project', 88, 'knowledge', 58, 'interview', 70, 'delivery', 67),
   '2026-07-15 20:00:00', 0, '2026-07-15 20:00:00', '2026-07-15 20:00:00');

INSERT INTO readiness_score_record (
  id, user_id, target_job_id, score_date, score, task_completion_rate,
  agent_success_rate, evidence_json, created_at, updated_at, deleted
) VALUES
  (9702401, @seed_user_id, 9701301, '2026-07-08', 61, 58.00, 88.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0),
  (9702402, @seed_user_id, 9701301, '2026-07-09', 62, 61.00, 90.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0),
  (9702403, @seed_user_id, 9701301, '2026-07-10', 63, 67.00, 92.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0),
  (9702404, @seed_user_id, 9701301, '2026-07-11', 64, 70.00, 92.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0),
  (9702405, @seed_user_id, 9701301, '2026-07-12', 65, 72.00, 94.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0),
  (9702406, @seed_user_id, 9701301, '2026-07-13', 66, 75.00, 94.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0),
  (9702407, @seed_user_id, 9701301, '2026-07-14', 68, 78.00, 95.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0),
  (9702408, @seed_user_id, 9701301, '2026-07-15', 69, 80.00, 95.00, JSON_OBJECT('source', 'FORMAL_TEST_SEED'), @seed_now, @seed_now, 0);

INSERT INTO skill_growth_snapshot (
  id, user_id, snapshot_date, skill_code, skill_name, score, task_count,
  done_count, source_type, source_id, created_at, updated_at, deleted
) VALUES
  (9702501, @seed_user_id, '2026-07-08', 'MYSQL', 'MySQL 性能优化', 66, 4, 2, 'FORMAL_TEST_SEED', 9701402, @seed_now, @seed_now, 0),
  (9702502, @seed_user_id, '2026-07-10', 'MYSQL', 'MySQL 性能优化', 71, 5, 4, 'FORMAL_TEST_SEED', 9701402, @seed_now, @seed_now, 0),
  (9702503, @seed_user_id, '2026-07-15', 'MYSQL', 'MySQL 性能优化', 78, 6, 5, 'FORMAL_TEST_SEED', 9701402, @seed_now, @seed_now, 0),
  (9702504, @seed_user_id, '2026-07-08', 'RELIABILITY', '异步可靠性', 68, 4, 3, 'FORMAL_TEST_SEED', 9701703, @seed_now, @seed_now, 0),
  (9702505, @seed_user_id, '2026-07-10', 'RELIABILITY', '异步可靠性', 74, 5, 4, 'FORMAL_TEST_SEED', 9701703, @seed_now, @seed_now, 0),
  (9702506, @seed_user_id, '2026-07-15', 'RELIABILITY', '异步可靠性', 82, 6, 6, 'FORMAL_TEST_SEED', 9701703, @seed_now, @seed_now, 0),
  (9702507, @seed_user_id, '2026-07-08', 'SYSTEM_DESIGN', '系统设计表达', 54, 4, 1, 'FORMAL_TEST_SEED', 9702201, @seed_now, @seed_now, 0),
  (9702508, @seed_user_id, '2026-07-10', 'SYSTEM_DESIGN', '系统设计表达', 60, 5, 3, 'FORMAL_TEST_SEED', 9702201, @seed_now, @seed_now, 0),
  (9702509, @seed_user_id, '2026-07-15', 'SYSTEM_DESIGN', '系统设计表达', 68, 6, 5, 'FORMAL_TEST_SEED', 9702201, @seed_now, @seed_now, 0),
  (9702510, @seed_user_id, '2026-07-08', 'OBSERVABILITY', '可观测性', 70, 3, 2, 'FORMAL_TEST_SEED', 9701701, @seed_now, @seed_now, 0),
  (9702511, @seed_user_id, '2026-07-10', 'OBSERVABILITY', '可观测性', 76, 4, 3, 'FORMAL_TEST_SEED', 9701701, @seed_now, @seed_now, 0),
  (9702512, @seed_user_id, '2026-07-15', 'OBSERVABILITY', '可观测性', 83, 5, 5, 'FORMAL_TEST_SEED', 9701701, @seed_now, @seed_now, 0);

INSERT INTO job_application_package (
  id, user_id, package_no, target_job_id, jd_analysis_id, resume_id,
  resume_version_id, match_report_id, application_id, company_name, job_title,
  readiness_level, readiness_score, readiness_reason, package_status, snapshot_json,
  checklist_json, actions_json, project_evidence_ids_json, trace_id, result_source,
  fallback, fallback_reason, snapshot_version, refreshed_at, created_at, updated_at, deleted
) VALUES (
  9702601, @seed_user_id, 'PKG-20260715-001', 9701301, 9701301, 9701001,
  9701203, 9701402, NULL, '澄观智研有限公司', '高级 Java 后端工程师',
  'NEAR_READY', 69, '最近一次可信就绪度快照为 NEAR_READY，完成系统设计复练后可推进投递。',
  'DRAFT',
  JSON_OBJECT('resumeVersionId', 9701203, 'matchScore', 86, 'readinessScore', 69),
  JSON_ARRAY(
    JSON_OBJECT('code', 'MATCH_SCORE_READY', 'passed', true),
    JSON_OBJECT('code', 'PROJECT_EVIDENCE_READY', 'passed', true),
    JSON_OBJECT('code', 'INTERVIEW_PREPARATION_READY', 'passed', false)
  ),
  JSON_ARRAY(JSON_OBJECT('action', 'PRACTICE_SYSTEM_DESIGN', 'title', '完成系统设计复练')),
  JSON_ARRAY(9701701, 9701702, 9701703), 'seed-pkg-9702601', 'REAL', 0, NULL, 1,
  '2026-07-15 20:30:00', '2026-07-15 20:30:00', '2026-07-15 20:30:00', 0
);

-- ============================================================================
-- Interview history and reports. The comparison/remediation records are left
-- for the real API flow so their current contract remains executable.
-- ============================================================================

INSERT INTO interview_session (
  id, user_id, application_id, application_package_id, resume_id, resume_version_id,
  target_job_id, jd_analysis_id, skill_profile_id, match_report_id, interview_mode,
  mode, title, target_position, experience_level, industry_template_id,
  industry_direction, industry_context, difficulty, interviewer_style,
  based_on_resume, status, report_status, training_scene, target_skill_domain,
  target_skill_codes, target_level, project_evidence_ids, follow_up_intensity,
  training_context_summary, source_report_id, source_requirement_ids,
  practice_purpose, remediation_strength, current_stage_id, current_question_id,
  current_question_group_id, answered_question_count, max_question_count,
  current_follow_up_count, total_score, start_time, end_time, failure_reason,
  created_at, updated_at, deleted
) VALUES
  (9702701, @seed_user_id, NULL, 9702601, 9701001, 9701202, 9701301, 9701301, 9702201, 9701401,
   'TECHNICAL_BASIC', 'TECHNICAL_BASIC', '高级 Java 后端工程师｜技术基础一面', '高级 Java 后端工程师', '5年',
   NULL, 'ONLINE_EDUCATION', '高并发学习平台服务与稳定性治理。', 'MEDIUM', 'STRUCTURED',
   1, 'COMPLETED', 'GENERATED', 'TECHNICAL', 'TECHNICAL', 'Java,MySQL,Redis,RocketMQ', 'ADVANCED',
   '9701701,9701703', 'NORMAL', '验证技术栈与可靠性基础表达。', NULL, NULL, NULL, NULL,
   NULL, NULL, 9700301, 5, 5, 0, 71, '2026-07-01 19:30:00', '2026-07-01 20:15:00', NULL,
   '2026-07-01 19:20:00', '2026-07-01 20:15:00', 0),
  (9702702, @seed_user_id, NULL, 9702601, 9701001, 9701203, 9701301, 9701301, 9702201, 9701402,
   'PROJECT_DEEP_DIVE', 'PROJECT_DEEP_DIVE', '高级 Java 后端工程师｜项目深挖二面', '高级 Java 后端工程师', '5年',
   NULL, 'ONLINE_EDUCATION', '围绕稳定性治理和异步可靠性项目进行深挖。', 'HARD', 'CHALLENGING',
   1, 'COMPLETED', 'GENERATED', 'PROJECT', 'PROJECT', 'SYSTEM_DESIGN,RELIABILITY,OBSERVABILITY', 'ADVANCED',
   '9701701,9701702,9701703', 'STRONG', '验证项目决策、容量估算和异常补偿边界。', NULL, NULL, NULL, NULL,
   NULL, NULL, 9700304, 5, 5, 0, 78, '2026-07-08 19:30:00', '2026-07-08 20:25:00', NULL,
   '2026-07-08 19:20:00', '2026-07-08 20:25:00', 0),
  (9702703, @seed_user_id, NULL, 9702601, 9701001, 9701203, 9701301, 9701301, 9702201, 9701402,
   'PROJECT_DEEP_DIVE', 'PROJECT_DEEP_DIVE', '高级 Java 后端工程师｜系统设计复练', '高级 Java 后端工程师', '5年',
   NULL, 'ONLINE_EDUCATION', '针对容量估算、降级策略和消息补偿的专项复练。', 'HARD', 'CHALLENGING',
   1, 'COMPLETED', 'GENERATED', 'SYSTEM_DESIGN', 'SYSTEM_DESIGN', 'SYSTEM_DESIGN,RELIABILITY', 'ADVANCED',
   '9701701,9701703', 'STRONG', '复练目标是建立容量、降级与补偿的一致表达。', 9702802, '9702006,9702008,9702012',
   '强化系统设计与可靠性表达。', 'STRONG', NULL, NULL, 9700303, 5, 5, 0, 86,
   '2026-07-11 19:30:00', '2026-07-11 20:25:00', NULL,
   '2026-07-11 19:20:00', '2026-07-11 20:25:00', 0);

INSERT INTO interview_stage (
  id, session_id, stage_type, stage_name, sort, stage_order, expected_question_count,
  asked_question_count, focus_points, based_on_resume, allow_follow_up,
  max_follow_up_count, status, score, created_at, updated_at, deleted
) VALUES
  (9702711, 9702701, 'TECHNICAL', 'Java 与并发基础', 10, 10, 2, 2, '集合、线程池、JVM', 1, 1, 2, 'COMPLETED', 72, @seed_now, @seed_now, 0),
  (9702712, 9702701, 'RELIABILITY', '数据一致性与消息可靠性', 20, 20, 3, 3, '幂等、缓存一致性、补偿', 1, 1, 2, 'COMPLETED', 70, @seed_now, @seed_now, 0),
  (9702721, 9702702, 'PROJECT', '稳定性治理项目深挖', 10, 10, 3, 3, '指标、方案取舍、量化结果', 1, 1, 3, 'COMPLETED', 80, @seed_now, @seed_now, 0),
  (9702722, 9702702, 'SYSTEM_DESIGN', '容量与降级设计', 20, 20, 2, 2, '容量估算、限流、降级', 1, 1, 3, 'COMPLETED', 76, @seed_now, @seed_now, 0),
  (9702731, 9702703, 'SYSTEM_DESIGN', '系统设计专项复练', 10, 10, 3, 3, '容量、缓存、限流与恢复', 1, 1, 3, 'COMPLETED', 87, @seed_now, @seed_now, 0),
  (9702732, 9702703, 'RELIABILITY', '消息补偿专项复练', 20, 20, 2, 2, '幂等、乱序、死信与人工介入', 1, 1, 3, 'COMPLETED', 85, @seed_now, @seed_now, 0);

INSERT INTO interview_message (
  id, session_id, stage_id, question_id, question_group_id, parent_message_id,
  generation_key, role, message_type, content, question_content, user_answer,
  ai_comment, ai_score, is_follow_up, follow_up_count, follow_up_reason,
  knowledge_points, score, comment, created_at, updated_at, deleted
) VALUES
  (9702741, 9702701, 9702711, 9700402, 9700301, NULL, 'seed-9702741', 'INTERVIEWER', 'QUESTION',
   '线程池参数如何根据业务负载设计？', '线程池参数如何根据业务负载设计？', NULL, NULL, NULL, 0, 0, NULL,
   '线程池、队列、拒绝策略', NULL, NULL, '2026-07-01 19:35:00', '2026-07-01 19:35:00', 0),
  (9702742, 9702701, 9702711, 9700402, 9700301, 9702741, 'seed-9702742', 'USER', 'ANSWER',
   '先依据 QPS 与平均耗时估算并发，再结合下游容量设置有界队列与拒绝降级。', NULL,
   '先依据 QPS 与平均耗时估算并发，再结合下游容量设置有界队列与拒绝降级。',
   '回答覆盖估算和保护，但没有充分展开任务类型和监控指标。', 72, 0, 0, NULL,
   '线程池、容量估算', 72, '可补充 CPU 与 IO 任务的差异。', '2026-07-01 19:38:00', '2026-07-01 19:38:00', 0),
  (9702751, 9702702, 9702721, 9700408, 9700304, NULL, 'seed-9702751', 'INTERVIEWER', 'QUESTION',
   '如何讲清一次系统稳定性治理项目？', '如何讲清一次系统稳定性治理项目？', NULL, NULL, NULL, 0, 0, NULL,
   'STAR、稳定性治理、项目复盘', NULL, NULL, '2026-07-08 19:35:00', '2026-07-08 19:35:00', 0),
  (9702752, 9702702, 9702721, 9700408, 9700304, 9702751, 'seed-9702752', 'USER', 'ANSWER',
   '我先用指标定位晚高峰排队和消息重复，再以可靠事件、幂等消费和热点缓存治理恢复核心链路，P95 从 620ms 降至 260ms。', NULL,
   '我先用指标定位晚高峰排队和消息重复，再以可靠事件、幂等消费和热点缓存治理恢复核心链路，P95 从 620ms 降至 260ms。',
   '结构清晰且有量化结果，建议继续补充容量估算和故障演练边界。', 80, 0, 0, NULL,
   '可观测性、幂等、缓存治理', 80, '项目结果可信，可继续强化方案取舍。', '2026-07-08 19:42:00', '2026-07-08 19:42:00', 0),
  (9702761, 9702703, 9702731, 9700405, 9700303, NULL, 'seed-9702761', 'INTERVIEWER', 'QUESTION',
   'Redis 与数据库如何实现最终一致？', 'Redis 与数据库如何实现最终一致？', NULL, NULL, NULL, 0, 0, NULL,
   '缓存一致性、补偿、版本控制', NULL, NULL, '2026-07-11 19:35:00', '2026-07-11 19:35:00', 0),
  (9702762, 9702703, 9702731, 9700405, 9700303, 9702761, 'seed-9702762', 'USER', 'ANSWER',
   '数据库提交是事实源，提交后删除缓存；失败由可靠事件和延迟补偿兜底，高并发写入使用版本号避免旧值覆盖。', NULL,
   '数据库提交是事实源，提交后删除缓存；失败由可靠事件和延迟补偿兜底，高并发写入使用版本号避免旧值覆盖。',
   '回答完整覆盖事实源、失效策略、失败补偿和并发边界。', 87, 0, 0, NULL,
   '缓存一致性、可靠事件、版本控制', 87, '可进一步说明热点 key 的降级策略。', '2026-07-11 19:40:00', '2026-07-11 19:40:00', 0);

INSERT INTO interview_report (
  id, session_id, user_id, status, total_score, stage_scores, weak_points,
  summary, strengths, weaknesses, main_problems, project_problems,
  review_suggestions, recommended_questions, qa_review, rubric_scores,
  follow_up_tree, advice_evidence, ability_profile_updates, rubric_version,
  report_content, generated_at, suggestions, failure_reason, generation_token,
  created_at, updated_at, deleted
) VALUES
  (9702801, 9702701, @seed_user_id, 'GENERATED', 71,
   JSON_OBJECT('TECHNICAL', 72, 'RELIABILITY', 70),
   JSON_ARRAY('系统设计表达不够完整'),
   '技术基础可靠，系统设计表达需要更好地说明容量估算与取舍。',
   JSON_ARRAY('线程池和消息幂等理解扎实', '能结合项目结果说明可靠性改造'),
   JSON_ARRAY('容量估算表达不足'),
   '需要建立系统设计的结构化回答框架。', '项目量化结果较少。',
   JSON_ARRAY('练习容量评估和降级策略'), JSON_ARRAY(9700402, 9700405),
   JSON_OBJECT('source', 'FORMAL_TEST_SEED'),
   JSON_ARRAY(
     JSON_OBJECT('dimension', 'TECHNICAL_DEPTH', 'score', 3.6),
     JSON_OBJECT('dimension', 'SYSTEM_DESIGN', 'score', 3.1),
     JSON_OBJECT('dimension', 'RELIABILITY', 'score', 3.7),
     JSON_OBJECT('dimension', 'COMMUNICATION', 'score', 3.5),
     JSON_OBJECT('dimension', 'PROJECT_EXPRESSION', 'score', 3.4)
   ),
   JSON_OBJECT('nodes', JSON_ARRAY()), NULL, NULL, 'INTERVIEW_RUBRIC_V1',
   JSON_OBJECT('summary', '技术基础可靠，系统设计表达需要更好地说明容量估算与取舍。'),
   '2026-07-01 20:18:00', '先补足容量估算和降级策略表达。', NULL, 'seed-report-9702801',
   '2026-07-01 20:18:00', '2026-07-01 20:18:00', 0),
  (9702802, 9702702, @seed_user_id, 'GENERATED', 78,
   JSON_OBJECT('PROJECT', 80, 'SYSTEM_DESIGN', 76),
   JSON_ARRAY('容量估算缺少清晰口径'),
   '项目深挖表现明显提升，稳定性治理的方案和结果具备可信证据。',
   JSON_ARRAY('能阐明可靠事件、幂等消费和缓存治理', '量化结果清晰'),
   JSON_ARRAY('系统设计中的峰值估算仍需更严谨'),
   '建议将容量、限流和降级串成完整决策链。', '需准备不同方案的成本比较。',
   JSON_ARRAY('进行系统设计专项复练'), JSON_ARRAY(9700405, 9700406),
   JSON_OBJECT('source', 'FORMAL_TEST_SEED'),
   JSON_ARRAY(
     JSON_OBJECT('dimension', 'TECHNICAL_DEPTH', 'score', 4.0),
     JSON_OBJECT('dimension', 'SYSTEM_DESIGN', 'score', 3.7),
     JSON_OBJECT('dimension', 'RELIABILITY', 'score', 4.1),
     JSON_OBJECT('dimension', 'COMMUNICATION', 'score', 3.8),
     JSON_OBJECT('dimension', 'PROJECT_EXPRESSION', 'score', 4.1)
   ),
   JSON_OBJECT('nodes', JSON_ARRAY()), NULL, NULL, 'INTERVIEW_RUBRIC_V1',
   JSON_OBJECT('summary', '项目深挖表现明显提升，稳定性治理的方案和结果具备可信证据。'),
   '2026-07-08 20:30:00', '围绕容量估算、限流和降级进行一轮强复练。', NULL, 'seed-report-9702802',
   '2026-07-08 20:30:00', '2026-07-08 20:30:00', 0),
  (9702803, 9702703, @seed_user_id, 'GENERATED', 86,
   JSON_OBJECT('SYSTEM_DESIGN', 87, 'RELIABILITY', 85),
   JSON_ARRAY('需继续积累跨区域故障演练案例'),
   '专项复练后，系统设计与可靠性表达已形成清晰的容量、降级和补偿闭环。',
   JSON_ARRAY('容量估算与限流策略清晰', '能说明缓存、消息与数据库的一致性边界'),
   JSON_ARRAY('跨区域容灾案例较少'),
   '后续应准备容灾演练与成本权衡案例。', '暂无高风险项目表述。',
   JSON_ARRAY('保留每周一次系统设计复练'), JSON_ARRAY(9700405, 9700406, 9700408),
   JSON_OBJECT('source', 'FORMAL_TEST_SEED'),
   JSON_ARRAY(
     JSON_OBJECT('dimension', 'TECHNICAL_DEPTH', 'score', 4.3),
     JSON_OBJECT('dimension', 'SYSTEM_DESIGN', 'score', 4.3),
     JSON_OBJECT('dimension', 'RELIABILITY', 'score', 4.4),
     JSON_OBJECT('dimension', 'COMMUNICATION', 'score', 4.2),
     JSON_OBJECT('dimension', 'PROJECT_EXPRESSION', 'score', 4.3)
   ),
   JSON_OBJECT('nodes', JSON_ARRAY()), NULL, NULL, 'INTERVIEW_RUBRIC_V1',
  JSON_OBJECT('summary', '专项复练后，系统设计与可靠性表达已形成清晰的容量、降级和补偿闭环。'),
   '2026-07-11 20:30:00', '保持每周一次系统设计复练，并补充容灾案例。', NULL, 'seed-report-9702803',
   '2026-07-11 20:30:00', '2026-07-11 20:30:00', 0);

-- The history endpoint owns rows through interview_session.user_id; reports
-- enrich those rows but are not a standalone history source.
UPDATE interview_session AS session
JOIN interview_report AS report ON report.session_id = session.id
SET session.user_id = @seed_user_id,
    session.status = 'COMPLETED',
    session.report_status = 'GENERATED',
    session.deleted = 0,
    session.updated_at = @seed_now,
    report.user_id = @seed_user_id,
    report.status = 'GENERATED',
    report.deleted = 0,
    report.updated_at = @seed_now
WHERE session.id IN (9702701, 9702702, 9702703)
  AND report.id IN (9702801, 9702802, 9702803);

-- ============================================================================
-- CRM applications, experiments, calendar imports, and Agent work
-- ============================================================================

INSERT INTO job_application (
  id, user_id, target_job_id, resume_version_id, match_report_id, company_name,
  job_title, source, status, applied_at, next_follow_up_at, note,
  import_fingerprint, created_at, updated_at, deleted
) VALUES
  (9703001, @seed_user_id, 9701301, 9701203, 9701402, '澄观智研有限公司', '高级 Java 后端工程师', '拉勾网', 'PREPARING', NULL, '2026-07-17 10:00:00', '等待系统设计复练完成后投递。', NULL, @seed_now, @seed_now, 0),
  (9703002, @seed_user_id, 9701301, 9701203, 9701402, '启航数据科技有限公司', 'Java 后端工程师', 'BOSS 直聘', 'APPLIED', '2026-07-05 09:30:00', '2026-07-17 14:00:00', '已完成简历定制，等待 HR 反馈。', SHA2('seed-app-9703002', 256), @seed_now, @seed_now, 0),
  (9703003, @seed_user_id, 9701302, 9701203, 9701402, '星穹企业服务有限公司', 'Java 平台研发工程师', '内推', 'INTERVIEWING', '2026-07-02 11:00:00', '2026-07-18 15:00:00', '已约技术一面，准备权限与审计场景。', SHA2('seed-app-9703003', 256), @seed_now, @seed_now, 0),
  (9703004, @seed_user_id, NULL, 9701203, 9701402, '云瀚教育科技有限公司', '高级后端工程师', '猎聘', 'OFFER', '2026-06-25 10:00:00', NULL, '已收到口头意向，等待书面 Offer。', SHA2('seed-app-9703004', 256), @seed_now, @seed_now, 0),
  (9703005, @seed_user_id, NULL, 9701202, 9701401, '远望信息技术有限公司', 'Java 后端工程师', '官网', 'REJECTED', '2026-06-22 14:00:00', NULL, '复盘：系统设计轮未能清晰说明限流与降级边界。', SHA2('seed-app-9703005', 256), @seed_now, @seed_now, 0),
  (9703006, @seed_user_id, NULL, 9701202, 9701401, '简途企业软件有限公司', '后端开发工程师', 'BOSS 直聘', 'CLOSED', '2026-06-18 09:00:00', NULL, '岗位预算调整，流程关闭。', SHA2('seed-app-9703006', 256), @seed_now, @seed_now, 0);

INSERT INTO job_application_event (
  id, user_id, application_id, event_type, event_time, summary, review_json,
  created_at, updated_at, deleted
) VALUES
  (9703101, @seed_user_id, 9703002, 'APPLIED', '2026-07-05 09:30:00', '通过 BOSS 直聘投递岗位定制简历。', NULL, @seed_now, @seed_now, 0),
  (9703102, @seed_user_id, 9703002, 'FOLLOW_UP_PLANNED', '2026-07-15 18:00:00', '已设置 7 月 17 日跟进提醒。', NULL, @seed_now, @seed_now, 0),
  (9703103, @seed_user_id, 9703003, 'APPLIED', '2026-07-02 11:00:00', '内推提交完成。', NULL, @seed_now, @seed_now, 0),
  (9703104, @seed_user_id, 9703003, 'INTERVIEW_INVITED', '2026-07-14 16:00:00', '已收到技术一面邀请。', NULL, @seed_now, @seed_now, 0),
  (9703105, @seed_user_id, 9703004, 'OFFER_RECEIVED', '2026-07-12 11:30:00', '收到口头 Offer，等待书面确认。', NULL, @seed_now, @seed_now, 0),
  (9703106, @seed_user_id, 9703005, 'REJECTED', '2026-07-07 10:00:00', '流程结束，已记录系统设计复盘。', JSON_OBJECT('lesson', '需要更清晰地解释降级边界。'), @seed_now, @seed_now, 0);

INSERT INTO job_search_experiment (
  id, user_id, title, goal, target_direction, start_date, end_date, status,
  sample_count, confidence_level, sample_warning, summary, next_strategy,
  demo_flag, deleted, created_at, updated_at
) VALUES (
  9703201, @seed_user_id, '岗位定制简历投递实验', '验证岗位定制摘要是否提升技术面试邀约率。',
  '高级 Java 后端工程师', '2026-06-20', '2026-07-15', 'REVIEWED', 6, 'LOW',
  '当前样本量仅用于观察趋势，不构成因果结论。',
  '定制版本在已记录样本中获得更多积极反馈，但样本不足且渠道差异仍存在。',
  '继续扩展共同分层样本，并固定渠道和岗位族后复盘。', 0, 0, @seed_now, @seed_now
);

INSERT INTO job_experiment_hypothesis (
  id, user_id, legacy_experiment_id, name, statement, primary_metric, status,
  attribution_window_days, min_sample_per_variant, allocation_salt,
  created_at, updated_at, deleted
) VALUES (
  9703301, @seed_user_id, 9703201, '定制摘要对技术面试邀约的影响',
  '在同一岗位族、渠道和时间窗口内，采用岗位定制摘要的简历将提高进入技术面试的比例。',
  'INTERVIEW', 'ANALYZED', 14, 3, 'formal-seed-20260716', @seed_now, @seed_now, 0
);

INSERT INTO job_experiment_variant (
  id, user_id, hypothesis_id, variant_code, name, description, treatment_json,
  allocation_weight, control_flag, created_at, updated_at, deleted
) VALUES
  (9703311, @seed_user_id, 9703301, 'CONTROL', '通用摘要', '使用通用版本摘要。',
   JSON_OBJECT('resumeVersionId', 9701202, 'summaryType', 'GENERAL'), 1, 1, @seed_now, @seed_now, 0),
  (9703312, @seed_user_id, 9703301, 'TREATMENT', '岗位定制摘要', '使用当前投递版本摘要。',
   JSON_OBJECT('resumeVersionId', 9701203, 'summaryType', 'TARGETED'), 1, 0, @seed_now, @seed_now, 0);

INSERT INTO job_experiment_assignment (
  id, user_id, hypothesis_id, variant_id, application_id, assignment_key,
  assignment_method, assigned_at, job_family, channel, time_bucket,
  created_at, updated_at, deleted
) VALUES
  (9703321, @seed_user_id, 9703301, 9703311, 9703005, 'seed-assignment-9703005', 'STABLE_HASH', '2026-06-22 14:00:00', 'JAVA_BACKEND', '官网', '2026-06-22', @seed_now, @seed_now, 0),
  (9703322, @seed_user_id, 9703301, 9703311, 9703006, 'seed-assignment-9703006', 'STABLE_HASH', '2026-06-18 09:00:00', 'JAVA_BACKEND', 'BOSS 直聘', '2026-06-18', @seed_now, @seed_now, 0),
  (9703323, @seed_user_id, 9703301, 9703311, 9703002, 'seed-assignment-9703002', 'STABLE_HASH', '2026-07-05 09:30:00', 'JAVA_BACKEND', 'BOSS 直聘', '2026-07-05', @seed_now, @seed_now, 0),
  (9703324, @seed_user_id, 9703301, 9703312, 9703003, 'seed-assignment-9703003', 'STABLE_HASH', '2026-07-02 11:00:00', 'JAVA_BACKEND', '内推', '2026-07-02', @seed_now, @seed_now, 0),
  (9703325, @seed_user_id, 9703301, 9703312, 9703004, 'seed-assignment-9703004', 'STABLE_HASH', '2026-06-25 10:00:00', 'JAVA_BACKEND', '猎聘', '2026-06-25', @seed_now, @seed_now, 0),
  (9703326, @seed_user_id, 9703301, 9703312, 9703001, 'seed-assignment-9703001', 'STABLE_HASH', '2026-07-15 20:30:00', 'JAVA_BACKEND', '拉勾网', '2026-07-15', @seed_now, @seed_now, 0);

INSERT INTO job_experiment_cohort (
  id, user_id, hypothesis_id, name, job_family, channel, window_start,
  window_end, outcome_type, min_sample_per_variant, created_at, updated_at, deleted
) VALUES (
  9703401, @seed_user_id, 9703301, '2026 年 6 月 Java 后端投递共同分层',
  'JAVA_BACKEND', NULL, '2026-06-18 00:00:00', '2026-07-15 23:59:59',
  'INTERVIEW', 3, @seed_now, @seed_now, 0
);

INSERT INTO job_experiment_attribution (
  id, user_id, hypothesis_id, cohort_id, as_of, method, comparable_flag,
  sample_count, common_strata_count, incomparable_reasons_json, limitations_json,
  result_json, created_at, updated_at, deleted
) VALUES (
  9703501, @seed_user_id, 9703301, 9703401, '2026-07-15 23:59:59',
  'STRATIFIED_RATE_COMPARISON', 0, 6, 0,
  JSON_ARRAY('样本量不足', '渠道与时间分层尚未形成足够共同样本'),
  JSON_ARRAY('仅反映测试数据中的观察趋势', '不能据此作出因果结论'),
  JSON_OBJECT('control', JSON_OBJECT('sampleCount', 3, 'interviewCount', 0),
              'treatment', JSON_OBJECT('sampleCount', 3, 'interviewCount', 1),
              'conclusion', '样本不足，仅作观察，不形成归因结论。'),
  @seed_now, @seed_now, 0
);

INSERT INTO career_import_batch (
  id, user_id, format, filename, timezone, duplicate_policy, status,
  total_count, success_count, error_count, duplicate_count, created_at, updated_at, deleted
) VALUES
  (9703601, @seed_user_id, 'CSV', '正式测试投递记录.csv', 'Asia/Shanghai', 'SKIP', 'COMPLETED',
   3, 2, 0, 1, '2026-07-15 09:00:00', '2026-07-15 09:02:00', 0),
  (9703602, @seed_user_id, 'ICS', '正式测试面试提醒.ics', 'Asia/Shanghai', 'CREATE', 'COMPLETED',
   2, 2, 0, 0, '2026-07-15 10:00:00', '2026-07-15 10:02:00', 0);

INSERT INTO career_calendar_event (
  id, user_id, application_id, title, event_type, starts_at_utc, ends_at_utc,
  timezone, all_day_flag, location, description, status, source_type, source_ref,
  external_uid, import_batch_id, created_at, updated_at, deleted
) VALUES
  (9703701, @seed_user_id, 9703002, '跟进启航数据科技招聘进度', 'FOLLOW_UP',
   '2026-07-17 06:00:00', '2026-07-17 06:30:00', 'Asia/Shanghai', 0, '线上',
   '确认 HR 是否完成初筛，并记录反馈。', 'CONFIRMED', 'CSV_IMPORT', 'row-2',
   'seed-csv-follow-up-9703701', 9703601, @seed_now, @seed_now, 0),
  (9703702, @seed_user_id, 9703003, '星穹企业服务技术一面', 'INTERVIEW',
   '2026-07-18 07:00:00', '2026-07-18 08:00:00', 'Asia/Shanghai', 0, '腾讯会议',
   '准备权限模型、审计链路和缓存一致性案例。', 'CONFIRMED', 'ICS_IMPORT', 'VEVENT-1',
   'seed-ics-interview-9703702', 9703602, @seed_now, @seed_now, 0),
  (9703703, @seed_user_id, 9703004, '确认书面 Offer 进度', 'FOLLOW_UP',
   '2026-07-20 02:00:00', '2026-07-20 02:30:00', 'Asia/Shanghai', 0, '电话',
   '确认岗位级别、入职时间和书面 Offer。', 'CONFIRMED', 'MANUAL', NULL,
   NULL, NULL, @seed_now, @seed_now, 0),
  (9703704, @seed_user_id, NULL, '本周系统设计复练', 'PRACTICE',
   '2026-07-16 11:00:00', '2026-07-16 12:00:00', 'Asia/Shanghai', 0, '线上',
   '围绕容量估算、限流降级和消息补偿完成 45 分钟复练。', 'CONFIRMED', 'MANUAL', NULL,
   NULL, NULL, @seed_now, @seed_now, 0);

INSERT INTO career_import_row (
  id, user_id, batch_id, `row_number`, disposition, raw_data_json, error_code,
  error_message, duplicate_candidates_json, application_id, calendar_event_id,
  created_at, updated_at, deleted
) VALUES
  (9703801, @seed_user_id, 9703601, 1, 'CREATED',
   JSON_OBJECT('companyName', '启航数据科技有限公司', 'jobTitle', 'Java 后端工程师', 'status', 'APPLIED'),
   NULL, NULL, NULL, 9703002, 9703701, @seed_now, @seed_now, 0),
  (9703802, @seed_user_id, 9703601, 2, 'SKIPPED_DUPLICATE',
   JSON_OBJECT('companyName', '启航数据科技有限公司', 'jobTitle', 'Java 后端工程师', 'status', 'APPLIED'),
   'CONCURRENT_DUPLICATE', '命中同一业务指纹的既有投递记录。',
   JSON_ARRAY(JSON_OBJECT('applicationId', 9703002, 'reason', 'same fingerprint')), 9703002, NULL, @seed_now, @seed_now, 0),
  (9703803, @seed_user_id, 9703602, 1, 'CREATED',
   JSON_OBJECT('title', '星穹企业服务技术一面', 'externalUid', 'seed-ics-interview-9703702'),
   NULL, NULL, NULL, 9703003, 9703702, @seed_now, @seed_now, 0),
  (9703804, @seed_user_id, 9703602, 2, 'CREATED',
   JSON_OBJECT('title', '本周系统设计复练'),
   NULL, NULL, NULL, NULL, 9703704, @seed_now, @seed_now, 0);

INSERT INTO agent_run (
  id, user_id, agent_type, target_job_id, plan_date, trigger_type, status,
  execution_token, input_snapshot_json, output_json, raw_output_text, prompt_type,
  prompt_version_id, model_name, trace_id, ai_call_log_id, result_source,
  token_input, token_output, duration_ms, error_code, error_message,
  started_at, finished_at, deleted, created_at, updated_at
) VALUES
  (9703901, @seed_user_id, 'JOB_COACH', 9701301, '2026-07-16', 'SCHEDULED', 'SUCCESS',
   'seed-agent-run-9703901', JSON_OBJECT('readiness', 69),
   JSON_OBJECT('summary', '生成本周系统设计复练与投递准备计划。'), NULL, 'JOB_COACH_WEEK_PLAN',
   NULL, 'formal-seed', 'seed-agent-9703901', NULL, 'RULE', 0, 0, 184, NULL, NULL,
   '2026-07-16 08:00:00', '2026-07-16 08:00:01', 0, @seed_now, @seed_now),
  (9703902, @seed_user_id, 'JOB_COACH', 9701301, '2026-07-15', 'MANUAL', 'SUCCESS',
   'seed-agent-run-9703902', JSON_OBJECT('readiness', 68),
   JSON_OBJECT('summary', '生成面试复盘任务。'), NULL, 'JOB_COACH_REVIEW',
   NULL, 'formal-seed', 'seed-agent-9703902', NULL, 'RULE', 0, 0, 152, NULL, NULL,
   '2026-07-15 20:45:00', '2026-07-15 20:45:01', 0, @seed_now, @seed_now);

INSERT INTO agent_task (
  id, user_id, agent_run_id, target_job_id, candidate_id, task_type, title,
  description, reason, priority, estimated_minutes, related_skill_code,
  related_skill_name, related_biz_type, related_biz_id, action_url, status,
  skip_reason, defer_reason, due_date, started_at, completed_at, deferred_at,
  skipped_at, sort_order, deleted, created_at, updated_at
) VALUES
  (9704001, @seed_user_id, 9703901, 9701301, 'system-design-1', 'PRACTICE', '完成系统设计专项复练',
   '围绕容量估算、限流降级和缓存一致性完成 45 分钟模拟练习。', '最近报告显示系统设计表达仍是主要可提升项。',
   'HIGH', 45, 'SYSTEM_DESIGN', '系统设计表达', 'INTERVIEW_REPORT', 9702802, '/interviews', 'TODO',
   NULL, NULL, '2026-07-16', NULL, NULL, NULL, NULL, 10, 0, @seed_now, @seed_now),
  (9704002, @seed_user_id, 9703901, 9701301, 'evidence-1', 'EVIDENCE', '补充跨区域故障演练复盘',
   '为稳定性治理项目补充容灾演练场景、影响评估和恢复验证。', '项目证据完整度高，但容灾经验仍是面试追问风险。',
   'MEDIUM', 30, 'OBSERVABILITY', '可观测性', 'PROJECT_EVIDENCE', 9701701, '/projects/evidence', 'DOING',
   NULL, NULL, '2026-07-16', '2026-07-16 09:00:00', NULL, NULL, NULL, 20, 0, @seed_now, @seed_now),
  (9704003, @seed_user_id, 9703901, 9701301, 'delivery-1', 'DELIVERY', '复核澄观智研投递包',
   '检查当前版本简历、岗位匹配和面试准备清单后决定是否投递。', '最新 readiness 为 NEAR_READY。',
   'HIGH', 20, 'DELIVERY', '投递准备', 'JOB_APPLICATION_PACKAGE', 9702601, '/resume/delivery', 'TODO',
   NULL, NULL, '2026-07-17', NULL, NULL, NULL, NULL, 30, 0, @seed_now, @seed_now),
  (9704004, @seed_user_id, 9703902, 9701301, 'mysql-1', 'STUDY', '复盘联合索引与范围查询',
   '用 EXPLAIN 完成三个组合查询的索引覆盖练习。', '面试报告建议加强索引取舍表达。',
   'MEDIUM', 35, 'MYSQL', 'MySQL 性能优化', 'QUESTION', 9700404, '/practice', 'DONE',
   NULL, NULL, '2026-07-15', '2026-07-15 18:00:00', '2026-07-15 18:35:00', NULL, NULL, 40, 0, @seed_now, @seed_now),
  (9704005, @seed_user_id, 9703902, 9701301, 'follow-up-1', 'CRM', '跟进启航数据科技招聘进度',
   '在约定时间联系 HR 并记录反馈。', '投递后已超过一周，需保持节奏。',
   'LOW', 10, 'CRM', '投递跟进', 'JOB_APPLICATION', 9703002, '/career', 'DEFERRED',
   NULL, '等待 HR 约定的反馈窗口。', '2026-07-17', NULL, NULL, '2026-07-15 17:00:00', NULL, 50, 0, @seed_now, @seed_now),
  (9704006, @seed_user_id, 9703902, 9701301, 'legacy-1', 'PRACTICE', '重复进行基础题刷题',
   '旧任务样本，用于验证跳过状态展示。', '当前优先级低于系统设计复练。',
   'LOW', 20, 'JAVA', 'Java 基础', 'QUESTION', 9700401, '/practice', 'SKIPPED',
   '本周优先投入系统设计和投递准备。', NULL, '2026-07-14', NULL, NULL, NULL, '2026-07-15 17:10:00', 60, 0, @seed_now, @seed_now);

INSERT INTO agent_week_plan (
  id, user_id, target_job_id, agent_run_id, target_scope_key, week_start_date,
  week_end_date, plan_status, summary, focus_json, trace_id, result_source,
  fallback, fallback_reason, snapshot_version, generated_at, refreshed_at,
  created_at, updated_at, deleted
) VALUES (
  9704101, @seed_user_id, 9701301, 9703901, 'ALL',
  '2026-07-13', '2026-07-19', 'ACTIVE',
  '本周优先完成系统设计复练、投递包复核和面试跟进，保持证据与行动闭环。',
  JSON_ARRAY('系统设计表达', '投递准备', '面试跟进'), 'seed-week-plan-9704101', 'RULE',
  0, NULL, 1, '2026-07-16 08:00:01', '2026-07-16 08:00:01', @seed_now, @seed_now, 0
);

INSERT INTO agent_week_plan_item (
  id, week_plan_id, user_id, layer, action_type, title, description, reason,
  related_biz_type, related_biz_id, related_biz_title, agent_task_id, priority,
  confidence, trust_status, fallback, fallback_reason, trace_id, snapshot_version,
  confidence_level, sample_insufficient, sample_warning, item_status, planned_date,
  due_date, action_url, evidence_json, sort_order, created_at, updated_at, deleted
) VALUES
  (9704201, 9704101, @seed_user_id, 'TODAY', 'AGENT_TASK', '完成系统设计专项复练',
   '完成 45 分钟容量估算、限流降级和缓存一致性练习。', '最近的项目深挖复盘显示，容量估算与方案取舍仍需形成完整表达。',
   'INTERVIEW_REPORT', 9702802, '面试复盘报告：项目深挖二面', 9704001, 'HIGH', 0.92, 'TRUSTED',
   0, NULL, 'seed-week-plan-9704101', 1, 'HIGH', 0, NULL, 'TODO', '2026-07-16', '2026-07-16',
   '/interviews', JSON_OBJECT('summary', '项目深挖复盘指出容量估算与方案取舍需要专项练习。'), 10, @seed_now, @seed_now, 0),
  (9704202, 9704101, @seed_user_id, 'TODAY', 'AGENT_TASK', '补充跨区域故障演练复盘',
   '补齐稳定性治理项目的容灾与恢复验证证据。', '当前项目成果已具备量化结果，仍需补充跨区域故障演练、影响评估和恢复验证。',
   'PROJECT_EVIDENCE', 9701701, '项目证据：学习平台稳定性治理', 9704002, 'MEDIUM', 0.82, 'PARTIAL',
   0, NULL, 'seed-week-plan-9704101', 1, 'MEDIUM', 0, NULL, 'IN_PROGRESS', '2026-07-16', '2026-07-16',
   '/projects/evidence', JSON_OBJECT('summary', '学习平台稳定性治理项目还需补齐容灾演练与恢复验证证据。'), 20, @seed_now, @seed_now, 0),
  (9704203, 9704101, @seed_user_id, 'WEEK', 'AGENT_TASK', '复核澄观智研投递包',
   '完成匹配、项目证据和面试准备清单的最终复核。', '当前准备度已接近可投递状态，完成复核后即可判断是否推进投递。',
   'JOB_APPLICATION_PACKAGE', 9702601, '投递准备：澄观智研高级 Java 后端岗位', 9704003, 'HIGH', 0.88, 'TRUSTED',
   0, NULL, 'seed-week-plan-9704101', 1, 'HIGH', 0, NULL, 'TODO', '2026-07-17', '2026-07-17',
   '/resume/delivery', JSON_OBJECT('summary', '岗位匹配、项目证据和面试准备清单已具备复核条件。'), 30, @seed_now, @seed_now, 0),
  (9704204, 9704101, @seed_user_id, 'WEEK', 'AGENT_TASK', '复盘联合索引与范围查询',
   '已完成 EXPLAIN 练习并记录复盘。', '已完成联合索引与范围查询训练，可作为本周能力巩固记录。',
   'QUESTION_PRACTICE', 9700404, '题库练习：MySQL 联合索引与范围查询', 9704004, 'MEDIUM', 0.90, 'TRUSTED',
   0, NULL, 'seed-week-plan-9704101', 1, 'HIGH', 0, NULL, 'DONE', '2026-07-15', '2026-07-15',
   '/practice', JSON_OBJECT('summary', '联合索引与范围查询训练已完成，并已记录复盘结论。'), 40, @seed_now, @seed_now, 0);

INSERT INTO agent_review (
  id, user_id, target_job_id, review_date, summary, done_count, skipped_count,
  todo_count, completion_rate, readiness_score, next_actions_json, review_json,
  agent_run_id, ai_call_log_id, created_at, updated_at, deleted
) VALUES (
  9704301, @seed_user_id, 9701301, '2026-07-15',
  '已完成 MySQL 索引复盘和项目深挖复盘；下一步集中完成系统设计专项复练并复核投递包。',
  2, 1, 3, 50.00, 69,
  JSON_ARRAY('完成系统设计复练', '补充容灾复盘', '复核投递包'),
  JSON_OBJECT('highlights', JSON_ARRAY('面试报告从 71 分提升至 86 分'), 'fallback', false),
  9703902, NULL, @seed_now, @seed_now, 0
);

INSERT INTO notification (
  id, user_id, type, title, content, biz_type, biz_id, read_status, read_at,
  resolved_status, resolved_at, resolved_reason, send_status, send_error,
  sent_at, created_at, updated_at, deleted
) VALUES
  (9704401, @seed_user_id, 'READINESS', '岗位就绪度已更新',
   '当前高级 Java 后端工程师岗位就绪度为 69 分，建议完成系统设计复练后推进投递。',
   'JOB_READINESS_SNAPSHOT', '9702303', 0, NULL, 0, NULL, NULL, 'SUCCESS', NULL,
   '2026-07-15 20:05:00', '2026-07-15 20:05:00', '2026-07-15 20:05:00', 0),
  (9704402, @seed_user_id, 'INTERVIEW', '系统设计复练待完成',
   '项目深挖报告建议完成容量估算、限流降级和消息补偿专项复练。',
   'INTERVIEW_REPORT', '9702802', 0, NULL, 0, NULL, NULL, 'SUCCESS', NULL,
   '2026-07-16 08:05:00', '2026-07-16 08:05:00', '2026-07-16 08:05:00', 0),
  (9704403, @seed_user_id, 'CAREER', '技术一面提醒',
   '星穹企业服务技术一面安排在 2026 年 7 月 18 日下午，请提前准备权限和审计场景。',
   'CAREER_CALENDAR_EVENT', '9703702', 0, NULL, 0, NULL, NULL, 'SUCCESS', NULL,
   '2026-07-16 09:00:00', '2026-07-16 09:00:00', '2026-07-16 09:00:00', 0);

COMMIT;

DROP PROCEDURE IF EXISTS clear_non_baseline_tables;

-- Post-reset integrity summary. This output is safe to record in a test report.
SELECT CONCAT('PRESERVED_USERS=', COUNT(*))
FROM sys_user
WHERE deleted = 0;

SELECT CONCAT('SEEDED_USER001_RESUMES=', COUNT(*))
FROM resume
WHERE user_id = @seed_user_id AND deleted = 0;

SELECT CONCAT('SEEDED_USER001_TARGET_JOBS=', COUNT(*))
FROM target_job
WHERE user_id = @seed_user_id AND deleted = 0;

SELECT CONCAT('SEEDED_USER001_INTERVIEW_REPORTS=', COUNT(*))
FROM interview_report
WHERE user_id = @seed_user_id AND deleted = 0;

SELECT CONCAT('SEEDED_USER001_PENDING_SUGGESTIONS=', COUNT(*))
FROM resume_suggestion
WHERE user_id = @seed_user_id AND status = 'PENDING' AND deleted = 0;

SELECT CONCAT('SEEDED_USER001_APPLICATIONS=', COUNT(*))
FROM job_application
WHERE user_id = @seed_user_id AND deleted = 0;
