-- V4_142: 题库内容标签治理——question_type 兜底回填 + experience_level 职级枚举归一（数据层保持英文枚举，中文统一在展示层）。
--
-- 依据（2026-09-16 对 sql/init.sql 与 sql/migration/V4_001..V4_141 及后端代码路径的静态排查）：
--   1) question.question_type 为 VARCHAR(32) NOT NULL DEFAULT 'SHORT_ANSWER'（init.sql L117），
--      种子数据实际值域为 SHORT_ANSWER(≈187)/SCENARIO(≈7)/CODING(≈14)/CASE_ANALYSIS(≈51，见 V4_132–V4_139)。
--      题卡"题型待确认"的根因是前端 QuestionMeta.vue / enums.ts 映射表缺 CASE_ANALYSIS 枚举值，
--      数据本身非空且合法，因此本迁移不回填该值域，仅做防御性清洗（大小写/空白变体归一）。
--   2) question.experience_level 为 VARCHAR(64) DEFAULT NULL（init.sql L118），init.sql 与 V4_129~V4_139
--      种子写入英文枚举 JUNIOR(≈45)/MID(≈133)/SENIOR(≈80)。该英文枚举即项目标准值域：
--      后端写入路径同值（AdminQuestionImportController L205 setExperienceLevel("MID")），
--      且 interview_session.experience_level 走的是另一值域（1_YEAR/3_YEARS/5_YEARS，经验年限），二者不可混写。
--      因此本迁移只归一大小写/首尾空白/MIDDLE→MID 等变体，不将数据改写为中文；
--      中英混排的修复放在展示层（前端 enums.ts 职级映射 + QuestionListView 渲染中文）。
--   3) 防御性回填：question_type 为 NULL/空串/纯空白 → 'SHORT_ANSWER'，与 DDL 默认值及后端
--      firstText(..., "SHORT_ANSWER") 兜底（QuestionServiceImpl L512、QuestionImportServiceImpl L1002、
--      QuestionGenerateConsumer L85/L117）一致，有明确依据；该列 NOT NULL，预期命中 0 行，纯防御。
--
-- 影响行数估计（注释说明，不在迁移中执行）：
--   SELECT COUNT(*) FROM question WHERE question_type IS NULL OR TRIM(question_type) = '';
--     -- 估计 0（列 NOT NULL DEFAULT，防御性兜底）
--   SELECT COUNT(*) FROM question WHERE question_type IS NOT NULL
--     AND UPPER(TRIM(question_type)) IN ('SHORT_ANSWER','SCENARIO','CODING','CASE_ANALYSIS')
--     AND BINARY question_type NOT IN ('SHORT_ANSWER','SCENARIO','CODING','CASE_ANALYSIS');
--     -- 估计 0（种子均为大写规范值；防御 AI 审核通过等旁路写入的小写/带空白变体）
--   SELECT COUNT(*) FROM question WHERE experience_level IS NOT NULL
--     AND UPPER(TRIM(experience_level)) IN ('JUNIOR','MID','MIDDLE','SENIOR')
--     AND BINARY experience_level NOT IN ('JUNIOR','MID','SENIOR');
--     -- 估计 0（种子均为大写规范值；MIDDLE 变体在既有 SQL 中未出现，属防御）
--   SELECT COUNT(*) FROM question WHERE question_type = 'CASE_ANALYSIS';
--     -- 估计 ≈51（V4_132:9/V4_133:6/V4_134:5/V4_135:4/V4_136:6/V4_137:6/V4_138:7/V4_139:8，本迁移不改数据，前端补映射）
--
-- 幂等：information_schema 守卫列存在性；UPDATE 谓词均排除"已是规范值"的行（BINARY 精确比较，
-- 避免 ci 排序规则下 'mid' = 'MID' 恒真导致漏改/空改）；重复执行 0 行变更。兼容旧 MySQL（无 CTE/窗口函数/JSON）。

SET @schema_name = DATABASE();

-- ===== 1. question_type：空值防御性回填为 SHORT_ANSWER =====
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'question' AND COLUMN_NAME = 'question_type');
SET @sql = IF(@col_exists > 0,
  'UPDATE `question` SET `question_type` = ''SHORT_ANSWER'' WHERE `question_type` IS NULL OR TRIM(`question_type`) = ''''',
  'SELECT ''question.question_type missing, skip''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 2. question_type：大小写/首尾空白变体归一（仅限标准值域内，不动其它值） =====
SET @sql = IF(@col_exists > 0,
  'UPDATE `question` SET `question_type` = UPPER(TRIM(`question_type`)) WHERE `question_type` IS NOT NULL AND UPPER(TRIM(`question_type`)) IN (''SHORT_ANSWER'',''SCENARIO'',''CODING'',''CASE_ANALYSIS'') AND BINARY `question_type` NOT IN (''SHORT_ANSWER'',''SCENARIO'',''CODING'',''CASE_ANALYSIS'')',
  'SELECT ''question.question_type missing, skip''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 3. experience_level：职级枚举归一（MIDDLE→MID、大小写/空白变体；中文/其它值与非空未知值不覆盖） =====
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'question' AND COLUMN_NAME = 'experience_level');
SET @sql = IF(@col_exists > 0,
  'UPDATE `question` SET `experience_level` = CASE UPPER(TRIM(`experience_level`)) WHEN ''JUNIOR'' THEN ''JUNIOR'' WHEN ''MID'' THEN ''MID'' WHEN ''MIDDLE'' THEN ''MID'' WHEN ''SENIOR'' THEN ''SENIOR'' END WHERE `experience_level` IS NOT NULL AND UPPER(TRIM(`experience_level`)) IN (''JUNIOR'',''MID'',''MIDDLE'',''SENIOR'') AND BINARY `experience_level` NOT IN (''JUNIOR'',''MID'',''SENIOR'')',
  'SELECT ''question.experience_level missing, skip''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
