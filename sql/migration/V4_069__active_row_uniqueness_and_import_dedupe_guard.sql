-- Forward-only repair for soft-delete uniqueness and import request deduplication.

SET @schema_name = DATABASE();

DELIMITER //
DROP PROCEDURE IF EXISTS assert_active_row_uniqueness_ready//
CREATE PROCEDURE assert_active_row_uniqueness_ready()
BEGIN
    DECLARE duplicate_count BIGINT DEFAULT 0;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
    ) THEN
        SELECT COUNT(1)
        INTO duplicate_count
        FROM (
            SELECT user_id, import_fingerprint
            FROM job_application
            WHERE deleted = 0
              AND import_fingerprint IS NOT NULL
            GROUP BY user_id, import_fingerprint
            HAVING COUNT(1) > 1
        ) duplicate_job_applications;

        IF duplicate_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V4_069 found duplicate active job_application import fingerprints; resolve duplicates before migration.';
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_experiment_assignment'
    ) THEN
        SELECT COUNT(1)
        INTO duplicate_count
        FROM (
            SELECT hypothesis_id, application_id
            FROM job_experiment_assignment
            WHERE deleted = 0
            GROUP BY hypothesis_id, application_id
            HAVING COUNT(1) > 1
        ) duplicate_experiment_assignments;

        IF duplicate_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V4_069 found duplicate active experiment assignments; resolve duplicates before migration.';
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'career_calendar_event'
    ) THEN
        SELECT COUNT(1)
        INTO duplicate_count
        FROM (
            SELECT user_id, external_uid COLLATE utf8mb4_bin
            FROM career_calendar_event
            WHERE deleted = 0
              AND external_uid IS NOT NULL
            GROUP BY user_id, external_uid COLLATE utf8mb4_bin
            HAVING COUNT(1) > 1
        ) duplicate_calendar_events;

        IF duplicate_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V4_069 found duplicate active calendar external UIDs; resolve duplicates before migration.';
        END IF;
    END IF;
END//
DELIMITER ;

CALL assert_active_row_uniqueness_ready();
DROP PROCEDURE IF EXISTS assert_active_row_uniqueness_ready;

CREATE TABLE IF NOT EXISTS career_import_dedupe_guard (
    user_id BIGINT NOT NULL,
    identity_hash CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, identity_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Atomic user-scoped guard for career import identities';

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'job_application'
          AND column_name = 'active_import_fingerprint'
    ),
    'ALTER TABLE `job_application`
       ADD COLUMN `active_import_fingerprint` VARCHAR(64)
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `import_fingerprint` ELSE NULL END) STORED
       COMMENT ''Active-only import fingerprint uniqueness key''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'job_application'
      AND index_name = 'uk_job_application_import_fingerprint'
);
SET @sql = IF(
    @index_columns IS NOT NULL
    AND @index_columns <> 'user_id,active_import_fingerprint',
    'ALTER TABLE `job_application`
       DROP INDEX `uk_job_application_import_fingerprint`,
       ADD UNIQUE KEY `uk_job_application_import_fingerprint`
       (`user_id`, `active_import_fingerprint`)',
    IF(
        @index_columns IS NULL
        AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'job_application'
              AND column_name = 'active_import_fingerprint'
        ),
        'ALTER TABLE `job_application`
           ADD UNIQUE KEY `uk_job_application_import_fingerprint`
           (`user_id`, `active_import_fingerprint`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'job_experiment_assignment'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'job_experiment_assignment'
          AND column_name = 'active_application_id'
    ),
    'ALTER TABLE `job_experiment_assignment`
       ADD COLUMN `active_application_id` BIGINT
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `application_id` ELSE NULL END) STORED
       COMMENT ''Active-only application assignment uniqueness key''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'job_experiment_assignment'
      AND index_name = 'uk_jea_hypothesis_application'
);
SET @sql = IF(
    @index_columns IS NOT NULL
    AND @index_columns <> 'hypothesis_id,active_application_id',
    'ALTER TABLE `job_experiment_assignment`
       DROP INDEX `uk_jea_hypothesis_application`,
       ADD UNIQUE KEY `uk_jea_hypothesis_application`
       (`hypothesis_id`, `active_application_id`)',
    IF(
        @index_columns IS NULL
        AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'job_experiment_assignment'
              AND column_name = 'active_application_id'
        ),
        'ALTER TABLE `job_experiment_assignment`
           ADD UNIQUE KEY `uk_jea_hypothesis_application`
           (`hypothesis_id`, `active_application_id`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'career_calendar_event'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'career_calendar_event'
          AND column_name = 'active_external_uid'
    ),
    'ALTER TABLE `career_calendar_event`
       ADD COLUMN `active_external_uid` VARCHAR(255)
       CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `external_uid` ELSE NULL END) STORED
       COMMENT ''Active-only binary external UID uniqueness key''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'career_calendar_event'
      AND index_name = 'uk_cce_user_external_uid'
);
SET @sql = IF(
    @index_columns IS NOT NULL
    AND @index_columns <> 'user_id,active_external_uid',
    'ALTER TABLE `career_calendar_event`
       DROP INDEX `uk_cce_user_external_uid`,
       ADD UNIQUE KEY `uk_cce_user_external_uid`
       (`user_id`, `active_external_uid`)',
    IF(
        @index_columns IS NULL
        AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'career_calendar_event'
              AND column_name = 'active_external_uid'
        ),
        'ALTER TABLE `career_calendar_event`
           ADD UNIQUE KEY `uk_cce_user_external_uid`
           (`user_id`, `active_external_uid`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
