-- Forward-only repair for sys_role_menu soft-delete lifecycle.
--
-- The original unique key (role_id, menu_id, deleted) permits one active and one
-- deleted row, but the second delete-recreate cycle collides with the first
-- tombstone. MySQL unique indexes allow multiple NULL values, so the generated
-- active_menu_id enforces uniqueness only for deleted = 0 rows.

SET @schema_name = DATABASE();

DELIMITER //
DROP PROCEDURE IF EXISTS assert_role_menu_active_uniqueness_ready//
CREATE PROCEDURE assert_role_menu_active_uniqueness_ready()
BEGIN
    DECLARE duplicate_count BIGINT DEFAULT 0;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'sys_role_menu'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_097 requires sys_role_menu from V3_015';
    END IF;

    SELECT COUNT(1)
    INTO duplicate_count
    FROM (
        SELECT role_id, menu_id
        FROM sys_role_menu
        WHERE deleted = 0
        GROUP BY role_id, menu_id
        HAVING COUNT(1) > 1
    ) duplicate_active_role_menus;

    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_097 found duplicate active sys_role_menu rows; resolve duplicates before migration';
    END IF;
END//
DELIMITER ;

CALL assert_role_menu_active_uniqueness_ready();
DROP PROCEDURE IF EXISTS assert_role_menu_active_uniqueness_ready;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = @schema_name
          AND table_name = 'sys_role_menu'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'sys_role_menu'
          AND column_name = 'active_menu_id'
    ),
    'ALTER TABLE `sys_role_menu`
       ADD COLUMN `active_menu_id` BIGINT
       GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `menu_id` ELSE NULL END) STORED
       COMMENT ''Active-only role-menu uniqueness key''
       AFTER `menu_id`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'sys_role_menu'
      AND index_name = 'uk_role_menu'
);

SET @index_non_unique = (
    SELECT MAX(non_unique)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'sys_role_menu'
      AND index_name = 'uk_role_menu'
);

SET @sql = IF(
    @index_columns IS NOT NULL
    AND (
        @index_columns <> 'role_id,active_menu_id'
        OR COALESCE(@index_non_unique, 1) <> 0
    ),
    'ALTER TABLE `sys_role_menu`
       DROP INDEX `uk_role_menu`,
       ADD UNIQUE KEY `uk_role_menu` (`role_id`, `active_menu_id`)',
    IF(
        @index_columns IS NULL
        AND EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = @schema_name
              AND table_name = 'sys_role_menu'
              AND column_name = 'active_menu_id'
        ),
        'ALTER TABLE `sys_role_menu`
           ADD UNIQUE KEY `uk_role_menu` (`role_id`, `active_menu_id`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @schema_name = NULL;
SET @index_columns = NULL;
SET @index_non_unique = NULL;
SET @sql = NULL;
