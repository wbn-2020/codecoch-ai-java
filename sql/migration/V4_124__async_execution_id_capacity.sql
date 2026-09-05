-- V4_124: preserve full execution correlation IDs across task, run, and AI log records.
-- Resume-job-match uses a business-scoped execution ID in the form
-- resume-job-match.analyze:<reportId>:<traceId>, which is longer than the
-- original V4_111 VARCHAR(64) contract. Do not truncate correlation IDs:
-- retries, task lookup, and AI trace joins require exact equality.

DROP PROCEDURE IF EXISTS widen_execution_id_capacity;
DELIMITER //
CREATE PROCEDURE widen_execution_id_capacity()
BEGIN
    DECLARE table_name_value VARCHAR(64);
    DECLARE column_name_value VARCHAR(64);
    DECLARE column_count INT DEFAULT 0;
    DECLARE current_length BIGINT DEFAULT 0;

    SET table_name_value = 'async_task';
    SET column_name_value = 'execution_id';
    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO column_count, current_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = table_name_value
       AND column_name = column_name_value;
    IF column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_124 requires async_task.execution_id from V4_111';
    END IF;
    IF current_length < 128 THEN
        ALTER TABLE `async_task`
            MODIFY COLUMN `execution_id`
                VARCHAR(128)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
                NULL
                COMMENT 'Stable business execution id';
    END IF;

    SET column_name_value = 'parent_execution_id';
    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO column_count, current_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = table_name_value
       AND column_name = column_name_value;
    IF column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_124 requires async_task.parent_execution_id from V4_111';
    END IF;
    IF current_length < 128 THEN
        ALTER TABLE `async_task`
            MODIFY COLUMN `parent_execution_id`
                VARCHAR(128)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
                NULL
                COMMENT 'Parent execution id for retry or compensation';
    END IF;

    SET table_name_value = 'agent_run';
    SET column_name_value = 'execution_id';
    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO column_count, current_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = table_name_value
       AND column_name = column_name_value;
    IF column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_124 requires agent_run.execution_id from V4_111';
    END IF;
    IF current_length < 128 THEN
        ALTER TABLE `agent_run`
            MODIFY COLUMN `execution_id`
                VARCHAR(128)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
                NULL
                COMMENT 'Stable business execution id';
    END IF;

    SET column_name_value = 'parent_execution_id';
    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO column_count, current_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = table_name_value
       AND column_name = column_name_value;
    IF column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_124 requires agent_run.parent_execution_id from V4_111';
    END IF;
    IF current_length < 128 THEN
        ALTER TABLE `agent_run`
            MODIFY COLUMN `parent_execution_id`
                VARCHAR(128)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
                NULL
                COMMENT 'Parent execution id for retry or compensation';
    END IF;

    SET table_name_value = 'ai_call_log';
    SET column_name_value = 'execution_id';
    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO column_count, current_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = table_name_value
       AND column_name = column_name_value;
    IF column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_124 requires ai_call_log.execution_id from V4_111';
    END IF;
    IF current_length < 128 THEN
        ALTER TABLE `ai_call_log`
            MODIFY COLUMN `execution_id`
                VARCHAR(128)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
                NULL
                COMMENT 'Stable business execution id';
    END IF;

    SET column_name_value = 'parent_execution_id';
    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO column_count, current_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = table_name_value
       AND column_name = column_name_value;
    IF column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_124 requires ai_call_log.parent_execution_id from V4_111';
    END IF;
    IF current_length < 128 THEN
        ALTER TABLE `ai_call_log`
            MODIFY COLUMN `parent_execution_id`
                VARCHAR(128)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
                NULL
                COMMENT 'Parent execution id for retry or compensation';
    END IF;
END//
DELIMITER ;

CALL widen_execution_id_capacity();
DROP PROCEDURE IF EXISTS widen_execution_id_capacity;
