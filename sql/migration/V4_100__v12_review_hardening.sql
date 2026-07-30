-- V12/V13 review hardening:
-- 1. Durable, serialized evidence feedback projections and stable overlay profiles.
-- 2. Replay/remediation claim fencing and soft-delete-aware idempotency.
-- 3. A managed V13 JOB_COACH_DAILY_PLAN prompt compatible with skill-gap context.
-- Forward-only and idempotent.

SET @v4_100_schema_name = DATABASE();

DELIMITER //
DROP PROCEDURE IF EXISTS v4_100_assert_prerequisites//
CREATE PROCEDURE v4_100_assert_prerequisites()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'career_evidence_usage'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'career_evidence_usage_result'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'skill_profile'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'skill_gap_item'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'interview_replay'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'interview_remediation'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'prompt_template'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'prompt_template_version'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_100 prerequisites are missing';
    END IF;
END//

DROP PROCEDURE IF EXISTS v4_100_add_column//
CREATE PROCEDURE v4_100_add_column(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value LONGTEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @v4_100_schema_name
           AND table_name = table_name_value
           AND column_name = column_name_value
    ) THEN
        SET @v4_100_ddl = CONCAT(
            'ALTER TABLE `', table_name_value,
            '` ADD COLUMN `', column_name_value, '` ',
            column_definition_value
        );
        PREPARE v4_100_stmt FROM @v4_100_ddl;
        EXECUTE v4_100_stmt;
        DEALLOCATE PREPARE v4_100_stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS v4_100_add_index//
CREATE PROCEDURE v4_100_add_index(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN index_definition_value LONGTEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = @v4_100_schema_name
           AND table_name = table_name_value
           AND index_name = index_name_value
    ) THEN
        SET @v4_100_ddl = CONCAT(
            'ALTER TABLE `', table_name_value, '` ADD ',
            index_definition_value
        );
        PREPARE v4_100_stmt FROM @v4_100_ddl;
        EXECUTE v4_100_stmt;
        DEALLOCATE PREPARE v4_100_stmt;
    END IF;
END//
DELIMITER ;

CALL v4_100_assert_prerequisites();
DROP PROCEDURE IF EXISTS v4_100_assert_prerequisites;

-- ---------------------------------------------------------------------------
-- Evidence feedback: nullable report linkage, one overlay per user/job, one
-- active projection per source, and a durable projection outbox.
-- ---------------------------------------------------------------------------

