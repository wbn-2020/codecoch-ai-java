-- V4_140: 错题间隔复习（Phase 2 战略项：1/3/7/15 天简单间隔）。
-- 模型：user_question_record 增加复习状态字段；wrong=1 的题目按
-- review_interval_days 决定下次到期时间（next_review_at）。
-- 掌握判定（重答成功，score>=80 或等级 GOOD/EXCELLENT）后推进到下一档；
-- 复习答错则回到第一档（1 天）。mastered 置 wrong=0 自然退出错题本。
-- 幂等：information_schema 守卫，兼容旧 MySQL（参考 V4_112 范式）。

SET @schema_name = DATABASE();

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND COLUMN_NAME = 'review_interval_days');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `user_question_record` ADD COLUMN `review_interval_days` INT NOT NULL DEFAULT 1 COMMENT ''间隔复习档位：1/3/7/15''',
  'SELECT ''review_interval_days exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND COLUMN_NAME = 'next_review_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `user_question_record` ADD COLUMN `next_review_at` DATETIME DEFAULT NULL COMMENT ''下次复习到期时间；wrong=1 时调度''',
  'SELECT ''next_review_at exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND COLUMN_NAME = 'review_stage');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `user_question_record` ADD COLUMN `review_stage` INT NOT NULL DEFAULT 0 COMMENT ''已完成复习次数（决定档位下标）''',
  'SELECT ''review_stage exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 为存量错题补默认调度：到期时间=最后作答+1 天
UPDATE `user_question_record`
SET review_interval_days = 1, review_stage = 0,
    next_review_at = TIMESTAMPADD(DAY, 1, COALESCE(last_answer_at, created_at))
WHERE wrong = 1 AND next_review_at IS NULL;

-- 到期查询索引
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND INDEX_NAME = 'idx_user_question_next_review');
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `user_question_record` ADD INDEX `idx_user_question_next_review` (`user_id`, `wrong`, `next_review_at`)',
  'SELECT ''idx exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
