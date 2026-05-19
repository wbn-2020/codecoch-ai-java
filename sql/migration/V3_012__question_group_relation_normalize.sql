-- ============================================================
-- V3_012: 题库增强 - 问题组/题目关系/归一化标题/审核状态/批量导入
-- 说明：使用 information_schema helper 保证 MySQL 8 重复执行安全。
-- ============================================================

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

DELIMITER //

CREATE PROCEDURE create_table_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN table_definition_value LONGTEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = table_name_value
  ) THEN
    SET @create_sql = table_definition_value;
    PREPARE create_stmt FROM @create_sql;
    EXECUTE create_stmt;
    DEALLOCATE PREPARE create_stmt;
  END IF;
END//

CREATE PROCEDURE add_column_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT,
  IN after_column_value VARCHAR(64)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @alter_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ',
      column_definition_value,
      IF(after_column_value IS NULL OR after_column_value = '', '', CONCAT(' AFTER `', after_column_value, '`'))
    );
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

CREATE PROCEDURE add_index_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN index_name_value VARCHAR(64),
  IN index_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND index_name = index_name_value
  ) THEN
    SET @alter_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END//

DELIMITER ;

CALL add_column_if_not_exists('question', 'normalized_title',
  'VARCHAR(512) DEFAULT NULL COMMENT ''归一化标题（去重用）''', 'title');
CALL add_column_if_not_exists('question', 'audit_status',
  'VARCHAR(16) NOT NULL DEFAULT ''APPROVED'' COMMENT ''PENDING/APPROVED/REJECTED''', 'status');
CALL add_column_if_not_exists('question', 'source_type',
  'VARCHAR(16) NOT NULL DEFAULT ''MANUAL'' COMMENT ''MANUAL/AI_GENERATED/IMPORT''', 'audit_status');
CALL add_column_if_not_exists('question', 'is_recommended',
  'TINYINT NOT NULL DEFAULT 0 COMMENT ''是否推荐''', 'is_high_frequency');
CALL add_index_if_not_exists('question', 'idx_question_normalized_title',
  'INDEX `idx_question_normalized_title` (`normalized_title`(128))');
CALL add_index_if_not_exists('question', 'idx_question_audit_status',
  'INDEX `idx_question_audit_status` (`audit_status`)');

