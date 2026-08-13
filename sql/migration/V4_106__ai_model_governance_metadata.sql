-- Add non-destructive governance metadata for AI model records.
-- The physical uniqueness guard remains provider-scoped (V4_046/V4_053).
-- Runtime routing and admin operations enforce one active global default; ambiguous legacy rows are not selected.
-- This migration does not disable, delete, or change the default flag of any model.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name = 'governance_status'
    ),
    'ALTER TABLE `ai_model_config`
       ADD COLUMN `governance_status` VARCHAR(32) NOT NULL DEFAULT ''ACTIVE''
       COMMENT ''ACTIVE/PLACEHOLDER/REVIEW_REQUIRED/RETAINED''
       AFTER `remark`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name = 'governance_note'
    ),
    'ALTER TABLE `ai_model_config`
       ADD COLUMN `governance_note` VARCHAR(512) DEFAULT NULL
       COMMENT ''Operator-facing governance note; never stores credentials''
       AFTER `governance_status`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name = 'default_model'
    ),
    'ALTER TABLE `ai_model_config`
       MODIFY COLUMN `default_model` TINYINT NOT NULL DEFAULT 0
       COMMENT ''1 marks a default candidate; runtime routing requires one enabled global default''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name = 'governance_status'
    )
    AND EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name = 'governance_note'
    ),
    'UPDATE `ai_model_config`
        SET `governance_status` = CASE
              WHEN `governance_status` = ''ACTIVE'' THEN ''PLACEHOLDER''
              ELSE `governance_status`
            END,
            `governance_note` = COALESCE(
              NULLIF(`governance_note`, ''''),
              ''Historical placeholder from V3_015. Review explicitly before disabling or cleanup.''
            )
      WHERE `provider` = ''OPENAI_COMPATIBLE''
        AND `model_code` = ''default-chat''
        AND `deleted` = 0',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
