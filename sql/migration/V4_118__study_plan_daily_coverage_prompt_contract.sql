-- V4_118: activate reviewed learning-plan prompts that require one or more
-- executable tasks for every day in the requested schedule.

DROP TEMPORARY TABLE IF EXISTS tmp_v4_118_prompt_definition;
CREATE TEMPORARY TABLE tmp_v4_118_prompt_definition (
  scene VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  version_name VARCHAR(128) NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  PRIMARY KEY (scene)
);

INSERT INTO tmp_v4_118_prompt_definition (
  scene, version_name, content, variables_json
) VALUES
(
  'LEARNING_PLAN_GENERATE',
  'V4.118 complete daily learning-plan coverage',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend interview coach. Generate a practical study plan in Chinese.',
    'learningPlanId: {{learningPlanId}}',
    'userId: {{userId}}',
    'reportId: {{reportId}}',
    'sessionId: {{sessionId}}',
    'targetPosition: {{targetPosition}}',
    'industryDirection: {{industryDirection}}',
    'experienceLevel: {{experienceLevel}}',
    'expectedDurationDays: {{expectedDurationDays}}',
    'dailyMinutes: {{dailyMinutes}}',
    'interviewSummary: {{interviewSummary}}',
    'weaknessSummary: {{weaknessSummary}}',
    'questionPerformanceSummary: {{questionPerformanceSummary}}',
    'resumeWeaknessSummary: {{resumeWeaknessSummary}}',
    'extraRequirements: {{extraRequirements}}',
    'Output only one JSON object with planTitle, planSummary, durationDays, and stages.',
    'Each stage must contain stageNo, stageTitle, and items.',
    'Each item must contain dayOffset, knowledgePoint, taskTitle, taskDescription, taskType, priority, estimatedMinutes, relatedTags, and resources.',
    'dayOffset values must cover every integer day from 1 through expectedDurationDays without gaps.',
    'Every day must contain at least one executable task; multiple tasks on the same day are allowed.',
    'For every dayOffset, total estimatedMinutes must not exceed dailyMinutes.',
    'Every task must trace to supplied weakness or performance evidence. Do not invent candidate evidence.'
  ),
  'learningPlanId,userId,reportId,sessionId,targetPosition,industryDirection,experienceLevel,expectedDurationDays,dailyMinutes,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements'
),
(
  'TARGETED_STUDY_PLAN_GENERATE',
  'V4.118 complete daily targeted-plan coverage',
  CONCAT_WS(CHAR(10),
    'You are a senior Java backend career coach. Generate a gap-driven study plan for the target job.',
    'learningPlanId: {{learningPlanId}}',
    'userId: {{userId}}',
    'targetJobId: {{targetJobId}}',
    'skillProfileId: {{skillProfileId}}',
    'matchReportId: {{matchReportId}}',
    'targetJobJson: {{targetJobJson}}',
    'skillProfileJson: {{skillProfileJson}}',
    'skillGapsJson: {{skillGapsJson}}',
    'availableDays: {{availableDays}}',
    'dailyMinutes: {{dailyMinutes}}',
    'startDate: {{startDate}}',
    'existingStudyPlansJson: {{existingStudyPlansJson}}',
    'planTitle: {{planTitle}}',
    'Output only one JSON object with planTitle, planSummary, durationDays, and stages.',
    'Each stage must contain stageNo, stageTitle, and items.',
    'Each item must contain dayOffset, skillName, sourceGapId, taskTitle, taskDescription, taskType, priority, estimatedMinutes, acceptance, relatedTags, and resources.',
    'dayOffset values must cover every integer day from 1 through availableDays without gaps.',
    'Every day must contain at least one executable task; multiple tasks on the same day are allowed.',
    'For every dayOffset, total estimatedMinutes must not exceed dailyMinutes.',
    'Every task must map to a selected gap. Do not invent candidate experience.'
  ),
  'learningPlanId,userId,targetJobId,skillProfileId,matchReportId,targetJobJson,skillProfileJson,skillGapsJson,availableDays,dailyMinutes,startDate,existingStudyPlansJson,planTitle'
);

DROP TEMPORARY TABLE IF EXISTS tmp_v4_118_prompt_target;
CREATE TEMPORARY TABLE tmp_v4_118_prompt_target (
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  version_name VARCHAR(128) NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  PRIMARY KEY (template_id),
  UNIQUE KEY uk_tmp_v4_118_prompt_target_scene (scene)
);

INSERT INTO tmp_v4_118_prompt_target (
  template_id, scene, version_name, content, variables_json, model_params_json
)
SELECT p.id,
       d.scene,
       d.version_name,
       d.content,
       d.variables_json,
       active_version.model_params_json
FROM prompt_template p
JOIN tmp_v4_118_prompt_definition d ON d.scene = p.scene
LEFT JOIN prompt_template_version active_version
  ON active_version.id = p.active_version_id
 AND active_version.deleted = 0
WHERE p.deleted = 0
  AND p.status = 1
  AND (p.enabled = 1 OR p.enabled IS NULL)
  AND p.id = (
    SELECT MAX(p2.id)
    FROM prompt_template p2
    WHERE p2.scene = p.scene
      AND p2.deleted = 0
      AND p2.status = 1
      AND (p2.enabled = 1 OR p2.enabled IS NULL)
  );

INSERT INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  model_params_json, status, is_active, activated_at, change_log, deleted
)
SELECT t.template_id,
       t.scene,
       'v4-118-daily-coverage',
       t.version_name,
       t.content,
       t.variables_json,
       t.model_params_json,
       'INACTIVE',
       0,
       NULL,
       'V4_118 requires complete daily task coverage and enforces daily-minute budgets',
       0
FROM tmp_v4_118_prompt_target t
ON DUPLICATE KEY UPDATE
  version_name = VALUES(version_name),
  content = VALUES(content),
  variables_json = VALUES(variables_json),
  model_params_json = VALUES(model_params_json),
  status = 'INACTIVE',
  is_active = 0,
  activated_at = NULL,
  change_log = VALUES(change_log),
  deleted = 0;

UPDATE prompt_template_version v
JOIN tmp_v4_118_prompt_target t ON t.template_id = v.template_id
SET v.status = 'INACTIVE',
    v.is_active = 0
WHERE v.deleted = 0
  AND v.version_code <> 'v4-118-daily-coverage'
  AND (v.is_active = 1 OR v.status = 'ACTIVE');

UPDATE prompt_template_version v
JOIN tmp_v4_118_prompt_target t
  ON t.template_id = v.template_id
 AND v.version_code = 'v4-118-daily-coverage'
SET v.status = 'ACTIVE',
    v.is_active = 1,
    v.activated_at = NOW(),
    v.deleted = 0;

UPDATE prompt_template p
JOIN tmp_v4_118_prompt_target t ON t.template_id = p.id
JOIN prompt_template_version v
  ON v.template_id = t.template_id
 AND v.version_code = 'v4-118-daily-coverage'
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.content = v.content,
    p.template_content = v.content,
    p.variables = v.variables_json,
    p.version = v.version_code,
    p.enabled = 1,
    p.status = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_118_prompt_target;
DROP TEMPORARY TABLE IF EXISTS tmp_v4_118_prompt_definition;
