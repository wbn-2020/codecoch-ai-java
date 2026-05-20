USE codecoachai_v1;

-- Extra V3 data needed by browser E2E. Idempotent, local-dev only.

SET @E2E_USER_ID = (SELECT id FROM sys_user WHERE username = 'e2e_user' LIMIT 1);

CREATE TABLE IF NOT EXISTS resume_optimize_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resume_id BIGINT NOT NULL,
  target_position VARCHAR(100) DEFAULT NULL,
  experience_years INT DEFAULT NULL,
  industry_direction VARCHAR(100) DEFAULT NULL,
  request_json LONGTEXT DEFAULT NULL,
  result_json LONGTEXT DEFAULT NULL,
  optimize_status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
  error_message VARCHAR(1000) DEFAULT NULL,
  ai_call_log_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_resume (user_id, resume_id),
  KEY idx_user_status (user_id, optimize_status),
  KEY idx_resume_created (resume_id, created_at),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO resume_optimize_record (
  id, user_id, resume_id, target_position, experience_years, industry_direction,
  request_json, result_json, optimize_status, error_message, ai_call_log_id, deleted
) VALUES (
  100000, @E2E_USER_ID, 100000, 'Java Backend Engineer', 3, 'E-commerce',
  '{"resumeId":100000,"targetPosition":"Java Backend Engineer","optimizeFocus":"project impact"}',
  '{"overallComment":"E2E_TEST resume optimization record is available.","projectSuggestions":[{"projectName":"CodeCoachAI","suggestion":"Add measurable latency and throughput results."}]}',
  'SUCCESS', NULL, 100001, 0
) ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  resume_id = VALUES(resume_id),
  target_position = VALUES(target_position),
  experience_years = VALUES(experience_years),
  industry_direction = VALUES(industry_direction),
  request_json = VALUES(request_json),
  result_json = VALUES(result_json),
  optimize_status = VALUES(optimize_status),
  error_message = VALUES(error_message),
  ai_call_log_id = VALUES(ai_call_log_id),
  deleted = 0;

INSERT INTO study_plan (
  id, user_id, source_type, source_id, target_job_id, skill_profile_id, match_report_id,
  report_id, session_id, resume_id, optimize_record_id, target_position, industry_direction,
  plan_title, plan_summary, plan_status, duration_days, daily_minutes, start_date,
  ai_call_log_id, request_json, result_json, failure_reason, deleted
) VALUES (
  100000, @E2E_USER_ID, 'INTERVIEW_REPORT', 100000, 100000, NULL, 100000,
  100000, 100000, 100000, 100000, 'Java Backend Engineer', 'E-commerce',
  'E2E_TEST Daily Study Plan',
  'E2E_TEST active plan for browser checkin and daily task smoke testing.',
  'ACTIVE', 7, 45, CURDATE(), 100001,
  '{"source":"e2e"}',
  '{"stages":["Java basics","MySQL indexing","Redis cache"]}',
  NULL, 0
) ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  source_type = VALUES(source_type),
  source_id = VALUES(source_id),
  target_job_id = VALUES(target_job_id),
  skill_profile_id = VALUES(skill_profile_id),
  match_report_id = VALUES(match_report_id),
  report_id = VALUES(report_id),
  session_id = VALUES(session_id),
  resume_id = VALUES(resume_id),
  optimize_record_id = VALUES(optimize_record_id),
  target_position = VALUES(target_position),
  industry_direction = VALUES(industry_direction),
  plan_title = VALUES(plan_title),
  plan_summary = VALUES(plan_summary),
  plan_status = VALUES(plan_status),
  duration_days = VALUES(duration_days),
  daily_minutes = VALUES(daily_minutes),
  start_date = VALUES(start_date),
  ai_call_log_id = VALUES(ai_call_log_id),
  request_json = VALUES(request_json),
  result_json = VALUES(result_json),
  failure_reason = VALUES(failure_reason),
  deleted = 0;

INSERT INTO study_task (
  id, plan_id, user_id, target_job_id, skill_profile_id, skill_gap_item_id, source_type,
  source_biz_id, stage_no, planned_date, stage_title, task_order, knowledge_point,
  task_title, task_description, task_type, priority, estimated_hours, estimated_minutes,
  acceptance_criteria, task_status, related_question_ids_json, related_tags_json,
  resources_json, deleted
) VALUES
  (
    100000, 100000, @E2E_USER_ID, 100000, NULL, NULL, 'INTERVIEW_REPORT',
    100000, 1, CURDATE(), 'Java Basics', 1, 'HashMap and concurrency',
    'E2E_TEST Review HashMap concurrency',
    'Review HashMap thread-safety risks and ConcurrentHashMap alternatives.',
    'KNOWLEDGE_REVIEW', 'HIGH', 1, 45, 'Can explain risk and replacement plan.',
    'TODO', '[100000]', '["Java","HashMap"]', '[]', 0
  ),
  (
    100001, 100000, @E2E_USER_ID, 100000, NULL, NULL, 'INTERVIEW_REPORT',
    100000, 2, CURDATE(), 'MySQL Index', 2, 'Composite index',
    'E2E_TEST Practice MySQL index explain',
    'Practice leftmost prefix and EXPLAIN output interpretation.',
    'PRACTICE', 'MEDIUM', 1, 45, 'Can read type/key/rows/Extra fields.',
    'TODO', '[100002]', '["MySQL","Index"]', '[]', 0
  )
ON DUPLICATE KEY UPDATE
  plan_id = VALUES(plan_id),
  user_id = VALUES(user_id),
  target_job_id = VALUES(target_job_id),
  skill_profile_id = VALUES(skill_profile_id),
  skill_gap_item_id = VALUES(skill_gap_item_id),
  source_type = VALUES(source_type),
  source_biz_id = VALUES(source_biz_id),
  stage_no = VALUES(stage_no),
  planned_date = VALUES(planned_date),
  stage_title = VALUES(stage_title),
  task_order = VALUES(task_order),
  knowledge_point = VALUES(knowledge_point),
  task_title = VALUES(task_title),
  task_description = VALUES(task_description),
  task_type = VALUES(task_type),
  priority = VALUES(priority),
  estimated_hours = VALUES(estimated_hours),
  estimated_minutes = VALUES(estimated_minutes),
  acceptance_criteria = VALUES(acceptance_criteria),
  task_status = VALUES(task_status),
  related_question_ids_json = VALUES(related_question_ids_json),
  related_tags_json = VALUES(related_tags_json),
  resources_json = VALUES(resources_json),
  deleted = 0;
