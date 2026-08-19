-- V4_111: compatible execution correlation and terminal-reason contract.
-- This migration only adds nullable metadata columns. It never rewrites or deletes history.

SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS add_execution_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_execution_column_if_missing(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN definition_sql TEXT
)
BEGIN
    DECLARE column_count INT DEFAULT 0;
    SELECT COUNT(1)
      INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = target_table
       AND column_name = target_column;
    IF column_count = 0 THEN
        SET @alter_sql = CONCAT(
            'ALTER TABLE `', target_table, '` ADD COLUMN `',
            target_column, '` ', definition_sql
        );
        PREPARE execution_stmt FROM @alter_sql;
        EXECUTE execution_stmt;
        DEALLOCATE PREPARE execution_stmt;
    END IF;
END//
DELIMITER ;

CALL add_execution_column_if_missing('agent_run', 'execution_id',
    'VARCHAR(64) NULL COMMENT ''Stable business execution id'' AFTER `status`');
CALL add_execution_column_if_missing('agent_run', 'parent_execution_id',
    'VARCHAR(64) NULL COMMENT ''Parent execution id for retry or compensation'' AFTER `execution_id`');
CALL add_execution_column_if_missing('agent_run', 'idempotency_key',
    'VARCHAR(128) NULL COMMENT ''Caller idempotency key'' AFTER `parent_execution_id`');
CALL add_execution_column_if_missing('agent_run', 'attempt_no',
    'INT NOT NULL DEFAULT 1 COMMENT ''Execution attempt number'' AFTER `idempotency_key`');
CALL add_execution_column_if_missing('agent_run', 'task_message_id',
    'VARCHAR(64) NULL COMMENT ''Registered async task message id'' AFTER `execution_token`');
CALL add_execution_column_if_missing('agent_run', 'terminal_reason_code',
    'VARCHAR(128) NULL COMMENT ''Unified terminal reason code'' AFTER `task_message_id`');
CALL add_execution_column_if_missing('agent_run', 'execution_source',
    'VARCHAR(32) NULL COMMENT ''PRIMARY_MODEL/FALLBACK_MODEL/RULE_ENGINE/MOCK'' AFTER `result_source`');
CALL add_execution_column_if_missing('agent_run', 'delivery_quality',
    'VARCHAR(32) NULL COMMENT ''COMPLETE/DEGRADED/FAILED'' AFTER `execution_source`');
CALL add_execution_column_if_missing('agent_run', 'fallback_reason_code',
    'VARCHAR(128) NULL COMMENT ''Reason for fallback or degraded delivery'' AFTER `delivery_quality`');
CALL add_execution_column_if_missing('agent_run', 'schema_version',
    'VARCHAR(64) NULL COMMENT ''Business result schema version'' AFTER `fallback_reason_code`');
CALL add_execution_column_if_missing('agent_run', 'validation_status',
    'VARCHAR(32) NULL COMMENT ''Business result validation status'' AFTER `schema_version`');

CALL add_execution_column_if_missing('async_task', 'execution_id',
    'VARCHAR(64) NULL COMMENT ''Stable business execution id'' AFTER `trace_id`');
CALL add_execution_column_if_missing('async_task', 'parent_execution_id',
    'VARCHAR(64) NULL COMMENT ''Parent execution id for retry or compensation'' AFTER `execution_id`');
CALL add_execution_column_if_missing('async_task', 'run_id',
    'BIGINT NULL COMMENT ''Related Agent or business run id'' AFTER `parent_execution_id`');
CALL add_execution_column_if_missing('async_task', 'attempt_no',
    'INT NOT NULL DEFAULT 1 COMMENT ''Execution attempt number'' AFTER `run_id`');
CALL add_execution_column_if_missing('async_task', 'idempotency_key',
    'VARCHAR(128) NULL COMMENT ''Caller idempotency key'' AFTER `attempt_no`');
CALL add_execution_column_if_missing('async_task', 'terminal_reason_code',
    'VARCHAR(128) NULL COMMENT ''Unified terminal reason code'' AFTER `failure_reason`');

CALL add_execution_column_if_missing('ai_call_log', 'execution_id',
    'VARCHAR(64) NULL COMMENT ''Stable business execution id'' AFTER `business_id`');
CALL add_execution_column_if_missing('ai_call_log', 'parent_execution_id',
    'VARCHAR(64) NULL COMMENT ''Parent execution id for retry or compensation'' AFTER `execution_id`');
CALL add_execution_column_if_missing('ai_call_log', 'attempt_no',
    'INT NULL COMMENT ''Execution attempt number'' AFTER `parent_execution_id`');
CALL add_execution_column_if_missing('ai_call_log', 'idempotency_key',
    'VARCHAR(128) NULL COMMENT ''Caller idempotency key'' AFTER `attempt_no`');
CALL add_execution_column_if_missing('ai_call_log', 'execution_source',
    'VARCHAR(32) NULL COMMENT ''PRIMARY_MODEL/FALLBACK_MODEL/RULE_ENGINE/MOCK'' AFTER `idempotency_key`');
CALL add_execution_column_if_missing('ai_call_log', 'delivery_quality',
    'VARCHAR(32) NULL COMMENT ''COMPLETE/DEGRADED/FAILED'' AFTER `execution_source`');
CALL add_execution_column_if_missing('ai_call_log', 'fallback_reason_code',
    'VARCHAR(128) NULL COMMENT ''Reason for fallback or degraded delivery'' AFTER `delivery_quality`');
CALL add_execution_column_if_missing('ai_call_log', 'schema_version',
    'VARCHAR(64) NULL COMMENT ''Business result schema version'' AFTER `fallback_reason_code`');
CALL add_execution_column_if_missing('ai_call_log', 'validation_status',
    'VARCHAR(32) NULL COMMENT ''Business result validation status'' AFTER `schema_version`');

DROP PROCEDURE IF EXISTS add_execution_column_if_missing;

SET @index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = @schema_name
               AND table_name = 'agent_run'
               AND index_name = 'idx_agent_run_execution_contract'
        ),
        'SELECT 1',
        'ALTER TABLE `agent_run` ADD INDEX `idx_agent_run_execution_contract` (`execution_id`, `user_id`, `status`, `deleted`)'
    )
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = @schema_name
               AND table_name = 'async_task'
               AND index_name = 'idx_async_task_execution_contract'
        ),
        'SELECT 1',
        'ALTER TABLE `async_task` ADD INDEX `idx_async_task_execution_contract` (`execution_id`, `status`, `deleted`, `created_at`)'
    )
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

SET @index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
              FROM information_schema.statistics
             WHERE table_schema = @schema_name
               AND table_name = 'ai_call_log'
               AND index_name = 'idx_ai_call_execution_contract'
        ),
        'SELECT 1',
        'ALTER TABLE `ai_call_log` ADD INDEX `idx_ai_call_execution_contract` (`execution_id`, `created_at`)'
    )
);
PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;
