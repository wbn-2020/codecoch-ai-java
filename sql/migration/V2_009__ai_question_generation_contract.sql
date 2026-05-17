-- CodeCoachAI V2 P0-1-B: AI question generation contract enhancement.
-- Scope: add target position to review drafts and seed AI_QUESTION_GENERATE prompt template.

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT,
    IN after_column_value VARCHAR(64)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND COLUMN_NAME = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', table_name_value, ' ADD COLUMN ', column_name_value, ' ', column_definition_value);
        IF after_column_value IS NOT NULL AND after_column_value <> '' THEN
            SET @ddl = CONCAT(@ddl, ' AFTER ', after_column_value);
        END IF;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_column_if_not_exists('question_review', 'target_position', 'VARCHAR(128) DEFAULT NULL', 'ai_call_log_id');

INSERT INTO prompt_template (scene, name, template_name, description, content, template_content, variables, version, enabled, status)
SELECT 'AI_QUESTION_GENERATE',
       'AI Question Generate',
       'AI Question Generate',
       'V2 AI question generation prompt',
       'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.',
       'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.',
       'targetPosition,technologyStack,knowledgePoint,questionType,difficulty,experienceYears,count,generateReferenceAnswer,generateFollowUps,generateTagSuggestions,generateCategorySuggestion,extraRequirements',
       'v2-a7',
       1,
       1
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scene = 'AI_QUESTION_GENERATE');

UPDATE prompt_template
SET name = 'AI Question Generate',
    template_name = 'AI Question Generate',
    content = 'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.',
    template_content = 'You are a Java backend interview question generator. Generate question drafts by targetPosition={{targetPosition}}, technologyStack={{technologyStack}}, knowledgePoint={{knowledgePoint}}, questionType={{questionType}}, difficulty={{difficulty}}, experienceYears={{experienceYears}}, count={{count}}. If targetPosition is empty, generate general Java backend interview questions. Output only one JSON object with questions array. Each item must contain title, content, referenceAnswer, analysis, difficulty, questionType, followUpQuestions, tagSuggestions, categorySuggestion and groupSuggestion.',
    variables = 'targetPosition,technologyStack,knowledgePoint,questionType,difficulty,experienceYears,count,generateReferenceAnswer,generateFollowUps,generateTagSuggestions,generateCategorySuggestion,extraRequirements',
    version = 'v2-a7',
    status = 1,
    enabled = 1
WHERE scene = 'AI_QUESTION_GENERATE';

INSERT IGNORE INTO prompt_template_version (
  template_id, scene, version_code, version_name, content, variables_json,
  status, is_active, activated_at, change_log
)
SELECT p.id,
       p.scene,
       COALESCE(NULLIF(p.version, ''), 'v1'),
       p.name,
       COALESCE(NULLIF(p.template_content, ''), p.content),
       p.variables,
       'ACTIVE',
       1,
       NOW(),
       'Initialized for AI question generation contract'
FROM prompt_template p
WHERE p.deleted = 0
  AND p.scene = 'AI_QUESTION_GENERATE';

UPDATE prompt_template p
JOIN prompt_template_version v
  ON v.template_id = p.id
 AND v.version_code = COALESCE(NULLIF(p.version, ''), 'v1')
 AND v.deleted = 0
SET p.active_version_id = v.id,
    p.enabled = p.status
WHERE p.deleted = 0
  AND p.scene = 'AI_QUESTION_GENERATE';

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
