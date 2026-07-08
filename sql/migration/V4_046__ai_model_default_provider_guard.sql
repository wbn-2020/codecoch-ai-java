-- Guard ai_model_config so each active provider has at most one default model.
-- The generated column makes MySQL's multi-NULL unique index behave like a partial unique index.

SET @schema_name = DATABASE();

UPDATE `ai_model_config` target
JOIN (
  SELECT id
  FROM (
    SELECT loser.id
    FROM `ai_model_config` loser
    JOIN `ai_model_config` winner
      ON winner.provider = loser.provider
     AND winner.deleted = 0
     AND winner.default_model = 1
     AND (
          winner.sort_order < loser.sort_order
       OR (winner.sort_order = loser.sort_order AND winner.updated_at > loser.updated_at)
       OR (winner.sort_order = loser.sort_order AND winner.updated_at = loser.updated_at AND winner.id > loser.id)
     )
    WHERE loser.deleted = 0
      AND loser.default_model = 1
  ) duplicate_defaults
) rows_to_clear
  ON rows_to_clear.id = target.id
SET target.default_model = 0;

SET @column_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'ai_model_config'
    AND column_name = 'active_default_provider'
);

SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE `ai_model_config`
     ADD COLUMN `active_default_provider` VARCHAR(64)
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 AND `default_model` = 1 THEN `provider` ELSE NULL END) STORED
       COMMENT ''Unique guard for one active default model per provider''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'ai_model_config'
    AND index_name = 'uk_ai_model_one_default_provider'
);

SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE `ai_model_config`
     ADD UNIQUE KEY `uk_ai_model_one_default_provider` (`active_default_provider`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
