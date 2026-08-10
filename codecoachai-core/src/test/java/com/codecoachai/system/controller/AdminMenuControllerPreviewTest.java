package com.codecoachai.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.system.domain.entity.SysMenu;
import com.codecoachai.system.domain.entity.SysRoleMenu;
import com.codecoachai.system.mapper.SysMenuMapper;
import com.codecoachai.system.mapper.SysRoleMenuMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminMenuControllerPreviewTest {

    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminPermissionCache adminPermissionCache;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminMenuController controller;

    @BeforeEach
    void setUp() {
        initTableInfo(SysMenu.class);
        initTableInfo(SysRoleMenu.class);
        controller = new AdminMenuController(
                menuMapper,
                roleMenuMapper,
                permissionGuard,
                adminPermissionCache,
                operationConfirmationGuard);
    }

    @Test
    void roleMenuPreviewReturnsActiveTreeAndPermissionSummary() {
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(
                roleMenu(10L),
                roleMenu(11L),
                roleMenu(99L)));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(10L, 0L, "Question", "MENU", null),
                menu(11L, 10L, "Question List", "BUTTON", "admin:question:list")));

        var result = controller.roleMenuPreview(7L).getData();

        assertEquals(7L, result.getRoleId());
        assertEquals(2, result.getAssignedMenuCount());
        assertEquals(1, result.getUnavailableAssignmentCount());
        assertEquals(List.of("admin:question:list"), result.getPermissionCodes());
        assertEquals(1, result.getPermissionCount());
        assertEquals(1, result.getMenuTree().size());
        assertEquals(1, result.getMenuTree().get(0).getChildren().size());
        verify(permissionGuard).requireAny("admin:role:list", "admin:menu:list");
    }

    @Test
    void roleMenuPreviewDoesNotQueryMenusWhenRoleHasNoAssignments() {
        when(roleMenuMapper.selectList(any())).thenReturn(List.of());

        var result = controller.roleMenuPreview(8L).getData();

        assertEquals(0, result.getAssignedMenuCount());
        assertEquals(0, result.getUnavailableAssignmentCount());
        assertEquals(List.of(), result.getPermissionCodes());
    }

    private static SysRoleMenu roleMenu(Long menuId) {
        SysRoleMenu relation = new SysRoleMenu();
        relation.setMenuId(menuId);
        return relation;
    }

    private static SysMenu menu(Long id, Long parentId, String name, String type, String permissionCode) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setPermissionCode(permissionCode);
        menu.setStatus(1);
        return menu;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
