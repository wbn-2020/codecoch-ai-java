-- V4_006: seed fine-grained V4 admin permissions for server-side checks.
-- Idempotent: only inserts missing sys_menu rows and ADMIN role bindings.

SET @admin_root_id = (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Analytics Metric Write', 'BUTTON', '/admin/analytics/metrics', 'admin:analytics:metric:write', 281, 0, 1, 'Create or update V4 analytics metric definitions'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:analytics:metric:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Analytics Job Run', 'BUTTON', '/admin/analytics/jobs', 'admin:analytics:job:run', 282, 0, 1, 'Rerun analytics jobs and trigger Agent daily plan jobs'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:analytics:job:run' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Prompt Regression List', 'MENU', '/admin/ai/prompt-regression', 'admin:agent:prompt-regression:list', 283, 1, 1, 'View V4 prompt regression cases and results'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:agent:prompt-regression:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Prompt Regression Write', 'BUTTON', '/admin/ai/prompt-regression', 'admin:agent:prompt-regression:write', 284, 0, 1, 'Create or update V4 prompt regression cases'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:agent:prompt-regression:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'V4 Prompt Regression Run', 'BUTTON', '/admin/ai/prompt-regression', 'admin:agent:prompt-regression:run', 285, 0, 1, 'Run V4 prompt regression cases'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:agent:prompt-regression:run' AND `deleted` = 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
JOIN `sys_menu` m ON m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.permission_code IN (
      'admin:analytics:metric:write',
      'admin:analytics:job:run',
      'admin:agent:prompt-regression:list',
      'admin:agent:prompt-regression:write',
      'admin:agent:prompt-regression:run'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );
