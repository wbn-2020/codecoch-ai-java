-- V4_009: clean low-quality demo business data and seed realistic Chinese interview dataset.
-- Scope:
--   1) Soft-delete business/demo data only.
--   2) Preserve accounts, roles, permissions, system config, AI/third-party config, prompts and logs.
--   3) Re-seed a coherent Chinese dataset across questions, reviews, duplicates, resumes, jobs,
--      match reports, skill gaps, study plans, interviews and notifications.
--
-- Safety guard:
--   This script intentionally resets broad business/demo data. It must never run
--   on production schemas. It is allowed only when the current schema is an
--   explicit local/dev/demo/test schema, or when the operator has set the
--   session variable @codecoachai_allow_v4_009_demo_seed = '1' after reviewing
--   the affected data and backup/rollback plan.

SET NAMES utf8mb4;

DELIMITER //
DROP PROCEDURE IF EXISTS assert_v4_009_demo_seed_allowed//
CREATE PROCEDURE assert_v4_009_demo_seed_allowed()
BEGIN
  DECLARE schema_name VARCHAR(128) DEFAULT LOWER(DATABASE());
  DECLARE explicit_allow VARCHAR(16) DEFAULT COALESCE(@codecoachai_allow_v4_009_demo_seed, '0');

  IF explicit_allow <> '1'
     AND schema_name NOT IN ('codecoachai_dev', 'codecoachai_demo', 'codecoachai_test', 'codecoachai_local')
     AND schema_name NOT REGEXP '(^|_)(dev|demo|test|local)($|_)' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V4_009 resets demo business data. Refuse to run outside local/dev/demo/test schema unless @codecoachai_allow_v4_009_demo_seed=1 is set for this session.';
  END IF;
END//
DELIMITER ;

CALL assert_v4_009_demo_seed_allowed();
DROP PROCEDURE IF EXISTS assert_v4_009_demo_seed_allowed;

-- ============================================================
-- 1. Soft-delete old business demo data
-- ============================================================

UPDATE question_duplicate_review SET deleted = 1 WHERE deleted = 0;
UPDATE question_review SET deleted = 1 WHERE deleted = 0;
UPDATE question_relation SET deleted = 1 WHERE deleted = 0;
UPDATE question_tag_relation SET deleted = 1 WHERE deleted = 0;
UPDATE user_question_record SET deleted = 1 WHERE deleted = 0;
UPDATE practice_record SET deleted = 1 WHERE deleted = 0;
UPDATE question_recommendation_item SET deleted = 1 WHERE deleted = 0;
UPDATE question_recommendation_batch SET deleted = 1 WHERE deleted = 0;
UPDATE question_embedding SET deleted = 1 WHERE deleted = 0;
UPDATE question SET deleted = 1 WHERE deleted = 0;
UPDATE question_group SET deleted = 1 WHERE deleted = 0;
UPDATE question_tag SET deleted = 1 WHERE deleted = 0;
UPDATE question_category SET deleted = 1 WHERE deleted = 0;

-- question_category.category_name and question_tag.tag_name are unique.
-- Rename archived rows first so the new clean Chinese taxonomy can reuse natural names safely.
UPDATE question_category
SET category_name = CONCAT('__archived_category_', id),
    updated_at = NOW()
WHERE deleted = 1
  AND category_name NOT LIKE '__archived_category_%';

UPDATE question_tag
SET tag_name = CONCAT('__archived_tag_', id),
    updated_at = NOW()
WHERE deleted = 1
  AND tag_name NOT LIKE '__archived_tag_%';

UPDATE study_plan_skill_relation SET deleted = 1 WHERE deleted = 0;
UPDATE study_checkin SET deleted = 1 WHERE deleted = 0;
UPDATE study_task SET deleted = 1 WHERE deleted = 0;
UPDATE study_plan SET deleted = 1 WHERE deleted = 0;

UPDATE interview_message SET deleted = 1 WHERE deleted = 0;
UPDATE interview_report SET deleted = 1 WHERE deleted = 0;
UPDATE interview_stage SET deleted = 1 WHERE deleted = 0;
UPDATE interview_session SET deleted = 1 WHERE deleted = 0;

UPDATE resume_suggestion_adoption SET deleted = 1 WHERE deleted = 0;
UPDATE job_application_event SET deleted = 1 WHERE deleted = 0;
UPDATE job_application SET deleted = 1 WHERE deleted = 0;
UPDATE resume_version SET deleted = 1 WHERE deleted = 0;
UPDATE resume_job_match_detail SET deleted = 1 WHERE deleted = 0;
UPDATE resume_job_match_report SET deleted = 1 WHERE deleted = 0;
UPDATE skill_gap_item SET deleted = 1 WHERE deleted = 0;
UPDATE skill_profile SET deleted = 1 WHERE deleted = 0;
UPDATE job_description_analysis SET deleted = 1 WHERE deleted = 0;
UPDATE resume_optimize_record SET deleted = 1 WHERE deleted = 0;
UPDATE resume_analysis_record SET deleted = 1 WHERE deleted = 0;
UPDATE resume_project SET deleted = 1 WHERE deleted = 0;
UPDATE target_job SET deleted = 1 WHERE deleted = 0;
UPDATE resume SET deleted = 1 WHERE deleted = 0;

UPDATE agent_task SET deleted = 1 WHERE deleted = 0;
UPDATE agent_run SET deleted = 1 WHERE deleted = 0;
UPDATE agent_memory SET deleted = 1 WHERE deleted = 0;
UPDATE skill_growth_snapshot SET deleted = 1 WHERE deleted = 0;
UPDATE notification SET deleted = 1 WHERE deleted = 0;

-- ============================================================
-- 2. Question categories, groups and tags
-- ============================================================

INSERT INTO question_category (id, parent_id, category_name, sort, sort_order, status, deleted, created_at, updated_at) VALUES
(920001, NULL, 'Java基础', 10, 10, 1, 0, NOW(), NOW()),
(920002, NULL, '集合', 20, 20, 1, 0, NOW(), NOW()),
(920003, NULL, '并发与JUC', 30, 30, 1, 0, NOW(), NOW()),
(920004, NULL, 'JVM', 40, 40, 1, 0, NOW(), NOW()),
(920005, NULL, 'Spring Boot', 50, 50, 1, 0, NOW(), NOW()),
(920006, NULL, 'MySQL', 60, 60, 1, 0, NOW(), NOW()),
(920007, NULL, 'Redis', 70, 70, 1, 0, NOW(), NOW()),
(920008, NULL, '微服务', 80, 80, 1, 0, NOW(), NOW()),
(920009, NULL, '项目场景', 90, 90, 1, 0, NOW(), NOW()),
(920010, NULL, '简历与面试表达', 100, 100, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name), sort = VALUES(sort), sort_order = VALUES(sort_order),
  status = VALUES(status), deleted = 0, updated_at = NOW();

