-- V3_015: V3 dashboard, interview context, admin governance, knowledge base and embedding foundation.

SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'target_job_id'),
    'ALTER TABLE interview_session ADD COLUMN target_job_id BIGINT NULL COMMENT ''V3 target job id'' AFTER resume_id',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'skill_profile_id'),
    'ALTER TABLE interview_session ADD COLUMN skill_profile_id BIGINT NULL COMMENT ''V3 skill profile id'' AFTER target_job_id',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'interview_session' AND column_name = 'match_report_id'),
    'ALTER TABLE interview_session ADD COLUMN match_report_id BIGINT NULL COMMENT ''V3 resume-job match report id'' AFTER skill_profile_id',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'interview_session')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'interview_session' AND index_name = 'idx_interview_v3_context'),
    'ALTER TABLE interview_session ADD INDEX idx_interview_v3_context(user_id, target_job_id, skill_profile_id, match_report_id)',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `ai_model_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `provider` VARCHAR(64) NOT NULL COMMENT 'AI provider',
    `model_code` VARCHAR(128) NOT NULL COMMENT 'Model code',
    `model_name` VARCHAR(128) NOT NULL COMMENT 'Display name',
    `capability_tags` VARCHAR(512) DEFAULT NULL COMMENT 'Capability tags',
    `default_model` TINYINT NOT NULL DEFAULT 0 COMMENT '1 default model in provider',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled',
    `sort_order` INT NOT NULL DEFAULT 100,
    `remark` VARCHAR(512) DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_model_provider_code` (`provider`, `model_code`, `deleted`),
    KEY `idx_ai_model_provider_enabled` (`provider`, `enabled`, `default_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI model governance config';

CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_id` BIGINT NOT NULL DEFAULT 0,
    `menu_name` VARCHAR(128) NOT NULL,
    `menu_type` VARCHAR(32) NOT NULL DEFAULT 'MENU',
    `path` VARCHAR(255) DEFAULT NULL,
    `component` VARCHAR(255) DEFAULT NULL,
    `permission_code` VARCHAR(128) DEFAULT NULL,
    `icon` VARCHAR(64) DEFAULT NULL,
    `sort_order` INT NOT NULL DEFAULT 100,
    `visible` TINYINT NOT NULL DEFAULT 1,
    `status` TINYINT NOT NULL DEFAULT 1,
    `remark` VARCHAR(512) DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sys_menu_parent` (`parent_id`, `sort_order`),
    KEY `idx_sys_menu_permission` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin menu and permission resource';

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`, `deleted`),
    KEY `idx_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Role menu relation';

CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `file_id` BIGINT DEFAULT NULL,
    `title` VARCHAR(255) NOT NULL,
    `source_type` VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    `status` VARCHAR(32) NOT NULL DEFAULT 'INDEXED',
    `content_text` MEDIUMTEXT DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_document_user` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Personal knowledge document';

CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `chunk_index` INT NOT NULL,
    `content_text` TEXT NOT NULL,
    `token_count` INT NOT NULL DEFAULT 0,
    `embedding_hash` CHAR(64) DEFAULT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_chunk_doc` (`document_id`, `chunk_index`),
    KEY `idx_knowledge_chunk_user` (`user_id`, `updated_at`),
    FULLTEXT KEY `ft_knowledge_chunk_content` (`content_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Personal knowledge document chunk';

CREATE TABLE IF NOT EXISTS `question_embedding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `question_id` BIGINT NOT NULL,
    `text_hash` CHAR(64) NOT NULL,
    `token_count` INT NOT NULL DEFAULT 0,
    `normalized_text` TEXT NOT NULL,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_embedding_question` (`question_id`),
    KEY `idx_question_embedding_hash` (`text_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Question semantic fingerprint';

INSERT INTO `ai_model_config` (`provider`, `model_code`, `model_name`, `capability_tags`, `default_model`, `enabled`, `sort_order`, `remark`)
SELECT 'OPENAI_COMPATIBLE', 'default-chat', 'Default Chat Model', 'chat,json,prompt-template', 1, 1, 100, 'Placeholder model config; real API keys remain in private config.'
WHERE NOT EXISTS (
    SELECT 1 FROM `ai_model_config` WHERE `provider` = 'OPENAI_COMPATIBLE' AND `model_code` = 'default-chat' AND `deleted` = 0
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT 0, 'V3 Governance', 'DIRECTORY', '/admin/v3', 'admin:v3', 10, 1, 1, 'V3 backend governance root'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:v3' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE((SELECT id FROM (SELECT id FROM `sys_menu` WHERE `permission_code` = 'admin:v3' AND `deleted` = 0 LIMIT 1) t), 0),
       'AI Models', 'MENU', '/admin/ai/models', 'admin:ai:model:list', 20, 1, 1, 'AI model governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:model:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE((SELECT id FROM (SELECT id FROM `sys_menu` WHERE `permission_code` = 'admin:v3' AND `deleted` = 0 LIMIT 1) t), 0),
       'Menus', 'MENU', '/admin/menus', 'admin:menu:list', 30, 1, 1, 'Menu permission governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:menu:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE((SELECT id FROM (SELECT id FROM `sys_menu` WHERE `permission_code` = 'admin:v3' AND `deleted` = 0 LIMIT 1) t), 0),
       'Interviews', 'MENU', '/admin/interviews', 'admin:interview:list', 40, 1, 1, 'Interview governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:interview:list' AND `deleted` = 0);
