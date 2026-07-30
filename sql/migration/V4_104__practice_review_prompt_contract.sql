-- Publish a managed Chinese JSON contract for practice-answer reviews.

DROP TEMPORARY TABLE IF EXISTS tmp_v4_104_practice_review_prompt;
CREATE TEMPORARY TABLE tmp_v4_104_practice_review_prompt (
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  PRIMARY KEY (template_id)
);

INSERT INTO tmp_v4_104_practice_review_prompt (
  template_id,
  scene,
  content,
  variables_json,
  model_params_json
)
SELECT template.id,
       template.scene,
       CONCAT_WS(CHAR(10),
         '你是资深 Java 后端面试刷题教练。请基于题目证据和用户实际答案生成正式中文点评。',
         'recordId: {{recordId}}',
         'userId: {{userId}}',
         'questionId: {{questionId}}',
         'questionTitle: {{questionTitle}}',
         'questionContent: {{questionContent}}',
         'questionType: {{questionType}}',
         'difficulty: {{difficulty}}',
         'technologyStack: {{technologyStack}}',
         'knowledgePoint: {{knowledgePoint}}',
         'referenceAnswer: {{referenceAnswer}}',
         'analysis: {{analysis}}',
         'userAnswer: {{userAnswer}}',
         'answerDurationSeconds: {{answerDurationSeconds}}',
         'targetPosition: {{targetPosition}}',
         'experienceLevel: {{experienceLevel}}',
         '',
         '严格要求：',
         '1. 只依据题目、参考答案、答案解析和用户答案进行点评，不得编造用户经历、项目、技术栈或结论。',
         '2. score 必须是 0 到 100 的整数。',
         '3. level 只能是 EXCELLENT、GOOD、NORMAL、WEAK。',
         '4. 所有面向用户的文本必须使用正式中文，输入中已经出现的技术名词可以保留英文。',
         '5. summary 必须简要说明整体表现，并明确对应本题知识点。',
         '6. strengths、weaknesses、improvementSuggestions、knowledgeGaps、suggestedFollowUps 必须是字符串数组。',
         '7. referenceComparison 必须对比用户答案与参考答案的关键差异。',
         '8. 不得增加或遗漏固定字段，不得把数组输出为字符串。',
         '9. 只输出一个合法 JSON 对象，不要输出 Markdown、代码块、注释或解释文字。',
         '',
         '顶层字段固定为：score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, suggestedFollowUps。',
         '输出示例：',
         '{"score":76,"level":"GOOD","summary":"回答覆盖了主要结论，但边界条件仍需补充。","strengths":["覆盖了核心概念"],"weaknesses":["缺少边界条件"],"improvementSuggestions":["补充一个生产场景"],"referenceComparison":"核心结论一致，参考答案还包含异常边界。","knowledgeGaps":["异常传播边界"],"suggestedFollowUps":["请说明该机制在自调用场景下的表现。"]}'
       ),
       'recordId,userId,questionId,questionTitle,questionContent,questionType,difficulty,technologyStack,knowledgePoint,referenceAnswer,analysis,userAnswer,answerDurationSeconds,targetPosition,experienceLevel',
       active_version.model_params_json
FROM prompt_template template
JOIN prompt_template_version active_version
  ON active_version.id = template.active_version_id
 AND active_version.deleted = 0
WHERE template.scene = 'PRACTICE_ANSWER_REVIEW'
  AND template.deleted = 0
  AND template.status = 1
  AND (template.enabled = 1 OR template.enabled IS NULL)
  AND template.id = (
    SELECT MAX(candidate.id)
    FROM prompt_template candidate
    WHERE candidate.scene = 'PRACTICE_ANSWER_REVIEW'
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
       'v4-104-practice-review-contract',
       'V4.104 practice review Chinese JSON contract',
       content,
       variables_json,
       model_params_json,
       'INACTIVE',
       0,
       NULL,
       'Require formal Chinese output, canonical level values and a complete JSON schema.',
       0
FROM tmp_v4_104_practice_review_prompt
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
JOIN tmp_v4_104_practice_review_prompt target
  ON target.template_id = version.template_id
SET version.status = 'INACTIVE',
    version.is_active = 0
WHERE version.deleted = 0
  AND version.version_code <> 'v4-104-practice-review-contract'
  AND (version.is_active = 1 OR version.status = 'ACTIVE');

UPDATE prompt_template_version version
JOIN tmp_v4_104_practice_review_prompt target
  ON target.template_id = version.template_id
 AND version.version_code = 'v4-104-practice-review-contract'
SET version.status = 'ACTIVE',
    version.is_active = 1,
    version.activated_at = NOW(),
    version.deleted = 0;

UPDATE prompt_template template
JOIN tmp_v4_104_practice_review_prompt target
  ON target.template_id = template.id
JOIN prompt_template_version version
  ON version.template_id = target.template_id
 AND version.version_code = 'v4-104-practice-review-contract'
 AND version.deleted = 0
SET template.active_version_id = version.id,
    template.content = version.content,
    template.template_content = version.content,
    template.variables = version.variables_json,
    template.version = version.version_code,
    template.enabled = 1,
    template.status = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_104_practice_review_prompt;
