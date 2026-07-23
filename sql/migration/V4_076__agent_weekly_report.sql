-- V6 stage 7: immutable AI career weekly report snapshots and source audit.
--
-- CREATE TABLE IF NOT EXISTS alone cannot recover a migration that stopped
-- after creating only some tables or after applying only some ALTER clauses.
-- Each table, required column, and named index is therefore checked
-- independently. Re-running this file repairs the remaining schema in place.

SET @schema_name = DATABASE();
SET SESSION group_concat_max_len = 16384;

CREATE TABLE IF NOT EXISTS `agent_weekly_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `user_id` BIGINT NOT NULL COMMENT 'owner user id',
    `target_job_id` BIGINT DEFAULT NULL COMMENT 'target job id, nullable for ALL scope',
    `target_scope_key` VARCHAR(64) NOT NULL COMMENT 'ALL or TARGET_JOB:{id}',
    `week_start_date` DATE NOT NULL COMMENT 'user-timezone Monday',
    `week_end_date` DATE NOT NULL COMMENT 'user-timezone Sunday',
    `timezone` VARCHAR(64) NOT NULL COMMENT 'validated IANA timezone',
    `current_snapshot_id` BIGINT DEFAULT NULL COMMENT 'current immutable snapshot id',
    `report_status` VARCHAR(24) NOT NULL DEFAULT 'NOT_GENERATED'
        COMMENT 'NOT_GENERATED/IN_PROGRESS/COMPLETED',
    `snapshot_version` INT NOT NULL DEFAULT 0 COMMENT 'current snapshot version',
    `summary` VARCHAR(2000) DEFAULT NULL COMMENT 'safe current summary',
    `confidence_level` VARCHAR(16) DEFAULT NULL COMMENT 'HIGH/MEDIUM/LOW/FACT_ONLY',
    `fallback` TINYINT NOT NULL DEFAULT 0 COMMENT 'whether current snapshot used fallback',
    `fallback_reason` VARCHAR(500) DEFAULT NULL COMMENT 'safe fallback reason',
    `generation_claim_fingerprint` CHAR(64) DEFAULT NULL,
    `generation_claim_token` VARCHAR(64) DEFAULT NULL,
    `generation_claim_idempotency_key_hash` CHAR(64) DEFAULT NULL,
    `generation_claim_payload_hash` CHAR(64) DEFAULT NULL,
    `generation_claimed_at` DATETIME DEFAULT NULL COMMENT 'UTC generation reservation time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `live_identity_key` VARCHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `target_scope_key` ELSE NULL END
        ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_aw_report_identity`
        (`user_id`, `week_start_date`, `timezone`, `live_identity_key`),
    KEY `idx_awr_user_week`
        (`user_id`, `week_start_date`, `week_end_date`, `deleted`, `updated_at`),
    KEY `idx_awr_current_snapshot` (`current_snapshot_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent weekly report business identity';

CREATE TABLE IF NOT EXISTS `agent_weekly_report_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `user_id` BIGINT NOT NULL COMMENT 'owner user id',
    `weekly_report_id` BIGINT NOT NULL COMMENT 'agent_weekly_report id',
    `snapshot_version` INT NOT NULL COMMENT 'immutable version within identity',
    `week_start_date` DATE NOT NULL,
    `week_end_date` DATE NOT NULL,
    `target_scope_key` VARCHAR(64) NOT NULL,
    `range_start_utc` DATETIME NOT NULL COMMENT 'inclusive UTC range start',
    `range_end_utc` DATETIME NOT NULL COMMENT 'exclusive UTC range end',
    `source_cutoff_at` DATETIME NOT NULL COMMENT 'server-side UTC evidence cutoff',
    `input_hash` CHAR(64) NOT NULL COMMENT 'canonical evidence input hash',
    `generation_fingerprint` CHAR(64) NOT NULL COMMENT 'input and schema generation fingerprint',
    `idempotency_key_hash` CHAR(64) DEFAULT NULL COMMENT 'user operation idempotency hash',
    `idempotency_payload_hash` CHAR(64) DEFAULT NULL COMMENT 'normalized request payload hash',
    `request_id` VARCHAR(128) DEFAULT NULL COMMENT 'safe request correlation id',
    `calculation_version` VARCHAR(32) NOT NULL,
    `prompt_schema_version` VARCHAR(32) NOT NULL DEFAULT 'NONE',
    `output_schema_version` VARCHAR(32) NOT NULL,
    `report_status` VARCHAR(24) NOT NULL,
    `summary` VARCHAR(2000) DEFAULT NULL,
    `confidence_level` VARCHAR(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW/FACT_ONLY',
    `facts_json` MEDIUMTEXT NOT NULL,
    `signals_json` MEDIUMTEXT NOT NULL,
    `hypotheses_json` MEDIUMTEXT NOT NULL,
    `experiment_suggestions_json` MEDIUMTEXT NOT NULL,
    `plan_draft_json` MEDIUMTEXT DEFAULT NULL,
    `coverage_json` TEXT NOT NULL,
    `result_source` VARCHAR(24) NOT NULL COMMENT 'AI/RULE/FALLBACK',
    `fallback` TINYINT NOT NULL DEFAULT 0,
    `fallback_reason` VARCHAR(500) DEFAULT NULL,
    `trace_id` VARCHAR(128) DEFAULT NULL,
    `ai_call_log_id` BIGINT DEFAULT NULL,
    `generated_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_awrs_version`
        (`weekly_report_id`, `snapshot_version`),
    UNIQUE KEY `uk_awrs_input_hash`
        (`weekly_report_id`, `input_hash`, `calculation_version`,
         `prompt_schema_version`, `output_schema_version`),
    UNIQUE KEY `uk_awrs_generation_fingerprint`
        (`weekly_report_id`, `generation_fingerprint`),
    UNIQUE KEY `uk_awrs_idempotency`
        (`user_id`, `idempotency_key_hash`),
    KEY `idx_awrs_scope_week`
        (`user_id`, `target_scope_key`, `week_start_date`, `deleted`),
    KEY `idx_awrs_cutoff` (`source_cutoff_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable Agent weekly report snapshot';

CREATE TABLE IF NOT EXISTS `agent_weekly_report_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `user_id` BIGINT NOT NULL COMMENT 'owner user id',
    `snapshot_id` BIGINT NOT NULL COMMENT 'agent_weekly_report_snapshot id',
    `source_type` VARCHAR(64) NOT NULL,
    `source_id` BIGINT DEFAULT NULL,
    `source_time` DATETIME DEFAULT NULL COMMENT 'source business time in UTC when applicable',
    `source_updated_at` DATETIME DEFAULT NULL COMMENT 'source update time at collection',
    `scope_key` VARCHAR(255) DEFAULT NULL,
    `inclusion_status` VARCHAR(16) NOT NULL
        COMMENT 'INCLUDED/EXCLUDED/UNAVAILABLE/TRUNCATED',
    `exclude_reason` VARCHAR(64) DEFAULT NULL,
    `source_hash` VARCHAR(80) DEFAULT NULL,
    `safe_summary` VARCHAR(500) DEFAULT NULL,
    `metadata_json` TEXT DEFAULT NULL COMMENT 'safe structured metadata only',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_awrs_source_snapshot`
        (`snapshot_id`, `inclusion_status`, `source_type`, `deleted`),
    KEY `idx_awrs_source_lookup`
        (`user_id`, `source_type`, `source_id`, `deleted`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent weekly report snapshot source audit';

-- Recover any missing agent_weekly_report columns in one ALTER. The generated
-- live identity column is added separately after its dependencies exist.
SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `agent_weekly_report` ',
            GROUP_CONCAT(ddl ORDER BY ordinal_position SEPARATOR ', ')
        )
    )
    FROM (
        SELECT 10 AS ordinal_position,
               'ADD COLUMN `id` BIGINT NOT NULL' AS ddl
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'id'
        )
        UNION ALL
        SELECT 20, 'ADD COLUMN `user_id` BIGINT NOT NULL COMMENT ''owner user id'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'user_id'
        )
        UNION ALL
        SELECT 30, 'ADD COLUMN `target_job_id` BIGINT NULL COMMENT ''target job id, nullable for ALL scope'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'target_job_id'
        )
        UNION ALL
        SELECT 40, 'ADD COLUMN `target_scope_key` VARCHAR(64) NOT NULL COMMENT ''ALL or TARGET_JOB:{id}'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'target_scope_key'
        )
        UNION ALL
        SELECT 50, 'ADD COLUMN `week_start_date` DATE NOT NULL COMMENT ''user-timezone Monday'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'week_start_date'
        )
        UNION ALL
        SELECT 60, 'ADD COLUMN `week_end_date` DATE NOT NULL COMMENT ''user-timezone Sunday'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'week_end_date'
        )
        UNION ALL
        SELECT 70, 'ADD COLUMN `timezone` VARCHAR(64) NOT NULL COMMENT ''validated IANA timezone'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'timezone'
        )
        UNION ALL
        SELECT 80, 'ADD COLUMN `current_snapshot_id` BIGINT NULL COMMENT ''current immutable snapshot id'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'current_snapshot_id'
        )
        UNION ALL
        SELECT 90, 'ADD COLUMN `report_status` VARCHAR(24) NOT NULL DEFAULT ''NOT_GENERATED'' COMMENT ''NOT_GENERATED/IN_PROGRESS/COMPLETED'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'report_status'
        )
        UNION ALL
        SELECT 100, 'ADD COLUMN `snapshot_version` INT NOT NULL DEFAULT 0 COMMENT ''current snapshot version'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'snapshot_version'
        )
        UNION ALL
        SELECT 110, 'ADD COLUMN `summary` VARCHAR(2000) NULL COMMENT ''safe current summary'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'summary'
        )
        UNION ALL
        SELECT 120, 'ADD COLUMN `confidence_level` VARCHAR(16) NULL COMMENT ''HIGH/MEDIUM/LOW/FACT_ONLY'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'confidence_level'
        )
        UNION ALL
        SELECT 130, 'ADD COLUMN `fallback` TINYINT NOT NULL DEFAULT 0 COMMENT ''whether current snapshot used fallback'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'fallback'
        )
        UNION ALL
        SELECT 140, 'ADD COLUMN `fallback_reason` VARCHAR(500) NULL COMMENT ''safe fallback reason'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'fallback_reason'
        )
        UNION ALL
        SELECT 150, 'ADD COLUMN `generation_claim_fingerprint` CHAR(64) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'generation_claim_fingerprint'
        )
        UNION ALL
        SELECT 160, 'ADD COLUMN `generation_claim_token` VARCHAR(64) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'generation_claim_token'
        )
        UNION ALL
        SELECT 170, 'ADD COLUMN `generation_claim_idempotency_key_hash` CHAR(64) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'generation_claim_idempotency_key_hash'
        )
        UNION ALL
        SELECT 180, 'ADD COLUMN `generation_claim_payload_hash` CHAR(64) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'generation_claim_payload_hash'
        )
        UNION ALL
        SELECT 190, 'ADD COLUMN `generation_claimed_at` DATETIME NULL COMMENT ''UTC generation reservation time'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'generation_claimed_at'
        )
        UNION ALL
        SELECT 200, 'ADD COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'created_at'
        )
        UNION ALL
        SELECT 210, 'ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'updated_at'
        )
        UNION ALL
        SELECT 220, 'ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report'
              AND column_name = 'deleted'
        )
    ) missing_columns
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Repair every named index by comparing its column order and uniqueness with
-- the expected contract. Wrong same-named indexes are dropped and recreated
-- in the same ALTER; absent indexes are added. This replaces the original
-- deleted-bearing unique keys without losing historical rows.
DROP TEMPORARY TABLE IF EXISTS `_v4_076_expected_index`;
CREATE TEMPORARY TABLE `_v4_076_expected_index` (
    `table_name` VARCHAR(64) NOT NULL,
    `index_name` VARCHAR(64) NOT NULL,
    `index_columns` VARCHAR(512) NOT NULL,
    `non_unique` TINYINT NOT NULL,
    `drop_ddl` VARCHAR(256) NOT NULL,
    `add_ddl` VARCHAR(1024) NOT NULL,
    `ordinal_position` INT NOT NULL,
    PRIMARY KEY (`table_name`, `index_name`)
);

INSERT INTO `_v4_076_expected_index` (
    `table_name`, `index_name`, `index_columns`, `non_unique`,
    `drop_ddl`, `add_ddl`, `ordinal_position`
) VALUES
    (
        'agent_weekly_report',
        'PRIMARY',
        'id',
        0,
        'DROP PRIMARY KEY',
        'ADD PRIMARY KEY (`id`)',
        10
    ),
    (
        'agent_weekly_report',
        'uk_aw_report_identity',
        'user_id,week_start_date,timezone,live_identity_key',
        0,
        'DROP INDEX `uk_aw_report_identity`',
        'ADD UNIQUE KEY `uk_aw_report_identity`
           (`user_id`, `week_start_date`, `timezone`, `live_identity_key`)',
        20
    ),
    (
        'agent_weekly_report',
        'idx_awr_user_week',
        'user_id,week_start_date,week_end_date,deleted,updated_at',
        1,
        'DROP INDEX `idx_awr_user_week`',
        'ADD KEY `idx_awr_user_week`
           (`user_id`, `week_start_date`, `week_end_date`, `deleted`, `updated_at`)',
        30
    ),
    (
        'agent_weekly_report',
        'idx_awr_current_snapshot',
        'current_snapshot_id,deleted',
        1,
        'DROP INDEX `idx_awr_current_snapshot`',
        'ADD KEY `idx_awr_current_snapshot` (`current_snapshot_id`, `deleted`)',
        40
    ),
    (
        'agent_weekly_report_snapshot',
        'PRIMARY',
        'id',
        0,
        'DROP PRIMARY KEY',
        'ADD PRIMARY KEY (`id`)',
        10
    ),
    (
        'agent_weekly_report_snapshot',
        'uk_awrs_version',
        'weekly_report_id,snapshot_version',
        0,
        'DROP INDEX `uk_awrs_version`',
        'ADD UNIQUE KEY `uk_awrs_version`
           (`weekly_report_id`, `snapshot_version`)',
        20
    ),
    (
        'agent_weekly_report_snapshot',
        'uk_awrs_input_hash',
        'weekly_report_id,input_hash,calculation_version,prompt_schema_version,output_schema_version',
        0,
        'DROP INDEX `uk_awrs_input_hash`',
        'ADD UNIQUE KEY `uk_awrs_input_hash`
           (`weekly_report_id`, `input_hash`, `calculation_version`,
            `prompt_schema_version`, `output_schema_version`)',
        30
    ),
    (
        'agent_weekly_report_snapshot',
        'uk_awrs_generation_fingerprint',
        'weekly_report_id,generation_fingerprint',
        0,
        'DROP INDEX `uk_awrs_generation_fingerprint`',
        'ADD UNIQUE KEY `uk_awrs_generation_fingerprint`
           (`weekly_report_id`, `generation_fingerprint`)',
        40
    ),
    (
        'agent_weekly_report_snapshot',
        'uk_awrs_idempotency',
        'user_id,idempotency_key_hash',
        0,
        'DROP INDEX `uk_awrs_idempotency`',
        'ADD UNIQUE KEY `uk_awrs_idempotency`
           (`user_id`, `idempotency_key_hash`)',
        50
    ),
    (
        'agent_weekly_report_snapshot',
        'idx_awrs_scope_week',
        'user_id,target_scope_key,week_start_date,deleted',
        1,
        'DROP INDEX `idx_awrs_scope_week`',
        'ADD KEY `idx_awrs_scope_week`
           (`user_id`, `target_scope_key`, `week_start_date`, `deleted`)',
        60
    ),
    (
        'agent_weekly_report_snapshot',
        'idx_awrs_cutoff',
        'source_cutoff_at,deleted',
        1,
        'DROP INDEX `idx_awrs_cutoff`',
        'ADD KEY `idx_awrs_cutoff` (`source_cutoff_at`, `deleted`)',
        70
    ),
    (
        'agent_weekly_report_source',
        'PRIMARY',
        'id',
        0,
        'DROP PRIMARY KEY',
        'ADD PRIMARY KEY (`id`)',
        10
    ),
    (
        'agent_weekly_report_source',
        'idx_awrs_source_snapshot',
        'snapshot_id,inclusion_status,source_type,deleted',
        1,
        'DROP INDEX `idx_awrs_source_snapshot`',
        'ADD KEY `idx_awrs_source_snapshot`
           (`snapshot_id`, `inclusion_status`, `source_type`, `deleted`)',
        20
    ),
    (
        'agent_weekly_report_source',
        'idx_awrs_source_lookup',
        'user_id,source_type,source_id,deleted,created_at',
        1,
        'DROP INDEX `idx_awrs_source_lookup`',
        'ADD KEY `idx_awrs_source_lookup`
           (`user_id`, `source_type`, `source_id`, `deleted`, `created_at`)',
        30
    );

-- Column repair continues below, so the index repair SQL is captured here
-- and executed only by the final pass after all columns are present. MySQL
-- cannot reopen the same temporary table in both branches of one UNION, so
-- drop and add clauses are collected with independent statements.
SET @v4_076_report_index_drop = (
    SELECT GROUP_CONCAT(
               expected.drop_ddl
               ORDER BY expected.ordinal_position
               SEPARATOR ', '
           )
    FROM `_v4_076_expected_index` expected
    INNER JOIN (
        SELECT index_name,
               GROUP_CONCAT(
                   column_name ORDER BY seq_in_index SEPARATOR ','
               ) AS index_columns,
               MAX(non_unique) AS non_unique
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report'
        GROUP BY index_name
    ) actual
      ON actual.index_name = expected.index_name
    WHERE expected.table_name = 'agent_weekly_report'
      AND (
          actual.index_columns <> expected.index_columns
          OR actual.non_unique <> expected.non_unique
      )
);
SET @v4_076_report_index_add = (
    SELECT GROUP_CONCAT(
               expected.add_ddl
               ORDER BY expected.ordinal_position
               SEPARATOR ', '
           )
    FROM `_v4_076_expected_index` expected
    LEFT JOIN (
        SELECT index_name,
               GROUP_CONCAT(
                   column_name ORDER BY seq_in_index SEPARATOR ','
               ) AS index_columns,
               MAX(non_unique) AS non_unique
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report'
        GROUP BY index_name
    ) actual
      ON actual.index_name = expected.index_name
     AND actual.index_columns = expected.index_columns
     AND actual.non_unique = expected.non_unique
    WHERE expected.table_name = 'agent_weekly_report'
      AND actual.index_name IS NULL
);
SET @v4_076_report_index_sql = IF(
    @v4_076_report_index_drop IS NULL
      AND @v4_076_report_index_add IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE `agent_weekly_report` ',
        CONCAT_WS(
            ', ',
            @v4_076_report_index_drop,
            @v4_076_report_index_add
        )
    )
);

SET @v4_076_snapshot_index_drop = (
    SELECT GROUP_CONCAT(
               expected.drop_ddl
               ORDER BY expected.ordinal_position
               SEPARATOR ', '
           )
    FROM `_v4_076_expected_index` expected
    INNER JOIN (
        SELECT index_name,
               GROUP_CONCAT(
                   column_name ORDER BY seq_in_index SEPARATOR ','
               ) AS index_columns,
               MAX(non_unique) AS non_unique
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
        GROUP BY index_name
    ) actual
      ON actual.index_name = expected.index_name
    WHERE expected.table_name = 'agent_weekly_report_snapshot'
      AND (
          actual.index_columns <> expected.index_columns
          OR actual.non_unique <> expected.non_unique
      )
);
SET @v4_076_snapshot_index_add = (
    SELECT GROUP_CONCAT(
               expected.add_ddl
               ORDER BY expected.ordinal_position
               SEPARATOR ', '
           )
    FROM `_v4_076_expected_index` expected
    LEFT JOIN (
        SELECT index_name,
               GROUP_CONCAT(
                   column_name ORDER BY seq_in_index SEPARATOR ','
               ) AS index_columns,
               MAX(non_unique) AS non_unique
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
        GROUP BY index_name
    ) actual
      ON actual.index_name = expected.index_name
     AND actual.index_columns = expected.index_columns
     AND actual.non_unique = expected.non_unique
    WHERE expected.table_name = 'agent_weekly_report_snapshot'
      AND actual.index_name IS NULL
);
SET @v4_076_snapshot_index_sql = IF(
    @v4_076_snapshot_index_drop IS NULL
      AND @v4_076_snapshot_index_add IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE `agent_weekly_report_snapshot` ',
        CONCAT_WS(
            ', ',
            @v4_076_snapshot_index_drop,
            @v4_076_snapshot_index_add
        )
    )
);

SET @v4_076_source_index_drop = (
    SELECT GROUP_CONCAT(
               expected.drop_ddl
               ORDER BY expected.ordinal_position
               SEPARATOR ', '
           )
    FROM `_v4_076_expected_index` expected
    INNER JOIN (
        SELECT index_name,
               GROUP_CONCAT(
                   column_name ORDER BY seq_in_index SEPARATOR ','
               ) AS index_columns,
               MAX(non_unique) AS non_unique
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_source'
        GROUP BY index_name
    ) actual
      ON actual.index_name = expected.index_name
    WHERE expected.table_name = 'agent_weekly_report_source'
      AND (
          actual.index_columns <> expected.index_columns
          OR actual.non_unique <> expected.non_unique
      )
);
SET @v4_076_source_index_add = (
    SELECT GROUP_CONCAT(
               expected.add_ddl
               ORDER BY expected.ordinal_position
               SEPARATOR ', '
           )
    FROM `_v4_076_expected_index` expected
    LEFT JOIN (
        SELECT index_name,
               GROUP_CONCAT(
                   column_name ORDER BY seq_in_index SEPARATOR ','
               ) AS index_columns,
               MAX(non_unique) AS non_unique
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_source'
        GROUP BY index_name
    ) actual
      ON actual.index_name = expected.index_name
     AND actual.index_columns = expected.index_columns
     AND actual.non_unique = expected.non_unique
    WHERE expected.table_name = 'agent_weekly_report_source'
      AND actual.index_name IS NULL
);
SET @v4_076_source_index_sql = IF(
    @v4_076_source_index_drop IS NULL
      AND @v4_076_source_index_add IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE `agent_weekly_report_source` ',
        CONCAT_WS(
            ', ',
            @v4_076_source_index_drop,
            @v4_076_source_index_add
        )
    )
);

-- Keep the expected-index table for the final repair pass.

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report'
          AND column_name = 'target_scope_key'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report'
          AND column_name = 'deleted'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report'
          AND column_name = 'live_identity_key'
    ),
    'ALTER TABLE `agent_weekly_report`
       ADD COLUMN `live_identity_key` VARCHAR(64)
         GENERATED ALWAYS AS (
           CASE WHEN `deleted` = 0 THEN `target_scope_key` ELSE NULL END
         ) STORED',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Recover all ordinary snapshot columns first. generation_fingerprint is added
-- nullable afterwards so existing rows can be backfilled deterministically.
SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `agent_weekly_report_snapshot` ',
            GROUP_CONCAT(ddl ORDER BY ordinal_position SEPARATOR ', ')
        )
    )
    FROM (
        SELECT 10 AS ordinal_position,
               'ADD COLUMN `id` BIGINT NOT NULL' AS ddl
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'id'
        )
        UNION ALL
        SELECT 20, 'ADD COLUMN `user_id` BIGINT NOT NULL COMMENT ''owner user id'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'user_id'
        )
        UNION ALL
        SELECT 30, 'ADD COLUMN `weekly_report_id` BIGINT NOT NULL COMMENT ''agent_weekly_report id'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'weekly_report_id'
        )
        UNION ALL
        SELECT 40, 'ADD COLUMN `snapshot_version` INT NOT NULL COMMENT ''immutable version within identity'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'snapshot_version'
        )
        UNION ALL
        SELECT 50, 'ADD COLUMN `week_start_date` DATE NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'week_start_date'
        )
        UNION ALL
        SELECT 60, 'ADD COLUMN `week_end_date` DATE NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'week_end_date'
        )
        UNION ALL
        SELECT 70, 'ADD COLUMN `target_scope_key` VARCHAR(64) NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'target_scope_key'
        )
        UNION ALL
        SELECT 80, 'ADD COLUMN `range_start_utc` DATETIME NOT NULL COMMENT ''inclusive UTC range start'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'range_start_utc'
        )
        UNION ALL
        SELECT 90, 'ADD COLUMN `range_end_utc` DATETIME NOT NULL COMMENT ''exclusive UTC range end'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'range_end_utc'
        )
        UNION ALL
        SELECT 100, 'ADD COLUMN `source_cutoff_at` DATETIME NOT NULL COMMENT ''server-side UTC evidence cutoff'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'source_cutoff_at'
        )
        UNION ALL
        SELECT 110, 'ADD COLUMN `input_hash` CHAR(64) NOT NULL COMMENT ''canonical evidence input hash'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'input_hash'
        )
        UNION ALL
        SELECT 130, 'ADD COLUMN `idempotency_key_hash` CHAR(64) NULL COMMENT ''user operation idempotency hash'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'idempotency_key_hash'
        )
        UNION ALL
        SELECT 140, 'ADD COLUMN `idempotency_payload_hash` CHAR(64) NULL COMMENT ''normalized request payload hash'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'idempotency_payload_hash'
        )
        UNION ALL
        SELECT 150, 'ADD COLUMN `request_id` VARCHAR(128) NULL COMMENT ''safe request correlation id'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'request_id'
        )
        UNION ALL
        SELECT 160, 'ADD COLUMN `calculation_version` VARCHAR(32) NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'calculation_version'
        )
        UNION ALL
        SELECT 170, 'ADD COLUMN `prompt_schema_version` VARCHAR(32) NOT NULL DEFAULT ''NONE'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'prompt_schema_version'
        )
        UNION ALL
        SELECT 180, 'ADD COLUMN `output_schema_version` VARCHAR(32) NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'output_schema_version'
        )
        UNION ALL
        SELECT 190, 'ADD COLUMN `report_status` VARCHAR(24) NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'report_status'
        )
        UNION ALL
        SELECT 200, 'ADD COLUMN `summary` VARCHAR(2000) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'summary'
        )
        UNION ALL
        SELECT 210, 'ADD COLUMN `confidence_level` VARCHAR(16) NOT NULL COMMENT ''HIGH/MEDIUM/LOW/FACT_ONLY'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'confidence_level'
        )
        UNION ALL
        SELECT 220, 'ADD COLUMN `facts_json` MEDIUMTEXT NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'facts_json'
        )
        UNION ALL
        SELECT 230, 'ADD COLUMN `signals_json` MEDIUMTEXT NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'signals_json'
        )
        UNION ALL
        SELECT 240, 'ADD COLUMN `hypotheses_json` MEDIUMTEXT NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'hypotheses_json'
        )
        UNION ALL
        SELECT 250, 'ADD COLUMN `experiment_suggestions_json` MEDIUMTEXT NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'experiment_suggestions_json'
        )
        UNION ALL
        SELECT 260, 'ADD COLUMN `plan_draft_json` MEDIUMTEXT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'plan_draft_json'
        )
        UNION ALL
        SELECT 270, 'ADD COLUMN `coverage_json` TEXT NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'coverage_json'
        )
        UNION ALL
        SELECT 280, 'ADD COLUMN `result_source` VARCHAR(24) NOT NULL COMMENT ''AI/RULE/FALLBACK'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'result_source'
        )
        UNION ALL
        SELECT 290, 'ADD COLUMN `fallback` TINYINT NOT NULL DEFAULT 0'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'fallback'
        )
        UNION ALL
        SELECT 300, 'ADD COLUMN `fallback_reason` VARCHAR(500) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'fallback_reason'
        )
        UNION ALL
        SELECT 310, 'ADD COLUMN `trace_id` VARCHAR(128) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'trace_id'
        )
        UNION ALL
        SELECT 320, 'ADD COLUMN `ai_call_log_id` BIGINT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'ai_call_log_id'
        )
        UNION ALL
        SELECT 330, 'ADD COLUMN `generated_at` DATETIME NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'generated_at'
        )
        UNION ALL
        SELECT 340, 'ADD COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'created_at'
        )
        UNION ALL
        SELECT 350, 'ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'updated_at'
        )
        UNION ALL
        SELECT 360, 'ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_snapshot'
              AND column_name = 'deleted'
        )
    ) missing_columns
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
          AND column_name = 'generation_fingerprint'
    ),
    'ALTER TABLE `agent_weekly_report_snapshot`
       ADD COLUMN `generation_fingerprint` CHAR(64) NULL
         COMMENT ''input and schema generation fingerprint''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Recover all source columns before repairing source indexes.
SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `agent_weekly_report_source` ',
            GROUP_CONCAT(ddl ORDER BY ordinal_position SEPARATOR ', ')
        )
    )
    FROM (
        SELECT 10 AS ordinal_position,
               'ADD COLUMN `id` BIGINT NOT NULL' AS ddl
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'id'
        )
        UNION ALL
        SELECT 20, 'ADD COLUMN `user_id` BIGINT NOT NULL COMMENT ''owner user id'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'user_id'
        )
        UNION ALL
        SELECT 30, 'ADD COLUMN `snapshot_id` BIGINT NOT NULL COMMENT ''agent_weekly_report_snapshot id'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'snapshot_id'
        )
        UNION ALL
        SELECT 40, 'ADD COLUMN `source_type` VARCHAR(64) NOT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'source_type'
        )
        UNION ALL
        SELECT 50, 'ADD COLUMN `source_id` BIGINT NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'source_id'
        )
        UNION ALL
        SELECT 60, 'ADD COLUMN `source_time` DATETIME NULL COMMENT ''source business time in UTC when applicable'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'source_time'
        )
        UNION ALL
        SELECT 70, 'ADD COLUMN `source_updated_at` DATETIME NULL COMMENT ''source update time at collection'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'source_updated_at'
        )
        UNION ALL
        SELECT 80, 'ADD COLUMN `scope_key` VARCHAR(255) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'scope_key'
        )
        UNION ALL
        SELECT 90, 'ADD COLUMN `inclusion_status` VARCHAR(16) NOT NULL COMMENT ''INCLUDED/EXCLUDED/UNAVAILABLE/TRUNCATED'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'inclusion_status'
        )
        UNION ALL
        SELECT 100, 'ADD COLUMN `exclude_reason` VARCHAR(64) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'exclude_reason'
        )
        UNION ALL
        SELECT 110, 'ADD COLUMN `source_hash` VARCHAR(80) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'source_hash'
        )
        UNION ALL
        SELECT 120, 'ADD COLUMN `safe_summary` VARCHAR(500) NULL'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'safe_summary'
        )
        UNION ALL
        SELECT 130, 'ADD COLUMN `metadata_json` TEXT NULL COMMENT ''safe structured metadata only'''
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'metadata_json'
        )
        UNION ALL
        SELECT 140, 'ADD COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'created_at'
        )
        UNION ALL
        SELECT 150, 'ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'updated_at'
        )
        UNION ALL
        SELECT 160, 'ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'agent_weekly_report_source'
              AND column_name = 'deleted'
        )
    ) missing_columns
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Immutable rows retain their original audit timestamp even if an old draft
-- schema created updated_at with an automatic update clause.
SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
          AND column_name = 'updated_at'
          AND LOWER(extra) LIKE '%on update%'
    ),
    'ALTER TABLE `agent_weekly_report_snapshot`
       MODIFY COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_source'
          AND column_name = 'updated_at'
          AND LOWER(extra) LIKE '%on update%'
    ),
    'ALTER TABLE `agent_weekly_report_source`
       MODIFY COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Normalize the nullable prompt version used by the first draft. A non-null
-- value is required so the composite input uniqueness has exact NULL-safe
-- semantics across calculation, prompt, and output versions.
SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
          AND column_name = 'prompt_schema_version'
          AND is_nullable = 'YES'
    ),
    'UPDATE `agent_weekly_report_snapshot`
        SET `prompt_schema_version` = ''NONE''
      WHERE `prompt_schema_version` IS NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
          AND column_name = 'prompt_schema_version'
          AND is_nullable = 'YES'
    ),
    'ALTER TABLE `agent_weekly_report_snapshot`
       MODIFY COLUMN `prompt_schema_version` VARCHAR(32) NOT NULL DEFAULT ''NONE''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill the generation fingerprint for installations that created the
-- original draft snapshot table before the fingerprint column was added.
-- This mirrors WeeklyReportHashUtils.hash(List.of(...)): Jackson emits a
-- compact JSON array for these controlled string values.
SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
          AND column_name = 'generation_fingerprint'
    ),
    'UPDATE `agent_weekly_report_snapshot`
        SET `generation_fingerprint` = SHA2(
          CONCAT(
            ''["'',
            `input_hash`,
            ''","'',
            `calculation_version`,
            ''","'',
            `prompt_schema_version`,
            ''","'',
            `output_schema_version`,
            ''"]''
          ),
          256
        )
      WHERE `generation_fingerprint` IS NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
          AND column_name = 'generation_fingerprint'
          AND is_nullable = 'YES'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM `agent_weekly_report_snapshot`
        WHERE `generation_fingerprint` IS NULL
    ),
    'ALTER TABLE `agent_weekly_report_snapshot`
       MODIFY COLUMN `generation_fingerprint` CHAR(64) NOT NULL
         COMMENT ''input and schema generation fingerprint''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- All nullable draft values are normalized before the unique indexes are
-- created or repaired.
SET @sql = @v4_076_report_index_sql;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = @v4_076_snapshot_index_sql;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = @v4_076_source_index_sql;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- A missing id column is first added without AUTO_INCREMENT so the final
-- primary-key repair can run in the same migration. Enforce AUTO_INCREMENT
-- only after the primary key exists.
SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report'
          AND column_name = 'id'
          AND LOWER(extra) NOT LIKE '%auto_increment%'
    ),
    'ALTER TABLE `agent_weekly_report`
       MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary key''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_snapshot'
          AND column_name = 'id'
          AND LOWER(extra) NOT LIKE '%auto_increment%'
    ),
    'ALTER TABLE `agent_weekly_report_snapshot`
       MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary key''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'agent_weekly_report_source'
          AND column_name = 'id'
          AND LOWER(extra) NOT LIKE '%auto_increment%'
    ),
    'ALTER TABLE `agent_weekly_report_source`
       MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''primary key''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS `_v4_076_expected_index`;
