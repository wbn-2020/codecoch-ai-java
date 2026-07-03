CREATE TABLE IF NOT EXISTS `ability_skill_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `code` VARCHAR(64) NOT NULL COMMENT 'skill code',
    `name` VARCHAR(128) NOT NULL COMMENT 'skill display name',
    `domain_code` VARCHAR(64) NOT NULL COMMENT 'domain code',
    `domain_name` VARCHAR(128) NOT NULL COMMENT 'domain display name',
    `description` VARCHAR(512) DEFAULT NULL COMMENT 'skill description',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'sort order',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT 'enabled flag',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete flag',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ability_skill_node_code` (`code`),
    KEY `idx_ability_skill_node_domain` (`domain_code`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Java backend ability skill nodes';

CREATE TABLE IF NOT EXISTS `user_ability_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `user_id` BIGINT NOT NULL COMMENT 'owner user id',
    `skill_code` VARCHAR(64) NOT NULL COMMENT 'skill code',
    `status` VARCHAR(32) NOT NULL DEFAULT 'UNASSESSED' COMMENT 'UNASSESSED/WEAK/BASIC/COMPETENT/STRONG',
    `evidence_count` INT NOT NULL DEFAULT 0 COMMENT 'linked evidence count',
    `last_evaluated_at` DATETIME DEFAULT NULL COMMENT 'latest evaluation time',
    `confidence` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/LOW/MEDIUM/HIGH',
    `summary` VARCHAR(512) DEFAULT NULL COMMENT 'short profile summary',
    `source_type` VARCHAR(32) DEFAULT NULL COMMENT 'source type',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete flag',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_ability_profile_user_skill` (`user_id`, `skill_code`),
    KEY `idx_user_ability_profile_user` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='basic user ability profile overlay';

INSERT INTO `ability_skill_node` (`code`, `name`, `domain_code`, `domain_name`, `description`, `sort_order`, `enabled`)
VALUES
('JAVA_CORE', 'Java 基础', 'JAVA_CORE', 'Java 基础', '语法、面向对象、异常、泛型、IO 与常用 JDK 能力。', 10, 1),
('COLLECTION_HASHMAP', '集合', 'COLLECTION', '集合', 'List、Map、HashMap、ConcurrentHashMap 与集合选型。', 20, 1),
('JUC_THREAD_POOL', '并发', 'CONCURRENCY', '并发', '线程、锁、线程池、AQS、并发容器与可见性。', 30, 1),
('JVM_MEMORY_GC', 'JVM', 'JVM', 'JVM', '内存模型、类加载、GC、调优与故障排查。', 40, 1),
('MYSQL_INDEX_TX', 'MySQL', 'MYSQL', 'MySQL', '索引、事务、锁、执行计划与 SQL 优化。', 50, 1),
('REDIS_CACHE', 'Redis', 'REDIS', 'Redis', '缓存设计、数据结构、持久化、分布式锁与高可用。', 60, 1),
('SPRING_BOOT', 'Spring / Spring Boot', 'SPRING', 'Spring / Spring Boot', 'IoC、AOP、事务、自动配置与 Web 开发。', 70, 1),
('MYBATIS_ORM', 'MyBatis', 'MYBATIS', 'MyBatis', 'Mapper、动态 SQL、分页、缓存与常见坑。', 80, 1),
('MICROSERVICE', '微服务', 'MICROSERVICE', '微服务', '服务拆分、注册发现、配置、网关、限流与熔断。', 90, 1),
('MESSAGE_QUEUE', '消息队列', 'MESSAGE_QUEUE', '消息队列', '异步解耦、可靠消息、顺序、幂等与积压治理。', 100, 1),
('DISTRIBUTED_SYSTEM', '分布式', 'DISTRIBUTED', '分布式', '一致性、分布式事务、分布式锁、CAP 与高可用。', 110, 1),
('SYSTEM_DESIGN', '系统设计', 'SYSTEM_DESIGN', '系统设计', '架构分层、容量估算、扩展性、可用性与取舍表达。', 120, 1),
('PROJECT_EXPRESSION', '项目表达', 'PROJECT_EXPRESSION', '项目表达', '项目背景、职责、难点、方案、结果与复盘表达。', 130, 1),
('ENGINEERING_PRACTICE', '工程实践', 'ENGINEERING', '工程实践', '测试、日志、监控、发布、代码质量与协作规范。', 140, 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `domain_code` = VALUES(`domain_code`),
    `domain_name` = VALUES(`domain_name`),
    `description` = VALUES(`description`),
    `sort_order` = VALUES(`sort_order`),
    `enabled` = VALUES(`enabled`);

SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'training_scene'),
    'ALTER TABLE `interview_session` ADD COLUMN `training_scene` VARCHAR(32) DEFAULT NULL COMMENT ''training scene'' AFTER `report_status`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'target_skill_domain'),
    'ALTER TABLE `interview_session` ADD COLUMN `target_skill_domain` VARCHAR(64) DEFAULT NULL COMMENT ''target skill domain'' AFTER `training_scene`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'target_skill_codes'),
    'ALTER TABLE `interview_session` ADD COLUMN `target_skill_codes` VARCHAR(1024) DEFAULT NULL COMMENT ''target skill code json'' AFTER `target_skill_domain`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'target_level'),
    'ALTER TABLE `interview_session` ADD COLUMN `target_level` VARCHAR(32) DEFAULT NULL COMMENT ''target level'' AFTER `target_skill_codes`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'project_evidence_ids'),
    'ALTER TABLE `interview_session` ADD COLUMN `project_evidence_ids` VARCHAR(1024) DEFAULT NULL COMMENT ''project evidence id json'' AFTER `target_level`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'follow_up_intensity'),
    'ALTER TABLE `interview_session` ADD COLUMN `follow_up_intensity` VARCHAR(32) DEFAULT NULL COMMENT ''follow-up intensity'' AFTER `project_evidence_ids`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'training_context_summary'),
    'ALTER TABLE `interview_session` ADD COLUMN `training_context_summary` TEXT DEFAULT NULL COMMENT ''sanitized training context summary'' AFTER `follow_up_intensity`',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
