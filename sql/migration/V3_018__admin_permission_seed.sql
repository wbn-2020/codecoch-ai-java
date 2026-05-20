-- V3_018: seed admin permission resources used by the Vue admin routes.
-- Idempotent: only inserts missing sys_menu rows and missing ADMIN role bindings.

SET @admin_root_id = (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Admin Dashboard', 'MENU', '/admin', 'admin:v3', 10, 1, 1, 'Admin dashboard'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:v3' AND `deleted` = 0);

SET @admin_root_id = (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'File Governance', 'MENU', '/admin/files', 'admin:file:list', 20, 1, 1, 'File governance list'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:file:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'User Management', 'MENU', '/admin/users', 'admin:user:list', 30, 1, 1, 'User management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:user:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Role Management', 'MENU', '/admin/roles', 'admin:role:list', 40, 1, 1, 'Role management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:role:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Management', 'MENU', '/admin/questions', 'admin:question:list', 50, 1, 1, 'Question management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Question Generate', 'MENU', '/admin/ai/questions/generate', 'admin:question:generate', 60, 1, 1, 'AI question generation'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:generate' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Review', 'MENU', '/admin/question-reviews', 'admin:question:review', 70, 1, 1, 'Question review'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:review' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Duplicate Review', 'MENU', '/admin/question-duplicate-reviews', 'admin:question:dedupe', 80, 1, 1, 'Question duplicate review'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:dedupe' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Relation', 'MENU', '/admin/question-relations', 'admin:question:relation', 90, 1, 1, 'Question relation management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:relation' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Category', 'MENU', '/admin/question-categories', 'admin:question:category', 100, 1, 1, 'Question category management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:category' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Tag', 'MENU', '/admin/question-tags', 'admin:question:tag', 110, 1, 1, 'Question tag management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:tag' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Group', 'MENU', '/admin/question-groups', 'admin:question:group', 120, 1, 1, 'Question group management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:group' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Industry Template', 'MENU', '/admin/industry-templates', 'admin:industry-template:list', 130, 1, 1, 'Industry template management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:industry-template:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Prompt Template', 'MENU', '/admin/ai/prompts', 'admin:ai:prompt:list', 140, 1, 1, 'Prompt template governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:prompt:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Call Log', 'MENU', '/admin/ai/logs', 'admin:ai:log:list', 150, 1, 1, 'AI call log governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:log:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Model Config', 'MENU', '/admin/ai/models', 'admin:ai:model:list', 160, 1, 1, 'AI model governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:model:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Menu Permission', 'MENU', '/admin/menus', 'admin:menu:list', 170, 1, 1, 'Menu permission governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:menu:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Notification Management', 'MENU', '/admin/notices', 'admin:notice:list', 180, 1, 1, 'Notification management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:notice:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Operation Log', 'MENU', '/admin/operation-logs', 'admin:audit:operation-log', 190, 1, 1, 'Operation audit log'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:audit:operation-log' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Login Log', 'MENU', '/admin/login-logs', 'admin:audit:login-log', 200, 1, 1, 'Login audit log'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:audit:login-log' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Interview Management', 'MENU', '/admin/interviews', 'admin:interview:list', 210, 1, 1, 'Interview governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:interview:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Interview Report', 'MENU', '/admin/interview-reports', 'admin:interview:report', 220, 1, 1, 'Interview report governance'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:interview:report' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Async Task Center', 'MENU', '/admin/async-tasks', 'admin:task:list', 230, 1, 1, 'Async task center'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:task:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'System Config', 'MENU', '/admin/system/configs', 'admin:system:config:list', 240, 1, 1, 'System config management'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:system:config:list' AND `deleted` = 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
JOIN `sys_menu` m ON m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.permission_code IN (
      'admin:v3',
      'admin:file:list',
      'admin:user:list',
      'admin:role:list',
      'admin:question:list',
      'admin:question:generate',
      'admin:question:review',
      'admin:question:dedupe',
      'admin:question:relation',
      'admin:question:category',
      'admin:question:tag',
      'admin:question:group',
      'admin:industry-template:list',
      'admin:ai:prompt:list',
      'admin:ai:log:list',
      'admin:ai:model:list',
      'admin:menu:list',
      'admin:notice:list',
      'admin:audit:operation-log',
      'admin:audit:login-log',
      'admin:interview:list',
      'admin:interview:report',
      'admin:task:list',
      'admin:system:config:list'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );
