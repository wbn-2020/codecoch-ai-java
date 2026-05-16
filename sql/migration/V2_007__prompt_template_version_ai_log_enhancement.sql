ALTER TABLE prompt_template
  ADD COLUMN description VARCHAR(500) DEFAULT NULL AFTER template_name,
  MODIFY COLUMN content LONGTEXT NOT NULL,
  MODIFY COLUMN template_content LONGTEXT,
  ADD COLUMN active_version_id BIGINT DEFAULT NULL AFTER version,
  ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER active_version_id;

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

ALTER TABLE ai_call_log
  ADD COLUMN prompt_template_version_id BIGINT DEFAULT NULL AFTER prompt_template_id,
  ADD COLUMN prompt_version VARCHAR(64) DEFAULT NULL AFTER prompt_template_version_id,
  ADD COLUMN request_id VARCHAR(64) DEFAULT NULL AFTER prompt_version,
  ADD COLUMN trace_id VARCHAR(128) DEFAULT NULL AFTER request_id,
  ADD COLUMN input_variables_json LONGTEXT DEFAULT NULL AFTER trace_id,
  ADD COLUMN model_params_json LONGTEXT DEFAULT NULL AFTER input_variables_json,
  ADD COLUMN prompt_hash VARCHAR(64) DEFAULT NULL AFTER model_params_json,
  ADD COLUMN response_format VARCHAR(64) DEFAULT NULL AFTER prompt_hash,
  MODIFY COLUMN request_prompt LONGTEXT,
  MODIFY COLUMN request_body LONGTEXT,
  MODIFY COLUMN response_body LONGTEXT,
  ADD COLUMN success TINYINT DEFAULT NULL AFTER cost_millis,
  ADD COLUMN prompt_tokens INT DEFAULT NULL AFTER success,
  ADD COLUMN completion_tokens INT DEFAULT NULL AFTER prompt_tokens,
  ADD COLUMN total_tokens INT DEFAULT NULL AFTER completion_tokens,
  ADD KEY idx_ai_call_business (business_id),
  ADD KEY idx_ai_call_prompt_version (prompt_template_version_id),
  ADD KEY idx_ai_call_trace (trace_id),
  ADD KEY idx_ai_call_created (created_at);

UPDATE ai_call_log
SET success = status
WHERE success IS NULL;
