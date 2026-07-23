CREATE TABLE IF NOT EXISTS `interview_rubric_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `rubric_code` VARCHAR(64) NOT NULL COMMENT 'stable rubric code',
    `version_no` INT NOT NULL COMMENT 'monotonic version number within rubric code',
    `rubric_name` VARCHAR(128) NOT NULL COMMENT 'display name',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'version description',
    `locale` VARCHAR(32) NOT NULL DEFAULT 'zh-CN' COMMENT 'content locale',
    `dimensions_json` LONGTEXT NOT NULL COMMENT 'structured scoring dimensions and weights',
    `version_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
    `created_by` BIGINT DEFAULT NULL COMMENT 'creator user id',
    `published_at` DATETIME DEFAULT NULL COMMENT 'publish time',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete flag',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_irv_code_version` (`rubric_code`, `version_no`),
    KEY `idx_irv_current` (`rubric_code`, `version_status`, `version_no`),
    KEY `idx_irv_created_by` (`created_by`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='immutable interview rubric versions';

CREATE TABLE IF NOT EXISTS `interview_scenario_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `scenario_code` VARCHAR(64) NOT NULL COMMENT 'stable scenario code',
    `version_no` INT NOT NULL COMMENT 'monotonic version number within scenario code',
    `scenario_name` VARCHAR(128) NOT NULL COMMENT 'display name',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'version description',
    `locale` VARCHAR(32) NOT NULL DEFAULT 'zh-CN' COMMENT 'content locale',
    `script_json` LONGTEXT NOT NULL COMMENT 'structured interview stages and prompts',
    `rubric_version_id` BIGINT NOT NULL COMMENT 'bound immutable rubric version',
    `version_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
    `created_by` BIGINT DEFAULT NULL COMMENT 'creator user id',
    `published_at` DATETIME DEFAULT NULL COMMENT 'publish time',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete flag',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_isv_code_version` (`scenario_code`, `version_no`),
    KEY `idx_isv_current` (`scenario_code`, `version_status`, `version_no`),
    KEY `idx_isv_rubric` (`rubric_version_id`),
    KEY `idx_isv_created_by` (`created_by`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='immutable interview scenario versions';

CREATE TABLE IF NOT EXISTS `interview_scenario_binding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `user_id` BIGINT NOT NULL COMMENT 'owner user id',
    `session_id` BIGINT NOT NULL COMMENT 'interview session id',
    `scenario_version_id` BIGINT NOT NULL COMMENT 'immutable scenario version',
    `rubric_version_id` BIGINT NOT NULL COMMENT 'rubric version captured from scenario',
    `binding_source` VARCHAR(32) NOT NULL DEFAULT 'USER_SELECTED' COMMENT 'binding source',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'logic delete flag',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_isb_session` (`session_id`),
    KEY `idx_isb_user_session` (`user_id`, `session_id`, `deleted`),
    KEY `idx_isb_versions` (`scenario_version_id`, `rubric_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='immutable scenario and rubric binding for an interview session';

INSERT INTO `interview_rubric_version`
    (`rubric_code`, `version_no`, `rubric_name`, `description`, `locale`,
     `dimensions_json`, `version_status`, `created_by`, `published_at`)
SELECT
    'CODECOACH_CORE',
    1,
    'CodeCoachAI Core Interview Rubric',
    'Shared evidence-based rubric for built-in interview scenarios',
    'zh-CN',
    '[{"code":"COMMUNICATION_STRUCTURE","name":"表达结构","weight":20,"anchors":{"strong":"结论、依据、案例和边界完整","weak":"仅有零散结论"}},{"code":"TECHNICAL_DEPTH","name":"技术深度","weight":25,"anchors":{"strong":"能解释原理、取舍和失败模式","weak":"只复述术语"}},{"code":"PROJECT_EVIDENCE","name":"项目证据","weight":20,"anchors":{"strong":"职责、动作和结果可核对","weak":"缺少个人贡献或结果"}},{"code":"PROBLEM_SOLVING","name":"问题解决","weight":20,"anchors":{"strong":"能拆解问题并验证方案","weak":"方案缺少验证路径"}},{"code":"RISK_AWARENESS","name":"风险意识","weight":15,"anchors":{"strong":"能说明容量、异常和回滚","weak":"忽略边界和失败场景"}}]',
    'PUBLISHED',
    NULL,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
      FROM `interview_rubric_version`
     WHERE `rubric_code` = 'CODECOACH_CORE'
       AND `version_no` = 1
);

INSERT INTO `interview_scenario_version`
    (`scenario_code`, `version_no`, `scenario_name`, `description`, `locale`,
     `script_json`, `rubric_version_id`, `version_status`, `created_by`, `published_at`)
SELECT
    seed.scenario_code,
    1,
    seed.scenario_name,
    seed.description,
    'zh-CN',
    seed.script_json,
    rubric.id,
    'PUBLISHED',
    NULL,
    CURRENT_TIMESTAMP
FROM (
    SELECT
        'HR_SCREENING' AS scenario_code,
        'HR 初筛' AS scenario_name,
        '训练自我介绍、动机、稳定性和岗位匹配表达' AS description,
        '{"questionBudget":5,"timeBudgetMinutes":25,"stages":[{"code":"INTRODUCTION","name":"自我介绍","questionCount":1,"timeBudgetMinutes":4,"focusPoints":["经历主线","岗位匹配"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":1},{"code":"MOTIVATION","name":"求职动机","questionCount":2,"timeBudgetMinutes":8,"focusPoints":["选择原因","目标一致性"],"allowFollowUp":true,"maxFollowUpCount":1},{"code":"BEHAVIOR","name":"行为经历","questionCount":1,"timeBudgetMinutes":7,"focusPoints":["冲突协作","结果复盘"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":2},{"code":"CLOSING","name":"候选人提问","questionCount":1,"timeBudgetMinutes":6,"focusPoints":["岗位理解","有效提问"],"allowFollowUp":false}]}' AS script_json
    UNION ALL
    SELECT
        'TECHNICAL_FOUNDATION',
        '技术基础',
        '训练语言、并发、JVM、数据库和缓存基础',
        '{"questionBudget":7,"timeBudgetMinutes":40,"stages":[{"code":"LANGUAGE_CORE","name":"语言与集合","questionCount":2,"focusPoints":["Java 核心","集合原理"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"CONCURRENCY","name":"并发与 JVM","questionCount":2,"focusPoints":["线程安全","内存与 GC"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"DATABASE","name":"数据库","questionCount":2,"focusPoints":["索引","事务与锁"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"CACHE","name":"缓存","questionCount":1,"focusPoints":["一致性","失效与容灾"],"allowFollowUp":true,"maxFollowUpCount":1}]}'
    UNION ALL
    SELECT
        'TECHNICAL_ROUND_1',
        '技术一面',
        '覆盖基础能力、项目证据和常见线上问题',
        '{"questionBudget":7,"timeBudgetMinutes":45,"stages":[{"code":"FOUNDATION","name":"技术基础","questionCount":2,"focusPoints":["核心原理","知识边界"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"PROJECT","name":"项目追问","questionCount":3,"focusPoints":["个人职责","技术难点","量化结果"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":2},{"code":"TROUBLESHOOTING","name":"故障排查","questionCount":2,"focusPoints":["定位路径","验证与回滚"],"allowFollowUp":true,"maxFollowUpCount":2}]}'
    UNION ALL
    SELECT
        'TECHNICAL_ROUND_2',
        '技术二面',
        '强化架构取舍、复杂问题与工程领导力',
        '{"questionBudget":6,"timeBudgetMinutes":50,"stages":[{"code":"ARCHITECTURE","name":"架构设计","questionCount":2,"focusPoints":["边界划分","技术取舍"],"allowFollowUp":true,"maxFollowUpCount":3},{"code":"COMPLEX_PROBLEM","name":"复杂问题","questionCount":2,"focusPoints":["系统性分析","推进与复盘"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":2},{"code":"ENGINEERING","name":"工程治理","questionCount":1,"focusPoints":["质量","可观测性","交付效率"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"LEADERSHIP","name":"协作影响力","questionCount":1,"focusPoints":["决策","协作与影响"],"allowFollowUp":true,"maxFollowUpCount":1}]}'
    UNION ALL
    SELECT
        'PROJECT_DEEP_DIVE',
        '项目深挖',
        '围绕真实项目训练背景、职责、难点、方案和结果',
        '{"questionBudget":7,"timeBudgetMinutes":45,"stages":[{"code":"PROJECT_CONTEXT","name":"项目背景","questionCount":1,"focusPoints":["业务目标","系统范围"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":1},{"code":"OWNERSHIP","name":"个人职责","questionCount":1,"focusPoints":["职责边界","协作对象"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":2},{"code":"TECHNICAL_CHALLENGE","name":"技术难点","questionCount":2,"focusPoints":["问题成因","方案取舍"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":3},{"code":"RESULTS","name":"结果与证据","questionCount":2,"focusPoints":["量化结果","验证方法"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":2},{"code":"REFLECTION","name":"复盘改进","questionCount":1,"focusPoints":["失败经验","下一步优化"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":1}]}'
    UNION ALL
    SELECT
        'SYSTEM_DESIGN',
        '系统设计',
        '训练需求澄清、容量估算、架构、数据与故障处理',
        '{"questionBudget":6,"timeBudgetMinutes":55,"stages":[{"code":"REQUIREMENTS","name":"需求澄清","questionCount":1,"focusPoints":["功能范围","非功能约束"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"CAPACITY","name":"容量估算","questionCount":1,"focusPoints":["流量","存储与峰值"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"HIGH_LEVEL_DESIGN","name":"总体架构","questionCount":2,"focusPoints":["模块边界","数据流与取舍"],"allowFollowUp":true,"maxFollowUpCount":3},{"code":"DATA_RELIABILITY","name":"数据与可靠性","questionCount":1,"focusPoints":["一致性","容灾与恢复"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"BOTTLENECKS","name":"瓶颈演进","questionCount":1,"focusPoints":["观测","扩展与降级"],"allowFollowUp":true,"maxFollowUpCount":2}]}'
    UNION ALL
    SELECT
        'BEHAVIORAL',
        '行为面试',
        '使用可核对经历训练协作、冲突、失败与影响力',
        '{"questionBudget":6,"timeBudgetMinutes":35,"stages":[{"code":"COLLABORATION","name":"协作","questionCount":2,"focusPoints":["角色","行动与结果"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":2},{"code":"CONFLICT","name":"冲突处理","questionCount":1,"focusPoints":["分歧","决策依据"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"FAILURE","name":"失败复盘","questionCount":1,"focusPoints":["责任","学习与改进"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"INFLUENCE","name":"影响力","questionCount":1,"focusPoints":["推动方式","可验证结果"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"GROWTH","name":"成长","questionCount":1,"focusPoints":["反馈","能力演进"],"allowFollowUp":true,"maxFollowUpCount":1}]}'
    UNION ALL
    SELECT
        'STRESS_COMPREHENSIVE',
        '压力追问与综合面试',
        '在明确训练边界下进行高强度追问和综合判断',
        '{"questionBudget":7,"timeBudgetMinutes":50,"stages":[{"code":"OPENING","name":"快速开场","questionCount":1,"focusPoints":["简洁表达","核心优势"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":1},{"code":"TECHNICAL_CHALLENGE","name":"技术压力追问","questionCount":2,"focusPoints":["证据","边界与反例"],"allowFollowUp":true,"maxFollowUpCount":3},{"code":"PROJECT_CHALLENGE","name":"项目压力追问","questionCount":2,"focusPoints":["个人贡献","结果可信度"],"basedOnResume":true,"allowFollowUp":true,"maxFollowUpCount":3},{"code":"SCENARIO_DECISION","name":"场景决策","questionCount":1,"focusPoints":["取舍","风险与回滚"],"allowFollowUp":true,"maxFollowUpCount":2},{"code":"REFLECTION","name":"即时复盘","questionCount":1,"focusPoints":["承压表达","改进动作"],"allowFollowUp":false}]}'
) seed
JOIN `interview_rubric_version` rubric
  ON rubric.`rubric_code` = 'CODECOACH_CORE'
 AND rubric.`version_no` = 1
WHERE NOT EXISTS (
    SELECT 1
      FROM `interview_scenario_version` existing
     WHERE existing.`scenario_code` = seed.scenario_code
       AND existing.`version_no` = 1
);
