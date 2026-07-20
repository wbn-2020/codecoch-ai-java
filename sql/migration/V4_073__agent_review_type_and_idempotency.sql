-- Add explicit review identity fields and backfill legacy agent reviews.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'review_type'
    ),
    'ALTER TABLE `agent_review`
       ADD COLUMN `review_type` VARCHAR(24) NOT NULL DEFAULT ''DAILY'' AFTER `review_date`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'source_task_id'
    ),
    'ALTER TABLE `agent_review`
       ADD COLUMN `source_task_id` BIGINT NULL AFTER `review_type`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'idempotency_key'
    ),
    'ALTER TABLE `agent_review`
       ADD COLUMN `idempotency_key` VARCHAR(160) NULL AFTER `source_task_id`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'review_type'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'idempotency_key'
    ),
    'UPDATE `agent_review`
        SET `review_type` = CASE
            WHEN JSON_VALID(`review_json`)
             AND JSON_TYPE(JSON_EXTRACT(`review_json`, ''$.taskId''))
                 IN (''INTEGER'', ''STRING'')
             AND JSON_UNQUOTE(JSON_EXTRACT(`review_json`, ''$.taskId''))
                 REGEXP ''^[1-9][0-9]{0,18}$''
             AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`review_json`, ''$.taskId''))
                      AS DECIMAL(19,0)) <= 9223372036854775807
              THEN ''TASK''
            ELSE ''DAILY''
        END
      WHERE `idempotency_key` IS NULL
         OR `idempotency_key` = ''''
         OR `idempotency_key` LIKE ''LEGACY:%''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'source_task_id'
    ),
    'UPDATE `agent_review`
        SET `source_task_id` = CASE
            WHEN JSON_VALID(`review_json`)
             AND JSON_TYPE(JSON_EXTRACT(`review_json`, ''$.taskId''))
                 IN (''INTEGER'', ''STRING'')
             AND JSON_UNQUOTE(JSON_EXTRACT(`review_json`, ''$.taskId''))
                 REGEXP ''^[1-9][0-9]{0,18}$''
             AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`review_json`, ''$.taskId''))
                      AS DECIMAL(19,0)) <= 9223372036854775807
              THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(`review_json`, ''$.taskId'')) AS UNSIGNED)
            ELSE NULL
        END
      WHERE `review_type` = ''TASK''
        AND `source_task_id` IS NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'idempotency_key'
    ),
    'UPDATE `agent_review`
        SET `idempotency_key` = CONCAT(''LEGACY:'', `id`)
      WHERE `idempotency_key` IS NULL
         OR `idempotency_key` = ''''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Normalize legacy NULL soft-delete values before building the active-row key.
SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'deleted'
    ),
    'UPDATE `agent_review` SET `deleted` = 0 WHERE `deleted` IS NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Keep historical tombstones, but enforce idempotency only for active rows.
SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'idempotency_key'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'deleted'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'live_idempotency_key'
    ),
    'ALTER TABLE `agent_review`
       ADD COLUMN `live_idempotency_key` VARCHAR(160)
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `idempotency_key` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review'
      AND index_name = 'uk_agent_review_idempotency'
);
SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'agent_review'
      AND index_name = 'uk_agent_review_idempotency'
);
SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_review'
          AND column_name = 'live_idempotency_key'
    )
    AND (
        @index_columns IS NULL
        OR @index_columns <> 'user_id,live_idempotency_key'
        OR @index_non_unique <> 0
    ),
    IF(
        @index_columns IS NULL,
        'ALTER TABLE `agent_review`
           ADD UNIQUE KEY `uk_agent_review_idempotency`
           (`user_id`, `live_idempotency_key`)',
        'ALTER TABLE `agent_review`
           DROP INDEX `uk_agent_review_idempotency`,
           ADD UNIQUE KEY `uk_agent_review_idempotency`
           (`user_id`, `live_idempotency_key`)'
    ),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