SET @v4_100_ddl = IF(
    EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = @v4_100_schema_name
           AND table_name = 'skill_profile'
           AND column_name = 'match_report_id'
           AND is_nullable = 'NO'
    ),
    'ALTER TABLE `skill_profile`
       MODIFY COLUMN `match_report_id` BIGINT NULL
       COMMENT ''resume_job_match_report id; NULL for non-match overlays''',
    'SELECT 1'
);
PREPARE v4_100_stmt FROM @v4_100_ddl;
EXECUTE v4_100_stmt;
DEALLOCATE PREPARE v4_100_stmt;

UPDATE skill_profile
   SET match_report_id = NULL
 WHERE match_report_id = 0;

UPDATE skill_profile
   SET source_biz_id = target_job_id,
       match_report_id = NULL
 WHERE source_type = 'EVIDENCE_USAGE'
   AND deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_100_overlay_keep;
CREATE TEMPORARY TABLE tmp_v4_100_overlay_keep (
    user_id BIGINT NOT NULL,
    target_job_id BIGINT NOT NULL,
    keep_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, target_job_id)
);

INSERT INTO tmp_v4_100_overlay_keep (user_id, target_job_id, keep_id)
SELECT user_id, target_job_id, MAX(id)
  FROM skill_profile
 WHERE source_type = 'EVIDENCE_USAGE'
   AND deleted = 0
 GROUP BY user_id, target_job_id;

UPDATE skill_gap_item g
JOIN skill_profile p
  ON p.id = g.profile_id
JOIN tmp_v4_100_overlay_keep k
  ON k.user_id = p.user_id
 AND k.target_job_id = p.target_job_id
SET g.profile_id = k.keep_id
WHERE p.source_type = 'EVIDENCE_USAGE'
  AND p.deleted = 0
  AND p.id <> k.keep_id
  AND g.deleted = 0;

UPDATE skill_profile p
JOIN tmp_v4_100_overlay_keep k
  ON k.user_id = p.user_id
 AND k.target_job_id = p.target_job_id
SET p.deleted = 1
WHERE p.source_type = 'EVIDENCE_USAGE'
  AND p.deleted = 0
  AND p.id <> k.keep_id;

INSERT INTO skill_profile (
    user_id, target_job_id, match_report_id, profile_name,
    overall_level, overall_score, summary, source_type, source_biz_id,
    status, created_at, updated_at, deleted
)
SELECT DISTINCT
       g.user_id,
       g.target_job_id,
       NULL,
       '证据实战反馈画像',
       2,
       60,
       '由证据使用结果反馈自动创建，用于承载实战反馈缺口。',
       'EVIDENCE_USAGE',
       g.target_job_id,
       'SUCCESS',
       NOW(),
       NOW(),
       0
  FROM skill_gap_item g
  LEFT JOIN skill_profile p
    ON p.user_id = g.user_id
   AND p.target_job_id = g.target_job_id
   AND p.source_type = 'EVIDENCE_USAGE'
   AND p.deleted = 0
 WHERE g.deleted = 0
   AND g.target_job_id IS NOT NULL
   AND LEFT(g.source_type, 15) = 'EVIDENCE_USAGE_'
   AND p.id IS NULL;

TRUNCATE TABLE tmp_v4_100_overlay_keep;
INSERT INTO tmp_v4_100_overlay_keep (user_id, target_job_id, keep_id)
SELECT user_id, target_job_id, MAX(id)
  FROM skill_profile
 WHERE source_type = 'EVIDENCE_USAGE'
   AND deleted = 0
 GROUP BY user_id, target_job_id;

UPDATE skill_gap_item g
JOIN tmp_v4_100_overlay_keep k
  ON k.user_id = g.user_id
 AND k.target_job_id = g.target_job_id
SET g.profile_id = k.keep_id
WHERE g.deleted = 0
  AND LEFT(g.source_type, 15) = 'EVIDENCE_USAGE_';

DROP TEMPORARY TABLE IF EXISTS tmp_v4_100_gap_keep;
CREATE TEMPORARY TABLE tmp_v4_100_gap_keep (
    user_id BIGINT NOT NULL,
    target_job_id BIGINT NOT NULL,
    source_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_biz_id BIGINT NOT NULL,
    keep_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, target_job_id, source_type, source_biz_id)
);

INSERT INTO tmp_v4_100_gap_keep (
    user_id, target_job_id, source_type, source_biz_id, keep_id
)
SELECT user_id, target_job_id, source_type, source_biz_id, MAX(id)
  FROM skill_gap_item
 WHERE deleted = 0
   AND target_job_id IS NOT NULL
   AND source_biz_id IS NOT NULL
   AND LEFT(source_type, 15) = 'EVIDENCE_USAGE_'
 GROUP BY user_id, target_job_id, source_type, source_biz_id;

UPDATE skill_gap_item g
JOIN tmp_v4_100_gap_keep k
 ON k.user_id = g.user_id
 AND k.target_job_id = g.target_job_id
 AND k.source_type = g.source_type COLLATE utf8mb4_bin
 AND k.source_biz_id = g.source_biz_id
SET g.deleted = 1
WHERE g.deleted = 0
  AND g.id <> k.keep_id;

CALL v4_100_add_column(
    'skill_profile',
    'active_evidence_profile_key',
    'VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin
       GENERATED ALWAYS AS (
         CASE
           WHEN deleted = 0 AND source_type = ''EVIDENCE_USAGE''
           THEN CONCAT(user_id, '':'', target_job_id)
           ELSE NULL
         END
       ) STORED
       COMMENT ''Active-only evidence overlay uniqueness key'''
);

CALL v4_100_add_index(
    'skill_profile',
    'uk_skill_profile_active_evidence',
    'UNIQUE KEY `uk_skill_profile_active_evidence` (`active_evidence_profile_key`)'
);

CALL v4_100_add_index(
    'skill_profile',
    'idx_skill_profile_evidence_lookup',
    'KEY `idx_skill_profile_evidence_lookup`
       (`user_id`, `target_job_id`, `source_type`, `status`, `deleted`, `id`)'
);

