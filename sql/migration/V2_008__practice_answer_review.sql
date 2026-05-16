CREATE TABLE IF NOT EXISTS practice_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_content TEXT NOT NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  score INT DEFAULT NULL,
  mastery_status VARCHAR(32) DEFAULT NULL,
  ai_comment TEXT,
  suggestions TEXT,
  knowledge_points TEXT,
  reference_answer TEXT,
  ai_call_log_id BIGINT DEFAULT NULL,
  error_message VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_practice_user_question (user_id, question_id),
  KEY idx_practice_user_status (user_id, review_status),
  KEY idx_practice_ai_log (ai_call_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User short-answer practice AI review records';

INSERT INTO prompt_template (
  scene, name, template_name, description, content, template_content, variables, version, enabled, status
)
SELECT 'PRACTICE_ANSWER_REVIEW',
       '简答题 AI 点评',
       '简答题 AI 点评',
       'V2 practice short-answer AI review prompt',
       '你是 Java 后端刷题教练。请基于题目、参考答案和用户答案生成简答题点评。题目：{{questionContent}}。参考答案：{{referenceAnswer}}。用户答案：{{answerContent}}。只输出 JSON：{"score":80,"masteryStatus":"FAMILIAR","comment":"点评","suggestions":"建议","knowledgePoints":"知识点"}',
       '你是 Java 后端刷题教练。请基于题目、参考答案和用户答案生成简答题点评。题目：{{questionContent}}。参考答案：{{referenceAnswer}}。用户答案：{{answerContent}}。只输出 JSON：{"score":80,"masteryStatus":"FAMILIAR","comment":"点评","suggestions":"建议","knowledgePoints":"知识点"}',
       'recordId,userId,questionId,questionTitle,questionContent,referenceAnswer,answerContent',
       'v2-a12-practice',
       1,
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'PRACTICE_ANSWER_REVIEW' AND deleted = 0
);

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       COALESCE(NULLIF(p.version, ''), 'v2-a12-practice'),
       p.name,
       COALESCE(NULLIF(p.template_content, ''), p.content),
       p.variables,
       'ACTIVE',
       1,
       NOW(),
       'Initialized for V2 practice answer review'
FROM prompt_template p
WHERE p.scene = 'PRACTICE_ANSWER_REVIEW'
  AND p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = COALESCE(NULLIF(p.version, ''), 'v2-a12-practice')
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.enabled = 1,
    p.status = 1
WHERE p.scene = 'PRACTICE_ANSWER_REVIEW'
  AND p.deleted = 0;
