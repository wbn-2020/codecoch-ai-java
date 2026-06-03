-- V4_023: seed fine-grained permissions for sensitive admin operations.
-- Idempotent: only inserts missing sys_menu rows and ADMIN role bindings.

SET @admin_root_id := (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id ASC LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Prompt Write', 'BUTTON', '/admin/ai/prompts', 'admin:ai:prompt:write', 301, 0, 1, 'Create, update, delete or enable Prompt templates'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:prompt:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Prompt Publish', 'BUTTON', '/admin/ai/prompts', 'admin:ai:prompt:publish', 302, 0, 1, 'Activate, rollback or disable Prompt versions'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:prompt:publish' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Prompt Test', 'BUTTON', '/admin/ai/prompts', 'admin:ai:prompt:test', 303, 0, 1, 'Run Prompt version tests'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:prompt:test' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Log Raw View', 'BUTTON', '/admin/ai/logs', 'admin:ai:log:raw:view', 304, 0, 1, 'View raw AI prompt, request and response content'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:log:raw:view' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Model Write', 'BUTTON', '/admin/ai/models', 'admin:ai:model:write', 305, 0, 1, 'Create, update or delete AI model configs'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:model:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'AI Model Publish', 'BUTTON', '/admin/ai/models', 'admin:ai:model:publish', 306, 0, 1, 'Set default model or change model runtime status'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:model:publish' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Async Task Retry', 'BUTTON', '/admin/async-tasks', 'admin:task:retry', 307, 0, 1, 'Retry failed async tasks and recover dead letters'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:task:retry' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Admin File Download', 'BUTTON', '/admin/files', 'admin:file:download', 308, 0, 1, 'Download user-uploaded files from admin'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:file:download' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'User Write', 'BUTTON', '/admin/users', 'admin:user:write', 309, 0, 1, 'Enable, disable or update users'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:user:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'User Password Reset', 'BUTTON', '/admin/users', 'admin:user:password:reset', 310, 0, 1, 'Reset user password from admin'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:user:password:reset' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Role Write', 'BUTTON', '/admin/roles', 'admin:role:write', 311, 0, 1, 'Create, update, delete or disable roles'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:role:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Role Assign', 'BUTTON', '/admin/roles', 'admin:role:assign', 312, 0, 1, 'Assign roles and role menu permissions'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:role:assign' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Menu Write', 'BUTTON', '/admin/menus', 'admin:menu:write', 313, 0, 1, 'Create, update or delete menu permission definitions'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:menu:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'System Config Write', 'BUTTON', '/admin/system/configs', 'admin:system:config:write', 314, 0, 1, 'Create, update, delete or toggle system configs'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:system:config:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'System Overview', 'MENU', '/admin/dashboard', 'admin:system:overview', 315, 1, 1, 'View admin dashboard and system overview'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:system:overview' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'System Config Raw View', 'BUTTON', '/admin/system/configs', 'admin:system:config:raw:view', 316, 0, 1, 'View raw sensitive system config values'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:system:config:raw:view' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Resume Admin Search', 'BUTTON', '/admin/search/resumes', 'admin:search:resume', 317, 0, 1, 'Search resumes across users from admin'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:search:resume' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Interview Admin Search', 'BUTTON', '/admin/search/interviews', 'admin:search:interview', 318, 0, 1, 'Search interviews across users from admin'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:search:interview' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Search Index Rebuild', 'BUTTON', '/admin/search', 'admin:search:index:rebuild', 319, 0, 1, 'Rebuild Elasticsearch indexes'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:search:index:rebuild' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Notice Write', 'BUTTON', '/admin/notices', 'admin:notice:write', 320, 0, 1, 'Send, broadcast or delete admin notices'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:notice:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Announcement List', 'MENU', '/admin/announcements', 'admin:announcement:list', 321, 1, 1, 'View system announcements from admin'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:announcement:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Announcement Write', 'BUTTON', '/admin/announcements', 'admin:announcement:write', 322, 0, 1, 'Create, update or delete system announcements'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:announcement:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Announcement Publish', 'BUTTON', '/admin/announcements', 'admin:announcement:publish', 323, 0, 1, 'Publish or offline system announcements'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:announcement:publish' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Admin Stats List', 'BUTTON', '/admin/stats', 'admin:stats:list', 324, 0, 1, 'View cross-user admin statistics'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:stats:list' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Write', 'BUTTON', '/admin/questions', 'admin:question:write', 325, 0, 1, 'Create, update, delete or change question status'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:write' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Import', 'BUTTON', '/admin/questions/import', 'admin:question:import', 326, 0, 1, 'Import question bank data'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:import' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Export', 'BUTTON', '/admin/questions/export', 'admin:question:export', 327, 0, 1, 'Export question bank data'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:export' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Question Embedding Rebuild', 'BUTTON', '/admin/questions/embedding', 'admin:question:embedding:rebuild', 328, 0, 1, 'Rebuild or retry question vector embeddings'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:question:embedding:rebuild' AND `deleted` = 0);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), 'Industry Template Write', 'BUTTON', '/admin/industry-templates', 'admin:industry-template:write', 329, 0, 1, 'Create, update, enable, disable or delete industry templates'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:industry-template:write' AND `deleted` = 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
JOIN `sys_menu` m ON m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.permission_code IN (
      'admin:ai:prompt:write',
      'admin:ai:prompt:publish',
      'admin:ai:prompt:test',
      'admin:ai:log:raw:view',
      'admin:ai:model:write',
      'admin:ai:model:publish',
      'admin:task:retry',
      'admin:file:download',
      'admin:user:write',
      'admin:user:password:reset',
      'admin:role:write',
      'admin:role:assign',
      'admin:menu:write',
      'admin:system:config:write',
      'admin:system:overview',
      'admin:system:config:raw:view',
      'admin:search:resume',
      'admin:search:interview',
      'admin:search:index:rebuild',
      'admin:notice:write',
      'admin:announcement:list',
      'admin:announcement:write',
      'admin:announcement:publish',
      'admin:stats:list',
      'admin:question:write',
      'admin:question:import',
      'admin:question:export',
      'admin:question:embedding:rebuild',
      'admin:industry-template:write'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );
