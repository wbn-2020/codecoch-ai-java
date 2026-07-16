-- Targeted repair for the formal Chinese test-data seed executed on 2026-07-16.
-- Preconditions: run only in the CodeCoachAI test database after the corresponding seed reset.

START TRANSACTION;

SET @seed_user_id = (
  SELECT id
  FROM sys_user
  WHERE username = 'user001'
    AND deleted = 0
  LIMIT 1
);

UPDATE interview_session AS s
JOIN interview_report AS r ON r.session_id = s.id
SET s.user_id = @seed_user_id,
    s.status = 'COMPLETED',
    s.report_status = 'GENERATED',
    s.deleted = 0,
    r.user_id = @seed_user_id,
    r.status = 'GENERATED',
    r.deleted = 0
WHERE s.id IN (9702701, 9702702, 9702703)
  AND r.id IN (9702801, 9702802, 9702803);

UPDATE agent_run
SET user_id = @seed_user_id,
    target_job_id = 9701301,
    plan_date = '2026-07-16',
    agent_type = 'JOB_COACH',
    status = 'SUCCESS',
    deleted = 0
WHERE id = 9703901;

UPDATE agent_task
SET user_id = @seed_user_id,
    agent_run_id = 9703901,
    target_job_id = 9701301,
    due_date = '2026-07-16',
    status = CASE id
      WHEN 9704001 THEN 'TODO'
      ELSE 'DOING'
    END,
    deleted = 0
WHERE id IN (9704001, 9704002);

-- Remove duplicate plans before assigning the canonical ALL scope, otherwise
-- the live-scope unique key conflicts with the generated duplicate plan.
UPDATE agent_plan_influence AS influence
JOIN agent_week_plan AS plan ON plan.id = influence.week_plan_id
SET influence.deleted = 1
WHERE plan.user_id = @seed_user_id
  AND plan.week_start_date = '2026-07-13'
  AND plan.week_end_date = '2026-07-19'
  AND plan.id <> 9704101
  AND influence.deleted = 0;

UPDATE agent_week_plan_item AS item
JOIN agent_week_plan AS plan ON plan.id = item.week_plan_id
SET item.deleted = 1
WHERE plan.user_id = @seed_user_id
  AND plan.week_start_date = '2026-07-13'
  AND plan.week_end_date = '2026-07-19'
  AND plan.id <> 9704101
  AND item.deleted = 0;

UPDATE agent_week_plan
SET target_scope_key = CONCAT('SUPERSEDED:', id),
    plan_status = 'SUPERSEDED',
    deleted = 1
WHERE user_id = @seed_user_id
  AND week_start_date = '2026-07-13'
  AND week_end_date = '2026-07-19'
  AND id <> 9704101
  AND deleted = 0;

UPDATE agent_week_plan
SET user_id = @seed_user_id,
    target_job_id = 9701301,
    agent_run_id = 9703901,
    target_scope_key = 'ALL',
    week_start_date = '2026-07-13',
    week_end_date = '2026-07-19',
    plan_status = 'ACTIVE',
    deleted = 0
WHERE id = 9704101;

UPDATE agent_week_plan_item
SET layer = CASE id
      WHEN 9704201 THEN 'TODAY'
      WHEN 9704202 THEN 'TODAY'
      ELSE 'WEEK'
    END,
    planned_date = CASE
      WHEN id IN (9704201, 9704202) THEN '2026-07-16'
      ELSE planned_date
    END,
    due_date = CASE
      WHEN id IN (9704201, 9704202) THEN '2026-07-16'
      ELSE due_date
    END,
    item_status = CASE
      WHEN id = 9704202 THEN 'IN_PROGRESS'
      ELSE item_status
    END,
    deleted = 0
WHERE id IN (9704201, 9704202, 9704203, 9704204);

UPDATE agent_week_plan_item
SET description = CASE id
      WHEN 9704201 THEN '完成 45 分钟容量估算、限流降级和缓存一致性练习。'
      WHEN 9704202 THEN '补齐稳定性治理项目的容灾与恢复验证证据。'
      WHEN 9704203 THEN '完成匹配、项目证据和面试准备清单的最终复核。'
      WHEN 9704204 THEN '已完成 EXPLAIN 练习并记录复盘。'
    END,
    related_biz_type = CASE id
      WHEN 9704204 THEN 'QUESTION_PRACTICE'
      ELSE related_biz_type
    END,
    related_biz_title = CASE id
      WHEN 9704201 THEN '面试复盘报告：项目深挖二面'
      WHEN 9704202 THEN '项目证据：学习平台稳定性治理'
      WHEN 9704203 THEN '投递准备：澄观智研高级 Java 后端岗位'
      WHEN 9704204 THEN '题库练习：MySQL 联合索引与范围查询'
    END,
    reason = CASE id
      WHEN 9704201 THEN '最近的项目深挖复盘显示，容量估算与方案取舍仍需形成完整表达。'
      WHEN 9704202 THEN '当前项目成果已具备量化结果，仍需补充跨区域故障演练、影响评估和恢复验证。'
      WHEN 9704203 THEN '当前准备度已接近可投递状态，完成复核后即可判断是否推进投递。'
      WHEN 9704204 THEN '已完成联合索引与范围查询训练，可作为本周能力巩固记录。'
    END,
    evidence_json = CASE id
      WHEN 9704201 THEN JSON_OBJECT(
        'summary', '项目深挖复盘指出容量估算与方案取舍需要专项练习。'
      )
      WHEN 9704202 THEN JSON_OBJECT(
        'summary', '学习平台稳定性治理项目还需补齐容灾演练与恢复验证证据。'
      )
      WHEN 9704203 THEN JSON_OBJECT(
        'summary', '岗位匹配、项目证据和面试准备清单已具备复核条件。'
      )
      WHEN 9704204 THEN JSON_OBJECT(
        'summary', '联合索引与范围查询训练已完成，并已记录复盘结论。'
      )
    END,
    updated_at = '2026-07-16 10:00:00'
WHERE id IN (9704201, 9704202, 9704203, 9704204)
  AND deleted = 0;

COMMIT;
