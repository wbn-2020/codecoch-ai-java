-- V4_005: seed V4 admin menu permissions used by the Vue admin routes.
-- Idempotent: only inserts missing sys_menu rows and missing ADMIN role bindings.

SET @admin_root_id = (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT 0, 'Admin Dashboard', 'MENU', '/admin', 'admin:v3', 10, 1, 1, 'Admin dashboard'
WHERE @admin_root_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:v3' AND `deleted` = 0);

SET @admin_root_id = (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Prompt Template', 'MENU', '/admin/ai/prompts', 'admin:ai:prompt:list', 140, 1, 1, 'Prompt template governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:prompt:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Agent Analytics', 'MENU', '/admin/analytics/agent', 'admin:analytics:agent', 250, 1, 1, 'V4 Agent analytics dashboard'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:analytics:agent' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 AI Ops Analytics', 'MENU', '/admin/analytics/ai', 'admin:analytics:ai', 260, 1, 1, 'V4 AI Ops analytics dashboard'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:analytics:ai' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Agent Runs', 'MENU', '/admin/agent/runs', 'admin:agent:run:list', 270, 1, 1, 'V4 Agent run governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:agent:run:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Agent Tasks', 'MENU', '/admin/agent/tasks', 'admin:agent:task:list', 280, 1, 1, 'V4 Agent task governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:agent:task:list' AND `deleted` = 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
JOIN `sys_menu` m ON m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.permission_code IN (
      'admin:v3',
      'admin:ai:prompt:list',
      'admin:analytics:agent',
      'admin:analytics:ai',
      'admin:agent:run:list',
      'admin:agent:task:list'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );
