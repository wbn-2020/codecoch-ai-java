-- V4_114: human governance metadata for failed and stale asynchronous tasks.
-- Adds nullable/recoverable metadata only. No historical task is deleted or retried.

DROP PROCEDURE IF EXISTS add_task_governance_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_task_governance_column_if_missing(
    IN target_column VARCHAR(64),
    IN definition_sql TEXT
)
BEGIN
    DECLARE column_count INT DEFAULT 0;
    SELECT COUNT(1)
      INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'async_task'
       AND column_name = target_column;
    IF column_count = 0 THEN
        SET @alter_sql = CONCAT(
            'ALTER TABLE `async_task` ADD COLUMN `',
            target_column, '` ', definition_sql
        );
        PREPARE governance_stmt FROM @alter_sql;
        EXECUTE governance_stmt;
        DEALLOCATE PREPARE governance_stmt;
    END IF;
END//
DELIMITER ;

CALL add_task_governance_column_if_missing('governance_status',
    'VARCHAR(32) NOT NULL DEFAULT ''UNASSESSED'' COMMENT ''UNASSESSED/RETRY_APPROVED/RETRYING/RESOLVED/WONT_RETRY/MANUAL_ACTION_REQUIRED'' AFTER `terminal_reason_code`');
CALL add_task_governance_column_if_missing('governance_reason',
    'VARCHAR(500) NULL COMMENT ''Operator or system governance reason'' AFTER `governance_status`');
CALL add_task_governance_column_if_missing('governance_owner',
    'VARCHAR(128) NULL COMMENT ''Responsible team or operator role'' AFTER `governance_reason`');
CALL add_task_governance_column_if_missing('governance_updated_at',
    'DATETIME NULL COMMENT ''Governance lifecycle update time'' AFTER `governance_owner`');
CALL add_task_governance_column_if_missing('retry_preview_hash',
    'VARCHAR(64) NULL COMMENT ''Hash of the reviewed retry/governance preview'' AFTER `governance_updated_at`');

DROP PROCEDURE IF EXISTS add_task_governance_column_if_missing;

SET @schema_name = DATABASE();
SET @index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = @schema_name
               AND table_name = 'async_task'
               AND index_name = 'idx_async_task_governance'
        ),
        'SELECT 1',
        'ALTER TABLE `async_task` ADD INDEX `idx_async_task_governance` (`governance_status`, `status`, `deleted`, `created_at`)'
    )
);
PREPARE governance_index_stmt FROM @index_sql;
EXECUTE governance_index_stmt;
DEALLOCATE PREPARE governance_index_stmt;
