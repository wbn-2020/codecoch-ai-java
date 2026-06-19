-- V4_029: seed fine-grained permission for controlled Prompt raw content access.
-- Idempotent: only inserts missing sys_menu rows and baseline ADMIN role bindings.

SET @admin_root_id := (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id ASC LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), '提示词原文查看', 'BUTTON', '/admin/ai/prompts', 'admin:ai:prompt:raw:view', 331, 0, 1, '二次确认后查看 Prompt 模板和版本原文'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:ai:prompt:raw:view' AND `deleted` = 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
JOIN `sys_menu` m ON m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.permission_code = 'admin:ai:prompt:raw:view'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );
