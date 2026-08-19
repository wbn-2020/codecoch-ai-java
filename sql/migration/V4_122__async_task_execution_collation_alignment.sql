-- V4_122: keep the async execution correlation key compatible across tables.
-- agent_run uses utf8mb4_unicode_ci while async_task historically inherited the
-- MySQL 8 database default (utf8mb4_0900_ai_ci). Cross-table equality checks
-- otherwise fail with "Illegal mix of collations".

DROP PROCEDURE IF EXISTS align_async_task_execution_collation;
DELIMITER //
CREATE PROCEDURE align_async_task_execution_collation()
BEGIN
    DECLARE execution_column_count INT DEFAULT 0;
    DECLARE execution_collation VARCHAR(64) DEFAULT NULL;

    SELECT COUNT(1), MAX(collation_name)
      INTO execution_column_count, execution_collation
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'async_task'
       AND column_name = 'execution_id';

    IF execution_column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_122 requires async_task.execution_id from V4_111';
    END IF;

    IF execution_collation <> 'utf8mb4_unicode_ci' THEN
        ALTER TABLE `async_task`
            MODIFY COLUMN `execution_id`
                VARCHAR(64)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_unicode_ci
                NULL
                COMMENT 'Stable business execution id';
    END IF;
END//
DELIMITER ;

CALL align_async_task_execution_collation();
DROP PROCEDURE IF EXISTS align_async_task_execution_collation;
