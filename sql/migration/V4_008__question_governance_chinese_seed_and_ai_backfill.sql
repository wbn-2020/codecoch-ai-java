-- V4_008: question governance seed data cleanup and AI-generated question metadata backfill.
-- Idempotent: only renames known seed records and backfills clearly identifiable mock-generated questions.

UPDATE question_category
SET category_name = CASE category_name
  WHEN 'Java Basics' THEN 'Java基础'
  WHEN 'Collections' THEN '集合'
  WHEN 'Concurrency' THEN '并发'
  WHEN 'Microservices' THEN '微服务'
  WHEN 'Design Patterns' THEN '设计模式'
  WHEN 'Project Scenario' THEN '项目场景'
  ELSE category_name
END
WHERE category_name IN ('Java Basics', 'Collections', 'Concurrency', 'Microservices', 'Design Patterns', 'Project Scenario');

UPDATE question_tag
SET tag_name = CASE tag_name
  WHEN 'ThreadPool' THEN '线程池'
  WHEN 'MySQL Index' THEN 'MySQL索引'
  WHEN 'Redis Cache' THEN 'Redis缓存'
  WHEN 'Spring Transaction' THEN 'Spring事务'
  ELSE tag_name
END
WHERE tag_name IN ('ThreadPool', 'MySQL Index', 'Redis Cache', 'Spring Transaction');

UPDATE question_group
SET canonical_title = CASE group_name
    WHEN 'HashMap' THEN 'HashMap 的 put/get 和扩容流程是什么？'
    WHEN 'JVM GC' THEN '常见 Full GC 原因有哪些，如何排查？'
    WHEN 'ThreadPool' THEN '线程池核心参数应该如何配置？'
    WHEN 'MySQL Index' THEN 'MySQL 索引失效的常见场景有哪些？'
    WHEN 'Redis Cache Consistency' THEN 'Redis 缓存与数据库一致性如何保障？'
    ELSE canonical_title
  END,
  canonical_answer = CASE group_name
    WHEN 'HashMap' THEN 'HashMap 通过 hash 定位桶位，链表或红黑树解决冲突，超过阈值后扩容并迁移节点。'
    WHEN 'JVM GC' THEN '常见原因包括老年代压力、元空间压力、显式 System.gc、晋升失败等，排查时结合 GC 日志、堆 dump、分配速率、监控和变更记录。'
    WHEN 'ThreadPool' THEN '线程池参数需要结合 CPU/IO 类型、响应时间、队列容量、拒绝策略和监控指标综合设计。'
    WHEN 'MySQL Index' THEN '常见场景包括前置通配符、函数计算、隐式类型转换、不满足最左前缀等，需要用 EXPLAIN 验证。'
    WHEN 'Redis Cache Consistency' THEN '常见做法是先更新数据库再删除缓存，配合 TTL、重试、幂等和监控，复杂场景可引入补偿机制。'
    ELSE canonical_answer
  END,
  main_knowledge_point = CASE group_name
    WHEN 'HashMap' THEN 'HashMap 冲突与扩容'
    WHEN 'JVM GC' THEN 'JVM GC 排查'
    WHEN 'ThreadPool' THEN '线程池参数'
    WHEN 'MySQL Index' THEN 'MySQL 索引与 EXPLAIN'
    WHEN 'Redis Cache Consistency' THEN 'Redis 缓存一致性'
    ELSE main_knowledge_point
  END,
  description = CASE group_name
    WHEN 'HashMap' THEN '考察 HashMap 存储结构、扩容和冲突处理。'
    WHEN 'JVM GC' THEN '考察 JVM 内存模型、垃圾回收和线上排查。'
    WHEN 'ThreadPool' THEN '考察线程池参数、队列、拒绝策略和监控。'
    WHEN 'MySQL Index' THEN '考察索引使用、失效场景和 SQL 优化。'
    WHEN 'Redis Cache Consistency' THEN '考察缓存一致性、失败补偿和工程边界。'
    ELSE description
  END,
  group_name = CASE group_name
    WHEN 'ThreadPool' THEN '线程池'
    WHEN 'MySQL Index' THEN 'MySQL索引'
    WHEN 'Redis Cache Consistency' THEN 'Redis缓存一致性'
    ELSE group_name
  END
