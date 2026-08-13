-- Enforce one active global default AI model without deleting or disabling model records.
-- Stable winner order: lowest sort_order, newest updated_at, then highest id.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
    )
    AND EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name IN ('id', 'default_model', 'deleted', 'sort_order', 'updated_at')
        GROUP BY table_schema, table_name
        HAVING COUNT(DISTINCT column_name) = 5
    ),
    'UPDATE `ai_model_config` target
       JOIN (
         SELECT id
         FROM (
           SELECT loser.id
           FROM `ai_model_config` loser
           JOIN `ai_model_config` winner
             ON winner.deleted = 0
            AND winner.default_model = 1
            AND (
                 COALESCE(winner.sort_order, 2147483647) < COALESCE(loser.sort_order, 2147483647)
              OR (COALESCE(winner.sort_order, 2147483647) = COALESCE(loser.sort_order, 2147483647)
                  AND COALESCE(winner.updated_at, ''1970-01-01'') > COALESCE(loser.updated_at, ''1970-01-01''))
              OR (COALESCE(winner.sort_order, 2147483647) = COALESCE(loser.sort_order, 2147483647)
                  AND COALESCE(winner.updated_at, ''1970-01-01'') = COALESCE(loser.updated_at, ''1970-01-01'')
                  AND winner.id > loser.id)
            )
           WHERE loser.deleted = 0
             AND loser.default_model = 1
         ) duplicate_defaults
       ) rows_to_clear ON rows_to_clear.id = target.id
        SET target.default_model = 0',
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
    AND EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name IN ('default_model', 'deleted')
        GROUP BY table_schema, table_name
        HAVING COUNT(DISTINCT column_name) = 2
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND column_name = 'active_global_default_guard'
    ),
    'ALTER TABLE `ai_model_config`
       ADD COLUMN `active_global_default_guard` TINYINT
       GENERATED ALWAYS AS (
         CASE WHEN `deleted` = 0 AND `default_model` = 1 THEN 1 ELSE NULL END
       ) STORED
       COMMENT ''Unique guard for one active global default model''',
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
          AND column_name = 'active_global_default_guard'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'ai_model_config'
          AND index_name = 'uk_ai_model_one_global_default'
    ),
    'ALTER TABLE `ai_model_config`
       ADD UNIQUE KEY `uk_ai_model_one_global_default` (`active_global_default_guard`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
