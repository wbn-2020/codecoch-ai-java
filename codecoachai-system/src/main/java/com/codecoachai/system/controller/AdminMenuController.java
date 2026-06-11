package com.codecoachai.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.system.domain.dto.RoleMenuAssignDTO;
import com.codecoachai.system.domain.dto.SysMenuSaveDTO;
import com.codecoachai.system.domain.entity.SysMenu;
import com.codecoachai.system.domain.entity.SysRoleMenu;
import com.codecoachai.system.domain.vo.SysMenuTreeVO;
import com.codecoachai.system.mapper.SysMenuMapper;
import com.codecoachai.system.mapper.SysRoleMenuMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminMenuController {

    private static final String MENU_TYPE_BUTTON = "BUTTON";
    private static final String PERM_MENU_LIST = "admin:menu:list";
    private static final String PERM_MENU_WRITE = "admin:menu:write";
    private static final String PERM_ROLE_LIST = "admin:role:list";
    private static final String PERM_ROLE_ASSIGN = "admin:role:assign";

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final AdminPermissionGuard permissionGuard;

    @GetMapping("/admin/menus")
    public Result<List<SysMenuTreeVO>> tree() {
        permissionGuard.require(PERM_MENU_LIST);
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .and(wrapper -> wrapper.eq(SysMenu::getStatus, 1).or().isNull(SysMenu::getStatus))
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        return Result.success(toTree(menus));
    }

    @PostMapping("/admin/menus")
    @OperationLog(module = "system", action = "CREATE_MENU", description = "新增菜单权限")
    public Result<SysMenu> create(@RequestBody SysMenuSaveDTO dto) {
        permissionGuard.require(PERM_MENU_WRITE);
        SysMenu menu = new SysMenu();
        apply(menu, dto);
        menuMapper.insert(menu);
        return Result.success(menu);
    }

    @PutMapping("/admin/menus/{id}")
    @OperationLog(module = "system", action = "UPDATE_MENU", description = "编辑菜单权限")
    public Result<SysMenu> update(@PathVariable Long id, @RequestBody SysMenuSaveDTO dto) {
        permissionGuard.require(PERM_MENU_WRITE);
        SysMenu menu = get(id);
        apply(menu, dto);
        menuMapper.updateById(menu);
        return Result.success(menu);
    }

    @DeleteMapping("/admin/menus/{id}")
    @OperationLog(module = "system", action = "DELETE_MENU", description = "删除菜单权限")
    public Result<Void> delete(@PathVariable Long id) {
        permissionGuard.require(PERM_MENU_WRITE);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        menuMapper.deleteById(id);
        return Result.success();
    }

    @GetMapping("/admin/roles/{roleId}/menus")
    public Result<List<Long>> roleMenus(@PathVariable Long roleId) {
        permissionGuard.requireAny(PERM_ROLE_LIST, PERM_MENU_LIST);
        return Result.success(roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList());
    }

    @PutMapping("/admin/roles/{roleId}/menus")
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "system", action = "ASSIGN_ROLE_MENU", description = "分配角色菜单权限")
    public Result<Void> assignRoleMenus(@PathVariable Long roleId, @RequestBody RoleMenuAssignDTO dto) {
        permissionGuard.require(PERM_ROLE_ASSIGN);
        doAssignRoleMenus(roleId, dto);
        return Result.success();
    }

    @PostMapping("/admin/roles/{roleId}/menus")
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "system", action = "ASSIGN_ROLE_MENU_COMPAT", description = "兼容入口分配角色菜单权限")
    public Result<Void> assignRoleMenusByPost(@PathVariable Long roleId, @RequestBody RoleMenuAssignDTO dto) {
        permissionGuard.require(PERM_ROLE_ASSIGN);
        doAssignRoleMenus(roleId, dto);
        return Result.success();
    }

    private void doAssignRoleMenus(Long roleId, RoleMenuAssignDTO dto) {
        Set<Long> menuIds = resolveGrantMenuIds(dto);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        }
    }

    private Set<Long> resolveGrantMenuIds(RoleMenuAssignDTO dto) {
        if (dto == null || dto.getMenuIds() == null || dto.getMenuIds().isEmpty()) {
            return Set.of();
        }
        Set<Long> requestedIds = dto.getMenuIds().stream()
                .filter(id -> id != null && id > 0)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (requestedIds.isEmpty()) {
            return Set.of();
        }

        Map<Long, SysMenu> menuById = new LinkedHashMap<>();
        menuMapper.selectList(new LambdaQueryWrapper<SysMenu>())
                .forEach(menu -> menuById.put(menu.getId(), menu));

        List<Long> missingIds = requestedIds.stream()
                .filter(id -> !menuById.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部分菜单不存在或已不可用：" + missingIds);
        }

        Set<Long> resolvedIds = new LinkedHashSet<>();
        for (Long menuId : requestedIds) {
            collectMenuAndAncestors(menuId, menuById, resolvedIds);
        }
        return resolvedIds;
    }

    private void collectMenuAndAncestors(Long menuId, Map<Long, SysMenu> menuById, Set<Long> resolvedIds) {
        Long currentId = menuId;
        while (currentId != null && currentId > 0 && menuById.containsKey(currentId)) {
            resolvedIds.add(currentId);
            currentId = menuById.get(currentId).getParentId();
        }
    }

    private void apply(SysMenu menu, SysMenuSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getMenuName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "菜单名称不能为空");
        }
        menu.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        menu.setMenuName(dto.getMenuName().trim());
        menu.setMenuType(StringUtils.hasText(dto.getMenuType()) ? dto.getMenuType().trim() : "MENU");
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setPermissionCode(dto.getPermissionCode());
        menu.setIcon(dto.getIcon());
        menu.setSortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder());
        menu.setVisible(dto.getVisible() == null ? 1 : dto.getVisible());
        menu.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        menu.setRemark(dto.getRemark());
    }

    private SysMenu get(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "菜单不存在或已不可用");
        }
        return menu;
    }

    private List<SysMenuTreeVO> toTree(List<SysMenu> menus) {
        Map<Long, SysMenuTreeVO> index = new LinkedHashMap<>();
        Map<String, Long> menuByParentAndPath = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            index.put(menu.getId(), toVO(menu));
            if (!isButton(menu.getMenuType()) && StringUtils.hasText(menu.getPath())) {
                menuByParentAndPath.putIfAbsent(pathKey(menu.getParentId(), menu.getPath()), menu.getId());
            }
        }
        List<SysMenuTreeVO> roots = new ArrayList<>();
        for (SysMenuTreeVO vo : index.values()) {
            Long parentId = displayParentId(vo, menuByParentAndPath);
            if (parentId == null || parentId == 0 || !index.containsKey(parentId)) {
                roots.add(vo);
            } else {
                index.get(parentId).getChildren().add(vo);
            }
        }
        sortTree(roots);
        return roots;
    }

    private Long displayParentId(SysMenuTreeVO vo, Map<String, Long> menuByParentAndPath) {
        Long parentId = vo.getParentId();
        if (isButton(vo.getMenuType()) && StringUtils.hasText(vo.getPath())) {
            Long menuId = menuByParentAndPath.get(pathKey(parentId, vo.getPath()));
            if (menuId != null && !menuId.equals(vo.getId())) {
                return menuId;
            }
        }
        return parentId;
    }

    private boolean isButton(String menuType) {
        return MENU_TYPE_BUTTON.equalsIgnoreCase(menuType);
    }

    private String pathKey(Long parentId, String path) {
        return (parentId == null ? 0L : parentId) + ":" + path.trim();
    }

    private void sortTree(List<SysMenuTreeVO> nodes) {
        nodes.sort(Comparator.comparing(SysMenuTreeVO::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysMenuTreeVO::getId));
        nodes.forEach(node -> sortTree(node.getChildren()));
    }

    private SysMenuTreeVO toVO(SysMenu menu) {
        SysMenuTreeVO vo = new SysMenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setPermissionCode(menu.getPermissionCode());
        vo.setIcon(menu.getIcon());
        vo.setSortOrder(menu.getSortOrder());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        vo.setRemark(menu.getRemark());
        return vo;
    }
}
