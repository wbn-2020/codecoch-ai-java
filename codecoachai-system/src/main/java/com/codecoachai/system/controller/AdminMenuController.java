package com.codecoachai.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.system.domain.dto.RoleMenuAssignDTO;
import com.codecoachai.system.domain.dto.SysMenuSaveDTO;
import com.codecoachai.system.domain.entity.SysMenu;
import com.codecoachai.system.domain.entity.SysRoleMenu;
import com.codecoachai.system.domain.vo.SysMenuTreeVO;
import com.codecoachai.system.mapper.SysMenuMapper;
import com.codecoachai.system.mapper.SysRoleMenuMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @GetMapping("/admin/menus")
    public Result<List<SysMenuTreeVO>> tree() {
        SecurityAssert.requireAdmin();
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        return Result.success(toTree(menus));
    }

    @PostMapping("/admin/menus")
    public Result<SysMenu> create(@RequestBody SysMenuSaveDTO dto) {
        SecurityAssert.requireAdmin();
        SysMenu menu = new SysMenu();
        apply(menu, dto);
        menuMapper.insert(menu);
        return Result.success(menu);
    }

    @PutMapping("/admin/menus/{id}")
    public Result<SysMenu> update(@PathVariable Long id, @RequestBody SysMenuSaveDTO dto) {
        SecurityAssert.requireAdmin();
        SysMenu menu = get(id);
        apply(menu, dto);
        menuMapper.updateById(menu);
        return Result.success(menu);
    }

    @DeleteMapping("/admin/menus/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        menuMapper.deleteById(id);
        return Result.success();
    }

    @GetMapping("/admin/roles/{roleId}/menus")
    public Result<List<Long>> roleMenus(@PathVariable Long roleId) {
        SecurityAssert.requireAdmin();
        return Result.success(roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList());
    }

    @PutMapping("/admin/roles/{roleId}/menus")
    public Result<Void> assignRoleMenus(@PathVariable Long roleId, @RequestBody RoleMenuAssignDTO dto) {
        SecurityAssert.requireAdmin();
        doAssignRoleMenus(roleId, dto);
        return Result.success();
    }

    @PostMapping("/admin/roles/{roleId}/menus")
    public Result<Void> assignRoleMenusByPost(@PathVariable Long roleId, @RequestBody RoleMenuAssignDTO dto) {
        SecurityAssert.requireAdmin();
        doAssignRoleMenus(roleId, dto);
        return Result.success();
    }

    private void doAssignRoleMenus(Long roleId, RoleMenuAssignDTO dto) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (dto != null && dto.getMenuIds() != null) {
            for (Long menuId : dto.getMenuIds().stream().distinct().toList()) {
                SysRoleMenu relation = new SysRoleMenu();
                relation.setRoleId(roleId);
                relation.setMenuId(menuId);
                roleMenuMapper.insert(relation);
            }
        }
    }

    private void apply(SysMenu menu, SysMenuSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getMenuName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "menuName is required");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "menu not found");
        }
        return menu;
    }

    private List<SysMenuTreeVO> toTree(List<SysMenu> menus) {
        Map<Long, SysMenuTreeVO> index = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            index.put(menu.getId(), toVO(menu));
        }
        List<SysMenuTreeVO> roots = new ArrayList<>();
        for (SysMenuTreeVO vo : index.values()) {
            if (vo.getParentId() == null || vo.getParentId() == 0 || !index.containsKey(vo.getParentId())) {
                roots.add(vo);
            } else {
                index.get(vo.getParentId()).getChildren().add(vo);
            }
        }
        sortTree(roots);
        return roots;
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