WHERE group_name IN ('HashMap', 'JVM GC', 'ThreadPool', 'MySQL Index', 'Redis Cache Consistency');

SET @concurrency_category_id := (
  SELECT id FROM question_category
  WHERE category_name IN ('并发', 'Concurrency')
  ORDER BY id ASC LIMIT 1
);

INSERT INTO question_group (group_name, canonical_title, canonical_answer, main_knowledge_point, difficulty, description, category_id, status)
SELECT '多线程基础',
       'Java 多线程的核心问题应该如何分析？',
       '多线程题目通常需要围绕线程安全、可见性、原子性、锁、线程池和排查手段展开，并结合具体业务场景说明取舍。',
       '多线程与 JUC',
       'MEDIUM',
       '考察 Java 并发基础、线程安全和 JUC 常见机制。',
       @concurrency_category_id,
       1
WHERE @concurrency_category_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM question_group WHERE group_name = '多线程基础' AND deleted = 0);

SET @thread_group_id := (
  SELECT id FROM question_group
  WHERE group_name IN ('多线程基础', '线程池', 'ThreadPool')
  ORDER BY FIELD(group_name, '多线程基础', '线程池', 'ThreadPool'), id ASC LIMIT 1
);

SET @thread_tag_id := (
  SELECT id FROM question_tag
  WHERE tag_name IN ('线程池', 'ThreadPool')
  ORDER BY FIELD(tag_name, '线程池', 'ThreadPool'), id ASC LIMIT 1
);

UPDATE question
SET category_id = COALESCE(category_id, @concurrency_category_id),
    group_id = COALESCE(group_id, @thread_group_id)
WHERE title LIKE '多线程相关 核心面试题%'
  AND (category_id IS NULL OR group_id IS NULL);

INSERT INTO question_tag_relation (question_id, tag_id)
SELECT q.id, @thread_tag_id
FROM question q
WHERE @thread_tag_id IS NOT NULL
  AND q.title LIKE '多线程相关 核心面试题%'
  AND NOT EXISTS (
    SELECT 1 FROM question_tag_relation r
    WHERE r.question_id = q.id
      AND r.tag_id = @thread_tag_id
      AND r.deleted = 0
  );

UPDATE industry_template
SET industry_name = CASE industry_code
    WHEN 'ECOMMERCE' THEN '电商'
    WHEN 'FINANCE_PAYMENT' THEN '金融支付'
    WHEN 'ONLINE_EDUCATION' THEN '在线教育'
    WHEN 'SAAS' THEN '企业SaaS'
    WHEN 'CONTENT_COMMUNITY' THEN '内容社区'
    WHEN 'ERP_LOGISTICS' THEN 'ERP物流'
    WHEN 'GENERAL_BACKEND' THEN '通用后端'
    ELSE industry_name
  END,
  description = CASE industry_code
    WHEN 'ECOMMERCE' THEN '面向商品、订单、库存、支付、营销和售后的后端业务场景。'
    WHEN 'FINANCE_PAYMENT' THEN '面向支付、账务、风控、对账和资金安全的后端业务场景。'
    WHEN 'ONLINE_EDUCATION' THEN '面向课程、排课、直播、作业和学习进度的后端业务场景。'
    WHEN 'SAAS' THEN '面向多租户、组织、权限、订阅计费和审计的企业服务场景。'
    WHEN 'CONTENT_COMMUNITY' THEN '面向内容生产、互动、审核、推荐和反作弊的社区业务场景。'
    WHEN 'ERP_LOGISTICS' THEN '面向库存、仓储、调度和订单流转的物流 ERP 场景。'
    WHEN 'GENERAL_BACKEND' THEN '面向管理后台和通用业务系统的后端场景。'
    ELSE description
  END
WHERE industry_code IN ('ECOMMERCE', 'FINANCE_PAYMENT', 'ONLINE_EDUCATION', 'SAAS', 'CONTENT_COMMUNITY', 'ERP_LOGISTICS', 'GENERAL_BACKEND');
