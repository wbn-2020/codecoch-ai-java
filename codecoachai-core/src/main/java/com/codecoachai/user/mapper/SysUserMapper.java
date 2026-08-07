package com.codecoachai.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.user.domain.entity.SysUser;
import org.apache.ibatis.annotations.Param;

public interface SysUserMapper extends BaseMapper<SysUser> {

    Page<SysUser> selectAdminUserPage(Page<SysUser> page,
                                      @Param("keyword") String keyword,
                                      @Param("status") Integer status,
                                      @Param("roleCode") String roleCode);
}