CALL create_table_if_not_exists('question_group',
  'CREATE TABLE `question_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `group_name` VARCHAR(128) DEFAULT NULL COMMENT ''问题组名称'',
    `canonical_title` VARCHAR(512) NOT NULL COMMENT ''标准问题标题'',
    `canonical_answer` TEXT DEFAULT NULL COMMENT ''标准答案'',
    `main_knowledge_point` VARCHAR(128) DEFAULT NULL COMMENT ''主知识点'',
    `difficulty` VARCHAR(16) DEFAULT NULL,
    `description` VARCHAR(512) DEFAULT NULL COMMENT ''考察意图说明'',
    `category_id` BIGINT DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_qg_category` (`category_id`),
    KEY `idx_qg_knowledge` (`main_knowledge_point`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''问题组（归并同义题）'''
);

CALL add_column_if_not_exists('question_group', 'canonical_title',
  'VARCHAR(512) NOT NULL COMMENT ''标准问题标题''', 'group_name');
CALL add_column_if_not_exists('question_group', 'canonical_answer',
  'TEXT DEFAULT NULL COMMENT ''标准答案''', 'canonical_title');
CALL add_column_if_not_exists('question_group', 'main_knowledge_point',
  'VARCHAR(128) DEFAULT NULL COMMENT ''主知识点''', 'canonical_answer');
CALL add_column_if_not_exists('question_group', 'difficulty',
  'VARCHAR(16) DEFAULT NULL', 'main_knowledge_point');
CALL add_column_if_not_exists('question_group', 'description',
  'VARCHAR(512) DEFAULT NULL COMMENT ''考察意图说明''', 'difficulty');
CALL add_column_if_not_exists('question_group', 'category_id',
  'BIGINT DEFAULT NULL', 'description');
CALL add_index_if_not_exists('question_group', 'idx_qg_category',
  'INDEX `idx_qg_category` (`category_id`)');
CALL add_index_if_not_exists('question_group', 'idx_qg_knowledge',
  'INDEX `idx_qg_knowledge` (`main_knowledge_point`)');

CALL create_table_if_not_exists('question_relation',
  'CREATE TABLE `question_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `source_question_id` BIGINT NOT NULL,
    `target_question_id` BIGINT NOT NULL,
    `relation_type` VARCHAR(32) NOT NULL COMMENT ''SAME_INTENT/FOLLOW_UP/RELATED/ADVANCED/PREREQUISITE/COMPARE'',
    `relation_status` VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''ACTIVE/INACTIVE'',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT ''关系说明'',
    `similarity_score` DECIMAL(5,4) DEFAULT NULL COMMENT ''相似度分数 0~1'',
    `created_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''题目关系'''
);

CALL add_column_if_not_exists('question_relation', 'source_question_id', 'BIGINT NOT NULL', 'id');
CALL add_column_if_not_exists('question_relation', 'target_question_id', 'BIGINT NOT NULL', 'source_question_id');
CALL add_column_if_not_exists('question_relation', 'relation_type',
  'VARCHAR(32) NOT NULL COMMENT ''SAME_INTENT/FOLLOW_UP/RELATED/ADVANCED/PREREQUISITE/COMPARE''', 'target_question_id');
CALL add_column_if_not_exists('question_relation', 'relation_status',
  'VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''ACTIVE/INACTIVE''', 'relation_type');
CALL add_column_if_not_exists('question_relation', 'reason',
  'VARCHAR(512) DEFAULT NULL COMMENT ''关系说明''', 'relation_status');
CALL add_column_if_not_exists('question_relation', 'similarity_score',
  'DECIMAL(5,4) DEFAULT NULL COMMENT ''相似度分数 0~1''', 'reason');
CALL add_column_if_not_exists('question_relation', 'created_by', 'BIGINT DEFAULT NULL', 'similarity_score');
CALL add_index_if_not_exists('question_relation', 'idx_qr_source',
  'INDEX `idx_qr_source` (`source_question_id`)');
CALL add_index_if_not_exists('question_relation', 'idx_qr_target',
  'INDEX `idx_qr_target` (`target_question_id`)');
CALL add_index_if_not_exists('question_relation', 'idx_qr_type',
  'INDEX `idx_qr_type` (`relation_type`)');
-- question_duplicate_review 已由 V2_005 创建并被当前实体使用。
-- V3_012 不再重定义该表，避免 source/target 字段体系与 question/candidate 字段体系分叉。

CALL create_table_if_not_exists('question_import_batch',
  'CREATE TABLE `question_import_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `file_name` VARCHAR(255) NOT NULL COMMENT ''导入文件名'',
    `file_type` VARCHAR(16) NOT NULL COMMENT ''EXCEL/MD/DOCX/PDF'',
    `file_id` BIGINT DEFAULT NULL COMMENT ''关联 file_info.id'',
    `total_count` INT NOT NULL DEFAULT 0 COMMENT ''总题目数'',
    `success_count` INT NOT NULL DEFAULT 0,
    `fail_count` INT NOT NULL DEFAULT 0,
    `duplicate_count` INT NOT NULL DEFAULT 0 COMMENT ''疑似重复数'',
    `status` VARCHAR(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/PROCESSING/COMPLETED/FAILED'',
    `error_message` VARCHAR(2000) DEFAULT NULL,
    `imported_by` BIGINT NOT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_qib_status` (`status`),
    KEY `idx_qib_imported_by` (`imported_by`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''题目导入批次'''
);

DROP PROCEDURE IF EXISTS create_table_if_not_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
