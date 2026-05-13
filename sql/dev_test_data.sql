USE codecoachai_v1;

-- CodeCoachAI V1 local integration and demo data.
-- This script is idempotent and safe to run repeatedly in a local dev database.
-- It does not DROP, TRUNCATE, DELETE, or clear existing data.
--
-- Local-only test accounts:
--   e2e_user  / E2eUser@123
--   e2e_admin / E2eAdmin@123
-- Password values below are BCrypt hashes. Plaintext passwords above are only for local E2E testing.

SET @E2E_USER_PASSWORD_HASH = '$2a$10$bOyTAOeYZgCiar6oHUHRb.Uc.FkO3.LwSZpzqTolrR6XM229.gRaG';
SET @E2E_ADMIN_PASSWORD_HASH = '$2a$10$m/SVVth0osXf9ifYI8HA7.CkBNDFuLq4vRyhlKKx15zFFh2shSDWO';

INSERT INTO sys_role (id, role_code, role_name, description, status, deleted)
VALUES
  (1, 'USER', 'User', 'Default user role', 1, 0),
  (2, 'ADMIN', 'Admin', 'Default admin role', 1, 0)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  description = VALUES(description),
  status = VALUES(status),
  deleted = 0;

INSERT INTO sys_user (id, username, password, nickname, avatar, email, phone, status, deleted)
VALUES
  (100000, 'e2e_user', @E2E_USER_PASSWORD_HASH, 'E2E_TEST_普通用户', 'https://api.dicebear.com/7.x/initials/svg?seed=E2E_TEST_USER', 'e2e_user@codecoachai.local', '13910000001', 1, 0),
  (100001, 'e2e_admin', @E2E_ADMIN_PASSWORD_HASH, 'E2E_TEST_管理员', 'https://api.dicebear.com/7.x/initials/svg?seed=E2E_TEST_ADMIN', 'e2e_admin@codecoachai.local', '13910000002', 1, 0)
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  nickname = VALUES(nickname),
  avatar = VALUES(avatar),
  email = VALUES(email),
  phone = VALUES(phone),
  status = VALUES(status),
  deleted = 0;

SET @E2E_USER_ID = (SELECT id FROM sys_user WHERE username = 'e2e_user' LIMIT 1);
SET @E2E_ADMIN_ID = (SELECT id FROM sys_user WHERE username = 'e2e_admin' LIMIT 1);
SET @ROLE_USER_ID = (SELECT id FROM sys_role WHERE role_code = 'USER' AND deleted = 0 LIMIT 1);
SET @ROLE_ADMIN_ID = (SELECT id FROM sys_role WHERE role_code = 'ADMIN' AND deleted = 0 LIMIT 1);

INSERT INTO sys_user_role (id, user_id, role_id, deleted)
VALUES
  (100000, @E2E_USER_ID, @ROLE_USER_ID, 0),
  (100001, @E2E_ADMIN_ID, @ROLE_ADMIN_ID, 0)
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  role_id = VALUES(role_id),
  deleted = 0;

INSERT INTO question_category (id, category_name, sort, status, deleted)
VALUES
  (100000, 'E2E_TEST_Java基础', 100, 1, 0),
  (100001, 'E2E_TEST_MySQL', 110, 1, 0),
  (100002, 'E2E_TEST_Redis', 120, 1, 0),
  (100003, 'E2E_TEST_SpringBoot', 130, 1, 0),
  (100004, 'E2E_TEST_项目场景题', 140, 1, 0)
ON DUPLICATE KEY UPDATE
  category_name = VALUES(category_name),
  sort = VALUES(sort),
  status = VALUES(status),
  deleted = 0;

INSERT INTO question_tag (id, tag_name, status, deleted)
VALUES
  (100000, 'E2E_TEST_集合', 1, 0),
  (100001, 'E2E_TEST_索引', 1, 0),
  (100002, 'E2E_TEST_缓存', 1, 0),
  (100003, 'E2E_TEST_事务', 1, 0),
  (100004, 'E2E_TEST_项目深挖', 1, 0)
