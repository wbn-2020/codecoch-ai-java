-- Activate a fact-bounded JOB_DESCRIPTION_PARSE prompt without rewriting prior migrations.

DROP TEMPORARY TABLE IF EXISTS tmp_v4_105_jd_parse_prompt;
CREATE TEMPORARY TABLE tmp_v4_105_jd_parse_prompt (
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  PRIMARY KEY (template_id)
);

INSERT INTO tmp_v4_105_jd_parse_prompt (
  template_id,
  scene,
  content,
  variables_json,
  model_params_json
)
SELECT template.id,
       template.scene,
       CONCAT_WS(CHAR(10),
         'You are a senior Java backend career coach. Parse the target job JD into structured JSON.',
         'targetJobId: {{targetJobId}}',
         'userId: {{userId}}',
         'jobTitle: {{jobTitle}}',
         'companyName: {{companyName}}',
         'jobLevel: {{jobLevel}}',
         'jdSource: {{jdSource}}',
         'userTargetDirection: {{userTargetDirection}}',
         'JD:',
         '{{jdText}}',
         '',
         'Strict fact constraints:',
         '1. JD text is the only source of technical facts. jobTitle, companyName, jobLevel, and userTargetDirection are role context only; userTargetDirection must not supply technical facts.',
         '2. Do not infer a concrete product from a general concept. Containerized deployment, cloud native, and microservices do not imply Kubernetes/K8s or any other specific product.',
         '3. A concrete technology product may appear in any output field only when it is explicitly stated in JD text. When evidence is absent, preserve the general wording or use an empty value.',
         '4. Do not invent company facts beyond the JD.',
         '',
         'Output only one valid JSON object. Do not output Markdown, code fences, comments, or explanations.',
         'Top-level fields must be: jobTitle, companyName, jobLevel, responsibilities, requiredSkills, bonusSkills, techStackKeywords, businessKeywords, experienceRequirement, projectExperienceRequirement, interviewFocusPoints, skillWeights, summary.',
         'responsibilities, requiredSkills, bonusSkills, techStackKeywords, businessKeywords, and interviewFocusPoints must be arrays.',
         'requiredSkills and bonusSkills items must contain name, category, requiredLevel, weight, and evidence.',
         'interviewFocusPoints items must contain topic and reason. skillWeights must be an object keyed by skill name.'
       ),
       'targetJobId,userId,jobTitle,companyName,jobLevel,jdText,jdSource,userTargetDirection',
       active_version.model_params_json
FROM prompt_template template
LEFT JOIN prompt_template_version active_version
  ON active_version.id = template.active_version_id
 AND active_version.deleted = 0
WHERE template.scene = 'JOB_DESCRIPTION_PARSE'
  AND template.deleted = 0
  AND template.status = 1
  AND (template.enabled = 1 OR template.enabled IS NULL)
  AND template.id = (
    SELECT MAX(candidate.id)
    FROM prompt_template candidate
    WHERE candidate.scene = 'JOB_DESCRIPTION_PARSE'
      AND candidate.deleted = 0
      AND candidate.status = 1
      AND (candidate.enabled = 1 OR candidate.enabled IS NULL)
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
       'v4-105-jd-fact-boundary',
       'V4.105 JD parse technical fact boundary',
       content,
       variables_json,
       model_params_json,
       'INACTIVE',
       0,
       NULL,
       'Restrict technical facts to JD text and prohibit product inference from general concepts.',
       0
FROM tmp_v4_105_jd_parse_prompt
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
JOIN tmp_v4_105_jd_parse_prompt target
  ON target.template_id = version.template_id
SET version.status = 'INACTIVE',
    version.is_active = 0
WHERE version.deleted = 0
  AND version.version_code <> 'v4-105-jd-fact-boundary'
  AND (version.is_active = 1 OR version.status = 'ACTIVE');

UPDATE prompt_template_version version
JOIN tmp_v4_105_jd_parse_prompt target
  ON target.template_id = version.template_id
 AND version.version_code = 'v4-105-jd-fact-boundary'
SET version.status = 'ACTIVE',
    version.is_active = 1,
    version.activated_at = NOW(),
    version.deleted = 0;

UPDATE prompt_template template
JOIN tmp_v4_105_jd_parse_prompt target
  ON target.template_id = template.id
JOIN prompt_template_version version
  ON version.template_id = target.template_id
 AND version.version_code = 'v4-105-jd-fact-boundary'
 AND version.deleted = 0
SET template.active_version_id = version.id,
    template.content = version.content,
    template.template_content = version.content,
    template.variables = version.variables_json,
    template.version = version.version_code,
    template.enabled = 1,
    template.status = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_105_jd_parse_prompt;
