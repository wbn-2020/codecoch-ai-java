-- V3_016: Add runtime connection fields for admin AI model governance.

SET @schema_name = DATABASE();

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'ai_model_config')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'api_base_url'),
    'ALTER TABLE ai_model_config ADD COLUMN api_base_url VARCHAR(512) NULL COMMENT ''OpenAI-compatible API base URL'' AFTER capability_tags',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'ai_model_config')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'api_key'),
    'ALTER TABLE ai_model_config ADD COLUMN api_key VARCHAR(1024) NULL COMMENT ''Encrypted or private API key'' AFTER api_base_url',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'ai_model_config')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'temperature'),
    'ALTER TABLE ai_model_config ADD COLUMN temperature DECIMAL(4,2) NULL COMMENT ''Default generation temperature'' AFTER api_key',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'ai_model_config')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_model_config' AND column_name = 'max_tokens'),
    'ALTER TABLE ai_model_config ADD COLUMN max_tokens INT NULL COMMENT ''Default max tokens'' AFTER temperature',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