ON DUPLICATE KEY UPDATE
  tag_name = VALUES(tag_name),
  status = VALUES(status),
  deleted = 0;

INSERT INTO question_group (id, group_name, description, category_id, status, deleted)
VALUES
  (100000, 'E2E_TEST_HashMap核心原理', '围绕 HashMap 数据结构、扩容、线程安全和高频追问。', 100000, 1, 0),
  (100001, 'E2E_TEST_MySQL索引优化', '围绕联合索引、最左前缀、覆盖索引和执行计划。', 100001, 1, 0),
  (100002, 'E2E_TEST_Redis缓存治理', '围绕缓存穿透、击穿、雪崩和数据库缓存一致性。', 100002, 1, 0),
  (100003, 'E2E_TEST_Spring事务边界', '围绕声明式事务、AOP 代理、回滚规则和失效场景。', 100003, 1, 0),
  (100004, 'E2E_TEST_项目缓存设计', '围绕真实项目中的缓存设计、热点数据和性能优化。', 100004, 1, 0)
ON DUPLICATE KEY UPDATE
  group_name = VALUES(group_name),
  description = VALUES(description),
  category_id = VALUES(category_id),
  status = VALUES(status),
  deleted = 0;

INSERT INTO question (id, title, content, reference_answer, analysis, category_id, group_id, difficulty, status, deleted)
VALUES
  (100000, 'E2E_TEST_高频_HashMap 为什么线程不安全？',
   '请结合 JDK 8 HashMap 的数组、链表、红黑树结构，说明并发 put 可能产生的问题。',
   'HashMap 本身没有同步控制，并发 put 可能导致数据覆盖、size 不准确、可见性问题以及扩容期间结构异常。多线程共享写场景应使用 ConcurrentHashMap、Collections.synchronizedMap 或外部锁。',
   '这是一道 Java 集合高频题，重点考察候选人能否区分 HashMap 和 ConcurrentHashMap，并能说明扩容和并发写风险。',
   100000, 100000, 'MEDIUM', 1, 0),
  (100001, 'E2E_TEST_ArrayList 和 LinkedList 的区别是什么？',
   '请从底层结构、随机访问、插入删除、内存占用和实际业务选择几个角度说明。',
   'ArrayList 基于动态数组，随机访问快，尾部追加性能好；LinkedList 基于双向链表，按下标访问慢，节点额外占用内存，只有在已定位节点后插入删除才有优势。大多数业务列表查询场景优先 ArrayList。',
   '这道题适合题目详情和参考答案展示，难度简单。',
   100000, 100000, 'EASY', 1, 0),
  (100002, 'E2E_TEST_高频_MySQL 联合索引为什么遵循最左前缀？',
   '假设存在联合索引 (a,b,c)，请说明哪些查询能较好使用索引，哪些会退化。',
   '联合索引按照从左到右的顺序组织 B+Tree。查询条件需要从最左列开始连续匹配；跳过 a 只查 b/c 通常无法完整利用索引；遇到范围查询后，后续列的有序性使用会受限制。',
   '高频 MySQL 索引题，重点考察 B+Tree 有序性、范围条件和 explain 分析能力。',
   100001, 100001, 'HARD', 1, 0),
  (100003, 'E2E_TEST_如何排查一条慢 SQL？',
   '请给出从发现慢查询到验证优化效果的完整排查步骤。',
   '可以从慢查询日志、业务调用链、SQL 复现、EXPLAIN 执行计划、索引命中、扫描行数、回表情况、排序临时表、返回字段和分页策略等角度排查，优化后通过压测或线上指标验证。',
   '适合管理端题库列表、关键词搜索和困难题展示。',
   100001, 100001, 'MEDIUM', 1, 0),
  (100004, 'E2E_TEST_高频_Redis 缓存穿透、击穿、雪崩如何处理？',
   '请分别解释三类缓存问题，并给出常见工程治理方案。',
   '缓存穿透可用空值缓存、布隆过滤器和参数校验；缓存击穿可用互斥锁、逻辑过期和热点预热；缓存雪崩可用过期时间随机化、多级缓存、限流降级和分批重建。',
   '高频 Redis 题，适合错题本和收藏题页面展示。',
   100002, 100002, 'MEDIUM', 1, 0),
  (100005, 'E2E_TEST_Redis 和 MySQL 双写一致性怎么保证？',
   '请说明先写库还是先删缓存，以及延迟双删、重试补偿和最终一致性的取舍。',
   '常见做法是先更新数据库再删除缓存，并通过重试或订阅 binlog 保障最终一致。对强一致要求高的场景需要降低缓存参与度或引入更严格的事务边界。V1 演示侧重点是 cache-aside 与最终一致，不展开 MQ 方案。',
   '这道题用于项目场景追问，强调业务一致性和工程权衡。',
   100002, 100002, 'HARD', 1, 0),
  (100006, 'E2E_TEST_@Transactional 在哪些场景下会失效？',
   '请列举 Spring 声明式事务常见失效原因，并说明如何排查。',
   '常见原因包括自调用绕过代理、方法不是 public、异常被 catch 未抛出、受检异常未配置 rollbackFor、类未被 Spring 管理、数据库引擎不支持事务，以及事务传播行为使用不当。',
   '这道题考察 AOP 代理机制和回滚规则，适合提交答案后展示解析。',
   100003, 100003, 'MEDIUM', 1, 0),
  (100007, 'E2E_TEST_Spring Boot 自动配置原理是什么？',
   '请说明自动配置类如何被加载、条件注解如何生效，以及如何排查某个 Bean 未生效。',
   'Spring Boot 通过自动配置导入机制加载候选配置类，再通过 @ConditionalOnClass、@ConditionalOnMissingBean、@ConditionalOnProperty 等条件判断是否生效。排查时可查看条件评估报告、配置属性和 Bean 定义。',
   '适合 SpringBoot 分类基础展示。',
   100003, 100003, 'MEDIUM', 1, 0),
  (100008, 'E2E_TEST_项目中如何设计热点商品缓存？',
   '请结合电商订单系统，说明热点商品信息缓存的数据结构、过期策略、更新策略和降级方案。',
   '可以用 Redis String/Hash 缓存商品核心信息，设置随机过期时间并对热点商品预热。更新时先写数据库再删除缓存，失败走重试补偿。极端流量下可增加本地缓存、限流和兜底降级。',
   '项目深挖题，适合 AI 模拟面试从简历项目中抽取追问。',
   100004, 100004, 'HARD', 1, 0),
  (100009, 'E2E_TEST_Gateway 如何透传用户上下文？',
   '登录成功后，网关如何校验 token，并把用户信息传递给 auth、question、resume、interview 等服务？',
   'Gateway 校验真实 token 后，通过 X-User-Id、X-Username、X-Roles 和 Authorization 向下游服务透传用户上下文。服务端通过公共安全上下文读取，/inner/** 接口还需要 X-Internal-Call 和 X-Service-Name 保护。',
   '用于验证当前 V1 认证链路和微服务边界。',
   100004, 100004, 'EASY', 1, 0),
  (100010, 'E2E_TEST_禁用题_仅用于管理端状态筛选',
   '这条题目 status=0，用于验证管理端禁用状态筛选，不应出现在用户端题库。',
   '管理端按 status=0 查询时可以看到该题；用户端 /questions 始终叠加启用状态，只返回 status=1 的题目。',
   '这是 V1 联调测试数据，不参与普通用户答题闭环。',
   100000, 100000, 'EASY', 0, 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  reference_answer = VALUES(reference_answer),
  analysis = VALUES(analysis),
  category_id = VALUES(category_id),
  group_id = VALUES(group_id),
  difficulty = VALUES(difficulty),
  status = VALUES(status),
  deleted = 0;

INSERT INTO question_tag_relation (id, question_id, tag_id, deleted)
VALUES
  (100000, 100000, 100000, 0),
  (100001, 100001, 100000, 0),
  (100002, 100002, 100001, 0),
  (100003, 100003, 100001, 0),
  (100004, 100004, 100002, 0),
  (100005, 100005, 100002, 0),
  (100006, 100006, 100003, 0),
  (100007, 100007, 100003, 0),
  (100008, 100008, 100004, 0),
  (100009, 100009, 100004, 0),
  (100010, 100008, 100002, 0),
  (100011, 100009, 100003, 0),
  (100012, 100010, 100000, 0)
ON DUPLICATE KEY UPDATE
  question_id = VALUES(question_id),
  tag_id = VALUES(tag_id),
  deleted = 0;

INSERT INTO user_question_record (id, user_id, question_id, answer_content, mastery_status, wrong, favorite, last_answer_at, deleted)
VALUES
  (100000, @E2E_USER_ID, 100000, 'HashMap 没有同步控制，并发 put 可能导致数据覆盖和扩容期间结构异常，生产中应使用 ConcurrentHashMap。', 'MASTERED', 0, 1, NOW() - INTERVAL 3 DAY, 0),
  (100001, @E2E_USER_ID, 100002, '联合索引要从最左列开始匹配，范围条件后面的列可能无法继续充分利用。', 'NOT_MASTERED', 1, 0, NOW() - INTERVAL 2 DAY, 0),
  (100002, @E2E_USER_ID, 100004, '缓存穿透用空值或布隆过滤器，击穿用互斥锁，雪崩要随机过期时间。', 'NOT_MASTERED', 1, 1, NOW() - INTERVAL 1 DAY, 0),
  (100003, @E2E_USER_ID, 100006, '事务失效常见于自调用、异常被捕获、非 public 方法和 rollbackFor 未配置。', 'MASTERED', 0, 0, NOW() - INTERVAL 6 HOUR, 0)
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  question_id = VALUES(question_id),
  answer_content = VALUES(answer_content),
  mastery_status = VALUES(mastery_status),
  wrong = VALUES(wrong),
  favorite = VALUES(favorite),
  last_answer_at = VALUES(last_answer_at),
  deleted = 0;

INSERT INTO resume (id, user_id, title, real_name, email, phone, summary, is_default, status, deleted)
VALUES
  (100000, @E2E_USER_ID, 'E2E_TEST_Java后端三年经验简历', 'E2E_TEST_张同学', 'e2e_user@codecoachai.local', '13910000001',
   '目标岗位：Java 后端开发。技术栈：Java、Spring Boot、MyBatis、MySQL、Redis、Spring Cloud Alibaba。工作经历：三年 Java 后端开发经验，参与过后台管理、电商订单和缓存优化相关项目。教育经历：本科，计算机相关专业。',
   1, 1, 0)
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  title = VALUES(title),
  real_name = VALUES(real_name),
  email = VALUES(email),
  phone = VALUES(phone),
  summary = VALUES(summary),
  is_default = VALUES(is_default),
  status = VALUES(status),
  deleted = 0;

INSERT INTO resume_project (id, resume_id, project_name, role, tech_stack, description, highlights, sort, deleted)
VALUES
  (100000, 100000, 'E2E_TEST_电商订单系统', 'Java 后端开发',
   'Spring Boot, MyBatis, MySQL, Redis',
   '项目时间：2023.03 - 2024.06。项目背景：面向电商业务的订单、库存和支付回调管理系统。本人负责订单创建、库存扣减、支付回调幂等和缓存设计。',
   '核心功能：订单状态流转、库存扣减、支付回调处理。难点：高并发下库存一致性和支付回调重复通知。优化：使用 Redis 缓存热点商品信息，优化订单查询性能。补充：用于 AI 项目深挖面试测试。',
   1, 0),
  (100001, 100000, 'E2E_TEST_面试题库训练平台', '后端负责人',
   'Spring Cloud Alibaba, Gateway, Nacos, MySQL, Redis',
   '项目时间：2024.07 - 2025.03。项目背景：面向 Java 求职者的题库和模拟面试系统。本人负责题库管理、问题组、面试状态机和 AI 调用日志。',
   '核心功能：题库 CRUD、问题组归并、AI 面试流程编排。难点：面试流程状态控制和动态追问次数限制。优化：通过问题组避免同一场面试重复问同类问题。补充：用于综合模拟面试测试。',
   2, 0)
ON DUPLICATE KEY UPDATE
  resume_id = VALUES(resume_id),
  project_name = VALUES(project_name),
  role = VALUES(role),
  tech_stack = VALUES(tech_stack),
  description = VALUES(description),
  highlights = VALUES(highlights),
  sort = VALUES(sort),
  deleted = 0;

INSERT INTO prompt_template (id, scene, name, content, status, deleted)
VALUES
  (100000, 'INTERVIEW_QUESTION_GENERATE', 'E2E_TEST_八股文提问模板', '请根据面试阶段、候选人经验和题库题目，生成一个清晰的 Java 八股文面试问题。输出包含 questionText 和 scene。', 1, 0),
  (100001, 'PROJECT_DEEP_DIVE_GENERATE', 'E2E_TEST_项目深挖提问模板', '请结合候选人简历项目，围绕项目背景、技术选型、难点和优化结果生成项目深挖问题。', 1, 0),
  (100002, 'INTERVIEW_ANSWER_EVALUATE', 'E2E_TEST_回答评分模板', '请根据题目、参考答案和候选人回答，输出 score、comment 和 nextAction。nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE、FINISH。', 1, 0),
  (100003, 'INTERVIEW_FOLLOW_UP_GENERATE', 'E2E_TEST_动态追问模板', '当候选人回答不够深入时，请生成一个更具体的追问，避免重复原问题。', 1, 0),
  (100004, 'INTERVIEW_REPORT_GENERATE', 'E2E_TEST_面试报告生成模板', '请根据整场面试消息生成结构化中文报告，包含总分来源、回答亮点、主要问题、薄弱知识点和复习建议。', 1, 0),
  (100005, 'INTERVIEW_REPORT_GENERATE', 'E2E_TEST_停用报告模板样例', '该模板仅用于管理端 status=0 筛选验证，不参与实际报告生成。', 0, 0)
ON DUPLICATE KEY UPDATE
  scene = VALUES(scene),
  name = VALUES(name),
  content = VALUES(content),
  status = VALUES(status),
  deleted = 0;

INSERT INTO ai_call_log (id, scene, request_body, response_body, cost_millis, status, error_message, deleted)
VALUES
  (100000, 'INTERVIEW_QUESTION_GENERATE',
   CONCAT('{"userId":', @E2E_USER_ID, ',"sessionId":100000,"stage":"Java基础","prompt":"请围绕 HashMap 线程安全生成问题"}'),
   '{"status":"SUCCESS","questionText":"请说明 HashMap 为什么线程不安全，并说出生产替代方案。"}',
   42, 1, NULL, 0),
  (100001, 'INTERVIEW_ANSWER_EVALUATE',
   CONCAT('{"userId":', @E2E_USER_ID, ',"sessionId":100000,"answer":"HashMap 并发 put 不安全"}'),
   '{"status":"SUCCESS","score":82,"comment":"回答覆盖核心点，建议补充扩容和可见性问题。","nextAction":"FOLLOW_UP"}',
   55, 1, NULL, 0)
ON DUPLICATE KEY UPDATE
  scene = VALUES(scene),
  request_body = VALUES(request_body),
  response_body = VALUES(response_body),
  cost_millis = VALUES(cost_millis),
  status = VALUES(status),
  error_message = VALUES(error_message),
  deleted = 0;

INSERT INTO interview_session (id, user_id, resume_id, mode, title, target_position, experience_level, industry_direction, difficulty, interviewer_style, based_on_resume, status, report_status, current_stage_id, current_question_id, current_question_group_id, answered_question_count, max_question_count, current_follow_up_count, failure_reason, deleted)
VALUES
  (100000, @E2E_USER_ID, 100000, 'COMPREHENSIVE', 'E2E_TEST_综合模拟面试', 'Java 后端开发', '3 年', '电商', 'MEDIUM', '普通', 1, 'COMPLETED', 'GENERATED', 100003, 100008, 100004, 4, 5, 0, NULL, 0)
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  resume_id = VALUES(resume_id),
  mode = VALUES(mode),
  title = VALUES(title),
  target_position = VALUES(target_position),
  experience_level = VALUES(experience_level),
  industry_direction = VALUES(industry_direction),
  difficulty = VALUES(difficulty),
  interviewer_style = VALUES(interviewer_style),
  based_on_resume = VALUES(based_on_resume),
  status = VALUES(status),
  report_status = VALUES(report_status),
  current_stage_id = VALUES(current_stage_id),
  current_question_id = VALUES(current_question_id),
  current_question_group_id = VALUES(current_question_group_id),
  answered_question_count = VALUES(answered_question_count),
  max_question_count = VALUES(max_question_count),
  current_follow_up_count = VALUES(current_follow_up_count),
  failure_reason = VALUES(failure_reason),
  deleted = 0;

INSERT INTO interview_stage (id, session_id, stage_type, stage_name, sort, status, deleted)
VALUES
  (100000, 100000, 'JAVA_BASIC', 'E2E_TEST_Java 基础', 1, 'COMPLETED', 0),
  (100001, 100000, 'MYSQL', 'E2E_TEST_MySQL', 2, 'COMPLETED', 0),
  (100002, 100000, 'REDIS', 'E2E_TEST_Redis', 3, 'COMPLETED', 0),
  (100003, 100000, 'PROJECT_DEEP_DIVE', 'E2E_TEST_项目深挖', 4, 'COMPLETED', 0)
ON DUPLICATE KEY UPDATE
  session_id = VALUES(session_id),
  stage_type = VALUES(stage_type),
  stage_name = VALUES(stage_name),
  sort = VALUES(sort),
  status = VALUES(status),
  deleted = 0;

INSERT INTO interview_message (id, session_id, stage_id, question_id, question_group_id, role, message_type, content, score, comment, deleted)
VALUES
  (100000, 100000, 100000, 100000, 100000, 'AI', 'QUESTION', 'E2E_TEST_AI问题：请说明 HashMap 为什么线程不安全，并说出生产替代方案。', NULL, NULL, 0),
  (100001, 100000, 100000, 100000, 100000, 'USER', 'ANSWER', 'E2E_TEST_用户回答：HashMap 没有同步控制，并发 put 可能覆盖数据，生产中会使用 ConcurrentHashMap。', NULL, NULL, 0),
  (100002, 100000, 100000, 100000, 100000, 'AI', 'EVALUATION', 'E2E_TEST_AI点评：回答覆盖核心点，但可以补充扩容和可见性问题。', 82, '基础扎实，建议补充 JDK8 扩容细节。', 0),
  (100003, 100000, 100000, 100000, 100000, 'AI', 'FOLLOW_UP', 'E2E_TEST_AI追问：ConcurrentHashMap 在 JDK8 中如何降低锁粒度？', NULL, NULL, 0),
  (100004, 100000, 100001, 100002, 100001, 'AI', 'QUESTION', 'E2E_TEST_AI问题：MySQL 联合索引为什么遵循最左前缀原则？', NULL, NULL, 0),
  (100005, 100000, 100001, 100002, 100001, 'USER', 'ANSWER', 'E2E_TEST_用户回答：联合索引按列顺序构建 B+Tree，需要从最左列连续匹配，范围查询后续列可能受影响。', NULL, NULL, 0),
  (100006, 100000, 100001, 100002, 100001, 'AI', 'EVALUATION', 'E2E_TEST_AI点评：回答较完整，可以继续结合 EXPLAIN 验证索引命中。', 84, '索引理解较好，建议补充执行计划字段。', 0),
  (100007, 100000, 100002, 100004, 100002, 'AI', 'QUESTION', 'E2E_TEST_AI问题：Redis 缓存穿透、击穿、雪崩分别如何治理？', NULL, NULL, 0),
  (100008, 100000, 100002, 100004, 100002, 'USER', 'ANSWER', 'E2E_TEST_用户回答：穿透用空值或布隆过滤器，击穿用互斥锁，雪崩用随机过期和限流降级。', NULL, NULL, 0),
  (100009, 100000, 100002, 100004, 100002, 'AI', 'EVALUATION', 'E2E_TEST_AI点评：能区分三类问题，建议结合项目里的热点商品缓存说明。', 80, '方案清晰，项目结合可以更强。', 0),
  (100010, 100000, 100003, 100008, 100004, 'AI', 'QUESTION', 'E2E_TEST_AI问题：在电商订单系统中，你如何设计热点商品缓存？', NULL, NULL, 0),
  (100011, 100000, 100003, 100008, 100004, 'USER', 'ANSWER', 'E2E_TEST_用户回答：商品核心信息放 Redis，设置随机 TTL，更新时先写库再删缓存，失败走重试补偿。', NULL, NULL, 0),
  (100012, 100000, 100003, 100008, 100004, 'AI', 'EVALUATION', 'E2E_TEST_AI点评：项目方案完整，能说明一致性和降级，但还可以补充热点预热策略。', 82, '项目表达清晰，建议补充量化指标。', 0)
ON DUPLICATE KEY UPDATE
  session_id = VALUES(session_id),
  stage_id = VALUES(stage_id),
  question_id = VALUES(question_id),
  question_group_id = VALUES(question_group_id),
  role = VALUES(role),
  message_type = VALUES(message_type),
  content = VALUES(content),
  score = VALUES(score),
  comment = VALUES(comment),
  deleted = 0;

INSERT INTO interview_report (id, session_id, status, total_score, summary, strengths, weaknesses, suggestions, failure_reason, deleted)
VALUES
  (100000, 100000, 'GENERATED', 82,
   '## E2E_TEST_面试总结\n本场综合模拟面试总分 82。总分来源：Java 基础 82、MySQL 84、Redis 80、项目深挖 82，按四个已完成环节取平均后得到 82。候选人具备三年 Java 后端开发所需的基础能力，能够围绕集合、MySQL、Redis 和项目缓存设计进行表达。',
   '回答亮点：1. 能准确说明 HashMap 并发写风险，并给出 ConcurrentHashMap 作为生产替代方案；2. 能解释联合索引最左前缀和范围查询影响；3. 能区分缓存穿透、击穿、雪崩，并给出工程治理方案；4. 项目深挖中能把 Redis 缓存和先写库再删缓存的策略串联起来。',
   '主要问题：1. HashMap 扩容过程和 JDK8 ConcurrentHashMap 锁粒度说明不够细；2. MySQL 优化缺少 EXPLAIN type、key、rows、Extra 等字段解释；3. Redis 热点缓存缺少预热、降级指标和压测验证描述；4. 项目表达需要补充量化结果，例如接口耗时、缓存命中率和慢 SQL 优化前后对比。',
   '复习建议：1. 复盘 HashMap 与 ConcurrentHashMap 的 put、扩容和并发控制流程；2. 准备 3 条 MySQL 慢 SQL 优化案例，并能读懂 EXPLAIN 常见字段；3. 将电商订单系统的热点缓存优化整理成 STAR 案例，补充指标、风险和兜底方案；4. 面试回答时先给结论，再补原理、项目实践和边界条件。',
   NULL, 0)
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  total_score = VALUES(total_score),
  summary = VALUES(summary),
  strengths = VALUES(strengths),
  weaknesses = VALUES(weaknesses),
  suggestions = VALUES(suggestions),
  failure_reason = VALUES(failure_reason),
  deleted = 0;

INSERT INTO system_config (id, config_key, config_value, value_type, description, status, deleted)
VALUES
  (100000, 'interview.max-question-count', '5', 'INTEGER', 'E2E_TEST_每场面试最大主问题数', 1, 0),
  (100001, 'interview.max-follow-up-count', '2', 'INTEGER', 'E2E_TEST_每个主问题最大追问次数', 1, 0),
  (100002, 'ai.timeout-seconds', '60', 'INTEGER', 'E2E_TEST_AI 调用超时时间', 1, 0),
  (100003, 'ai.provider', 'mock', 'STRING', 'E2E_TEST_本地联调 AI 提供方，当前使用 mock', 1, 0),
  (100004, 'report.generate-mode', 'sync', 'STRING', 'E2E_TEST_面试报告生成模式', 1, 0)
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  value_type = VALUES(value_type),
  description = VALUES(description),
  status = VALUES(status),
  deleted = 0;
