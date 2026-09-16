-- V4_144: 弱口令/管理员重置后强制改密标记。
-- 不改写存量哈希，不回填默认 admin 口令；登录时若明文口令过弱再置位。
-- 幂等：information_schema 守卫。

SET @schema_name = DATABASE();

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'must_change_password');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT ''1=登录后必须先改密；管理员重置或弱口令登录置位，改密成功清零''',
  'SELECT ''must_change_password exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
