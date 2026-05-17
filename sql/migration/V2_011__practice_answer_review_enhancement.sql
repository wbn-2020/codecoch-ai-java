ALTER TABLE practice_record
  ADD COLUMN answer_duration_seconds INT DEFAULT NULL AFTER answer_content,
  ADD COLUMN source VARCHAR(64) NOT NULL DEFAULT 'QUESTION_BANK' AFTER answer_duration_seconds,
  ADD COLUMN level VARCHAR(32) DEFAULT NULL AFTER score,
  ADD COLUMN strengths TEXT AFTER knowledge_points,
  ADD COLUMN weaknesses TEXT AFTER strengths,
  ADD COLUMN improvement_suggestions TEXT AFTER weaknesses,
  ADD COLUMN reference_comparison TEXT AFTER improvement_suggestions,
  ADD COLUMN knowledge_gaps TEXT AFTER reference_comparison,
  ADD COLUMN suggested_follow_ups TEXT AFTER knowledge_gaps,
  ADD COLUMN reference_answer_snapshot TEXT AFTER suggested_follow_ups,
  ADD COLUMN question_snapshot_json LONGTEXT AFTER reference_answer_snapshot,
  ADD COLUMN review_json LONGTEXT AFTER question_snapshot_json;

UPDATE practice_record
SET reference_answer_snapshot = reference_answer
WHERE reference_answer_snapshot IS NULL
  AND reference_answer IS NOT NULL;

UPDATE prompt_template
SET name = 'Question Answer AI Review',
    template_name = 'Question Answer AI Review',
    description = 'P0-4 short-answer practice AI review prompt',
    content = 'You are a senior Java backend interview coach. Review the user short-answer practice response using questionTitle, questionContent, referenceAnswer, analysis, userAnswer, difficulty, knowledgePoint, and answerDurationSeconds. Output one JSON object only with score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, and suggestedFollowUps.',
    template_content = 'You are a senior Java backend interview coach. Review the user short-answer practice response using questionTitle, questionContent, referenceAnswer, analysis, userAnswer, difficulty, knowledgePoint, and answerDurationSeconds. Output one JSON object only with score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, and suggestedFollowUps.',
    variables = 'recordId,userId,questionId,questionTitle,questionContent,questionType,difficulty,technologyStack,knowledgePoint,referenceAnswer,analysis,userAnswer,answerDurationSeconds,targetPosition,experienceLevel',
    version = 'v2-p0-4-practice-review',
    enabled = 1,
    status = 1
WHERE scene = 'PRACTICE_ANSWER_REVIEW'
  AND deleted = 0;

INSERT INTO prompt_template (
  scene, name, template_name, description, content, template_content, variables, version, enabled, status
)
SELECT 'PRACTICE_ANSWER_REVIEW',
       'Question Answer AI Review',
       'Question Answer AI Review',
       'P0-4 short-answer practice AI review prompt',
       'You are a senior Java backend interview coach. Review the user short-answer practice response using questionTitle, questionContent, referenceAnswer, analysis, userAnswer, difficulty, knowledgePoint, and answerDurationSeconds. Output one JSON object only with score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, and suggestedFollowUps.',
       'You are a senior Java backend interview coach. Review the user short-answer practice response using questionTitle, questionContent, referenceAnswer, analysis, userAnswer, difficulty, knowledgePoint, and answerDurationSeconds. Output one JSON object only with score, level, summary, strengths, weaknesses, improvementSuggestions, referenceComparison, knowledgeGaps, and suggestedFollowUps.',
       'recordId,userId,questionId,questionTitle,questionContent,questionType,difficulty,technologyStack,knowledgePoint,referenceAnswer,analysis,userAnswer,answerDurationSeconds,targetPosition,experienceLevel',
       'v2-p0-4-practice-review',
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
       'v2-p0-4-practice-review',
       'Question Answer AI Review',
       p.template_content,
       p.variables,
       'ACTIVE',
       1,
       NOW(),
       'P0-4 short-answer practice AI review JSON contract'
FROM prompt_template p
WHERE p.scene = 'PRACTICE_ANSWER_REVIEW'
  AND p.deleted = 0;

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = 'v2-p0-4-practice-review'
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.enabled = 1,
    p.status = 1
WHERE p.scene = 'PRACTICE_ANSWER_REVIEW'
  AND p.deleted = 0;
