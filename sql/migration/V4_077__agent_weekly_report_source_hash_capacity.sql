-- Weekly evidence services expose source hashes as "sha256:" plus a 64-character digest.
-- Repair installations that ran an earlier V4_076 draft with CHAR(64).

SET @v4_077_source_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_weekly_report_source'
);

SET @v4_077_source_hash_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_weekly_report_source'
      AND column_name = 'source_hash'
);

SET @v4_077_source_hash_capacity = (
    SELECT COALESCE(MAX(character_maximum_length), 0)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_weekly_report_source'
      AND column_name = 'source_hash'
);

SET @v4_077_source_hash_sql = IF(
    @v4_077_source_table_exists = 0,
    'SELECT 1',
    IF(
        @v4_077_source_hash_exists = 0,
        'ALTER TABLE `agent_weekly_report_source`
             ADD COLUMN `source_hash` VARCHAR(80) NULL AFTER `exclude_reason`',
        IF(
            @v4_077_source_hash_capacity < 80,
            'ALTER TABLE `agent_weekly_report_source`
                 MODIFY COLUMN `source_hash` VARCHAR(80) NULL',
            'SELECT 1'
        )
    )
);

PREPARE v4_077_source_hash_stmt FROM @v4_077_source_hash_sql;
EXECUTE v4_077_source_hash_stmt;
DEALLOCATE PREPARE v4_077_source_hash_stmt;