INSERT INTO question_tag (id, tag_name, status, deleted, created_at, updated_at) VALUES
(920001, 'HashMap', 1, 0, NOW(), NOW()),
(920002, '集合源码', 1, 0, NOW(), NOW()),
(920003, '线程池', 1, 0, NOW(), NOW()),
(920004, 'JUC', 1, 0, NOW(), NOW()),
(920005, 'GC调优', 1, 0, NOW(), NOW()),
(920006, 'Spring事务', 1, 0, NOW(), NOW()),
(920007, 'MySQL索引', 1, 0, NOW(), NOW()),
(920008, '慢SQL优化', 1, 0, NOW(), NOW()),
(920009, 'Redis缓存', 1, 0, NOW(), NOW()),
(920010, '分布式锁', 1, 0, NOW(), NOW()),
(920011, '网关鉴权', 1, 0, NOW(), NOW()),
(920012, '接口幂等', 1, 0, NOW(), NOW()),
(920013, '异步任务', 1, 0, NOW(), NOW()),
(920014, '项目亮点', 1, 0, NOW(), NOW()),
(920015, 'STAR表达', 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE tag_name = VALUES(tag_name), status = VALUES(status), deleted = 0, updated_at = NOW();

INSERT INTO question_group
(id, group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status, deleted, created_at, updated_at) VALUES
(920001, 'HashMap底层结构', 'HashMap 的 put/get 和扩容流程是什么？',
 'HashMap 通过扰动后的 hash 定位桶位，冲突时使用链表或红黑树保存节点。put 时要判断覆盖、追加、树化和扩容；扩容会按高低位拆分迁移，避免重新计算全部 hash。',
 'HashMap 冲突、树化与扩容', 'MEDIUM', '考察集合源码、复杂度和扩容迁移过程。', 920002, 1, 0, NOW(), NOW()),
(920002, '线程池参数设计', '线程池核心参数应该如何结合业务配置？',
 '先识别 CPU 密集或 IO 密集，再结合 QPS、平均耗时、峰值流量和可接受排队时间估算线程数与队列容量。拒绝策略要配合降级、告警和任务补偿，线程名要便于排障。',
 '线程池参数、队列与拒绝策略', 'MEDIUM', '考察并发基础和工程化治理。', 920003, 1, 0, NOW(), NOW()),
(920003, 'JVM GC排查', '线上频繁 Full GC 应该如何排查？',
 '从监控确认 GC 频率和停顿时间，结合 GC 日志、堆 dump、对象分配速率、老年代占用、元空间和最近发布变更定位原因，再通过参数调整、对象生命周期优化或代码修复解决。',
 'Full GC 原因定位', 'HARD', '考察 JVM 内存、工具链和线上排障闭环。', 920004, 1, 0, NOW(), NOW()),
(920004, 'Spring事务边界', 'Spring 事务失效有哪些常见原因？',
 '常见原因包括同类内部调用绕过代理、方法非 public、异常被捕获、受检异常未配置 rollbackFor、传播行为使用不当以及数据库引擎不支持事务。排查时要看代理对象、异常链和真实提交记录。',
 'AOP代理与事务传播', 'MEDIUM', '考察 Spring 事务原理和排查能力。', 920005, 1, 0, NOW(), NOW()),
(920005, 'MySQL索引与慢SQL', 'MySQL 慢查询应该如何分析和优化？',
 '先通过慢查询日志/APM 定位 SQL，再用 EXPLAIN 看 type、key、rows、Extra，结合业务查询频率设计联合索引、覆盖索引或改写 SQL，并用压测和线上指标验证。',
 'EXPLAIN、联合索引与回表', 'MEDIUM', '考察数据库优化闭环。', 920006, 1, 0, NOW(), NOW()),
(920006, 'Redis缓存一致性', 'Redis 缓存和数据库如何保证最终一致？',
 '常见做法是先更新数据库再删除缓存，配合 TTL、失败重试、延迟双删或 MQ 补偿。高一致场景可加版本号或读写串行化，但要说明性能成本和业务边界。',
 '缓存一致性与补偿机制', 'HARD', '考察缓存模式和工程权衡。', 920007, 1, 0, NOW(), NOW()),
(920007, '微服务网关鉴权', '网关鉴权和服务内鉴权如何分工？',
 '网关负责统一认证、Token 校验、基础权限、限流和黑白名单；服务内负责资源归属、细粒度权限和业务规则；内部接口还要有内网隔离或签名，防止绕过网关。',
 '认证授权边界', 'MEDIUM', '考察微服务安全边界。', 920008, 1, 0, NOW(), NOW()),
(920008, '接口幂等与状态机', '接口幂等如何设计？',
 '客户端或服务端生成幂等键，服务端用唯一索引、Redis 原子写或状态机保证同一业务请求只处理一次。异步场景要返回已有任务和结果，状态流转不能倒退。',
 '幂等键、唯一约束和状态流转', 'MEDIUM', '考察分布式业务一致性。', 920009, 1, 0, NOW(), NOW()),
(920009, '项目表达与简历亮点', '简历项目如何讲出技术亮点？',
 '按 STAR 结构说明背景、目标、个人职责、技术难点、方案取舍和结果指标。避免只堆技术栈，要能把性能、稳定性、成本或效率变化量化。',
 'STAR表达和项目复盘', 'EASY', '考察项目沟通和面试表达。', 920010, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE group_name = VALUES(group_name), canonical_title = VALUES(canonical_title),
  canonical_answer = VALUES(canonical_answer), main_knowledge_point = VALUES(main_knowledge_point),
  difficulty = VALUES(difficulty), description = VALUES(description), category_id = VALUES(category_id),
  status = VALUES(status), deleted = 0, updated_at = NOW();

-- ============================================================
-- 3. Approved question bank
-- ============================================================

INSERT INTO question
(id, title, normalized_title, content, reference_answer, analysis, category_id, group_id, difficulty, question_type,
 experience_level, is_high_frequency, is_recommended, status, audit_status, source_type, deleted, created_at, updated_at) VALUES
(920001, 'HashMap 在 Java 8 中为什么会引入红黑树？', 'hashmap java8 为什么引入红黑树',
 '请说明 HashMap 链表转红黑树的触发条件、查询复杂度变化，以及为什么不是一开始就使用红黑树。',
 'Java 8 在哈希冲突严重时会将链表树化，典型条件是桶内节点达到阈值且数组容量足够。这样能把极端冲突下的查询从 O(n) 降到 O(log n)。不是默认使用红黑树，是因为节点少时链表更省内存，维护成本也更低。',
 '重点看候选人是否能讲清冲突、树化阈值、扩容优先和复杂度权衡。', 920002, 920001, 'MEDIUM', 'SHORT_ANSWER', '1-3年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920002, 'ArrayList 和 LinkedList 在真实业务中如何选择？', 'arraylist linkedlist 业务选择',
 '请结合查询、插入、内存占用和实际业务访问模式说明二者差异。',
 'ArrayList 基于连续数组，随机访问快，尾部追加和遍历友好，但中间插入删除需要移动元素；LinkedList 基于双向链表，节点额外内存更多，随机访问慢。真实业务中大多数读多写少或分页列表优先 ArrayList，只有明确频繁在已定位节点附近插入删除时才考虑 LinkedList。',
 '不要只背“增删快/查询慢”，要结合定位成本和 CPU cache 友好性。', 920002, 920001, 'EASY', 'SHORT_ANSWER', '0-1年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920003, '线程池核心参数如何按业务压测结果配置？', '线程池核心参数 业务压测 配置',
 '假设有一个简历解析异步任务，平均耗时 800ms，峰值每秒 80 个请求，你会如何设计线程池参数？',
 '先判断任务是 IO 密集还是 CPU 密集。可用 QPS * RT 粗估并发量，80 * 0.8 约 64 个并发，再结合机器核数、下游限流和任务 SLA 设置核心线程数、最大线程数和有界队列。拒绝策略不能简单丢弃，应记录失败、提示稍后重试或进入补偿队列。',
 '回答要体现估算方法、下游保护、队列边界和监控告警。', 920003, 920002, 'MEDIUM', 'SCENARIO', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920004, 'CompletableFuture 使用时有哪些坑？', 'completablefuture 使用 坑',
 '请说明线程池隔离、异常处理、超时控制和上下文传递方面的注意点。',
 '默认使用 commonPool 容易和其他任务互相影响，生产建议传入业务线程池。每个异步阶段要处理异常，避免异常吞掉；要设置超时和降级；日志 traceId、用户上下文等需要显式传递。聚合多个任务时要考虑部分失败和取消策略。',
 '考察并发工具在工程中的可靠性，而不是只会写 thenApply。', 920003, 920002, 'HARD', 'SHORT_ANSWER', '3-5年', 0, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920005, '线上频繁 Full GC 怎么定位？', '线上 频繁 full gc 定位',
 '请按发现、定位、验证和修复几个步骤说明排查流程。',
 '先从监控确认 Full GC 次数、停顿时间和内存曲线；再看 GC 日志、堆 dump、线程栈和对象分配热点；结合发布记录判断是否有大对象、缓存无界、类加载或元空间问题。修复后通过压测和线上指标确认停顿下降。',
 '好的回答会形成闭环：指标发现、工具定位、代码/参数修复、验证效果。', 920004, 920003, 'HARD', 'SHORT_ANSWER', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920006, 'Spring 事务什么时候会失效？', 'spring 事务 失效 原因',
 '请列举事务失效的常见原因，并说明如何在代码里规避。',
 '同类内部调用会绕过代理，非 public 方法可能不被代理，异常被捕获或抛出受检异常但没有 rollbackFor 都会导致不回滚。还要注意传播行为、事务方法中开启新线程、数据库引擎和手动提交。规避方式是明确事务边界、保留异常、配置 rollbackFor 并用测试覆盖。',
 '核心是把 AOP 代理、异常和数据库提交边界讲清楚。', 920005, 920004, 'MEDIUM', 'SHORT_ANSWER', '1-3年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920007, 'MySQL 联合索引最左前缀怎么理解？', 'mysql 联合索引 最左前缀',
 '联合索引 (user_id, status, created_at) 在哪些查询里能被较好利用？排序和范围查询有什么影响？',
 '联合索引按从左到右的顺序组织，等值条件 user_id、user_id+status、user_id+status+created_at 通常能利用。只查 status 很难利用该索引。遇到范围查询后，后续列的有序性利用会受限。排序能否利用索引取决于 where 条件和 order by 顺序是否一致。',
 '回答要结合 EXPLAIN、扫描行数、Using filesort 和覆盖索引。', 920006, 920005, 'MEDIUM', 'SHORT_ANSWER', '1-3年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920008, '候选人列表慢查询如何优化？', '候选人列表 慢查询 优化',
 '后台候选人列表支持岗位、状态、城市、更新时间筛选并按更新时间倒序分页，你会如何优化？',
 '先统计高频查询组合和字段选择性，再设计如 (tenant_id, job_id, status, updated_at) 的联合索引。大偏移分页可改为游标分页或搜索 after_id/updated_at。只返回必要列，复杂统计异步化或预聚合，最后用 EXPLAIN 和压测验证。',
 '考察从业务查询模式出发做索引，而不是盲目给每列建索引。', 920006, 920005, 'HARD', 'SCENARIO', '3-5年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920009, 'Redis 缓存穿透、击穿、雪崩分别是什么？', 'redis 缓存穿透 击穿 雪崩',
 '请用业务例子区分三类问题，并给出治理方案。',
 '穿透是查询不存在数据导致请求打到数据库，可用参数校验、空值缓存和布隆过滤器；击穿是热点 key 过期瞬间大量请求回源，可用互斥锁、逻辑过期和热点预热；雪崩是大量 key 同时过期或 Redis 故障，可用过期时间随机化、多级缓存、限流降级和高可用。',
 '关键是能区分触发点，而不是混在一起回答。', 920007, 920006, 'MEDIUM', 'SHORT_ANSWER', '1-3年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920010, 'Redis 分布式锁如何避免误删？', 'redis 分布式锁 避免误删',
 '请说明 value 唯一标识、过期时间、Lua 释放和续期分别解决什么问题。',
 '加锁时使用唯一 value 标识持锁方并设置过期时间，避免服务异常导致死锁。释放时用 Lua 脚本先比较 value 再删除，保证原子性，避免删掉其他请求的锁。长任务要考虑 watchdog 续期或拆分任务，并保证业务幂等。',
 '重点看是否理解“比较和删除必须原子化”。', 920007, 920006, 'MEDIUM', 'SHORT_ANSWER', '1-3年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920011, '网关鉴权和服务内鉴权如何分工？', '网关鉴权 服务内鉴权 分工',
 '在微服务系统中，哪些校验适合放网关，哪些必须放业务服务？',
 '网关适合做统一登录态校验、Token 解析、基础角色权限、限流和黑白名单。业务服务必须做资源归属、数据权限、状态机校验和关键业务规则。内部接口要通过网络隔离、内部签名或服务身份避免绕过网关。',
 '好的回答会体现纵深防御，而不是把所有安全都压到网关。', 920008, 920007, 'MEDIUM', 'SHORT_ANSWER', '3-5年', 0, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920012, '接口幂等如何设计？', '接口幂等 设计',
 '请以订单创建或简历解析任务提交为例，说明幂等键、唯一索引和状态机如何配合。',
 '客户端传入幂等键或服务端按业务字段生成幂等键，数据库唯一索引保证同一业务请求只落一条记录。重复请求返回已有任务或结果。异步场景要记录 PENDING/RUNNING/SUCCESS/FAILED 状态，失败可重试但状态不能随意回退。',
 '答案要落到存储约束和状态流转，不能只说“前端防重复点击”。', 920009, 920008, 'MEDIUM', 'SCENARIO', '1-3年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920013, '异步任务状态表应该如何设计？', '异步任务 状态表 设计',
 '请设计一个 AI 简历解析任务表的核心字段，并说明如何支持重试、查询和补偿。',
 '核心字段包括任务号、用户 ID、业务类型、业务 ID、状态、输入快照、输出结果、重试次数、最大重试次数、失败原因、开始/完成时间和 traceId。任务号或业务键做唯一约束，前端轮询查状态，失败任务进入补偿或人工处理。',
 '考察任务治理、幂等、可观测性和失败补偿。', 920009, 920008, 'MEDIUM', 'SCENARIO', '3-5年', 0, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW()),
(920014, '如何讲清楚 CodeCoachAI 这类项目的技术亮点？', 'codecoachai 项目 技术亮点 表达',
 '请围绕业务目标、架构、个人职责、难点和结果，组织一段 2 分钟项目介绍。',
 '可以先说项目解决求职者简历诊断、岗位匹配、模拟面试和学习计划闭环问题；架构上使用 Spring Cloud、Nacos、Redis、MySQL、RocketMQ 和前端管理端；个人负责题库治理、AI 生成审核、操作日志、通知中心等模块；亮点包括异步任务状态追踪、重复题审核、慢 SQL 记录和可跳转通知。',
 '重点是用业务闭环串起技术栈和个人贡献。', 920010, 920009, 'EASY', 'SHORT_ANSWER', '0-1年', 1, 1, 1, 'APPROVED', 'MANUAL', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), normalized_title = VALUES(normalized_title),
  content = VALUES(content), reference_answer = VALUES(reference_answer), analysis = VALUES(analysis),
  category_id = VALUES(category_id), group_id = VALUES(group_id), difficulty = VALUES(difficulty),
  question_type = VALUES(question_type), experience_level = VALUES(experience_level),
  is_high_frequency = VALUES(is_high_frequency), is_recommended = VALUES(is_recommended),
  status = VALUES(status), audit_status = VALUES(audit_status), source_type = VALUES(source_type),
  deleted = 0, updated_at = NOW();

INSERT INTO question_tag_relation (id, question_id, tag_id, deleted, created_at, updated_at) VALUES
(920001, 920001, 920001, 0, NOW(), NOW()), (920002, 920001, 920002, 0, NOW(), NOW()),
(920003, 920002, 920002, 0, NOW(), NOW()),
(920004, 920003, 920003, 0, NOW(), NOW()), (920005, 920003, 920004, 0, NOW(), NOW()),
(920006, 920004, 920004, 0, NOW(), NOW()),
(920007, 920005, 920005, 0, NOW(), NOW()),
(920008, 920006, 920006, 0, NOW(), NOW()),
(920009, 920007, 920007, 0, NOW(), NOW()), (920010, 920007, 920008, 0, NOW(), NOW()),
(920011, 920008, 920007, 0, NOW(), NOW()), (920012, 920008, 920008, 0, NOW(), NOW()),
(920013, 920009, 920009, 0, NOW(), NOW()),
(920014, 920010, 920010, 0, NOW(), NOW()),
(920015, 920011, 920011, 0, NOW(), NOW()),
(920016, 920012, 920012, 0, NOW(), NOW()),
(920017, 920013, 920013, 0, NOW(), NOW()),
(920018, 920014, 920014, 0, NOW(), NOW()), (920019, 920014, 920015, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE question_id = VALUES(question_id), tag_id = VALUES(tag_id), deleted = 0, updated_at = NOW();

INSERT INTO question_relation
(id, source_question_id, target_question_id, relation_type, relation_status, reason, similarity_score, created_by, deleted, created_at, updated_at) VALUES
(920001, 920003, 920004, 'RELATED', 'ACTIVE', '线程池与 CompletableFuture 都考察异步执行和线程池隔离。', 72.00, 1, 0, NOW(), NOW()),
(920002, 920007, 920008, 'ADVANCED', 'ACTIVE', '慢查询案例是在联合索引基础上的业务进阶题。', 81.00, 1, 0, NOW(), NOW()),
(920003, 920009, 920010, 'RELATED', 'ACTIVE', '缓存问题与分布式锁都属于 Redis 工程实践。', 68.00, 1, 0, NOW(), NOW()),
(920004, 920012, 920013, 'RELATED', 'ACTIVE', '接口幂等和异步任务状态表经常配套出现。', 78.00, 1, 0, NOW(), NOW()),
(920005, 920011, 920012, 'PREREQUISITE', 'ACTIVE', '微服务鉴权理解后更容易讨论业务接口幂等边界。', 55.00, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE source_question_id = VALUES(source_question_id), target_question_id = VALUES(target_question_id),
  relation_type = VALUES(relation_type), relation_status = VALUES(relation_status), reason = VALUES(reason),
  similarity_score = VALUES(similarity_score), created_by = VALUES(created_by), deleted = 0, updated_at = NOW();

-- ============================================================
-- 4. AI review and duplicate review examples
-- ============================================================

INSERT INTO question_review
(id, batch_id, created_by, review_status, target_position, technology_stack, knowledge_point, question_type, difficulty,
 experience_years, question_title, question_content, reference_answer, analysis, follow_up_questions_json,
 tag_suggestions_json, category_suggestion, group_suggestion, category_id, group_id, tag_ids_json, deleted, created_at, updated_at) VALUES
(920001, 'QG-DEMO-20260527-01', 1, 'PENDING', '高级 Java 后端工程师', 'Java, Spring Boot, MySQL, Redis, RocketMQ', 'RocketMQ可靠消息',
 'SCENARIO', 'HARD', 5, 'RocketMQ 如何保证订单支付消息不丢失？',
 '请从生产端、Broker、消费端、幂等和补偿几个角度说明可靠消息方案。',
 '生产端使用事务消息或本地消息表，发送失败要重试；Broker 开启刷盘和主从策略；消费端手动 ACK、失败重试和死信队列；业务处理必须幂等，并通过对账任务补偿异常订单。',
 '重点看候选人是否能覆盖发送、存储、消费和补偿闭环。',
 JSON_ARRAY('如果消费成功但 ACK 失败会发生什么？', '本地消息表和事务消息如何选择？'),
 JSON_ARRAY('RocketMQ', '异步任务'), '项目场景', '异步任务状态表', 920009, 920008, JSON_ARRAY(920013), 0, NOW(), NOW()),
(920002, 'QG-DEMO-20260527-01', 1, 'PENDING', '中级 Java 开发工程师', 'Spring Boot, MySQL, Redis', '订单幂等',
 'SCENARIO', 'MEDIUM', 3, '订单创建接口如何防止重复提交？',
 '用户快速点击提交按钮或网络重试时，后端如何保证只创建一笔订单？',
 '前端防重复点击只是体验优化，后端应使用幂等键、业务唯一约束或 Redis 原子写入。订单表可以用用户 ID + 幂等键或业务单号建立唯一索引，重复请求返回已有订单结果。',
 '答案必须落到后端存储约束和状态机。',
 JSON_ARRAY('如果第一次请求已经扣库存但返回超时，第二次请求应该怎么处理？'),
 JSON_ARRAY('接口幂等', '状态机'), '项目场景', '接口幂等与状态机', 920009, 920008, JSON_ARRAY(920012), 0, NOW(), NOW()),
(920003, 'QG-DEMO-20260527-01', 1, 'PENDING', 'Java 后端开发工程师', 'JVM, Arthas, Linux', '内存泄漏排查',
 'SHORT_ANSWER', 'HARD', 4, '如何定位一个 Java 服务的内存泄漏？',
 '请说明从监控告警到 dump 分析再到代码修复的完整链路。',
 '先确认老年代持续上涨且 GC 后无法回落，再通过 jmap/Arthas 导出堆 dump，用 MAT 查看 dominator tree、GC Roots 和大对象引用链，结合代码和近期发布定位缓存、集合、ThreadLocal 或监听器未释放问题。',
 '考察是否能讲出工具和引用链，而不是泛泛说调 JVM 参数。',
 JSON_ARRAY('ThreadLocal 泄漏为什么常见？'),
 JSON_ARRAY('GC调优', '线上排障'), 'JVM', 'JVM GC排查', 920004, 920003, JSON_ARRAY(920005), 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE review_status = VALUES(review_status), question_title = VALUES(question_title),
  question_content = VALUES(question_content), reference_answer = VALUES(reference_answer), analysis = VALUES(analysis),
  category_id = VALUES(category_id), group_id = VALUES(group_id), tag_ids_json = VALUES(tag_ids_json),
  deleted = 0, updated_at = NOW();

INSERT INTO question_duplicate_review
(id, source_question_id, target_question_id, review_status, match_type, similarity_score, match_reason,
 source_title_snapshot, target_title_snapshot, source_content_snapshot, target_content_snapshot,
 source_group_id, target_group_id, created_by, deleted, created_at, updated_at) VALUES
(920001, 920007, 920008, 'PENDING', 'CONTENT_SIMILAR', 63.50, '两题都涉及 MySQL 索引，但一个考基础规则，一个考业务慢查询优化，建议保留并建立进阶关系。',
 'MySQL 联合索引最左前缀怎么理解？', '候选人列表慢查询如何优化？',
 '联合索引 (user_id, status, created_at) 在哪些查询里能被较好利用？', '后台候选人列表支持岗位、状态、城市、更新时间筛选并按更新时间倒序分页，你会如何优化？',
 920005, 920005, 1, 0, NOW(), NOW()),
(920002, 920009, 920010, 'PENDING', 'TITLE_SIMILAR', 58.00, '两题都属于 Redis 主题，但一个是缓存治理，一个是分布式锁，建议保留。',
 'Redis 缓存穿透、击穿、雪崩分别是什么？', 'Redis 分布式锁如何避免误删？',
 '请用业务例子区分三类问题，并给出治理方案。', '请说明 value 唯一标识、过期时间、Lua 释放和续期分别解决什么问题。',
 920006, 920006, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE review_status = VALUES(review_status), match_type = VALUES(match_type),
  similarity_score = VALUES(similarity_score), match_reason = VALUES(match_reason), deleted = 0, updated_at = NOW();

-- ============================================================
-- 5. Resume, JD, match and skill-gap data
-- ============================================================

INSERT INTO resume
(id, user_id, title, resume_name, real_name, target_position, skill_stack, work_experience, education_experience,
 email, phone, summary, is_default, status, deleted, created_at, updated_at) VALUES
(920001, 1, '陈晨-高级Java后端-面试投递版', '陈晨-高级Java后端-面试投递版', '陈晨', '高级 Java 后端工程师',
 'Java, Spring Boot, Spring Cloud Alibaba, MySQL, Redis, RocketMQ, Elasticsearch, Vue',
 '5年 Java 后端开发经验，负责过智能面试辅导平台、招聘运营后台和订单履约系统，熟悉微服务治理、缓存一致性、异步任务和慢 SQL 优化。',
 '2017.09-2021.06 南京邮电大学 软件工程 本科', 'chenchen@example.com', '13810002001',
 '偏平台型后端，能从业务闭环、系统稳定性和可观测性角度推进项目落地。', 1, 1, 0, NOW(), NOW()),
(920002, 2, '张明-Java后端开发-3年经验', '张明-Java后端开发-3年经验', '张明', 'Java 后端开发工程师',
 'Java, Spring Boot, MyBatis, MySQL, Redis, RabbitMQ, Linux',
 '3年后端开发经验，参与招聘流程管理、简历解析、任务通知等模块，熟悉 RBAC、异步任务和接口幂等。',
 '2018.09-2022.06 合肥工业大学 计算机科学与技术 本科', 'zhangming@example.com', '13810002002',
 '具备扎实后端 CRUD 和业务建模能力，正在补强中间件和性能优化。', 1, 1, 0, NOW(), NOW()),
(920003, 3, '李娜-中台Java开发-4年经验', '李娜-中台Java开发-4年经验', '李娜', '中级 Java 开发工程师',
 'Java, Spring Security, MySQL, Redis, XXL-Job, Vue',
 '4年企业中台开发经验，负责候选人管理、权限中心、报表任务和审批流。',
 '2016.09-2020.06 上海理工大学 信息管理与信息系统 本科', 'lina@example.com', '13810002003',
 '擅长业务流程抽象和权限模型设计，需要继续强化源码和高并发场景表达。', 1, 1, 0, NOW(), NOW()),
(920004, 4, '王磊-Java实习生-校招版', '王磊-Java实习生-校招版', '王磊', 'Java 后端实习生',
 'Java, Spring Boot, MySQL, Redis, Git',
 '校园项目经验为主，做过二手交易平台、课程问答社区和后台管理系统。',
 '2022.09-2026.06 杭州电子科技大学 软件工程 本科', 'wanglei@example.com', '13810002004',
 '基础较完整，项目表达需要从功能描述升级为问题和方案。', 1, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), resume_name = VALUES(resume_name), real_name = VALUES(real_name),
  target_position = VALUES(target_position), skill_stack = VALUES(skill_stack), work_experience = VALUES(work_experience),
  education_experience = VALUES(education_experience), summary = VALUES(summary), is_default = VALUES(is_default),
  status = VALUES(status), deleted = 0, updated_at = NOW();

INSERT INTO resume_project
(id, resume_id, project_name, project_period, project_background, role, tech_stack, responsibility, core_features,
 technical_difficulties, optimization_results, description, highlights, sort, sort_order, deleted, created_at, updated_at) VALUES
(920001, 920001, 'CodeCoachAI 智能面试辅导平台', '2025.11-2026.05', '面向 Java 求职者的简历诊断、岗位匹配、模拟面试和学习计划平台。',
 '后端核心开发', 'Spring Cloud Alibaba, Nacos, MySQL, Redis, RocketMQ, Elasticsearch, Vue',
 '负责题库治理、AI 生成审核、重复题审核、通知中心、操作日志和慢 SQL 记录模块。',
 'AI 生成题目进入审核池；审核通过后进入题库；重复题进入待处理列表；通知可查看详情或跳转业务页面。',
 'AI 结果不稳定、题目元数据缺失、重复题过多、管理端操作链路缺少审计。',
 '补齐分类/题组/标签回填，重复题记录从 45 条垃圾样本降为可解释审核样本，操作日志覆盖管理端与用户端关键模块。',
 '以求职闭环为主线，覆盖简历、岗位、题库、面试、学习计划。',
 '把 AI 生成题目从“直接入库”改为“审核-去重-题库”的治理链路。', 1, 1, 0, NOW(), NOW()),
(920002, 920001, '招聘运营后台慢查询治理', '2024.03-2025.08', '企业 HR 使用的候选人检索、面试安排和投递跟进后台。',
 '后端负责人', 'Spring Boot, MySQL, Redis, XXL-Job, Vue',
 '负责候选人列表查询、筛选统计、操作日志和任务通知。',
 '多条件筛选、按更新时间倒序分页、批量导出和权限隔离。',
 '候选人列表在大偏移分页时响应超过 5 秒，且筛选条件组合较多。',
 '增加联合索引和游标分页后，核心查询 P95 从 5.2s 降到 480ms。',
 '典型后台系统性能优化案例。',
 '用 EXPLAIN 和慢查询日志驱动索引设计，而不是盲目加索引。', 2, 2, 0, NOW(), NOW()),
(920003, 920002, '简历解析与学习计划系统', '2024.06-2025.04', '帮助求职者上传简历、解析项目经历并生成学习任务。',
 '后端开发', 'Spring Boot, MyBatis, MySQL, Redis, RabbitMQ',
 '实现简历解析任务、任务状态轮询、站内通知和失败重试。',
 '文件上传、异步解析、结果确认、学习计划任务生成。',
 'AI 调用耗时长且失败不可控，需要让用户看到任务进度。',
 '引入任务状态表和通知，减少用户重复提交，失败任务可重试。',
 '求职场景下的异步任务治理。',
 '通过状态机和幂等键解决重复提交和任务可见性。', 1, 1, 0, NOW(), NOW()),
(920004, 920003, '企业中台权限与审批系统', '2023.02-2025.01', '面向内部运营团队的权限、审批和报表平台。',
 '后端开发', 'Spring Security, MySQL, Redis, XXL-Job',
 '负责 RBAC 权限模型、审批状态机和报表任务。',
 '菜单权限、数据权限、审批流、定时报表。',
 '服务内资源归属校验容易被忽略，审批状态流转需要防止回退。',
 '补充服务内鉴权和状态机校验，降低越权和重复审批问题。',
 '企业中台项目。',
 '能讲清网关鉴权和服务内鉴权的边界。', 1, 1, 0, NOW(), NOW()),
(920005, 920004, '校园二手交易平台', '2024.03-2024.09', '校内学生发布商品、搜索商品、下单和评价。',
 '后端开发', 'Spring Boot, MyBatis, MySQL, Redis',
 '实现商品发布、订单创建、Redis 缓存和后台审核。',
 '商品列表、订单创建、热门商品缓存、管理员审核。',
 '订单重复提交和热门商品缓存失效。',
 '增加订单幂等键和缓存 TTL 随机化，减少重复订单和缓存同时失效。',
 '校招项目。',
 '从功能项目升级为“幂等和缓存治理”案例。', 1, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE project_name = VALUES(project_name), project_period = VALUES(project_period),
  project_background = VALUES(project_background), role = VALUES(role), tech_stack = VALUES(tech_stack),
  responsibility = VALUES(responsibility), core_features = VALUES(core_features),
  technical_difficulties = VALUES(technical_difficulties), optimization_results = VALUES(optimization_results),
  description = VALUES(description), highlights = VALUES(highlights), deleted = 0, updated_at = NOW();

INSERT INTO target_job
(id, user_id, job_title, company_name, job_level, jd_text, jd_source, current_flag, status, priority,
 preparation_status, parse_status, deleted, created_at, updated_at) VALUES
(920001, 1, '高级 Java 后端工程师', '星河智能科技有限公司', '高级',
 '负责 AI 学习平台核心后端服务，要求熟悉 Spring Cloud、MySQL 优化、Redis 缓存治理、RocketMQ 和线上故障排查，有复杂业务抽象和团队协作经验。',
 'MANUAL', 1, 1, 95, 'INTERVIEWING', 'PARSED', 0, NOW(), NOW()),
(920002, 2, 'Java 后端开发工程师', '北京云启科技有限公司', '中级',
 '负责招聘 SaaS 后端模块开发，要求掌握 Spring Boot、MySQL、Redis、接口幂等和基础性能优化。',
 'MANUAL', 1, 1, 90, 'PREPARING', 'PARSED', 0, NOW(), NOW()),
(920003, 3, '中级 Java 开发工程师', '上海数澜信息技术有限公司', '中级',
 '负责企业中台权限、审批和报表模块，要求熟悉 Spring Security、RBAC、MySQL 联合索引和业务建模。',
 'MANUAL', 1, 1, 88, 'PREPARING', 'PARSED', 0, NOW(), NOW()),
(920004, 4, 'Java 后端实习生', '杭州星河网络科技有限公司', '实习',
 '参与 Java 后端接口开发和测试，要求 Java 基础扎实，了解 Spring Boot、MySQL、Redis，有项目表达能力。',
 'MANUAL', 1, 1, 80, 'PREPARING', 'PARSED', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE job_title = VALUES(job_title), company_name = VALUES(company_name), job_level = VALUES(job_level),
  jd_text = VALUES(jd_text), current_flag = VALUES(current_flag), status = VALUES(status), priority = VALUES(priority),
  preparation_status = VALUES(preparation_status), parse_status = VALUES(parse_status), deleted = 0, updated_at = NOW();

INSERT INTO job_description_analysis
(id, target_job_id, user_id, job_title, company_name, job_level, responsibilities_json, required_skills_json,
 bonus_skills_json, tech_keywords_json, business_keywords_json, experience_requirement, project_experience_requirement,
 interview_focus_json, skill_weights_json, summary, raw_result_json, parse_status, deleted, created_at, updated_at) VALUES
(920001, 920001, 1, '高级 Java 后端工程师', '星河智能科技有限公司', '高级',
 JSON_ARRAY('负责 AI 学习平台核心后端服务', '治理题库、面试和学习计划链路', '保障线上稳定性和性能'),
 JSON_ARRAY('Spring Cloud', 'MySQL 优化', 'Redis 缓存一致性', 'RocketMQ', '线上排障'),
 JSON_ARRAY('AI 工程化经验', 'Elasticsearch 检索', '前后端协作经验'),
 JSON_ARRAY('Java', 'Spring Boot', 'MySQL', 'Redis', 'RocketMQ', 'TraceId'),
 JSON_ARRAY('AI 学习平台', '求职辅导', '题库治理'), '5年以上后端经验，有复杂系统稳定性治理经验。',
 '至少能讲清一个微服务平台项目的架构、难点和量化结果。',
 JSON_ARRAY('项目深挖', '慢 SQL 优化', '缓存一致性', '异步任务可靠性'),
 JSON_OBJECT('Java基础', 20, '数据库', 25, '缓存', 20, '消息队列', 15, '项目表达', 20),
 '岗位偏平台后端，需要候选人既能写业务，也能讲清稳定性和性能治理。',
 JSON_OBJECT('source', 'seed'), 'PARSED', 0, NOW(), NOW()),
(920002, 920002, 2, 'Java 后端开发工程师', '北京云启科技有限公司', '中级',
 JSON_ARRAY('招聘 SaaS 模块开发', '接口性能优化', '任务通知和权限维护'),
 JSON_ARRAY('Spring Boot', 'MySQL', 'Redis', '接口幂等'),
 JSON_ARRAY('RabbitMQ', 'Vue 管理端经验'), JSON_ARRAY('Java', 'MySQL', 'Redis', '幂等'),
 JSON_ARRAY('招聘 SaaS', '后台系统'), '3年以上 Java 后端经验。',
 '有后台系统和异步任务经验优先。', JSON_ARRAY('MySQL索引', 'Redis缓存', '项目表达'),
 JSON_OBJECT('Java基础', 25, '数据库', 25, '缓存', 20, '项目表达', 30),
 '适合有完整后台模块经验的中级候选人。', JSON_OBJECT('source', 'seed'), 'PARSED', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE responsibilities_json = VALUES(responsibilities_json), required_skills_json = VALUES(required_skills_json),
  bonus_skills_json = VALUES(bonus_skills_json), tech_keywords_json = VALUES(tech_keywords_json),
  business_keywords_json = VALUES(business_keywords_json), summary = VALUES(summary), parse_status = VALUES(parse_status),
  deleted = 0, updated_at = NOW();

INSERT INTO resume_job_match_report
(id, user_id, resume_id, target_job_id, jd_analysis_id, overall_score, tech_stack_score, project_experience_score,
 business_fit_score, communication_score, strengths_json, gaps_json, resume_risks_json, optimization_suggestions_json,
 recommended_learning_topics_json, recommended_interview_topics_json, summary, raw_result_json, status, deleted, created_at, updated_at) VALUES
(920001, 1, 920001, 920001, 920001, 86, 88, 90, 84, 82,
 JSON_ARRAY('微服务和异步任务经验匹配度高', '有题库治理和通知中心等完整业务闭环', '慢 SQL 优化案例可量化'),
 JSON_ARRAY('RocketMQ 可靠消息表达还可以更系统', 'JVM 排障案例需要补充工具链细节'),
 JSON_ARRAY('部分项目亮点指标需要再具体', 'AI 工程化经验可再突出成本和稳定性'),
 JSON_ARRAY('把 CodeCoachAI 项目按“审核-去重-入库-通知”链路重写', '补充 RocketMQ 失败补偿和死信处理', '准备一次 Full GC 排查案例'),
 JSON_ARRAY('RocketMQ可靠消息', 'JVM内存排查', 'Redis缓存一致性'),
 JSON_ARRAY('项目深挖', '慢SQL优化', '异步任务可靠性'), '整体匹配度较高，适合进入技术二面，重点准备中间件可靠性。',
 JSON_OBJECT('source', 'seed'), 'SUCCESS', 0, NOW(), NOW()),
(920002, 2, 920002, 920002, 920002, 81, 78, 82, 85, 76,
 JSON_ARRAY('后台业务模块经验完整', '异步任务和通知链路能落地'),
 JSON_ARRAY('MySQL 性能优化表达不够深入', 'Redis 缓存一致性需要结合案例'),
 JSON_ARRAY('项目量化结果偏少'), JSON_ARRAY('补充候选人列表慢查询优化案例', '重写简历解析异步任务亮点'),
 JSON_ARRAY('MySQL联合索引', 'Redis缓存治理', '接口幂等'),
 JSON_ARRAY('后台项目表达', '索引设计', '任务状态机'), '基本匹配中级岗位，建议加强数据库和项目表达。',
 JSON_OBJECT('source', 'seed'), 'SUCCESS', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE overall_score = VALUES(overall_score), tech_stack_score = VALUES(tech_stack_score),
  project_experience_score = VALUES(project_experience_score), business_fit_score = VALUES(business_fit_score),
  communication_score = VALUES(communication_score), strengths_json = VALUES(strengths_json), gaps_json = VALUES(gaps_json),
  resume_risks_json = VALUES(resume_risks_json), optimization_suggestions_json = VALUES(optimization_suggestions_json),
  recommended_learning_topics_json = VALUES(recommended_learning_topics_json),
  recommended_interview_topics_json = VALUES(recommended_interview_topics_json), summary = VALUES(summary),
  status = VALUES(status), deleted = 0, updated_at = NOW();

INSERT INTO resume_job_match_detail
(id, report_id, user_id, dimension, skill_name, match_level, score, evidence, gap_description, suggestion, deleted, created_at, updated_at) VALUES
(920001, 920001, 1, 'TECH_STACK', 'Spring Cloud / Spring Boot', 'HIGH', 90, 'CodeCoachAI 使用 Spring Cloud Alibaba 和多服务拆分。', '需要突出服务间调用和降级边界。', '准备网关鉴权、服务内鉴权和内部接口防护案例。', 0, NOW(), NOW()),
(920002, 920001, 1, 'DATABASE', 'MySQL 慢查询优化', 'MEDIUM_HIGH', 84, '有候选人列表慢查询治理案例。', 'EXPLAIN 字段和索引选择过程可再细化。', '准备一条从慢日志到索引上线的完整复盘。', 0, NOW(), NOW()),
(920003, 920001, 1, 'CACHE', 'Redis 缓存一致性', 'MEDIUM_HIGH', 82, '题库和通知中心可结合缓存讨论。', '需要说明删除缓存失败的补偿方案。', '结合缓存穿透/击穿/雪崩题目练习。', 0, NOW(), NOW()),
(920004, 920001, 1, 'MQ', 'RocketMQ 可靠消息', 'MEDIUM', 76, '项目中有异步任务和通知链路。', '消息可靠性表达不够系统。', '补充本地消息表、事务消息、死信和对账。', 0, NOW(), NOW()),
(920005, 920002, 2, 'DATABASE', 'MySQL 联合索引', 'MEDIUM', 78, '有后台筛选列表开发经验。', '索引设计还停留在经验层。', '用候选人列表场景复盘最左前缀和排序。', 0, NOW(), NOW()),
(920006, 920002, 2, 'PROJECT', '项目亮点表达', 'MEDIUM', 74, '能讲出业务模块。', '缺少指标和故障复盘。', '用 STAR 重写简历解析任务项目。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE dimension = VALUES(dimension), skill_name = VALUES(skill_name), match_level = VALUES(match_level),
  score = VALUES(score), evidence = VALUES(evidence), gap_description = VALUES(gap_description),
  suggestion = VALUES(suggestion), deleted = 0, updated_at = NOW();

INSERT INTO skill_profile
(id, user_id, target_job_id, match_report_id, profile_name, overall_level, overall_score, summary,
 source_type, source_biz_id, status, raw_result_json, deleted, created_at, updated_at) VALUES
(920001, 1, 920001, 920001, '陈晨-高级 Java 后端岗位能力画像', 4, 86,
 '具备平台后端和业务闭环经验，数据库、缓存和项目表达较强，消息可靠性和 JVM 排障需要继续强化。',
 'RESUME_JOB_MATCH', 920001, 'SUCCESS', JSON_OBJECT('source', 'seed'), 0, NOW(), NOW()),
(920002, 2, 920002, 920002, '张明-Java 后端岗位能力画像', 3, 81,
 '中级岗位匹配度较好，后台模块经验完整，建议补强 MySQL 索引、Redis 一致性和量化表达。',
 'RESUME_JOB_MATCH', 920002, 'SUCCESS', JSON_OBJECT('source', 'seed'), 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE profile_name = VALUES(profile_name), overall_level = VALUES(overall_level),
  overall_score = VALUES(overall_score), summary = VALUES(summary), status = VALUES(status),
  raw_result_json = VALUES(raw_result_json), deleted = 0, updated_at = NOW();

INSERT INTO skill_gap_item
(id, profile_id, user_id, target_job_id, skill_name, category, target_level, current_level, gap_level, confidence,
 severity, evidence_sources_json, gap_description, recommended_actions_json, priority, source_type, source_biz_id,
 deleted, created_at, updated_at) VALUES
(920001, 920001, 1, 920001, 'RocketMQ 可靠消息', 'MQ', 5, 3, 2, 0.9100, 'HIGH',
 JSON_ARRAY('岗位 JD 要求消息可靠性', '简历中异步任务案例未展开 MQ 细节'),
 '需要能系统说明生产、Broker、消费、幂等和补偿。',
 JSON_ARRAY('复盘本地消息表和事务消息差异', '练习可靠消息面试题', '补充死信队列和对账任务'), 1, 'RESUME_JOB_MATCH', 920001, 0, NOW(), NOW()),
(920002, 920001, 1, 920001, 'JVM 内存泄漏排查', 'JVM', 4, 2, 2, 0.8600, 'HIGH',
 JSON_ARRAY('岗位关注线上排障', '简历中缺少 JVM dump 案例'),
 '需要掌握 GC 日志、堆 dump 和引用链分析。',
 JSON_ARRAY('准备 MAT dominator tree 案例', '练习 Full GC 排查题'), 2, 'RESUME_JOB_MATCH', 920001, 0, NOW(), NOW()),
(920003, 920001, 1, 920001, 'MySQL 慢查询优化', 'DATABASE', 5, 4, 1, 0.9000, 'MEDIUM',
 JSON_ARRAY('已有慢查询项目案例', 'EXPLAIN 细节可增强'),
 '需要把索引选择和验证过程讲得更细。',
 JSON_ARRAY('整理一条真实慢 SQL 优化复盘', '练习联合索引最左前缀'), 3, 'RESUME_JOB_MATCH', 920001, 0, NOW(), NOW()),
(920004, 920002, 2, 920002, 'Redis 缓存一致性', 'CACHE', 4, 2, 2, 0.8400, 'HIGH',
 JSON_ARRAY('岗位要求 Redis', '项目中缓存案例较薄'),
 '需要结合业务讲清删除缓存失败和补偿。',
 JSON_ARRAY('练习缓存穿透/击穿/雪崩', '补充最终一致性方案'), 1, 'RESUME_JOB_MATCH', 920002, 0, NOW(), NOW()),
(920005, 920002, 2, 920002, '项目量化表达', 'COMMUNICATION', 4, 3, 1, 0.8000, 'MEDIUM',
 JSON_ARRAY('简历项目有业务但缺指标'),
 '需要用 STAR 和指标讲项目价值。',
 JSON_ARRAY('重写简历解析任务项目亮点', '准备 2 分钟项目介绍'), 2, 'RESUME_JOB_MATCH', 920002, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE skill_name = VALUES(skill_name), category = VALUES(category), target_level = VALUES(target_level),
  current_level = VALUES(current_level), gap_level = VALUES(gap_level), confidence = VALUES(confidence),
  severity = VALUES(severity), evidence_sources_json = VALUES(evidence_sources_json),
  gap_description = VALUES(gap_description), recommended_actions_json = VALUES(recommended_actions_json),
  priority = VALUES(priority), deleted = 0, updated_at = NOW();

-- ============================================================
-- 6. Study plans, recommendations, practice and interviews
-- ============================================================

INSERT INTO study_plan
(id, user_id, source_type, source_id, target_job_id, skill_profile_id, match_report_id, resume_id, target_position,
 industry_direction, plan_title, plan_summary, plan_status, duration_days, daily_minutes, start_date,
 request_json, result_json, deleted, created_at, updated_at) VALUES
(920001, 1, 'RESUME_JOB_MATCH', 920001, 920001, 920001, 920001, 920001, '高级 Java 后端工程师', 'AI 面试与学习平台',
 '陈晨 14 天高级 Java 后端面试冲刺计划',
 '围绕 RocketMQ 可靠消息、JVM 排障、MySQL 慢查询和项目表达四条主线，每天 60 分钟完成知识复盘、题目练习和项目话术打磨。',
 'ACTIVE', 14, 60, CURDATE(), JSON_OBJECT('source', 'seed'), JSON_OBJECT('source', 'seed'), 0, NOW(), NOW()),
(920002, 2, 'RESUME_JOB_MATCH', 920002, 920002, 920002, 920002, 920002, 'Java 后端开发工程师', '招聘 SaaS',
 '张明 10 天 Java 后端岗位补强计划',
 '聚焦 MySQL 联合索引、Redis 缓存一致性、接口幂等和项目量化表达，适合中级岗位面试前冲刺。',
 'ACTIVE', 10, 45, CURDATE(), JSON_OBJECT('source', 'seed'), JSON_OBJECT('source', 'seed'), 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE plan_title = VALUES(plan_title), plan_summary = VALUES(plan_summary),
  plan_status = VALUES(plan_status), duration_days = VALUES(duration_days), daily_minutes = VALUES(daily_minutes),
  start_date = VALUES(start_date), deleted = 0, updated_at = NOW();

INSERT INTO study_task
(id, plan_id, user_id, target_job_id, skill_profile_id, skill_gap_item_id, source_type, source_biz_id,
 stage_no, planned_date, stage_title, task_order, knowledge_point, task_title, task_description, task_type,
 priority, estimated_hours, estimated_minutes, acceptance_criteria, task_status, related_question_ids_json,
 related_tags_json, resources_json, deleted, created_at, updated_at) VALUES
(920001, 920001, 1, 920001, 920001, 920001, 'SKILL_GAP', 920001, 1, CURDATE(), '消息可靠性', 1,
 'RocketMQ 可靠消息', '画出订单支付可靠消息链路', '从本地事务、消息发送、消费幂等、死信和对账补偿五个节点画流程图。',
 'KNOWLEDGE_REVIEW', 'HIGH', 1, 60, '能口述完整可靠消息闭环并说明失败分支。', 'TODO', JSON_ARRAY(920013), JSON_ARRAY('RocketMQ', '异步任务'), JSON_ARRAY('官方文档', '项目复盘'), 0, NOW(), NOW()),
(920002, 920001, 1, 920001, 920001, 920002, 'SKILL_GAP', 920002, 1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'JVM排障', 2,
 'Full GC 排查', '复盘一次内存泄漏定位案例', '准备监控截图、GC 日志、dump 分析和修复验证四段话术。',
 'CASE_REVIEW', 'HIGH', 1, 60, '能说明 MAT dominator tree 和 GC Roots 的作用。', 'TODO', JSON_ARRAY(920005), JSON_ARRAY('GC调优'), JSON_ARRAY('Arthas', 'MAT'), 0, NOW(), NOW()),
(920003, 920001, 1, 920001, 920001, 920003, 'SKILL_GAP', 920003, 2, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '数据库优化', 1,
 'MySQL 慢 SQL', '准备候选人列表慢查询优化复盘', '按发现、EXPLAIN、索引设计、上线验证组织 2 分钟回答。',
 'PRACTICE', 'HIGH', 1, 45, '能解释联合索引字段顺序和分页优化。', 'DOING', JSON_ARRAY(920007, 920008), JSON_ARRAY('MySQL索引', '慢SQL优化'), JSON_ARRAY('EXPLAIN'), 0, NOW(), NOW()),
(920004, 920001, 1, 920001, 920001, NULL, 'PROJECT', 920001, 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '项目表达', 2,
 'CodeCoachAI 项目亮点', '重写 CodeCoachAI 项目 2 分钟介绍', '用业务闭环串起题库治理、审核、重复题、通知、日志和慢 SQL。',
 'RESUME_POLISH', 'MEDIUM', 1, 45, '介绍中包含个人职责、技术难点和量化结果。', 'TODO', JSON_ARRAY(920014), JSON_ARRAY('项目亮点', 'STAR表达'), JSON_ARRAY('简历项目'), 0, NOW(), NOW()),
(920005, 920002, 2, 920002, 920002, 920004, 'SKILL_GAP', 920004, 1, CURDATE(), '缓存治理', 1,
 'Redis 缓存一致性', '练习缓存穿透、击穿、雪崩和双写一致性', '分别准备定义、触发场景、解决方案和项目落地边界。',
 'PRACTICE', 'HIGH', 1, 45, '能区分三类缓存问题并讲出补偿方案。', 'TODO', JSON_ARRAY(920009, 920010), JSON_ARRAY('Redis缓存', '分布式锁'), JSON_ARRAY('题库练习'), 0, NOW(), NOW()),
(920006, 920002, 2, 920002, 920002, 920005, 'SKILL_GAP', 920005, 1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '项目表达', 2,
 '简历解析任务亮点', '按 STAR 改写简历解析异步任务项目', '强调幂等键、状态机、通知中心和失败重试。',
 'RESUME_POLISH', 'MEDIUM', 1, 45, '项目描述包含问题、方案和结果。', 'TODO', JSON_ARRAY(920012, 920013), JSON_ARRAY('接口幂等', '项目亮点'), JSON_ARRAY('简历优化'), 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE task_title = VALUES(task_title), task_description = VALUES(task_description),
  task_type = VALUES(task_type), priority = VALUES(priority), task_status = VALUES(task_status),
  related_question_ids_json = VALUES(related_question_ids_json), related_tags_json = VALUES(related_tags_json),
  deleted = 0, updated_at = NOW();

INSERT INTO study_plan_skill_relation
(id, user_id, study_plan_id, study_task_id, target_job_id, skill_profile_id, skill_gap_item_id,
 source_type, source_biz_id, priority, deleted, created_at, updated_at) VALUES
(920001, 1, 920001, 920001, 920001, 920001, 920001, 'SKILL_GAP', 920001, 1, 0, NOW(), NOW()),
(920002, 1, 920001, 920002, 920001, 920001, 920002, 'SKILL_GAP', 920002, 2, 0, NOW(), NOW()),
(920003, 1, 920001, 920003, 920001, 920001, 920003, 'SKILL_GAP', 920003, 3, 0, NOW(), NOW()),
(920004, 2, 920002, 920005, 920002, 920002, 920004, 'SKILL_GAP', 920004, 1, 0, NOW(), NOW()),
(920005, 2, 920002, 920006, 920002, 920002, 920005, 'SKILL_GAP', 920005, 2, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE study_task_id = VALUES(study_task_id), priority = VALUES(priority), deleted = 0, updated_at = NOW();

INSERT INTO question_recommendation_batch
(id, user_id, source_type, source_id, job_target_id, match_report_id, skill_profile_id, study_plan_id,
 strategy, question_count, status, request_json, result_json, deleted, created_at, updated_at) VALUES
(920001, 1, 'STUDY_PLAN', 920001, 920001, 920001, 920001, 920001, 'GAP_PRIORITY', 4, 'SUCCESS',
 JSON_OBJECT('source', 'seed'), JSON_OBJECT('matched', 4), 0, NOW(), NOW()),
(920002, 2, 'STUDY_PLAN', 920002, 920002, 920002, 920002, 920002, 'GAP_PRIORITY', 3, 'SUCCESS',
 JSON_OBJECT('source', 'seed'), JSON_OBJECT('matched', 3), 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE question_count = VALUES(question_count), status = VALUES(status),
  request_json = VALUES(request_json), result_json = VALUES(result_json), deleted = 0, updated_at = NOW();

INSERT INTO question_recommendation_item
(id, batch_id, user_id, question_id, question_title, question_content, question_type, difficulty, skill_code, skill_name,
 gap_severity, recommend_reason, answer_hint, evaluate_points, sort_order, match_status, practice_status,
 deleted, created_at, updated_at) VALUES
(920001, 920001, 1, 920013, '异步任务状态表应该如何设计？', '请设计一个 AI 简历解析任务表的核心字段，并说明如何支持重试、查询和补偿。',
 'SCENARIO', 'MEDIUM', 'MQ', 'RocketMQ 可靠消息', 'HIGH', '与可靠消息和任务补偿能力直接相关。',
 '从任务号、状态、重试和 traceId 展开。', '状态流转、幂等、补偿、可观测性', 1, 'MATCHED', 'UNPRACTICED', 0, NOW(), NOW()),
(920002, 920001, 1, 920005, '线上频繁 Full GC 怎么定位？', '请按发现、定位、验证和修复几个步骤说明排查流程。',
 'SHORT_ANSWER', 'HARD', 'JVM', 'JVM 内存泄漏排查', 'HIGH', '补齐岗位关注的 JVM 排障能力。',
 '先监控，再日志和 dump，最后修复验证。', 'GC日志、dump、引用链、验证', 2, 'MATCHED', 'UNPRACTICED', 0, NOW(), NOW()),
(920003, 920001, 1, 920008, '候选人列表慢查询如何优化？', '后台候选人列表支持岗位、状态、城市、更新时间筛选并按更新时间倒序分页，你会如何优化？',
 'SCENARIO', 'HARD', 'DATABASE', 'MySQL 慢查询优化', 'MEDIUM', '强化简历中的真实慢查询项目。',
 '结合业务筛选条件设计联合索引。', 'EXPLAIN、索引顺序、分页优化', 3, 'MATCHED', 'PRACTICED', 0, NOW(), NOW()),
(920004, 920001, 1, 920014, '如何讲清楚 CodeCoachAI 这类项目的技术亮点？', '请围绕业务目标、架构、个人职责、难点和结果，组织一段 2 分钟项目介绍。',
 'SHORT_ANSWER', 'EASY', 'COMMUNICATION', '项目表达', 'MEDIUM', '用于项目深挖开场。',
 '按业务闭环、职责、难点、结果组织。', '结构完整、结果量化、个人贡献', 4, 'MATCHED', 'UNPRACTICED', 0, NOW(), NOW()),
(920005, 920002, 2, 920009, 'Redis 缓存穿透、击穿、雪崩分别是什么？', '请用业务例子区分三类问题，并给出治理方案。',
 'SHORT_ANSWER', 'MEDIUM', 'CACHE', 'Redis 缓存一致性', 'HIGH', '补齐岗位要求的缓存治理。',
 '分别说触发点和治理方案。', '穿透、击穿、雪崩、补偿', 1, 'MATCHED', 'UNPRACTICED', 0, NOW(), NOW()),
(920006, 920002, 2, 920012, '接口幂等如何设计？', '请以订单创建或简历解析任务提交为例，说明幂等键、唯一索引和状态机如何配合。',
 'SCENARIO', 'MEDIUM', 'ARCHITECTURE', '项目量化表达', 'MEDIUM', '可以直接服务于简历解析任务项目表达。',
 '落到唯一索引和状态机。', '幂等键、唯一约束、状态机', 2, 'MATCHED', 'UNPRACTICED', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE question_id = VALUES(question_id), question_title = VALUES(question_title),
  question_content = VALUES(question_content), difficulty = VALUES(difficulty), skill_name = VALUES(skill_name),
  gap_severity = VALUES(gap_severity), recommend_reason = VALUES(recommend_reason),
  answer_hint = VALUES(answer_hint), evaluate_points = VALUES(evaluate_points),
  match_status = VALUES(match_status), practice_status = VALUES(practice_status), deleted = 0, updated_at = NOW();

INSERT INTO practice_record
(id, user_id, question_id, answer_content, answer_duration_seconds, source, recommendation_item_id, batch_id,
 source_type, source_id, skill_profile_id, study_plan_id, review_status, score, level, mastery_status,
 ai_comment, suggestions, knowledge_points, strengths, weaknesses, improvement_suggestions, reference_answer_snapshot,
 deleted, created_at, updated_at) VALUES
(920001, 1, 920008, '我会先从慢查询日志找到 SQL，再用 EXPLAIN 看 key、rows、Extra，根据 job_id、status、updated_at 设计联合索引，并把大偏移分页改成游标分页。', 180,
 'RECOMMENDATION', 920003, 920001, 'STUDY_PLAN', 920001, 920001, 920001, 'REVIEWED', 86, 'GOOD', 'PRACTICING',
 '回答结构完整，能结合业务筛选条件和分页方式。', '补充覆盖索引和回表成本说明。', 'MySQL索引,慢SQL优化',
 '能从业务查询模式出发。', 'EXPLAIN 字段解释略少。', '下一次回答补充 type、key、rows、Using filesort。', '先通过慢查询日志/APM 定位 SQL，再用 EXPLAIN 分析访问类型、索引命中、扫描行数和排序临时表。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE answer_content = VALUES(answer_content), review_status = VALUES(review_status),
  score = VALUES(score), ai_comment = VALUES(ai_comment), suggestions = VALUES(suggestions),
  mastery_status = VALUES(mastery_status), deleted = 0, updated_at = NOW();

INSERT INTO user_question_record
(id, user_id, question_id, answer_content, mastery_status, wrong, favorite, last_answer_at, deleted, created_at, updated_at) VALUES
(920001, 1, 920008, '从慢日志、EXPLAIN、联合索引、分页优化和压测验证几个步骤展开。', 'PRACTICING', 0, 1, NOW(), 0, NOW(), NOW()),
(920002, 1, 920005, NULL, 'UNKNOWN', 0, 1, NULL, 0, NOW(), NOW()),
(920003, 2, 920009, NULL, 'UNKNOWN', 0, 1, NULL, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE answer_content = VALUES(answer_content), mastery_status = VALUES(mastery_status),
  wrong = VALUES(wrong), favorite = VALUES(favorite), last_answer_at = VALUES(last_answer_at), deleted = 0, updated_at = NOW();

INSERT INTO interview_session
(id, user_id, resume_id, target_job_id, skill_profile_id, match_report_id, interview_mode, mode, title,
 target_position, experience_level, industry_template_id, industry_direction, industry_context, difficulty,
 interviewer_style, based_on_resume, status, report_status, current_stage_id, current_question_id, current_question_group_id,
 answered_question_count, max_question_count, current_follow_up_count, total_score, start_time, end_time,
 deleted, created_at, updated_at) VALUES
(920001, 1, 920001, 920001, 920001, 920001, 'COMPREHENSIVE', 'V3_JOB_TARGET', '陈晨 高级 Java 后端综合模拟面试',
 '高级 Java 后端工程师', '5年', 7, 'AI 面试与学习平台',
 '围绕 CodeCoachAI 项目、题库治理、通知中心、慢 SQL、缓存一致性和异步任务可靠性展开。',
 'HARD', '深挖项目和线上问题', 1, 'COMPLETED', 'GENERATED', 920003, 920008, 920005, 4, 5, 0, 84,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 42 MINUTE, 0, NOW(), NOW()),
(920002, 2, 920002, 920002, 920002, 920002, 'PROJECT_DEEP_DIVE', 'V3_JOB_TARGET', '张明 Java 后端项目深挖面试',
 'Java 后端开发工程师', '3年', 7, '招聘 SaaS', '围绕简历解析任务、通知中心、MySQL 索引和 Redis 缓存治理展开。',
 'MEDIUM', '循序追问', 1, 'COMPLETED', 'GENERATED', 920006, 920012, 920008, 3, 4, 0, 79,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 35 MINUTE, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), status = VALUES(status), report_status = VALUES(report_status),
  total_score = VALUES(total_score), deleted = 0, updated_at = NOW();

INSERT INTO interview_stage
(id, session_id, stage_type, stage_name, sort, stage_order, expected_question_count, asked_question_count,
 focus_points, based_on_resume, allow_follow_up, max_follow_up_count, status, score, deleted, created_at, updated_at) VALUES
(920001, 920001, 'JAVA_BASIC', 'Java 基础与并发', 1, 1, 1, 1, 'HashMap、线程池、CompletableFuture', 1, 1, 2, 'COMPLETED', 82, 0, NOW(), NOW()),
(920002, 920001, 'DATABASE', 'MySQL 与缓存', 2, 2, 1, 1, '慢 SQL、联合索引、Redis 一致性', 1, 1, 2, 'COMPLETED', 86, 0, NOW(), NOW()),
(920003, 920001, 'PROJECT_DEEP_DIVE', '项目深挖', 3, 3, 2, 2, 'CodeCoachAI 题库治理、通知、日志和慢 SQL', 1, 1, 2, 'COMPLETED', 84, 0, NOW(), NOW()),
(920004, 920002, 'PROJECT_DEEP_DIVE', '简历解析任务项目', 1, 1, 2, 2, '异步任务、幂等、通知', 1, 1, 2, 'COMPLETED', 78, 0, NOW(), NOW()),
(920005, 920002, 'TECHNICAL', '数据库与缓存追问', 2, 2, 1, 1, 'MySQL 索引和 Redis 缓存', 1, 1, 2, 'COMPLETED', 80, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE stage_name = VALUES(stage_name), asked_question_count = VALUES(asked_question_count),
  status = VALUES(status), score = VALUES(score), deleted = 0, updated_at = NOW();

INSERT INTO interview_message
(id, session_id, stage_id, question_id, question_group_id, role, message_type, content, question_content,
 user_answer, ai_comment, ai_score, is_follow_up, follow_up_count, knowledge_points, score, comment,
 deleted, created_at, updated_at) VALUES
(920001, 920001, 920001, 920003, 920002, 'AI', 'QUESTION', '线程池核心参数如何按业务压测结果配置？', '假设有一个简历解析异步任务，平均耗时 800ms，峰值每秒 80 个请求，你会如何设计线程池参数？',
 NULL, NULL, NULL, 0, 0, '线程池,压测估算', NULL, NULL, 0, NOW(), NOW()),
(920002, 920001, 920001, 920003, 920002, 'USER', 'ANSWER', '我会先判断任务是 IO 型还是 CPU 型，用 QPS 乘平均耗时估算并发，大概 64 个并发，再结合机器核数、下游限流和队列等待时间配置核心线程、最大线程和有界队列。拒绝时记录任务失败并通知用户重试。', NULL,
 '我会先判断任务是 IO 型还是 CPU 型，用 QPS 乘平均耗时估算并发，大概 64 个并发，再结合机器核数、下游限流和队列等待时间配置核心线程、最大线程和有界队列。拒绝时记录任务失败并通知用户重试。',
 '回答能结合业务指标和下游保护，建议补充线程池监控指标。', 84, 0, 0, '线程池,异步任务', 84, '结构完整。', 0, NOW(), NOW()),
(920003, 920001, 920002, 920008, 920005, 'AI', 'QUESTION', '候选人列表慢查询如何优化？', '后台候选人列表支持岗位、状态、城市、更新时间筛选并按更新时间倒序分页，你会如何优化？',
 NULL, NULL, NULL, 0, 0, 'MySQL索引,慢SQL优化', NULL, NULL, 0, NOW(), NOW()),
(920004, 920001, 920002, 920008, 920005, 'USER', 'ANSWER', '先从慢查询日志确认 SQL，再统计常用筛选条件，设计 tenant_id、job_id、status、updated_at 的联合索引。分页如果偏移很大，会改成基于 updated_at 和 id 的游标分页。上线后用 EXPLAIN 和 P95 验证。', NULL,
 '先从慢查询日志确认 SQL，再统计常用筛选条件，设计 tenant_id、job_id、status、updated_at 的联合索引。分页如果偏移很大，会改成基于 updated_at 和 id 的游标分页。上线后用 EXPLAIN 和 P95 验证。',
 '能形成完整优化闭环，是本场亮点回答。', 88, 0, 0, 'MySQL索引,分页优化', 88, '优秀。', 0, NOW(), NOW()),
(920005, 920001, 920003, 920014, 920009, 'AI', 'QUESTION', '如何讲清楚 CodeCoachAI 这类项目的技术亮点？', '请围绕业务目标、架构、个人职责、难点和结果，组织一段 2 分钟项目介绍。',
 NULL, NULL, NULL, 0, 0, '项目亮点,STAR表达', NULL, NULL, 0, NOW(), NOW()),
(920006, 920001, 920003, 920014, 920009, 'USER', 'ANSWER', 'CodeCoachAI 是求职辅导平台，我负责题库治理和管理端体验，重点把 AI 生成题目改成审核、去重、入库链路，同时补齐通知跳转、操作日志和慢 SQL 记录，方便管理员看到每个动作。', NULL,
 'CodeCoachAI 是求职辅导平台，我负责题库治理和管理端体验，重点把 AI 生成题目改成审核、去重、入库链路，同时补齐通知跳转、操作日志和慢 SQL 记录，方便管理员看到每个动作。',
 '业务闭环清楚，但可以再补充数据指标，例如重复审核记录从垃圾样本清理为可解释记录。', 81, 0, 0, '项目表达', 81, '建议量化。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE content = VALUES(content), question_content = VALUES(question_content),
  user_answer = VALUES(user_answer), ai_comment = VALUES(ai_comment), ai_score = VALUES(ai_score),
  score = VALUES(score), comment = VALUES(comment), deleted = 0, updated_at = NOW();

INSERT INTO interview_report
(id, session_id, user_id, status, total_score, stage_scores, weak_points, summary, strengths, weaknesses,
 main_problems, project_problems, review_suggestions, recommended_questions, qa_review, report_content,
 generated_at, suggestions, deleted, created_at, updated_at) VALUES
(920001, 920001, 1, 'GENERATED', 84,
 JSON_OBJECT('Java基础与并发', 82, 'MySQL与缓存', 86, '项目深挖', 84),
 JSON_ARRAY('RocketMQ 可靠消息表达还不够系统', 'JVM 排障工具链需要补充'),
 '整体表现良好，能把 CodeCoachAI 项目与岗位 JD 结合起来回答。MySQL 慢查询和项目治理链路表达较强。',
 '回答有业务闭环，数据库优化案例完整。', '消息队列和 JVM 排障需要更具体案例。',
 '中间件可靠性回答偏概念化。', '项目亮点可以继续补指标和上线效果。',
 '继续练习 RocketMQ 可靠消息和 Full GC 排查两类题。',
 JSON_ARRAY(920005, 920013), JSON_OBJECT('score', 84), '本次面试建议进入二面，但需要补强中间件和 JVM 排障。',
 NOW(), '准备 2 个线上故障复盘案例，并把项目数据指标写入简历。', 0, NOW(), NOW()),
(920002, 920002, 2, 'GENERATED', 79,
 JSON_OBJECT('项目深挖', 78, '数据库与缓存追问', 80),
 JSON_ARRAY('索引设计细节不足', '项目表达缺少量化指标'),
 '张明能讲清业务模块和异步任务状态，但数据库和缓存回答需要更深入。',
 '任务状态机和通知链路表达清晰。', 'MySQL 和 Redis 的工程边界不够清楚。',
 '回答容易停留在功能实现。', '简历解析项目需要突出幂等和失败补偿。',
 '按学习计划完成 MySQL 和 Redis 题目练习。',
 JSON_ARRAY(920009, 920012), JSON_OBJECT('score', 79), '适合中级岗位初面，建议强化数据库和缓存后再投递重点岗位。',
 NOW(), '用 STAR 重写简历解析任务项目，补充 P95 或失败率等指标。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE total_score = VALUES(total_score), stage_scores = VALUES(stage_scores),
  weak_points = VALUES(weak_points), summary = VALUES(summary), strengths = VALUES(strengths),
  weaknesses = VALUES(weaknesses), review_suggestions = VALUES(review_suggestions),
  recommended_questions = VALUES(recommended_questions), report_content = VALUES(report_content),
  suggestions = VALUES(suggestions), deleted = 0, updated_at = NOW();

-- ============================================================
-- 7. Notifications, applications, growth snapshots and templates
-- ============================================================

INSERT INTO notification
(id, user_id, type, title, content, biz_type, biz_id, read_status, read_at, deleted, created_at, updated_at) VALUES
(920001, 1, 'REPORT_DONE', '模拟面试报告已生成', '陈晨 高级 Java 后端综合模拟面试报告已生成，可查看得分、优势和待补强项。', 'INTERVIEW_REPORT', '920001', 0, NULL, 0, NOW(), NOW()),
(920002, 1, 'PLAN_READY', '14 天面试冲刺计划已生成', '系统已根据岗位匹配差距生成学习计划，建议从 RocketMQ 可靠消息开始。', 'STUDY_PLAN', '920001', 0, NULL, 0, NOW(), NOW()),
(920003, 1, 'TASK_DONE', '简历岗位匹配报告已生成', '高级 Java 后端岗位匹配报告已完成，总体匹配度 86 分。', 'RESUME_JOB_MATCH', '920001', 1, NOW(), 0, NOW(), NOW()),
(920004, 2, 'PLAN_READY', '张明的岗位补强计划已生成', '10 天 Java 后端岗位补强计划已生成，包含 MySQL、Redis 和项目表达任务。', 'STUDY_PLAN', '920002', 0, NULL, 0, NOW(), NOW()),
(920005, 0, 'SYSTEM', '演示数据已更新为中文业务样例', '题库、简历、岗位、面试和学习计划数据已切换为可演示的中文样例。', NULL, NULL, 0, NULL, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), biz_type = VALUES(biz_type),
  biz_id = VALUES(biz_id), read_status = VALUES(read_status), read_at = VALUES(read_at), deleted = 0, updated_at = NOW();

INSERT INTO job_application
(id, user_id, target_job_id, resume_version_id, company_name, job_title, source, status, applied_at,
 next_follow_up_at, note, deleted, created_at, updated_at) VALUES
(920001, 1, 920001, NULL, '星河智能科技有限公司', '高级 Java 后端工程师', 'Boss直聘', 'INTERVIEWING',
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY), '已完成一面，二面重点准备 RocketMQ 和 JVM 排障。', 0, NOW(), NOW()),
(920002, 2, 920002, NULL, '北京云启科技有限公司', 'Java 后端开发工程师', '拉勾', 'SAVED',
 NULL, DATE_ADD(NOW(), INTERVAL 3 DAY), '学习计划完成后投递。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE company_name = VALUES(company_name), job_title = VALUES(job_title), status = VALUES(status),
  note = VALUES(note), deleted = 0, updated_at = NOW();

INSERT INTO job_application_event
(id, user_id, application_id, event_type, event_time, summary, review_json, deleted, created_at, updated_at) VALUES
(920001, 1, 920001, 'INTERVIEW_INVITED', DATE_SUB(NOW(), INTERVAL 2 DAY), '收到一面邀请，岗位关注微服务和线上排障。', JSON_OBJECT('source', 'seed'), 0, NOW(), NOW()),
(920002, 1, 920001, 'INTERVIEW_DONE', DATE_SUB(NOW(), INTERVAL 1 DAY), '一面已完成，面试官追问了缓存一致性和慢 SQL。', JSON_OBJECT('score', 84), 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE event_type = VALUES(event_type), summary = VALUES(summary), review_json = VALUES(review_json),
  deleted = 0, updated_at = NOW();

INSERT INTO skill_growth_snapshot
(id, user_id, snapshot_date, skill_code, skill_name, score, task_count, done_count, source_type, source_id,
 deleted, created_at, updated_at) VALUES
(920001, 1, CURDATE(), 'MYSQL_OPTIMIZE', 'MySQL 慢查询优化', 82, 2, 1, 'STUDY_PLAN', 920001, 0, NOW(), NOW()),
(920002, 1, CURDATE(), 'MQ_RELIABILITY', 'RocketMQ 可靠消息', 68, 1, 0, 'STUDY_PLAN', 920001, 0, NOW(), NOW()),
(920003, 1, CURDATE(), 'PROJECT_STORY', '项目亮点表达', 76, 1, 0, 'STUDY_PLAN', 920001, 0, NOW(), NOW()),
(920004, 2, CURDATE(), 'REDIS_CACHE', 'Redis 缓存治理', 62, 1, 0, 'STUDY_PLAN', 920002, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE score = VALUES(score), task_count = VALUES(task_count), done_count = VALUES(done_count),
  deleted = 0, updated_at = NOW();

INSERT INTO agent_memory
(id, user_id, memory_type, content, source_type, source_id, confidence, enabled, deleted, created_at, updated_at) VALUES
(920001, 1, 'INTERVIEW_STYLE', '候选人陈晨擅长讲业务闭环和数据库优化，但中间件可靠性需要更多细节。', 'INTERVIEW_REPORT', 920001, 0.90, 1, 0, NOW(), NOW()),
(920002, 2, 'STUDY_PREFERENCE', '张明更适合用真实后台业务案例学习 MySQL 和 Redis。', 'STUDY_PLAN', 920002, 0.85, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE content = VALUES(content), confidence = VALUES(confidence), enabled = VALUES(enabled),
  deleted = 0, updated_at = NOW();

UPDATE industry_template
SET industry_name = CASE industry_code
    WHEN 'ECOMMERCE' THEN '电商零售'
    WHEN 'FINANCE_PAYMENT' THEN '金融支付'
    WHEN 'ONLINE_EDUCATION' THEN '在线教育'
    WHEN 'SAAS' THEN '企业SaaS'
    WHEN 'CONTENT_COMMUNITY' THEN '内容社区'
    WHEN 'ERP_LOGISTICS' THEN 'ERP与物流'
    WHEN 'GENERAL_BACKEND' THEN '通用后端'
    ELSE industry_name
  END,
  target_positions = CASE industry_code
    WHEN 'ECOMMERCE' THEN 'Java后端开发工程师,电商交易后端,订单履约后端'
    WHEN 'FINANCE_PAYMENT' THEN 'Java支付后端,风控后端,账务系统后端'
    WHEN 'ONLINE_EDUCATION' THEN '教育平台后端,课程系统后端,学习平台后端'
    WHEN 'SAAS' THEN '企业SaaS后端,权限平台后端,订阅计费后端'
    WHEN 'CONTENT_COMMUNITY' THEN '内容社区后端,推荐系统后端,审核平台后端'
    WHEN 'ERP_LOGISTICS' THEN 'ERP后端,仓储物流后端,供应链后端'
    WHEN 'GENERAL_BACKEND' THEN 'Java后端开发工程师,平台后端,管理后台后端'
    ELSE target_positions
  END,
  description = CASE industry_code
    WHEN 'ECOMMERCE' THEN '面向商品、订单、库存、支付、营销和售后的后端业务场景。'
    WHEN 'FINANCE_PAYMENT' THEN '面向支付、账务、风控、对账和资金安全的后端业务场景。'
    WHEN 'ONLINE_EDUCATION' THEN '面向课程、排课、直播、作业和学习进度的后端业务场景。'
    WHEN 'SAAS' THEN '面向多租户、组织、权限、订阅计费和审计的企业服务场景。'
    WHEN 'CONTENT_COMMUNITY' THEN '面向内容生产、互动、审核、推荐和反作弊的社区业务场景。'
    WHEN 'ERP_LOGISTICS' THEN '面向库存、仓储、调度和订单流转的 ERP/物流场景。'
    WHEN 'GENERAL_BACKEND' THEN '面向管理后台、平台服务和通用业务系统的后端场景。'
    ELSE description
  END,
  enabled = 1,
  deleted = 0,
  updated_at = NOW()
WHERE industry_code IN ('ECOMMERCE', 'FINANCE_PAYMENT', 'ONLINE_EDUCATION', 'SAAS', 'CONTENT_COMMUNITY', 'ERP_LOGISTICS', 'GENERAL_BACKEND');
