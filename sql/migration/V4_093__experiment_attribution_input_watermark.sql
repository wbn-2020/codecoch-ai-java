-- V9 stage three: database-backed Experiment V2 attribution input identity.
-- Forward-only and idempotent. Legacy rows keep nullable identity columns.

SET @v4_093_schema_name = DATABASE();

SET @v4_093_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
           AND column_name = 'data_cutoff_at'
    ),
    'ALTER TABLE `job_experiment_attribution`
       ADD COLUMN `data_cutoff_at` DATETIME NULL AFTER `as_of`',
    'SELECT 1'
);
PREPARE v4_093_stmt FROM @v4_093_sql;
EXECUTE v4_093_stmt;
DEALLOCATE PREPARE v4_093_stmt;

SET @v4_093_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
           AND column_name = 'input_hash'
    ),
    'ALTER TABLE `job_experiment_attribution`
       ADD COLUMN `input_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `data_cutoff_at`',
    'SELECT 1'
);
PREPARE v4_093_stmt FROM @v4_093_sql;
EXECUTE v4_093_stmt;
DEALLOCATE PREPARE v4_093_stmt;

SET @v4_093_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
           AND column_name = 'algorithm_version'
    ),
    'ALTER TABLE `job_experiment_attribution`
       ADD COLUMN `algorithm_version` VARCHAR(32) NULL AFTER `input_hash`',
    'SELECT 1'
);
PREPARE v4_093_stmt FROM @v4_093_sql;
EXECUTE v4_093_stmt;
DEALLOCATE PREPARE v4_093_stmt;

SET @v4_093_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
           AND column_name = 'source_watermark'
    ),
    'ALTER TABLE `job_experiment_attribution`
       ADD COLUMN `source_watermark` TEXT NULL AFTER `algorithm_version`',
    'SELECT 1'
);
PREPARE v4_093_stmt FROM @v4_093_sql;
EXECUTE v4_093_stmt;
DEALLOCATE PREPARE v4_093_stmt;

SET @v4_093_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
           AND column_name = 'result_source'
    ),
    'ALTER TABLE `job_experiment_attribution`
       ADD COLUMN `result_source` VARCHAR(24) NOT NULL DEFAULT ''RULE'' AFTER `source_watermark`',
    'SELECT 1'
);
PREPARE v4_093_stmt FROM @v4_093_sql;
EXECUTE v4_093_stmt;
DEALLOCATE PREPARE v4_093_stmt;

SET @v4_093_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
           AND column_name = 'fallback'
    ),
    'ALTER TABLE `job_experiment_attribution`
       ADD COLUMN `fallback` TINYINT NOT NULL DEFAULT 0 AFTER `result_source`',
    'SELECT 1'
);
PREPARE v4_093_stmt FROM @v4_093_sql;
EXECUTE v4_093_stmt;
DEALLOCATE PREPARE v4_093_stmt;

SET @v4_093_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
         WHERE table_schema = @v4_093_schema_name
           AND table_name = 'job_experiment_attribution'
           AND index_name = 'uk_job_experiment_attribution_input'
    ),
    'ALTER TABLE `job_experiment_attribution`
       ADD UNIQUE KEY `uk_job_experiment_attribution_input`
         (`user_id`, `cohort_id`, `input_hash`, `algorithm_version`)',
    'SELECT 1'
);
PREPARE v4_093_stmt FROM @v4_093_sql;
EXECUTE v4_093_stmt;
DEALLOCATE PREPARE v4_093_stmt;
