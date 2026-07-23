CREATE TABLE IF NOT EXISTS `resume_ats_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `template_code` VARCHAR(64) NOT NULL,
    `template_version` INT NOT NULL,
    `template_name` VARCHAR(120) NOT NULL,
    `layout_type` VARCHAR(32) NOT NULL DEFAULT 'SINGLE_COLUMN',
    `definition_json` TEXT NOT NULL,
    `definition_hash` CHAR(64) NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resume_ats_template_version` (`template_code`, `template_version`, `deleted`),
    KEY `idx_resume_ats_template_active` (`template_code`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned fixed ATS resume templates';

CREATE TABLE IF NOT EXISTS `resume_artifact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `artifact_type` VARCHAR(32) NOT NULL COMMENT 'RESUME_EXPORT/APPLICATION_ZIP',
    `source_resume_id` BIGINT DEFAULT NULL,
    `source_resume_version_id` BIGINT DEFAULT NULL,
    `source_application_package_id` BIGINT DEFAULT NULL,
    `source_hash` CHAR(64) NOT NULL,
    `template_code` VARCHAR(64) DEFAULT NULL,
    `template_version` INT DEFAULT NULL,
    `file_id` BIGINT DEFAULT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `mime_type` VARCHAR(120) NOT NULL,
    `file_size` BIGINT DEFAULT NULL,
    `sha256` CHAR(64) DEFAULT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'GENERATING',
    `manifest_json` MEDIUMTEXT DEFAULT NULL,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_resume_artifact_user_status` (`user_id`, `status`, `updated_at`),
    KEY `idx_resume_artifact_source_version` (`source_resume_version_id`, `deleted`),
    KEY `idx_resume_artifact_package` (`source_application_package_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Generated resume and application package artifact metadata';

CREATE TABLE IF NOT EXISTS `resume_export` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `resume_id` BIGINT NOT NULL,
    `resume_version_id` BIGINT NOT NULL,
    `source_hash` CHAR(64) NOT NULL,
    `template_id` BIGINT NOT NULL,
    `template_code` VARCHAR(64) NOT NULL,
    `template_version` INT NOT NULL,
    `export_format` VARCHAR(16) NOT NULL COMMENT 'PDF/DOCX',
    `artifact_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'GENERATING',
    `content_hash` CHAR(64) DEFAULT NULL,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_resume_export_user_version` (`user_id`, `resume_version_id`, `created_at`),
    KEY `idx_resume_export_artifact` (`artifact_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Formal resume export request and result';

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'ATS_SINGLE_COLUMN',
    1,
    'ATS Single Column',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":42,"fontFamily":"Noto Sans","nameFontPt":18,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.08,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false}',
    SHA2('{"pageSize":"A4","marginPt":42,"fontFamily":"Noto Sans","nameFontPt":18,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.08,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'ATS_SINGLE_COLUMN'
       AND `template_version` = 1
       AND `deleted` = 0
);

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'ATS_COMPACT',
    1,
    'ATS Compact',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":32,"fontFamily":"Arial","nameFontPt":17,"headlineFontPt":10,"contactFontPt":8,"headingFontPt":10,"bodyFontPt":9,"lineSpacing":1.0,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}',
    SHA2('{"pageSize":"A4","marginPt":32,"fontFamily":"Arial","nameFontPt":17,"headlineFontPt":10,"contactFontPt":8,"headingFontPt":10,"bodyFontPt":9,"lineSpacing":1.0,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'ATS_COMPACT'
       AND `template_version` = 1
       AND `deleted` = 0
);

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'ATS_PROJECT_FOCUS',
    1,
    'ATS Project Focus',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":40,"fontFamily":"Arial","nameFontPt":18,"headlineFontPt":11,"contactFontPt":9,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.12,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","PROJECTS","EXPERIENCE","EDUCATION"]}',
    SHA2('{"pageSize":"A4","marginPt":40,"fontFamily":"Arial","nameFontPt":18,"headlineFontPt":11,"contactFontPt":9,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.12,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","PROJECTS","EXPERIENCE","EDUCATION"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'ATS_PROJECT_FOCUS'
       AND `template_version` = 1
       AND `deleted` = 0
);
