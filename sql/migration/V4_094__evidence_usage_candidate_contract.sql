-- V9 stage four: evidence learning candidate decisions and governed Memory promotion.
-- Forward-only and idempotent.

SET @v4_094_schema_name = DATABASE();

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'review_id'
           AND is_nullable = 'NO'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       MODIFY COLUMN `review_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'snapshot_id'
           AND is_nullable = 'NO'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       MODIFY COLUMN `snapshot_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'candidate_scope_type'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `candidate_scope_type` VARCHAR(32) NULL AFTER `snapshot_id`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'candidate_scope_key'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `candidate_scope_key` VARCHAR(128) NULL AFTER `candidate_scope_type`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'candidate_type'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `candidate_type` VARCHAR(32) NULL AFTER `candidate_scope_key`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'usage_source_hash'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `usage_source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `candidate_type`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'evidence_count'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `evidence_count` INT NOT NULL DEFAULT 0 AFTER `usage_source_hash`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'sample_count'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `sample_count` INT NOT NULL DEFAULT 0 AFTER `evidence_count`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'limits_json'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `limits_json` TEXT NULL AFTER `sample_count`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'decision_code'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `decision_code` VARCHAR(16) NULL AFTER `decision_idempotency_key_hash`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'decision_payload_hash'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `decision_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `decision_code`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'decision_history_json'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `decision_history_json` TEXT NULL AFTER `decision_payload_hash`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'decision_at'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `decision_at` DATETIME NULL AFTER `decision_history_json`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'promoted_memory_id'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `promoted_memory_id` BIGINT NULL AFTER `decision_at`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_live_semantic_hash_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @v4_094_schema_name
       AND table_name = 'career_campaign_review_memory_candidate'
       AND column_name = 'live_semantic_hash'
);

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND @v4_094_live_semantic_hash_exists = 0,
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD COLUMN `live_semantic_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
         GENERATED ALWAYS AS (
           CASE
             WHEN `deleted` = 0 AND `status` NOT IN (''REJECTED'', ''EXPIRED'')
             THEN `semantic_hash`
             ELSE NULL
           END
         ) STORED AFTER `deleted`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND @v4_094_live_semantic_hash_exists > 0
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'live_semantic_hash'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       MODIFY COLUMN `live_semantic_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
         GENERATED ALWAYS AS (
           CASE
             WHEN `deleted` = 0 AND `status` NOT IN (''REJECTED'', ''EXPIRED'')
             THEN `semantic_hash`
             ELSE NULL
           END
         ) STORED',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND column_name = 'live_semantic_hash'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND index_name = 'uk_campaign_review_memory_live_semantic'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD UNIQUE KEY `uk_campaign_review_memory_live_semantic`
         (`user_id`, `live_semantic_hash`)',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'career_campaign_review_memory_candidate'
           AND index_name = 'idx_campaign_review_memory_scope'
    ),
    'ALTER TABLE `career_campaign_review_memory_candidate`
       ADD KEY `idx_campaign_review_memory_scope`
         (`user_id`, `candidate_scope_type`, `candidate_scope_key`, `status`, `deleted`, `id`)',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'promotion_key_hash'
    ),
    'ALTER TABLE `agent_memory`
       ADD COLUMN `promotion_key_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `source_id`',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'created_at'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'updated_at'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'deleted'
    ),
    'UPDATE `agent_memory`
        SET `created_at` = COALESCE(`created_at`, CURRENT_TIMESTAMP),
            `updated_at` = COALESCE(`updated_at`, CURRENT_TIMESTAMP),
            `deleted` = COALESCE(`deleted`, 0)
      WHERE `created_at` IS NULL
         OR `updated_at` IS NULL
         OR `deleted` IS NULL',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'created_at'
           AND is_nullable = 'YES'
    ),
    'ALTER TABLE `agent_memory`
       MODIFY COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'updated_at'
           AND is_nullable = 'YES'
    ),
    'ALTER TABLE `agent_memory`
       MODIFY COLUMN `updated_at` DATETIME NOT NULL
         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'deleted'
           AND is_nullable = 'YES'
    ),
    'ALTER TABLE `agent_memory`
       MODIFY COLUMN `deleted` TINYINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;

SET @v4_094_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND column_name = 'promotion_key_hash'
    )
    AND
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
         WHERE table_schema = @v4_094_schema_name
           AND table_name = 'agent_memory'
           AND index_name = 'uk_agent_memory_promotion'
    ),
    'ALTER TABLE `agent_memory`
       ADD UNIQUE KEY `uk_agent_memory_promotion` (`user_id`, `promotion_key_hash`)',
    'SELECT 1'
);
PREPARE v4_094_stmt FROM @v4_094_sql;
EXECUTE v4_094_stmt;
DEALLOCATE PREPARE v4_094_stmt;
