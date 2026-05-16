CREATE TABLE IF NOT EXISTS study_plan (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT DEFAULT NULL,
  report_id BIGINT DEFAULT NULL,
  session_id BIGINT DEFAULT NULL,
  resume_id BIGINT DEFAULT NULL,
  optimize_record_id BIGINT DEFAULT NULL,
  target_position VARCHAR(128) DEFAULT NULL,
  industry_direction VARCHAR(128) DEFAULT NULL,
  plan_title VARCHAR(200) DEFAULT NULL,
  plan_summary TEXT,
  plan_status VARCHAR(32) NOT NULL DEFAULT 'GENERATING',
  duration_days INT DEFAULT NULL,
  ai_call_log_id BIGINT DEFAULT NULL,
  request_json LONGTEXT,
  result_json LONGTEXT,
  failure_reason VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_study_plan_user_status (user_id, plan_status, created_at),
  KEY idx_study_plan_report (report_id),
  KEY idx_study_plan_session (session_id),
  KEY idx_study_plan_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Study plan generated from interview report and resume signals';

CREATE TABLE IF NOT EXISTS study_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  stage_no INT NOT NULL DEFAULT 1,
  stage_title VARCHAR(128) DEFAULT NULL,
  task_order INT NOT NULL DEFAULT 0,
  knowledge_point VARCHAR(128) DEFAULT NULL,
  task_title VARCHAR(200) NOT NULL,
  task_description TEXT,
  task_type VARCHAR(64) NOT NULL DEFAULT 'KNOWLEDGE_REVIEW',
  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  estimated_hours INT DEFAULT NULL,
  task_status VARCHAR(32) NOT NULL DEFAULT 'TODO',
  related_question_ids_json TEXT,
  related_tags_json TEXT,
  resources_json TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_study_task_plan (plan_id, stage_no, task_order),
  KEY idx_study_task_user_status (user_id, task_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tasks inside a study plan';

UPDATE prompt_template
SET name = CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
    template_name = CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
    content = 'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only one JSON object with planTitle, planSummary, durationDays and stages. Each stage contains stageNo, stageTitle and items. Each item contains knowledgePoint, taskTitle, taskDescription, taskType, priority, estimatedHours, relatedTags and resources.',
    template_content = 'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only JSON object, no Markdown, no code fences. taskType must be KNOWLEDGE_REVIEW, CODING_PRACTICE, PROJECT_REVIEW, INTERVIEW_PRACTICE or RESUME_IMPROVEMENT. priority must be HIGH, MEDIUM or LOW.',
    variables = 'targetPosition,industryDirection,experienceLevel,expectedDurationDays,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements',
    version = 'v2-a9',
    status = 1
WHERE scene = 'LEARNING_PLAN_GENERATE';

INSERT INTO prompt_template (scene, name, template_name, content, template_content, variables, version, status)
SELECT 'LEARNING_PLAN_GENERATE',
       CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
       CONVERT(0xE5ADA6E4B9A0E8AEA1E58892E7949FE68890 USING utf8mb4),
       'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only one JSON object with planTitle, planSummary, durationDays and stages. Each stage contains stageNo, stageTitle and items. Each item contains knowledgePoint, taskTitle, taskDescription, taskType, priority, estimatedHours, relatedTags and resources.',
       'You are a senior Java backend interview coach. Generate a practical study plan in Chinese. targetPosition={{targetPosition}}, industryDirection={{industryDirection}}, experienceLevel={{experienceLevel}}, expectedDurationDays={{expectedDurationDays}}. Use interviewSummary={{interviewSummary}}, weaknessSummary={{weaknessSummary}}, questionPerformanceSummary={{questionPerformanceSummary}}, resumeWeaknessSummary={{resumeWeaknessSummary}}, extraRequirements={{extraRequirements}}. Output only JSON object, no Markdown, no code fences. taskType must be KNOWLEDGE_REVIEW, CODING_PRACTICE, PROJECT_REVIEW, INTERVIEW_PRACTICE or RESUME_IMPROVEMENT. priority must be HIGH, MEDIUM or LOW.',
       'targetPosition,industryDirection,experienceLevel,expectedDurationDays,interviewSummary,weaknessSummary,questionPerformanceSummary,resumeWeaknessSummary,extraRequirements',
       'v2-a9',
       1
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template WHERE scene = 'LEARNING_PLAN_GENERATE'
);
