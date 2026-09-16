-- V4_143: 错题间隔复习升级为 FSRS-lite 记忆强度模型（产品差异化能力）。
-- 模型：user_question_record 增加记忆状态参数（stability/difficulty）与计数字段，
-- 复习调度由固定档位（1/3/7/15 天）改为连续稳定性推导间隔（ReviewScheduler 纯函数）。
-- 兼容：旧三列 review_interval_days/review_stage/next_review_at 保留不删（项目纪律：
-- 不物理删除，先兼容）；存量记录新列为 NULL，调度时走旧档位 fallback，下次复习时初始化模型。
-- 幂等：information_schema 守卫，范式同 V4_140。

SET @schema_name = DATABASE();

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND COLUMN_NAME = 'memory_stability');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `user_question_record` ADD COLUMN `memory_stability` DOUBLE DEFAULT NULL COMMENT ''记忆稳定性(天)，FSRS-lite：可保留率随时间衰减的特征时间；NULL=未初始化走旧档位''',
  'SELECT ''memory_stability exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND COLUMN_NAME = 'memory_difficulty');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `user_question_record` ADD COLUMN `memory_difficulty` DOUBLE DEFAULT NULL COMMENT ''记忆难度 1-10，FSRS-lite：答错+1.0、成功向基准回归；NULL=未初始化''',
  'SELECT ''memory_difficulty exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND COLUMN_NAME = 'review_reps');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `user_question_record` ADD COLUMN `review_reps` INT NOT NULL DEFAULT 0 COMMENT ''复习成功次数（FSRS-lite reps，可大于4不受旧档位上限约束）''',
  'SELECT ''review_reps exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'user_question_record' AND COLUMN_NAME = 'review_lapses');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `user_question_record` ADD COLUMN `review_lapses` INT NOT NULL DEFAULT 0 COMMENT ''复习遗忘（答错）次数''',
  'SELECT ''review_lapses exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 无数据回填：新列默认 NULL/0 即为"未初始化"语义，冷启动兼容由 ReviewScheduler 保证
-- （stability=NULL 的记录首次答错即初始化 stability=1.0/difficulty=5.0 -> 1 天后到期，与旧行为一致）。
-- 不新增索引：到期查询沿用 V4_140 的 idx_user_question_next_review（user_id, wrong, next_review_at）。
