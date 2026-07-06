-- V4_044: seed TraceCockpit entry permission used by the Vue admin route and menu.
-- Idempotent: only inserts the missing sys_menu row and missing ADMIN role binding.

SET @admin_root_id := (
    SELECT id FROM `sys_menu`
    WHERE `permission_code` = 'admin:v3' AND `deleted` = 0
    ORDER BY id ASC LIMIT 1
);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `permission_code`, `sort_order`, `visible`, `status`, `remark`)
SELECT COALESCE(@admin_root_id, 0), '链路驾驶舱', 'MENU', '/admin/trace-cockpit', 'admin:trace:cockpit:view', 155, 1, 1, '查看 TraceCockpit 聚合链路驾驶舱'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission_code` = 'admin:trace:cockpit:view' AND `deleted` = 0);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id
FROM `sys_role` r
JOIN `sys_menu` m ON m.deleted = 0
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.permission_code = 'admin:trace:cockpit:view'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.deleted = 0
  );
