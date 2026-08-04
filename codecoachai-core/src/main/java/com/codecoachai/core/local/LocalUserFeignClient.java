package com.codecoachai.core.local;

import com.codecoachai.auth.domain.dto.InnerCreateUserDTO;
import com.codecoachai.auth.domain.dto.InnerResetPasswordDTO;
import com.codecoachai.auth.domain.vo.InnerCreateUserVO;
import com.codecoachai.auth.domain.vo.InnerUserAuthVO;
import com.codecoachai.auth.domain.vo.InnerUserBasicVO;
import com.codecoachai.auth.domain.vo.InnerUserRoleVO;
import com.codecoachai.auth.feign.UserFeignClient;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.user.controller.InnerUserController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalUserFeignClient implements UserFeignClient {

    private final InnerUserController innerUserController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerUserAuthVO> getByUsername(String username) {
        return resultMapper.value(innerUserController.getByUsername(username), InnerUserAuthVO.class);
    }

    @Override
    public Result<InnerUserAuthVO> getByEmail(String email) {
        return resultMapper.value(innerUserController.getByEmail(email), InnerUserAuthVO.class);
    }

    @Override
    public Result<InnerCreateUserVO> createUser(InnerCreateUserDTO dto) {
        return resultMapper.value(
                innerUserController.createUser(
                        resultMapper.convert(dto, com.codecoachai.user.domain.dto.InnerCreateUserDTO.class)),
                InnerCreateUserVO.class);
    }

    @Override
    public Result<InnerUserRoleVO> getUserRoles(Long id) {
        return resultMapper.value(innerUserController.getRoles(id), InnerUserRoleVO.class);
    }

    @Override
    public Result<InnerUserBasicVO> getInnerUser(Long id) {
        return resultMapper.value(innerUserController.getUser(id), InnerUserBasicVO.class);
    }

    @Override
    public Result<Void> resetPassword(Long id, InnerResetPasswordDTO dto) {
        return resultMapper.empty(innerUserController.resetPassword(
                id, resultMapper.convert(dto, com.codecoachai.user.domain.dto.InnerResetPasswordDTO.class)));
    }
}
