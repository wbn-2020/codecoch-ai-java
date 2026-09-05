-- Make prompt-template scene and activation invariants atomic at the database boundary.
-- Historical conflicts are retained as soft-deleted/inactive rows so deployment remains auditable.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'prompt_template'
    ),
    'UPDATE `prompt_template` loser
       JOIN `prompt_template` winner
         ON winner.scene = loser.scene
        AND winner.deleted = 0
        AND loser.deleted = 0
        AND (
             COALESCE(winner.enabled, 0) > COALESCE(loser.enabled, 0)
          OR (COALESCE(winner.enabled, 0) = COALESCE(loser.enabled, 0)
              AND COALESCE(winner.status, 0) > COALESCE(loser.status, 0))
          OR (COALESCE(winner.enabled, 0) = COALESCE(loser.enabled, 0)
              AND COALESCE(winner.status, 0) = COALESCE(loser.status, 0)
              AND winner.active_version_id IS NOT NULL
              AND loser.active_version_id IS NULL)
          OR (COALESCE(winner.enabled, 0) = COALESCE(loser.enabled, 0)
              AND COALESCE(winner.status, 0) = COALESCE(loser.status, 0)
              AND (winner.active_version_id IS NULL) = (loser.active_version_id IS NULL)
              AND COALESCE(winner.updated_at, ''1970-01-01'') > COALESCE(loser.updated_at, ''1970-01-01''))
          OR (COALESCE(winner.enabled, 0) = COALESCE(loser.enabled, 0)
              AND COALESCE(winner.status, 0) = COALESCE(loser.status, 0)
              AND (winner.active_version_id IS NULL) = (loser.active_version_id IS NULL)
              AND COALESCE(winner.updated_at, ''1970-01-01'') = COALESCE(loser.updated_at, ''1970-01-01'')
              AND winner.id > loser.id)
        )
        SET loser.enabled = 0,
            loser.status = 0,
            loser.deleted = 1',
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
          AND table_name = 'prompt_template'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'prompt_template'
          AND column_name = 'live_scene_guard'
    ),
    'ALTER TABLE `prompt_template`
       ADD COLUMN `live_scene_guard` VARCHAR(64)
       GENERATED ALWAYS AS (
         CASE WHEN `deleted` = 0 THEN `scene` ELSE NULL END
       ) STORED
       COMMENT ''Unique guard for one live prompt template per scene''',
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
          AND table_name = 'prompt_template'
          AND column_name = 'live_scene_guard'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'prompt_template'
          AND index_name = 'uk_prompt_template_live_scene'
    ),
    'ALTER TABLE `prompt_template`
       ADD UNIQUE KEY `uk_prompt_template_live_scene` (`live_scene_guard`)',
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
          AND table_name = 'prompt_template_version'
    ),
    'UPDATE `prompt_template_version` loser
       JOIN `prompt_template_version` winner
         ON winner.scene = loser.scene
        AND winner.deleted = 0
        AND loser.deleted = 0
        AND winner.status = ''ACTIVE''
        AND loser.status = ''ACTIVE''
        AND winner.is_active = 1
        AND loser.is_active = 1
        AND (
             COALESCE(winner.activated_at, ''1970-01-01'') > COALESCE(loser.activated_at, ''1970-01-01'')
          OR (COALESCE(winner.activated_at, ''1970-01-01'') = COALESCE(loser.activated_at, ''1970-01-01'')
              AND COALESCE(winner.updated_at, ''1970-01-01'') > COALESCE(loser.updated_at, ''1970-01-01''))
          OR (COALESCE(winner.activated_at, ''1970-01-01'') = COALESCE(loser.activated_at, ''1970-01-01'')
              AND COALESCE(winner.updated_at, ''1970-01-01'') = COALESCE(loser.updated_at, ''1970-01-01'')
              AND winner.id > loser.id)
        )
        SET loser.status = ''INACTIVE'',
            loser.is_active = 0',
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
          AND table_name = 'prompt_template_version'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'prompt_template_version'
          AND column_name = 'active_scene_guard'
    ),
    'ALTER TABLE `prompt_template_version`
       ADD COLUMN `active_scene_guard` VARCHAR(64)
       GENERATED ALWAYS AS (
         CASE
           WHEN `deleted` = 0 AND `status` = ''ACTIVE'' AND `is_active` = 1 THEN `scene`
           ELSE NULL
         END
       ) STORED
       COMMENT ''Unique guard for one active prompt version per scene''',
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
          AND table_name = 'prompt_template_version'
          AND column_name = 'active_scene_guard'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'prompt_template_version'
          AND index_name = 'uk_prompt_version_active_scene'
    ),
    'ALTER TABLE `prompt_template_version`
       ADD UNIQUE KEY `uk_prompt_version_active_scene` (`active_scene_guard`)',
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
          AND table_name = 'prompt_template_version'
    )
    AND NOT EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'prompt_template_version'
          AND index_name = 'uk_prompt_template_version'
    ),
    'ALTER TABLE `prompt_template_version`
       ADD UNIQUE KEY `uk_prompt_template_version` (`template_id`, `version_code`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
