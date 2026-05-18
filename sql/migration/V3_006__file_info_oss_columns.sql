-- ============================================================
-- V3_006__file_info_oss_columns.sql
-- 用途：file_info 表增加 OSS / 文件指纹相关列
-- 影响：codecoachai-file 模块改造为支持阿里云 OSS 的前置
-- ============================================================

-- file_info 表加列（幂等：用 INFORMATION_SCHEMA 判断是否已存在）
SET @dbname = DATABASE();
SET @tbl = 'file_info';

-- oss_key（OSS 完整对象 Key，含 keyPrefix）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'oss_key') = 0,
    'ALTER TABLE file_info ADD COLUMN oss_key VARCHAR(500) NULL COMMENT ''OSS 对象 Key'' AFTER storage_path',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- md5（文件指纹，秒传 / 去重）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'md5') = 0,
    'ALTER TABLE file_info ADD COLUMN md5 VARCHAR(64) NULL COMMENT ''文件 MD5'' AFTER oss_key',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- etag（OSS 返回）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'etag') = 0,
    'ALTER TABLE file_info ADD COLUMN etag VARCHAR(64) NULL COMMENT ''OSS ETag'' AFTER md5',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- bucket（多 bucket 场景区分）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'bucket') = 0,
    'ALTER TABLE file_info ADD COLUMN bucket VARCHAR(128) NULL COMMENT ''OSS Bucket 名称'' AFTER etag',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 调整 storage_provider 默认值与可选值文档（LOCAL / ALIYUN_OSS）
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'storage_provider') > 0,
    'ALTER TABLE file_info MODIFY COLUMN storage_provider VARCHAR(32) NOT NULL DEFAULT ''LOCAL'' COMMENT ''存储提供方：LOCAL / ALIYUN_OSS''',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引：md5 + biz_type 加速重复文件检测；oss_key 唯一防错乱
SET @sql = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
       WHERE table_schema = @dbname AND table_name = @tbl AND index_name = 'idx_file_info_md5_biz') = 0,
    'CREATE INDEX idx_file_info_md5_biz ON file_info (md5, biz_type)',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
