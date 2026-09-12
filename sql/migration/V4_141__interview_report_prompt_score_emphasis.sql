-- 2026-09-11 全链路验收问题④的 Prompt 侧对齐：
-- 1. 模型在面试报告场景会漏掉顶层 totalScore（解析层已兜底，但应减少兜底触发）。
-- 2. 逐题评分与 totalScore 的一致性约束需要更显式的输出要求。
-- 基于当前 ACTIVE 版本派生新版本 v4-141-interview-score-emphasis，追加强化规则并切换激活。

DROP TEMPORARY TABLE IF EXISTS tmp_v4_141_interview_report_prompt;
CREATE TEMPORARY TABLE tmp_v4_141_interview_report_prompt (
  template_id BIGINT NOT NULL,
  scene VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json LONGTEXT NOT NULL,
  model_params_json LONGTEXT DEFAULT NULL,
  PRIMARY KEY (template_id)
);

INSERT INTO tmp_v4_141_interview_report_prompt (
  template_id, scene, content, variables_json, model_params_json
)
SELECT p.id,
       p.scene,
       CONCAT_WS(CHAR(10),
         REPLACE(v.content, '4. 仅在没有任何有效回答或没有任何可用评分证据时，totalScore 才能为 null，并在 summary 中明确说明原因。',
           CONCAT('4. 只要有任何一条 Type:ANSWER 存在对应的 Type:EVALUATION 与 Score，顶层 totalScore 就必须输出 ',
                  CHAR(10), '（缺失或为 null 都会导致整份报告被判为不可评分）。仅当完全没有任何有效回答时 totalScore 才能为 null，并在 summary 中说明原因。')),
         '',
         '11. focusSkills 若无法逐项给出 code 与 name，则输出技能名称的字符串数组，如 ["系统设计"]；两种形态都不允许省略该字段。',
         '12. rubricScores 至少输出一个维度（如 ANSWER_QUALITY），score 为该维度 1-5 的评分；不得输出空数组。'
       ),
       COALESCE(v.variables_json, p.variables, ''),
       v.model_params_json
FROM prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.deleted = 0
 AND v.is_active = 1
WHERE p.scene = 'INTERVIEW_REPORT_GENERATE'
  AND p.deleted = 0
  AND p.status = 1
  AND p.id = (
    SELECT MAX(p2.id)
    FROM prompt_template p2
    WHERE p2.scene = 'INTERVIEW_REPORT_GENERATE'
      AND p2.deleted = 0
      AND p2.status = 1
      AND (p2.enabled = 1 OR p2.enabled IS NULL)
  );

INSERT INTO prompt_template_version (
  template_id, scene, version_code, version_name, content,
  variables_json, model_params_json, status, is_active, activated_at, change_log, deleted
)
SELECT template_id, scene, 'v4-141-interview-score-emphasis',
       'V4.141 interview report score emphasis',
       content, variables_json, model_params_json,
       'INACTIVE', 0, NULL,
       'Emphasize mandatory totalScore, non-empty rubricScores and tolerant focusSkills shape.',
       0
FROM tmp_v4_141_interview_report_prompt
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
JOIN tmp_v4_141_interview_report_prompt target
  ON target.template_id = version.template_id
SET version.status = 'INACTIVE', version.is_active = 0
WHERE version.deleted = 0
  AND version.version_code <> 'v4-141-interview-score-emphasis'
  AND (version.is_active = 1 OR version.status = 'ACTIVE');

UPDATE prompt_template_version version
JOIN tmp_v4_141_interview_report_prompt target
  ON target.template_id = version.template_id
 AND version.version_code = 'v4-141-interview-score-emphasis'
SET version.status = 'ACTIVE',
    version.is_active = 1,
    version.activated_at = NOW()
WHERE version.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = 'v4-141-interview-score-emphasis'
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.updated_at = NOW()
WHERE p.deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_141_interview_report_prompt;
