-- Verifies the exact V4_058 through V4_071 release migration contract.
WITH expected_versions AS (
    SELECT '4.058' AS version UNION ALL
    SELECT '4.059' UNION ALL
    SELECT '4.060' UNION ALL
    SELECT '4.061' UNION ALL
    SELECT '4.062' UNION ALL
    SELECT '4.063' UNION ALL
    SELECT '4.064' UNION ALL
    SELECT '4.065' UNION ALL
    SELECT '4.066' UNION ALL
    SELECT '4.067' UNION ALL
    SELECT '4.068' UNION ALL
    SELECT '4.069' UNION ALL
    SELECT '4.070' UNION ALL
    SELECT '4.071'
)
SELECT 'migration_4_058_4_071_success_count' AS metric, COUNT(*) AS value
FROM expected_versions expected
JOIN flyway_schema_history history
  ON history.version = expected.version
 AND history.success = 1;

WITH expected_versions AS (
    SELECT '4.058' AS version UNION ALL
    SELECT '4.059' UNION ALL
    SELECT '4.060' UNION ALL
    SELECT '4.061' UNION ALL
    SELECT '4.062' UNION ALL
    SELECT '4.063' UNION ALL
    SELECT '4.064' UNION ALL
    SELECT '4.065' UNION ALL
    SELECT '4.066' UNION ALL
    SELECT '4.067' UNION ALL
    SELECT '4.068' UNION ALL
    SELECT '4.069' UNION ALL
    SELECT '4.070' UNION ALL
    SELECT '4.071'
)
SELECT 'migration_4_058_4_071_missing_count' AS metric, COUNT(*) AS value
FROM expected_versions expected
LEFT JOIN flyway_schema_history history
  ON history.version = expected.version
 AND history.success = 1
WHERE history.version IS NULL;

SELECT 'target_table_count' AS metric, COUNT(*) AS value
FROM information_schema.tables
WHERE table_schema = 'codecoachai_v1'
  AND table_name IN (
    'job_requirement',
    'job_requirement_evidence',
    'job_readiness_snapshot',
    'interview_remediation',
    'interview_comparison',
    'interview_rubric_version',
    'interview_scenario_version',
    'interview_scenario_binding',
    'interview_voice_device_check',
    'interview_voice_delivery_analysis',
    'interview_audio_retention_record',
    'interview_audio_cleanup_record',
    'resume_ats_template',
    'resume_artifact',
    'resume_export',
    'resume_suggestion',
    'resume_suggestion_decision',
    'resume_claim_audit',
    'resume_claim_audit_finding',
    'job_experiment_hypothesis',
    'job_experiment_variant',
    'job_experiment_assignment',
    'job_experiment_cohort',
    'job_experiment_attribution',
    'career_calendar_event',
    'career_import_batch',
    'career_import_row',
    'career_import_dedupe_guard'
  );

WITH expected_columns AS (
    SELECT 'project_evidence_id' AS column_name,
           5 AS ordinal_position,
           'bigint' AS data_type,
           'bigint' AS column_type,
           'YES' AS is_nullable,
           CAST(NULL AS CHAR(255)) AS column_default,
           CAST(NULL AS UNSIGNED) AS character_maximum_length,
           CAST(NULL AS CHAR(64)) AS character_set_name,
           'optional project evidence id' AS column_comment
    UNION ALL
    SELECT 'evidence_type', 7, 'varchar', 'varchar(32)', 'NO',
           'PROJECT_EVIDENCE', 32, 'utf8mb4',
           'PROJECT_EVIDENCE/RESUME_MATCH/INTERVIEW_REPORT/APPLICATION_RESULT/QUESTION_PRACTICE'
    UNION ALL
    SELECT 'evidence_id', 8, 'bigint', 'bigint', 'YES',
           NULL, NULL, NULL, 'source aggregate id'
    UNION ALL
    SELECT 'evidence_sub_id', 9, 'bigint', 'bigint', 'YES',
           NULL, NULL, NULL, 'source detail/session id'
    UNION ALL
    SELECT 'title', 10, 'varchar', 'varchar(255)', 'YES',
           NULL, 255, 'utf8mb4', 'evidence display title'
    UNION ALL
    SELECT 'excerpt', 11, 'text', 'text', 'YES',
           NULL, 65535, 'utf8mb4', 'evidence excerpt'
    UNION ALL
    SELECT 'result_source', 12, 'varchar', 'varchar(64)', 'YES',
           NULL, 64, 'utf8mb4', 'trusted business result/status source'
    UNION ALL
    SELECT 'result_score', 13, 'int', 'int', 'YES',
           NULL, NULL, NULL, 'normalized source score 0-100'
    UNION ALL
    SELECT 'occurred_at', 14, 'datetime', 'datetime', 'YES',
           NULL, NULL, NULL, 'business evidence occurrence time'
)
SELECT 'v4_067_evidence_columns_exact_count' AS metric, COUNT(*) AS value
FROM expected_columns expected
JOIN information_schema.columns actual
  ON actual.table_schema = 'codecoachai_v1'
 AND actual.table_name = 'job_requirement_evidence'
 AND actual.column_name = expected.column_name
 AND actual.ordinal_position = expected.ordinal_position
 AND LOWER(actual.data_type) = expected.data_type
 AND LOWER(actual.column_type) = expected.column_type
 AND actual.is_nullable = expected.is_nullable
 AND (actual.column_default <=> expected.column_default)
 AND (actual.character_maximum_length <=> expected.character_maximum_length)
 AND (actual.character_set_name <=> expected.character_set_name)
 AND actual.extra = ''
 AND COALESCE(actual.generation_expression, '') = ''
 AND actual.column_comment = expected.column_comment
