-- V4_131: JD 关键词 → 知识点/题目 的规则映射表（冷启动推荐地基）。
-- 用途：新用户零数据（无能力画像/匹配报告/学习计划）时，用 JD 文本命中关键词，
--       再按 category_id / group_id 直接从正式题库取题，不依赖 AI、不依赖历史批次。
-- 幂等：CREATE TABLE IF NOT EXISTS + 按 keyword 唯一键 ON DUPLICATE KEY UPDATE。

CREATE TABLE IF NOT EXISTS `question_knowledge_keyword` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `keyword` VARCHAR(64) NOT NULL COMMENT 'JD 关键词（匹配时两侧统一小写）',
  `category_id` BIGINT DEFAULT NULL COMMENT '命中的题目分类',
  `group_id` BIGINT DEFAULT NULL COMMENT '命中的知识点组（question_group）',
  `knowledge_point` VARCHAR(128) DEFAULT NULL COMMENT '知识点名称（用于推荐理由）',
  `weight` INT NOT NULL DEFAULT 1 COMMENT '权重，越大越优先',
  `status` TINYINT NOT NULL DEFAULT 1,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qkk_keyword` (`keyword`),
  KEY `idx_qkk_category` (`category_id`),
  KEY `idx_qkk_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JD 关键词到知识点的规则映射（冷启动推荐）';

INSERT INTO question_knowledge_keyword (id, keyword, category_id, group_id, knowledge_point, weight, status)
VALUES
  -- 集合（category_id=2，group_id 对应 V4_129 建的知识点组）
  (1, 'hashmap',            2, 1001, 'HashMap 冲突与扩容', 5, 1),
  (2, 'concurrenthashmap',  2, 1002, 'ConcurrentHashMap 线程安全', 5, 1),
  (3, 'arraylist',          2, 1003, 'List 实现与扩容', 3, 1),
  (4, 'linkedlist',         2, 1003, 'List 实现与扩容', 3, 1),
  (5, 'hashset',            2, 1005, 'Set 去重与有序', 3, 1),
  (6, 'treeset',            2, 1005, 'Set 去重与有序', 2, 1),
  (7, 'iterator',           2, 1004, '迭代器快速失败', 2, 1),
  (8, '迭代器',              2, 1004, '迭代器快速失败', 2, 1),
  (9, 'blockingqueue',      2, 1006, '队列与阻塞队列', 3, 1),
  (10, '阻塞队列',           2, 1006, '队列与阻塞队列', 3, 1),
  (11, '集合',               2, NULL, 'Java 集合框架', 3, 1),

  -- 并发（category_id=3，线程池沿用 init.sql 既有 group_id=3）
  (12, '线程池',             3, 3, '线程池参数', 5, 1),
  (13, 'threadpool',        3, 3, '线程池参数', 5, 1),
  (14, '并发',               3, NULL, 'Java 并发', 4, 1),
  (15, '多线程',             3, NULL, 'Java 并发', 4, 1),
  (16, 'concurrency',       3, NULL, 'Java 并发', 4, 1),
  (17, 'synchronized',      3, NULL, '锁机制', 4, 1),
  (18, 'reentrantlock',     3, NULL, '锁机制', 4, 1),
  (19, 'aqs',               3, NULL, 'AQS 与锁', 4, 1),
  (20, 'volatile',          3, NULL, '内存可见性', 4, 1),
  (21, 'cas',               3, NULL, 'CAS 与原子类', 3, 1),
  (22, 'threadlocal',       3, NULL, 'ThreadLocal', 3, 1),
  (23, '死锁',               3, NULL, '死锁排查', 3, 1),

  -- JVM（category_id=4，GC 沿用既有 group_id=2）
  (24, 'jvm',               4, 2, 'JVM GC 排查', 5, 1),
  (25, 'gc',                4, 2, 'JVM GC 排查', 4, 1),
  (26, '垃圾回收',           4, 2, 'JVM GC 排查', 4, 1),
  (27, 'oom',               4, NULL, 'JVM 内存排查', 3, 1),

  -- MySQL / Redis / Spring（沿用既有分类与组）
  (28, 'mysql',             6, 4, 'MySQL 索引与 EXPLAIN', 5, 1),
  (29, '索引',               6, 4, 'MySQL 索引与 EXPLAIN', 4, 1),
  (30, 'index',             6, 4, 'MySQL 索引与 EXPLAIN', 3, 1),
  (31, 'redis',             7, 5, 'Redis 缓存一致性', 5, 1),
  (32, '缓存',               7, 5, 'Redis 缓存一致性', 4, 1),
  (33, 'cache',             7, 5, 'Redis 缓存一致性', 3, 1),
  (34, 'spring',            5, NULL, 'Spring 框架', 4, 1),
  (35, 'springboot',        5, NULL, 'Spring Boot', 4, 1),
  (36, '事务',               6, NULL, '事务与一致性', 3, 1),

  -- 微服务 / 分布式 / 消息队列 / 设计模式
  (37, '微服务',             8, NULL, '微服务与分布式', 3, 1),
  (38, 'microservice',      8, NULL, '微服务与分布式', 3, 1),
  (39, '消息队列',           8, NULL, '消息队列', 3, 1),
  (40, '设计模式',           9, NULL, '设计模式', 3, 1)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id), group_id = VALUES(group_id),
  knowledge_point = VALUES(knowledge_point), weight = VALUES(weight), status = VALUES(status);
