-- Restore a usable resume-match report while preserving the evidence boundary.

DROP TEMPORARY TABLE IF EXISTS tmp_v4_101_resume_match_prompt;
CREATE TEMPORARY TABLE tmp_v4_101_resume_match_prompt (
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  PRIMARY KEY (template_id)
);

INSERT INTO tmp_v4_101_resume_match_prompt (
  template_id,
  scene,
  content,
  variables_json,
  model_params_json
)
SELECT p.id,
       p.scene,
       CONCAT_WS(CHAR(10),
         '你是资深 Java 后端求职教练。请严格基于本次输入的简历与目标岗位证据，生成中文匹配分析 JSON。',
         'reportId: {{reportId}}',
         'userId: {{userId}}',
         'resumeId: {{resumeId}}',
         'resumeVersionId: {{resumeVersionId}}',
         'targetJobId: {{targetJobId}}',
         'jdAnalysisId: {{jdAnalysisId}}',
         'userExperienceYears: {{userExperienceYears}}',
         'targetJobJson:',
         '{{targetJobJson}}',
         'resumeAnalysisJson:',
         '{{resumeAnalysisJson}}',
         'resumeSnapshotJson:',
         '{{resumeSnapshotJson}}',
         'jobDescriptionAnalysisJson:',
         '{{jobDescriptionAnalysisJson}}',
         '',
         '只输出一个合法 JSON 对象。不要输出 Markdown、代码块、注释、解释文字、尾逗号或单引号。所有 key 必须使用英文双引号。',
         '所有给用户看的标题、描述、摘要和建议必须使用正式中文，可以原样保留输入中已经出现的技术名词。',
         '',
         '严格证据约束：',
         '1. 只能使用上述输入 JSON 中明确出现的信息，不得使用行业常识补全事实。',
         '2. 输入中没有逐字出现的具体框架、中间件、数据库、云厂商、工具、公司、项目、年限、职责、指标或技术产品名称，不得出现在输出的任何字段中。',
         '3. 不得把通用能力要求推断成某个具体实现；输入只有通用概念时，只能沿用通用表述，不能自行补充具体产品名称。',
         '4. strengths 只能写简历中有直接证据的优势；evidence 必须引用或准确概括简历输入中的具体事实。',
         '5. gaps 只能来自岗位输入中明确出现、但简历证据不足的要求；不得新增岗位未要求的学习项。',
         '6. resumeRisks、optimizationSuggestions、recommendedLearningTopics、recommendedInterviewTopics 和 summary 同样受上述证据约束，不得借建议或风险引入输入中不存在的新事实。',
         '7. 如果信息不足，使用“证据不足”“需要补充项目细节”等通用中文表述，不要猜测。',
         '',
         '分数必须为 0 到 100 的整数。证据弱或缺失时降低相应维度分数。',
         '顶层字段固定为 overallScore, dimensionScores, strengths, gaps, resumeRisks, optimizationSuggestions, recommendedLearningTopics, recommendedInterviewTopics, summary，不得增加或遗漏。',
         'dimensionScores 必须包含 techStack, projectExperience, businessFit, communication，值均为 0 到 100 的整数。',
         'strengths 必须是数组，每项包含 title, evidence, relatedSkills；relatedSkills 必须是字符串数组。',
         'gaps 必须是数组，每项包含 skillName, category, severity, targetLevel, currentLevel, description, evidence, recommendedActions；recommendedActions 必须是字符串数组。',
         'resumeRisks 必须是数组，每项包含 riskType, description。',
         'optimizationSuggestions 必须是数组，每项包含 section, suggestion。',
         'recommendedLearningTopics 和 recommendedInterviewTopics 必须是字符串数组。',
         'summary 必须是非空中文字符串，并明确区分已证实优势与证据不足项。'
       ),
       'reportId,userId,resumeId,resumeVersionId,targetJobId,jdAnalysisId,userExperienceYears,resumeAnalysisJson,resumeSnapshotJson,jobDescriptionAnalysisJson,targetJobJson',
       active_version.model_params_json
FROM prompt_template p
LEFT JOIN prompt_template_version active_version
  ON active_version.id = p.active_version_id
 AND active_version.deleted = 0
WHERE p.scene = 'RESUME_JOB_MATCH'
  AND p.deleted = 0
  AND p.status = 1
  AND (p.enabled = 1 OR p.enabled IS NULL)
  AND p.id = (
    SELECT MAX(p2.id)
    FROM prompt_template p2
    WHERE p2.scene = 'RESUME_JOB_MATCH'
      AND p2.deleted = 0
      AND p2.status = 1
      AND (p2.enabled = 1 OR p2.enabled IS NULL)
  );

INSERT INTO prompt_template_version (
  template_id,
  scene,
  version_code,
  version_name,
  content,
  variables_json,
  model_params_json,
  status,
  is_active,
  activated_at,
  change_log,
  deleted
)
SELECT template_id,
       scene,
       'v4-101-evidence-bound',
       'V4.101 resume match strict evidence boundary',
       content,
       variables_json,
       model_params_json,
       'INACTIVE',
       0,
       NULL,
       'Restore strict JSON schema and prohibit inferred facts in all resume-match output fields.',
       0
FROM tmp_v4_101_resume_match_prompt
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

UPDATE prompt_template_version version
JOIN tmp_v4_101_resume_match_prompt target
  ON target.template_id = version.template_id
SET version.status = 'INACTIVE',
    version.is_active = 0
WHERE version.deleted = 0
  AND version.version_code <> 'v4-101-evidence-bound'
  AND (version.is_active = 1 OR version.status = 'ACTIVE');

UPDATE prompt_template_version version
JOIN tmp_v4_101_resume_match_prompt target
  ON target.template_id = version.template_id
 AND version.version_code = 'v4-101-evidence-bound'
SET version.status = 'ACTIVE',
    version.is_active = 1,
    version.activated_at = NOW(),
    version.deleted = 0;

UPDATE prompt_template template
JOIN tmp_v4_101_resume_match_prompt target
  ON target.template_id = template.id
JOIN prompt_template_version version
  ON version.template_id = target.template_id
 AND version.version_code = 'v4-101-evidence-bound'
 AND version.deleted = 0
SET template.active_version_id = version.id,
    template.content = version.content,
    template.template_content = version.content,
    template.variables = version.variables_json,
    template.version = version.version_code,
    template.enabled = 1,
    template.status = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_101_resume_match_prompt;
