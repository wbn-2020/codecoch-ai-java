SET @schema_name = DATABASE();

SET @ddl = (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'agent_task' AND column_name = 'defer_reason'),
    'ALTER TABLE `agent_task` ADD COLUMN `defer_reason` VARCHAR(512) NULL COMMENT ''Defer reason'' AFTER `skip_reason`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = @schema_name AND table_name = 'agent_task')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'agent_task' AND column_name = 'deferred_at'),
    'ALTER TABLE `agent_task` ADD COLUMN `deferred_at` DATETIME NULL COMMENT ''Deferred time'' AFTER `completed_at`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