CALL v4_100_add_column(
    'skill_gap_item',
    'active_evidence_gap_key',
    'VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin
       GENERATED ALWAYS AS (
         CASE
           WHEN deleted = 0
             AND target_job_id IS NOT NULL
             AND source_biz_id IS NOT NULL
             AND LEFT(source_type, 15) = ''EVIDENCE_USAGE_''
           THEN CONCAT(user_id, '':'', target_job_id, '':'', source_type, '':'', source_biz_id)
           ELSE NULL
         END
       ) STORED
       COMMENT ''Active-only evidence projection uniqueness key'''
);

CALL v4_100_add_index(
    'skill_gap_item',
    'uk_skill_gap_active_evidence',
    'UNIQUE KEY `uk_skill_gap_active_evidence` (`active_evidence_gap_key`)'
);

CALL v4_100_add_index(
    'skill_gap_item',
    'idx_skill_gap_evidence_lookup',
    'KEY `idx_skill_gap_evidence_lookup`
       (`user_id`, `target_job_id`, `source_type`, `source_biz_id`, `deleted`, `id`)'
);

CALL v4_100_add_index(
    'career_evidence_usage',
    'idx_career_evidence_usage_feedback',
    'KEY `idx_career_evidence_usage_feedback`
       (`user_id`, `target_job_id`, `asset_type`, `asset_id`, `deleted`, `id`)'
);

CREATE TABLE IF NOT EXISTS evidence_profile_feedback_lock (
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Serializes evidence feedback projection per user';

CREATE TABLE IF NOT EXISTS evidence_usage_ability_projection (
    id BIGINT NOT NULL AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    usage_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evidence_ability_projection_result_skill (result_id, skill_code),
    KEY idx_evidence_ability_projection_user_skill_usage
        (user_id, skill_code, usage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Current positive ability contribution of each evidence result';

CREATE TABLE IF NOT EXISTS evidence_profile_feedback_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    snapshot_version INT NOT NULL,
    evidence_projection_done TINYINT NOT NULL DEFAULT 0,
    ability_projection_done TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at DATETIME NULL,
    locked_by VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_error VARCHAR(1000) NULL,
    delivered_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evidence_feedback_outbox_event (result_id, snapshot_version),
    KEY idx_evidence_feedback_outbox_retry (status, next_retry_at, id, deleted),
    KEY idx_evidence_feedback_outbox_user (user_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Durable evidence usage result projection outbox';

CALL v4_100_add_column(
    'evidence_profile_feedback_outbox',
    'evidence_projection_done',
    'TINYINT NOT NULL DEFAULT 0 AFTER `snapshot_version`'
);
CALL v4_100_add_column(
    'evidence_profile_feedback_outbox',
    'ability_projection_done',
    'TINYINT NOT NULL DEFAULT 0 AFTER `evidence_projection_done`'
);

INSERT IGNORE INTO evidence_profile_feedback_outbox (
    result_id, user_id, snapshot_version, status, retry_count,
    next_retry_at, created_at, updated_at, deleted
)
SELECT r.id,
       r.user_id,
       r.snapshot_version,
       'PENDING',
       0,
       NOW(),
       NOW(),
       NOW(),
       0
  FROM career_evidence_usage_result r
 WHERE r.deleted = 0
   AND r.current_snapshot_id IS NOT NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_v4_100_gap_keep;
DROP TEMPORARY TABLE IF EXISTS tmp_v4_100_overlay_keep;

-- ---------------------------------------------------------------------------
-- Replay/remediation: claim fencing and active-only idempotency uniqueness.
-- ---------------------------------------------------------------------------

CALL v4_100_add_column(
    'interview_replay',
    'claim_token',
    'VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `idempotency_key`'
);
CALL v4_100_add_column(
    'interview_replay',
    'claimed_at',
    'DATETIME NULL AFTER `claim_token`'
);
CALL v4_100_add_column(
    'interview_replay',
    'active_idempotency_key',
    'VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
       GENERATED ALWAYS AS (
         CASE WHEN deleted = 0 THEN idempotency_key ELSE NULL END
       ) STORED AFTER `idempotency_key`'
);

ALTER TABLE interview_replay
    MODIFY COLUMN claim_token
        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;

SET @v4_100_index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
      FROM information_schema.statistics
     WHERE table_schema = @v4_100_schema_name
       AND table_name = 'interview_replay'
       AND index_name = 'uk_interview_replay_user_token'
);
SET @v4_100_ddl = IF(
    @v4_100_index_columns = 'user_id,idempotency_key',
    'ALTER TABLE interview_replay
       DROP INDEX uk_interview_replay_user_token,
       ADD UNIQUE KEY uk_interview_replay_user_token
         (user_id, active_idempotency_key)',
    IF(
        @v4_100_index_columns IS NULL,
        'ALTER TABLE interview_replay
           ADD UNIQUE KEY uk_interview_replay_user_token
             (user_id, active_idempotency_key)',
        'SELECT 1'
    )
);
PREPARE v4_100_stmt FROM @v4_100_ddl;
EXECUTE v4_100_stmt;
DEALLOCATE PREPARE v4_100_stmt;

CALL v4_100_add_index(
    'interview_replay',
    'idx_interview_replay_claim_recovery',
    'KEY `idx_interview_replay_claim_recovery` (`status`, `claimed_at`, `id`)'
);

CALL v4_100_add_column(
    'interview_remediation',
    'claim_token',
    'VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `idempotency_key`'
);
CALL v4_100_add_column(
    'interview_remediation',
    'claimed_at',
    'DATETIME NULL AFTER `claim_token`'
);
CALL v4_100_add_column(
    'interview_remediation',
    'active_idempotency_key',
    'VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
       GENERATED ALWAYS AS (
         CASE WHEN deleted = 0 THEN idempotency_key ELSE NULL END
       ) STORED AFTER `idempotency_key`'
);

ALTER TABLE interview_remediation
    MODIFY COLUMN claim_token
        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;

SET @v4_100_index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
      FROM information_schema.statistics
     WHERE table_schema = @v4_100_schema_name
       AND table_name = 'interview_remediation'
       AND index_name = 'uk_interview_remediation_user_token'
);
SET @v4_100_ddl = IF(
    @v4_100_index_columns = 'user_id,idempotency_key',
    'ALTER TABLE interview_remediation
       DROP INDEX uk_interview_remediation_user_token,
       ADD UNIQUE KEY uk_interview_remediation_user_token
         (user_id, active_idempotency_key)',
    IF(
        @v4_100_index_columns IS NULL,
        'ALTER TABLE interview_remediation
           ADD UNIQUE KEY uk_interview_remediation_user_token
             (user_id, active_idempotency_key)',
        'SELECT 1'
    )
);
PREPARE v4_100_stmt FROM @v4_100_ddl;
EXECUTE v4_100_stmt;
DEALLOCATE PREPARE v4_100_stmt;

CALL v4_100_add_index(
    'interview_remediation',
    'idx_interview_remediation_claim_recovery',
    'KEY `idx_interview_remediation_claim_recovery` (`status`, `claimed_at`, `id`)'
);

-- ---------------------------------------------------------------------------
-- Managed V13 prompt. DML is transactional so ACTIVE pointers and statuses
-- cannot diverge if publication fails.
-- ---------------------------------------------------------------------------

SET @v4_100_prompt_scene = 'JOB_COACH_DAILY_PLAN';
SET @v4_100_prompt_version = 'v13-agent-skill-gap-context';
SET @v4_100_prompt_variables =
    'contextJson,candidatesJson,taskCount,maxTotalMinutes';
SET @v4_100_prompt_content = CONCAT(
    '你是 CodeCoachAI 的求职训练 Agent。请根据用户上下文和候选任务生成今天的中文训练计划。', CHAR(10),
    '只能从候选任务中选择，必须返回 {{taskCount}} 个任务，总时长不得超过 {{maxTotalMinutes}} 分钟。', CHAR(10),
    '用户上下文中的 skillGaps 只允许映射到 relatedBizType 为 SKILL_GAP_ITEM 的候选任务。', CHAR(10),
    '选择 SKILL_GAP_ITEM 时，reason 必须引用对应短板的训练价值，不能虚构缺口、证据或经历。', CHAR(10),
    '没有 SUCCESS 匹配报告时，不得声称匹配报告证明了任何结论。', CHAR(10),
    '只输出 JSON，不要输出 Markdown、内部接口名或模型名。', CHAR(10),
    '用户上下文：', CHAR(10), '{{contextJson}}', CHAR(10),
    '候选任务：', CHAR(10), '{{candidatesJson}}', CHAR(10),
    'JSON 必须包含 summary、focusSkills 和 tasks；tasks 项必须包含 candidateId、type、title、description、reason、estimatedMinutes、priority、relatedSkillCode、relatedSkillName、relatedBizType、relatedBizId、actionUrl。'
);

START TRANSACTION;

INSERT INTO prompt_template (
    scene, name, template_name, description, content, template_content,
    variables, version, enabled, status, created_at, updated_at, deleted
)
SELECT @v4_100_prompt_scene,
       'Job Coach Daily Plan',
       'Job Coach Daily Plan',
       'V13 daily plan prompt with trusted skill-gap context',
       @v4_100_prompt_content,
       @v4_100_prompt_content,
       @v4_100_prompt_variables,
       @v4_100_prompt_version,
       1,
       1,
       NOW(),
       NOW(),
       0
 WHERE NOT EXISTS (
    SELECT 1
      FROM prompt_template
     WHERE scene = @v4_100_prompt_scene
       AND deleted = 0
 );

SET @v4_100_prompt_template_id = (
    SELECT id
      FROM prompt_template
     WHERE scene = @v4_100_prompt_scene
       AND deleted = 0
     ORDER BY enabled DESC, status DESC, updated_at DESC, id DESC
     LIMIT 1
);

SET @v4_100_prompt_model_params = (
    SELECT v.model_params_json
      FROM prompt_template p
      LEFT JOIN prompt_template_version v
        ON v.id = p.active_version_id
       AND v.deleted = 0
     WHERE p.id = @v4_100_prompt_template_id
     LIMIT 1
);

INSERT INTO prompt_template_version (
    template_id, scene, version_code, version_name, content,
    variables_json, model_params_json, status, is_active,
    activated_at, change_log, created_at, updated_at, deleted
)
VALUES (
    @v4_100_prompt_template_id,
    @v4_100_prompt_scene,
    @v4_100_prompt_version,
    'V13 agent skill-gap context',
    @v4_100_prompt_content,
    @v4_100_prompt_variables,
    COALESCE(
        @v4_100_prompt_model_params,
        '{"temperature":0.2,"responseFormat":"json_object"}'
    ),
    'INACTIVE',
    0,
    NULL,
    'V4_100 publishes the managed V13 skill-gap prompt contract',
    NOW(),
    NOW(),
    0
)
ON DUPLICATE KEY UPDATE
    version_name = VALUES(version_name),
    content = VALUES(content),
    variables_json = VALUES(variables_json),
    model_params_json = COALESCE(
        prompt_template_version.model_params_json,
        VALUES(model_params_json)
    ),
    status = 'INACTIVE',
    is_active = 0,
    activated_at = NULL,
    change_log = VALUES(change_log),
    updated_at = NOW(),
    deleted = 0;

UPDATE prompt_template_version
   SET status = 'INACTIVE',
       is_active = 0,
       updated_at = NOW()
 WHERE scene = @v4_100_prompt_scene
   AND deleted = 0
   AND NOT (
       template_id = @v4_100_prompt_template_id
       AND version_code = @v4_100_prompt_version
   );

UPDATE prompt_template_version
   SET status = 'ACTIVE',
       is_active = 1,
       activated_at = NOW(),
       updated_at = NOW(),
       deleted = 0
 WHERE template_id = @v4_100_prompt_template_id
   AND version_code = @v4_100_prompt_version;

SET @v4_100_prompt_version_id = (
    SELECT id
      FROM prompt_template_version
     WHERE template_id = @v4_100_prompt_template_id
       AND version_code = @v4_100_prompt_version
       AND deleted = 0
     LIMIT 1
);

UPDATE prompt_template
   SET enabled = 0,
       status = 0,
       updated_at = NOW()
 WHERE scene = @v4_100_prompt_scene
   AND deleted = 0
   AND id <> @v4_100_prompt_template_id;

UPDATE prompt_template
   SET active_version_id = @v4_100_prompt_version_id,
       content = @v4_100_prompt_content,
       template_content = @v4_100_prompt_content,
       variables = @v4_100_prompt_variables,
       version = @v4_100_prompt_version,
       enabled = 1,
       status = 1,
       updated_at = NOW()
 WHERE id = @v4_100_prompt_template_id;

COMMIT;

DROP PROCEDURE IF EXISTS v4_100_add_column;
DROP PROCEDURE IF EXISTS v4_100_add_index;

SET @v4_100_schema_name = NULL;
SET @v4_100_ddl = NULL;
SET @v4_100_index_columns = NULL;
SET @v4_100_prompt_scene = NULL;
SET @v4_100_prompt_version = NULL;
SET @v4_100_prompt_variables = NULL;
SET @v4_100_prompt_content = NULL;
SET @v4_100_prompt_template_id = NULL;
SET @v4_100_prompt_model_params = NULL;
SET @v4_100_prompt_version_id = NULL;
