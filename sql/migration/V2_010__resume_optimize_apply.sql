-- CodeCoachAI P0-3-B: trace resume drafts created from AI optimization records.

ALTER TABLE resume
  ADD COLUMN source_resume_id BIGINT DEFAULT NULL AFTER status,
  ADD COLUMN source_optimize_record_id BIGINT DEFAULT NULL AFTER source_resume_id,
  ADD COLUMN applied_at DATETIME DEFAULT NULL AFTER source_optimize_record_id,
  ADD KEY idx_resume_source_resume (source_resume_id),
  ADD KEY idx_resume_source_optimize_record (source_optimize_record_id);
