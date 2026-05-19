package com.codecoachai.user.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.user.domain.dto.AdminUserQueryDTO;
import com.codecoachai.user.domain.dto.InnerCreateUserDTO;
import com.codecoachai.user.domain.dto.UpdatePasswordDTO;
import com.codecoachai.user.domain.dto.UpdateUserProfileDTO;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.domain.vo.AdminUserPageVO;
import com.codecoachai.user.domain.vo.InnerCreateUserVO;
import com.codecoachai.user.domain.vo.InnerUserAuthVO;
import com.codecoachai.user.domain.vo.InnerUserBasicVO;
import com.codecoachai.user.domain.vo.InnerUserRoleVO;
import com.codecoachai.user.domain.vo.UserOverviewVO;
import com.codecoachai.user.domain.vo.UserDashboardOverviewVO;
import com.codecoachai.user.domain.vo.UserProfileVO;

public interface UserService {

    UserProfileVO getCurrentUserProfile();

    UserProfileVO updateCurrentUserProfile(UpdateUserProfileDTO dto);

    void updateCurrentUserPassword(UpdatePasswordDTO dto);

    void updateAvatar(String avatarUrl);

    void updatePhone(String phone);

    UserOverviewVO getOverview();

    UserDashboardOverviewVO getDashboardOverview();

    PageResult<AdminUserPageVO> pageAdminUsers(AdminUserQueryDTO query);

    void updateUserStatus(Long id, UpdateUserStatusDTO dto);

    String resetPassword(Long id);

    InnerUserAuthVO getInnerUserByUsername(String username);

    InnerCreateUserVO createInnerUser(InnerCreateUserDTO dto);

    InnerUserRoleVO getInnerUserRoles(Long id);

    InnerUserBasicVO getInnerUser(Long id);
}
