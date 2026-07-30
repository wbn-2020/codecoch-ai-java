-- Widen AI-generated resume match detail text without truncating stored evidence.

SET @v4_103_detail_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'resume_job_match_detail'
);

SET @v4_103_skill_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'resume_job_match_detail'
      AND index_name = 'idx_resume_match_detail_skill'
);

SET @v4_103_sql = IF(
    @v4_103_detail_table_exists = 0,
    'SELECT 1',
    IF(
        @v4_103_skill_index_exists > 0,
        'ALTER TABLE `resume_job_match_detail`
           DROP INDEX `idx_resume_match_detail_skill`,
           MODIFY COLUMN `dimension` VARCHAR(255) DEFAULT NULL COMMENT ''match dimension'',
           MODIFY COLUMN `skill_name` TEXT DEFAULT NULL COMMENT ''skill name or related skills JSON'',
           MODIFY COLUMN `evidence` MEDIUMTEXT DEFAULT NULL COMMENT ''resume or JD evidence'',
           MODIFY COLUMN `gap_description` MEDIUMTEXT DEFAULT NULL COMMENT ''gap description'',
           MODIFY COLUMN `suggestion` MEDIUMTEXT DEFAULT NULL COMMENT ''improvement suggestion or actions JSON'',
           ADD KEY `idx_resume_match_detail_skill` (`skill_name`(191), `deleted`)',
        'ALTER TABLE `resume_job_match_detail`
           MODIFY COLUMN `dimension` VARCHAR(255) DEFAULT NULL COMMENT ''match dimension'',
           MODIFY COLUMN `skill_name` TEXT DEFAULT NULL COMMENT ''skill name or related skills JSON'',
           MODIFY COLUMN `evidence` MEDIUMTEXT DEFAULT NULL COMMENT ''resume or JD evidence'',
           MODIFY COLUMN `gap_description` MEDIUMTEXT DEFAULT NULL COMMENT ''gap description'',
           MODIFY COLUMN `suggestion` MEDIUMTEXT DEFAULT NULL COMMENT ''improvement suggestion or actions JSON'',
           ADD KEY `idx_resume_match_detail_skill` (`skill_name`(191), `deleted`)'
    )
);

PREPARE v4_103_stmt FROM @v4_103_sql;
EXECUTE v4_103_stmt;
DEALLOCATE PREPARE v4_103_stmt;