JOIN information_schema.tables owner_table
  ON owner_table.table_schema = actual.table_schema
 AND owner_table.table_name = actual.table_name
 AND (
      (expected.character_set_name IS NULL AND actual.collation_name IS NULL)
      OR
      (expected.character_set_name IS NOT NULL
       AND actual.collation_name = owner_table.table_collation)
 );

SELECT 'v4_067_readiness_dimension_exact' AS metric, COUNT(*) AS value
FROM information_schema.columns actual
JOIN information_schema.tables owner_table
  ON owner_table.table_schema = actual.table_schema
 AND owner_table.table_name = actual.table_name
WHERE actual.table_schema = 'codecoachai_v1'
  AND actual.table_name = 'job_readiness_snapshot'
  AND actual.column_name = 'dimension_json'
  AND actual.ordinal_position = 19
  AND actual.data_type = 'longtext'
  AND actual.column_type = 'longtext'
  AND actual.is_nullable = 'YES'
  AND actual.column_default IS NULL
  AND actual.character_maximum_length = 4294967295
  AND actual.character_set_name = 'utf8mb4'
  AND actual.collation_name = owner_table.table_collation
  AND actual.extra = ''
  AND COALESCE(actual.generation_expression, '') = ''
  AND actual.column_comment = 'five-dimension readiness snapshot';

SELECT 'v4_067_evidence_project_index_exact' AS metric, COUNT(*) AS value
FROM (
    SELECT index_name, non_unique,
           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS index_columns
    FROM information_schema.statistics
    WHERE table_schema = 'codecoachai_v1'
      AND table_name = 'job_requirement_evidence'
      AND index_name = 'idx_requirement_evidence_project'
    GROUP BY index_name, non_unique
    HAVING non_unique = 1
       AND index_columns = 'project_evidence_id,project_skill_evidence_id'
) exact_index;

WITH expected_indexes AS (
    SELECT 'job_application' AS table_name,
           'uk_job_application_import_fingerprint' AS index_name,
           'user_id,active_import_fingerprint' AS index_columns
    UNION ALL
    SELECT 'job_experiment_assignment',
           'uk_jea_hypothesis_application',
           'hypothesis_id,active_application_id'
    UNION ALL
    SELECT 'career_calendar_event',
           'uk_cce_user_external_uid',
           'user_id,active_external_uid'
),
actual_indexes AS (
    SELECT table_name, index_name, non_unique,
           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS index_columns
    FROM information_schema.statistics
    WHERE table_schema = 'codecoachai_v1'
    GROUP BY table_name, index_name, non_unique
)
SELECT 'v4_069_active_unique_index_count' AS metric, COUNT(*) AS value
FROM expected_indexes expected
JOIN actual_indexes actual
  ON actual.table_name = expected.table_name
 AND actual.index_name = expected.index_name
 AND actual.non_unique = 0
 AND actual.index_columns = expected.index_columns;

