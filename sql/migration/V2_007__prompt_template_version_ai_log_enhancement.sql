DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_not_exists//
CREATE PROCEDURE add_column_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT,
  IN after_column_value VARCHAR(64)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
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

DROP PROCEDURE IF EXISTS add_index_if_not_exists//
CREATE PROCEDURE add_index_if_not_exists(
  IN table_name_value VARCHAR(64),
  IN index_name_value VARCHAR(64),
  IN index_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
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

CALL add_column_if_not_exists('prompt_template', 'description', 'VARCHAR(500) DEFAULT NULL', 'template_name');
ALTER TABLE prompt_template
  MODIFY COLUMN content LONGTEXT NOT NULL,
  MODIFY COLUMN template_content LONGTEXT;
CALL add_column_if_not_exists('prompt_template', 'active_version_id', 'BIGINT DEFAULT NULL', 'version');
CALL add_column_if_not_exists('prompt_template', 'enabled', 'TINYINT NOT NULL DEFAULT 1', 'active_version_id');

CREATE TABLE IF NOT EXISTS prompt_template_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) NOT NULL,
  version_code VARCHAR(64) NOT NULL,
  version_name VARCHAR(128) DEFAULT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT DEFAULT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  is_active TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT DEFAULT NULL,
  activated_by BIGINT DEFAULT NULL,
  activated_at DATETIME DEFAULT NULL,
  change_log VARCHAR(1000) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prompt_template_version (template_id, version_code),
  KEY idx_prompt_version_scene (scene, status, is_active),
  KEY idx_prompt_version_template (template_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt template version history';

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       COALESCE(NULLIF(p.version, ''), 'v1'),
       p.name,
       COALESCE(NULLIF(p.template_content, ''), p.content),
       p.variables,
       'ACTIVE',
       1,
       NOW(),
       'Migrated from prompt_template'
FROM prompt_template p
WHERE p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = COALESCE(NULLIF(p.version, ''), 'v1')
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.enabled = p.status
WHERE p.deleted = 0
  AND p.active_version_id IS NULL;

CALL add_column_if_not_exists('ai_call_log', 'prompt_template_version_id', 'BIGINT DEFAULT NULL', 'prompt_template_id');
CALL add_column_if_not_exists('ai_call_log', 'prompt_version', 'VARCHAR(64) DEFAULT NULL', 'prompt_template_version_id');
CALL add_column_if_not_exists('ai_call_log', 'request_id', 'VARCHAR(64) DEFAULT NULL', 'prompt_version');
CALL add_column_if_not_exists('ai_call_log', 'trace_id', 'VARCHAR(128) DEFAULT NULL', 'request_id');
CALL add_column_if_not_exists('ai_call_log', 'input_variables_json', 'LONGTEXT DEFAULT NULL', 'trace_id');
CALL add_column_if_not_exists('ai_call_log', 'model_params_json', 'LONGTEXT DEFAULT NULL', 'input_variables_json');
CALL add_column_if_not_exists('ai_call_log', 'prompt_hash', 'VARCHAR(64) DEFAULT NULL', 'model_params_json');
CALL add_column_if_not_exists('ai_call_log', 'response_format', 'VARCHAR(64) DEFAULT NULL', 'prompt_hash');
ALTER TABLE ai_call_log
  MODIFY COLUMN request_prompt LONGTEXT,
  MODIFY COLUMN request_body LONGTEXT,
  MODIFY COLUMN response_body LONGTEXT;
CALL add_column_if_not_exists('ai_call_log', 'success', 'TINYINT DEFAULT NULL', 'cost_millis');
CALL add_column_if_not_exists('ai_call_log', 'prompt_tokens', 'INT DEFAULT NULL', 'success');
CALL add_column_if_not_exists('ai_call_log', 'completion_tokens', 'INT DEFAULT NULL', 'prompt_tokens');
CALL add_column_if_not_exists('ai_call_log', 'total_tokens', 'INT DEFAULT NULL', 'completion_tokens');
CALL add_index_if_not_exists('ai_call_log', 'idx_ai_call_business', 'KEY idx_ai_call_business (business_id)');
CALL add_index_if_not_exists('ai_call_log', 'idx_ai_call_prompt_version', 'KEY idx_ai_call_prompt_version (prompt_template_version_id)');
CALL add_index_if_not_exists('ai_call_log', 'idx_ai_call_trace', 'KEY idx_ai_call_trace (trace_id)');
CALL add_index_if_not_exists('ai_call_log', 'idx_ai_call_created', 'KEY idx_ai_call_created (created_at)');

UPDATE ai_call_log
SET success = status
WHERE success IS NULL;

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
