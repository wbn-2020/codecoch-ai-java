-- Align the active interview-report prompt with the persisted scoring contract.

DROP TEMPORARY TABLE IF EXISTS tmp_v4_102_interview_report_prompt;
CREATE TEMPORARY TABLE tmp_v4_102_interview_report_prompt (
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  PRIMARY KEY (template_id)
);

INSERT INTO tmp_v4_102_interview_report_prompt (
  template_id,
  scene,
  content,
  variables_json,
  model_params_json
)
SELECT p.id,
       p.scene,
       CONCAT_WS(CHAR(10),
         '你是资深 Java 面试教练。请只依据本次输入中的简历、项目和面试消息生成正式中文报告。',
         'targetJobId: {{targetJobId}}',
         'skillProfileId: {{skillProfileId}}',
         'matchReportId: {{matchReportId}}',
         'skillGapContext: {{skillGapContext}}',
         'targetPosition: {{targetPosition}}',
         'experienceLevel: {{experienceLevel}}',
         'industryDirection: {{industryDirection}}',
         'industryContext: {{industryContext}}',
         'difficulty: {{difficulty}}',
         'resumeContent: {{resumeContent}}',
         'projectContent: {{projectContent}}',
         'historySummary:',
         '{{historySummary}}',
         'trainingScene: {{trainingScene}}',
         'targetSkillDomain: {{targetSkillDomain}}',
         'targetSkillCodes: {{targetSkillCodes}}',
         'targetLevel: {{targetLevel}}',
         'projectEvidenceIds: {{projectEvidenceIds}}',
         'trainingContextSummary: {{trainingContextSummary}}',
         'followUpIntensity: {{followUpIntensity}}',
         '',
         '输入消息使用 Role、Type、Question、CandidateAnswer、AiComment、Score、Content 标识真实题目、回答和逐题评分。',
         '严格要求：',
         '1. 只输出一个合法 JSON 对象，不要输出 Markdown、代码块、注释或解释文字。',
         '2. 只能使用输入中明确存在的事实，不得编造技术栈、项目、职责、指标、题目、回答或评分。',
         '3. 只要存在 Type:ANSWER 且有对应的 Type:EVALUATION 与 Score，totalScore 必须按真实逐题评分计算为 1 到 100 的整数。',
         '4. 仅在没有任何有效回答或没有任何可用评分证据时，totalScore 才能为 null，并在 summary 中明确说明原因。',
         '5. qaReview 必须与有效回答逐条对应。每项至少包含 question, answer, score, comment；score 必须来自输入评分证据。',
         '6. rubricScores 必须是非空数组。每项包含 dimension 和 score；dimension 为稳定英文标识，score 为 1 到 5 的数字。',
         '7. rubricScores 不得包含 fallback=true 或 sampleInsufficient=true；证据不足时应减少维度数量，不得伪造维度。',
         '8. summary 与 reportContent 必须是非空中文字符串，并明确说明评分依据和主要复盘结论。',
         '9. strengths、weakPoints、mainProblems、projectProblems、suggestions、reviewSuggestions、recommendedQuestions 必须使用数组；没有证据时返回空数组。',
         '10. stageScores 必须使用对象；followUpTree、adviceEvidence、abilityProfileUpdates 必须使用数组。',
         '',
         '顶层字段固定为：',
         'totalScore, summary, stageScores, weakPoints, strengths, weaknesses, mainProblems, projectProblems, suggestions, reviewSuggestions, recommendedQuestions, qaReview, rubricScores, followUpTree, adviceEvidence, abilityProfileUpdates, reportContent。',
         '不得增加或遗漏顶层字段。',
         '',
         '输出示例结构：',
         '{"totalScore":84,"summary":"依据两次有效回答及逐题评分生成。","stageScores":{"answerQuality":84},"weakPoints":[],"strengths":[],"weaknesses":"","mainProblems":[],"projectProblems":[],"suggestions":[],"reviewSuggestions":[],"recommendedQuestions":[],"qaReview":[{"question":"题目","answer":"候选人回答","score":84,"comment":"逐题点评"}],"rubricScores":[{"dimension":"ANSWER_QUALITY","score":4.2}],"followUpTree":[],"adviceEvidence":[],"abilityProfileUpdates":[],"reportContent":"正式中文报告正文"}'
       ),
       COALESCE(active_version.variables_json, p.variables, ''),
       active_version.model_params_json
FROM prompt_template p
JOIN prompt_template_version active_version
  ON active_version.id = p.active_version_id
 AND active_version.deleted = 0
WHERE p.scene = 'INTERVIEW_REPORT_GENERATE'
  AND p.deleted = 0
  AND p.status = 1
  AND (p.enabled = 1 OR p.enabled IS NULL)
  AND p.id = (
    SELECT MAX(p2.id)
    FROM prompt_template p2
    WHERE p2.scene = 'INTERVIEW_REPORT_GENERATE'
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
       'v4-102-interview-score-contract',
       'V4.102 interview report scoring contract',
       content,
       variables_json,
       model_params_json,
       'INACTIVE',
       0,
       NULL,
       'Preserve scored interview evidence and require totalScore, qaReview and rubricScores.',
       0
FROM tmp_v4_102_interview_report_prompt
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
JOIN tmp_v4_102_interview_report_prompt target
  ON target.template_id = version.template_id
SET version.status = 'INACTIVE',
    version.is_active = 0
WHERE version.deleted = 0
  AND version.version_code <> 'v4-102-interview-score-contract'
  AND (version.is_active = 1 OR version.status = 'ACTIVE');

UPDATE prompt_template_version version
JOIN tmp_v4_102_interview_report_prompt target
  ON target.template_id = version.template_id
 AND version.version_code = 'v4-102-interview-score-contract'
SET version.status = 'ACTIVE',
    version.is_active = 1,
    version.activated_at = NOW(),
    version.deleted = 0;

UPDATE prompt_template template
JOIN tmp_v4_102_interview_report_prompt target
  ON target.template_id = template.id
JOIN prompt_template_version version
  ON version.template_id = target.template_id
 AND version.version_code = 'v4-102-interview-score-contract'
 AND version.deleted = 0
SET template.active_version_id = version.id,
    template.content = version.content,
    template.template_content = version.content,
    template.variables = version.variables_json,
    template.version = version.version_code,
    template.enabled = 1,
    template.status = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_102_interview_report_prompt;
