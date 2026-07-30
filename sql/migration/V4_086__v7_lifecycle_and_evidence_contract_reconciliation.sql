-- V8 stage zero: reconcile V7 command audit and server-owned evidence metadata.
-- Forward-only and idempotent. Existing V4_079-V4_085 migrations remain unchanged.

SET @v4_086_schema_name = DATABASE();

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'job_application_event')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'job_application_event'
                      AND column_name = 'request_hash'),
    'ALTER TABLE `job_application_event`
       ADD COLUMN `request_hash` CHAR(64)
         CHARACTER SET ascii COLLATE ascii_bin NULL
         COMMENT ''Normalized command payload hash'' AFTER `idempotency_key_hash`',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

-- Repair columns that already exist with a definition different from the V8 contract.
SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'job_application_event')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_086_schema_name
                  AND table_name = 'job_application_event'
                  AND column_name = 'request_hash')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'job_application_event'
                      AND column_name = 'request_hash'
                      AND data_type = 'char'
                      AND character_maximum_length = 64
                      AND character_set_name = 'ascii'
                      AND collation_name = 'ascii_bin'
                      AND is_nullable = 'YES'
                      AND column_default IS NULL),
    'ALTER TABLE `job_application_event`
       MODIFY COLUMN `request_hash` CHAR(64)
         CHARACTER SET ascii COLLATE ascii_bin NULL
         COMMENT ''Normalized command payload hash''',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'job_application_event')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_086_schema_name
                  AND table_name = 'job_application_event'
                  AND column_name = 'result_lock_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'job_application_event'
                      AND column_name = 'result_lock_version'
                      AND data_type = 'int'
                      AND is_nullable = 'YES'
                      AND column_default IS NULL),
    'ALTER TABLE `job_application_event`
       MODIFY COLUMN `result_lock_version` INT NULL
         COMMENT ''Aggregate lock version after command''',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_event')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_086_schema_name
                  AND table_name = 'career_campaign_event'
                  AND column_name = 'request_hash')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_event'
                      AND column_name = 'request_hash'
                      AND data_type = 'char'
                      AND character_maximum_length = 64
                      AND character_set_name = 'ascii'
                      AND collation_name = 'ascii_bin'
                      AND is_nullable = 'YES'
                      AND column_default IS NULL),
    'ALTER TABLE `career_campaign_event`
       MODIFY COLUMN `request_hash` CHAR(64)
         CHARACTER SET ascii COLLATE ascii_bin NULL
         COMMENT ''Normalized command payload hash''',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_event')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_086_schema_name
                  AND table_name = 'career_campaign_event'
                  AND column_name = 'result_lock_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_event'
                      AND column_name = 'result_lock_version'
                      AND data_type = 'int'
                      AND is_nullable = 'YES'
                      AND column_default IS NULL),
    'ALTER TABLE `career_campaign_event`
       MODIFY COLUMN `result_lock_version` INT NULL
         COMMENT ''Aggregate lock version after command''',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_review_snapshot')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_086_schema_name
                  AND table_name = 'career_campaign_review_snapshot'
                  AND column_name = 'evidence_manifest_json')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_review_snapshot'
                      AND column_name = 'evidence_manifest_json'
                      AND data_type = 'mediumtext'
                      AND is_nullable = 'YES'
                      AND column_default IS NULL),
    'ALTER TABLE `career_campaign_review_snapshot`
       MODIFY COLUMN `evidence_manifest_json` MEDIUMTEXT NULL
         COMMENT ''Server-owned evidence envelope manifest''',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_review_snapshot')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_086_schema_name
                  AND table_name = 'career_campaign_review_snapshot'
                  AND column_name = 'evidence_schema_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_review_snapshot'
                      AND column_name = 'evidence_schema_version'
                      AND data_type = 'varchar'
                      AND character_maximum_length = 24
                      AND is_nullable = 'NO'
                      AND column_default = 'v1'),
    'ALTER TABLE `career_campaign_review_snapshot`
       MODIFY COLUMN `evidence_schema_version` VARCHAR(24) NOT NULL DEFAULT ''v1''
         COMMENT ''Evidence envelope schema version''',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_review_snapshot')
    AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = @v4_086_schema_name
                  AND table_name = 'career_campaign_review_snapshot'
                  AND column_name = 'rule_version')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_review_snapshot'
                      AND column_name = 'rule_version'
                      AND data_type = 'varchar'
                      AND character_maximum_length = 64
                      AND is_nullable = 'YES'
                      AND column_default IS NULL),
    'ALTER TABLE `career_campaign_review_snapshot`
       MODIFY COLUMN `rule_version` VARCHAR(64) NULL
         COMMENT ''Deterministic rule version''',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'job_application_event')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'job_application_event'
                      AND column_name = 'result_lock_version'),
    'ALTER TABLE `job_application_event`
       ADD COLUMN `result_lock_version` INT NULL
         COMMENT ''Aggregate lock version after command'' AFTER `request_hash`',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_event')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_event'
                      AND column_name = 'request_hash'),
    'ALTER TABLE `career_campaign_event`
       ADD COLUMN `request_hash` CHAR(64)
         CHARACTER SET ascii COLLATE ascii_bin NULL
         COMMENT ''Normalized command payload hash'' AFTER `idempotency_key_hash`',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_event')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_event'
                      AND column_name = 'result_lock_version'),
    'ALTER TABLE `career_campaign_event`
       ADD COLUMN `result_lock_version` INT NULL
         COMMENT ''Aggregate lock version after command'' AFTER `request_hash`',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_review_snapshot')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_review_snapshot'
                      AND column_name = 'evidence_manifest_json'),
    'ALTER TABLE `career_campaign_review_snapshot`
       ADD COLUMN `evidence_manifest_json` MEDIUMTEXT NULL
         COMMENT ''Server-owned evidence envelope manifest'' AFTER `next_cycle_actions_json`',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_review_snapshot')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_review_snapshot'
                      AND column_name = 'evidence_schema_version'),
    'ALTER TABLE `career_campaign_review_snapshot`
       ADD COLUMN `evidence_schema_version` VARCHAR(24) NOT NULL DEFAULT ''v1''
         COMMENT ''Evidence envelope schema version'' AFTER `evidence_manifest_json`',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;

SET @v4_086_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = @v4_086_schema_name
              AND table_name = 'career_campaign_review_snapshot')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = @v4_086_schema_name
                      AND table_name = 'career_campaign_review_snapshot'
                      AND column_name = 'rule_version'),
    'ALTER TABLE `career_campaign_review_snapshot`
       ADD COLUMN `rule_version` VARCHAR(64) NULL
         COMMENT ''Deterministic rule version'' AFTER `evidence_schema_version`',
    'SELECT 1'
);
PREPARE v4_086_stmt FROM @v4_086_sql;
EXECUTE v4_086_stmt;
DEALLOCATE PREPARE v4_086_stmt;