SELECT 'career_import_row_mediumtext_count' AS metric, COUNT(*) AS value
FROM information_schema.columns
WHERE table_schema = 'codecoachai_v1'
  AND table_name = 'career_import_row'
  AND column_name IN ('raw_data_json', 'duplicate_candidates_json')
  AND data_type = 'mediumtext'
  AND is_nullable = 'YES';

SELECT 'v4_071_rubric_seed_exact' AS metric, COUNT(*) AS value
FROM interview_rubric_version
WHERE rubric_code = 'CODECOACH_CORE'
  AND version_no = 1
  AND JSON_VALID(dimensions_json) = 1
  AND JSON_UNQUOTE(JSON_EXTRACT(dimensions_json, '$[0].name')) = '表达结构'
  AND SHA2(dimensions_json, 256) = 'a0e18c609f82c8285037c800b0331109454e5255a269bc48a4bea2f8b4c7683b';

WITH expected_scenarios AS (
    SELECT 'HR_SCREENING' AS scenario_code,
           'HR 初筛' AS scenario_name,
           '训练自我介绍、动机、稳定性和岗位匹配表达' AS description,
           'f40b118fdcdef5b3c1ccaf15358c28101eefca0f3891c870e3bbd29ed7ee8022' AS script_hash
    UNION ALL
    SELECT 'TECHNICAL_FOUNDATION',
           '技术基础',
           '训练语言、并发、JVM、数据库和缓存基础',
           '1509fefe4d46eb41240ce5db7a1ef772fb2c5fa9b39ca8d916562131eae429ce'
    UNION ALL
    SELECT 'TECHNICAL_ROUND_1',
           '技术一面',
           '覆盖基础能力、项目证据和常见线上问题',
           '4d455abf1a321143a03fe9764a88f529c4814c55ea6076e7db9ae6bc711daf3a'
    UNION ALL
    SELECT 'TECHNICAL_ROUND_2',
           '技术二面',
           '强化架构取舍、复杂问题与工程领导力',
           '1b442cdbfa825b91aac8471714c6a360e9bb612e082de52e4da37459c3c3c696'
    UNION ALL
    SELECT 'PROJECT_DEEP_DIVE',
           '项目深挖',
           '围绕真实项目训练背景、职责、难点、方案和结果',
           '74da63cf6ec438f7968aa2c4f58deaf6317f3126fcb67d67bc4ead664fc7a69a'
    UNION ALL
    SELECT 'SYSTEM_DESIGN',
           '系统设计',
           '训练需求澄清、容量估算、架构、数据与故障处理',
           '50970cd57d01c1dfebd64d434da93b2bd05fa62afefd51b5978df478b3ef6acb'
    UNION ALL
    SELECT 'BEHAVIORAL',
           '行为面试',
           '使用可核对经历训练协作、冲突、失败与影响力',
           '360664cbaeb468ec782bd6c9350350bf4ef883a5c6e05d2a5f572e51884b93e1'
    UNION ALL
    SELECT 'STRESS_COMPREHENSIVE',
           '压力追问与综合面试',
           '在明确训练边界下进行高强度追问和综合判断',
           '5602d667d0fd67d4c6b13fb315fcef4629fc26a87959401b6b6bf015e7c991de'
)
SELECT 'v4_071_scenario_seed_exact_count' AS metric, COUNT(*) AS value
FROM expected_scenarios expected
JOIN interview_scenario_version actual
  ON actual.scenario_code = expected.scenario_code
 AND actual.version_no = 1
 AND actual.scenario_name = expected.scenario_name
 AND actual.description = expected.description
 AND JSON_VALID(actual.script_json) = 1
 AND SHA2(actual.script_json, 256) = expected.script_hash;

WITH expected_templates AS (
    SELECT 'ATS_SINGLE_COLUMN' AS template_code UNION ALL
    SELECT 'ATS_COMPACT' UNION ALL
    SELECT 'ATS_PROJECT_FOCUS'
)
SELECT 'ats_active_template_exact_count' AS metric, COUNT(*) AS value
FROM expected_templates expected
JOIN resume_ats_template actual
  ON actual.template_code = expected.template_code
 AND actual.template_version = 1
 AND actual.status = 'ACTIVE'
 AND actual.deleted = 0;
