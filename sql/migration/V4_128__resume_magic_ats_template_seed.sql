-- V4_128: register the six MAGIC_* templates on the ATS export channel.
-- Server rendering stays single-column (PDFBox); only knobs the renderer can honor vary per template.
-- The designed look for these codes is delivered by the browser print channel, not by these rows.

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'MAGIC_TIMELINE',
    1,
    'Magic Timeline',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":40,"fontFamily":"Arial","nameFontPt":18,"headlineFontPt":10.5,"contactFontPt":8.5,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.1,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","EXPERIENCE","PROJECTS","EDUCATION","SKILLS"]}',
    SHA2('{"pageSize":"A4","marginPt":40,"fontFamily":"Arial","nameFontPt":18,"headlineFontPt":10.5,"contactFontPt":8.5,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.1,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","EXPERIENCE","PROJECTS","EDUCATION","SKILLS"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'MAGIC_TIMELINE'
       AND `template_version` = 1
       AND `deleted` = 0
);

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'MAGIC_MINIMALIST',
    1,
    'Magic Minimalist',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":48,"fontFamily":"Arial","nameFontPt":18,"headlineFontPt":10,"contactFontPt":8.5,"headingFontPt":10,"bodyFontPt":10,"lineSpacing":1.18,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}',
    SHA2('{"pageSize":"A4","marginPt":48,"fontFamily":"Arial","nameFontPt":18,"headlineFontPt":10,"contactFontPt":8.5,"headingFontPt":10,"bodyFontPt":10,"lineSpacing":1.18,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'MAGIC_MINIMALIST'
       AND `template_version` = 1
       AND `deleted` = 0
);

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'MAGIC_ELEGANT',
    1,
    'Magic Elegant',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":46,"fontFamily":"Arial","nameFontPt":19,"headlineFontPt":11,"contactFontPt":8.5,"headingFontPt":10.5,"bodyFontPt":9.5,"lineSpacing":1.22,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}',
    SHA2('{"pageSize":"A4","marginPt":46,"fontFamily":"Arial","nameFontPt":19,"headlineFontPt":11,"contactFontPt":8.5,"headingFontPt":10.5,"bodyFontPt":9.5,"lineSpacing":1.22,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'MAGIC_ELEGANT'
       AND `template_version` = 1
       AND `deleted` = 0
);

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'MAGIC_CREATIVE',
    1,
    'Magic Creative',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":38,"fontFamily":"Arial","nameFontPt":19,"headlineFontPt":11,"contactFontPt":9,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.08,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SKILLS","PROJECTS","SUMMARY","EXPERIENCE","EDUCATION"]}',
    SHA2('{"pageSize":"A4","marginPt":38,"fontFamily":"Arial","nameFontPt":19,"headlineFontPt":11,"contactFontPt":9,"headingFontPt":11,"bodyFontPt":10,"lineSpacing":1.08,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SKILLS","PROJECTS","SUMMARY","EXPERIENCE","EDUCATION"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'MAGIC_CREATIVE'
       AND `template_version` = 1
       AND `deleted` = 0
);

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'MAGIC_EDITORIAL',
    1,
    'Magic Editorial',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":44,"fontFamily":"Arial","nameFontPt":20,"headlineFontPt":10.5,"contactFontPt":8.5,"headingFontPt":10.5,"bodyFontPt":9.5,"lineSpacing":1.2,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","EXPERIENCE","PROJECTS","SKILLS","EDUCATION"]}',
    SHA2('{"pageSize":"A4","marginPt":44,"fontFamily":"Arial","nameFontPt":20,"headlineFontPt":10.5,"contactFontPt":8.5,"headingFontPt":10.5,"bodyFontPt":9.5,"lineSpacing":1.2,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","EXPERIENCE","PROJECTS","SKILLS","EDUCATION"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'MAGIC_EDITORIAL'
       AND `template_version` = 1
       AND `deleted` = 0
);

INSERT INTO `resume_ats_template`
    (`template_code`, `template_version`, `template_name`, `layout_type`,
     `definition_json`, `definition_hash`, `status`)
SELECT
    'MAGIC_SWISS',
    1,
    'Magic Swiss',
    'SINGLE_COLUMN',
    '{"pageSize":"A4","marginPt":40,"fontFamily":"Arial","nameFontPt":19,"headlineFontPt":10,"contactFontPt":8.5,"headingFontPt":10,"bodyFontPt":9.5,"lineSpacing":1.1,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}',
    SHA2('{"pageSize":"A4","marginPt":40,"fontFamily":"Arial","nameFontPt":19,"headlineFontPt":10,"contactFontPt":8.5,"headingFontPt":10,"bodyFontPt":9.5,"lineSpacing":1.1,"columns":1,"tables":false,"textBoxes":false,"headers":false,"footers":false,"sectionOrder":["SUMMARY","SKILLS","EXPERIENCE","PROJECTS","EDUCATION"]}', 256),
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `resume_ats_template`
     WHERE `template_code` = 'MAGIC_SWISS'
       AND `template_version` = 1
       AND `deleted` = 0
);
