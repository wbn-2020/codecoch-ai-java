-- V11 block A: owner-scoped portfolio rehearsal session state.
-- Persists which route the user is rehearsing, node cursor, completed nodes and elapsed timer.
-- Forward-only and idempotent. The CREATE TABLE statement is atomic in MySQL.

SET @v4_095_schema_name = DATABASE();

SET @v4_095_sql = IF(
    NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = @v4_095_schema_name
           AND table_name = 'portfolio_rehearsal_session'
    ),
    'CREATE TABLE `portfolio_rehearsal_session` (
       `id` BIGINT NOT NULL AUTO_INCREMENT,
       `user_id` BIGINT NOT NULL,
       `active_route_key` VARCHAR(32) NOT NULL DEFAULT ''quick'',
       `active_node_index` INT NOT NULL DEFAULT 0,
       `elapsed_seconds` INT NOT NULL DEFAULT 0,
       `completed_node_ids` VARCHAR(2000) NOT NULL DEFAULT ''[]'',
       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
       `deleted` TINYINT NOT NULL DEFAULT 0,
       PRIMARY KEY (`id`),
       UNIQUE KEY `uk_rehearsal_session_user` (`user_id`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
       COMMENT=''V11 portfolio rehearsal session state''',
    'SELECT 1'
);
PREPARE v4_095_stmt FROM @v4_095_sql;
EXECUTE v4_095_stmt;
DEALLOCATE PREPARE v4_095_stmt;
