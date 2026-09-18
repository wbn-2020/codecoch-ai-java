package com.codecoachai.core.local;

import com.codecoachai.auth.domain.dto.InnerCreateUserDTO;
import com.codecoachai.auth.domain.dto.InnerResetPasswordDTO;
import com.codecoachai.auth.domain.vo.InnerCreateUserVO;
import com.codecoachai.auth.domain.vo.InnerUserAuthVO;
import com.codecoachai.auth.domain.vo.InnerUserBasicVO;
import com.codecoachai.auth.domain.vo.InnerUserRoleVO;
import com.codecoachai.auth.feign.UserFeignClient;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalUserFeignClient implements UserFeignClient {

    private final UserService userService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerUserAuthVO> getByUsername(String username) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(username, "username");
            return resultMapper.value(
                    Result.success(userService.getInnerUserByUsername(username)),
                    InnerUserAuthVO.class);
        });
    }

    @Override
    public Result<InnerUserAuthVO> getByEmail(String email) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(email, "email");
            return resultMapper.value(
                    Result.success(userService.getInnerUserByEmail(email)),
                    InnerUserAuthVO.class);
        });
    }

    @Override
    public Result<InnerCreateUserVO> createUser(InnerCreateUserDTO dto) {
        return resultMapper.invoke(() -> resultMapper.value(
                Result.success(userService.createInnerUser(
                        resultMapper.convertValidatedBody(
                                dto,
                                com.codecoachai.user.domain.dto.InnerCreateUserDTO.class))),
                InnerCreateUserVO.class));
    }

    @Override
    public Result<InnerUserRoleVO> getUserRoles(Long id) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            return resultMapper.value(
                    Result.success(userService.getInnerUserRoles(id)),
                    InnerUserRoleVO.class);
        });
    }

    @Override
    public Result<InnerUserBasicVO> getInnerUser(Long id) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            return resultMapper.value(
                    Result.success(userService.getInnerUser(id)),
                    InnerUserBasicVO.class);
        });
    }

    @Override
    public Result<Void> resetPassword(Long id, InnerResetPasswordDTO dto) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            userService.resetInnerPassword(
                    id,
                    resultMapper.convertRequiredBody(
                            dto,
                            com.codecoachai.user.domain.dto.InnerResetPasswordDTO.class));
            return Result.success();
        });
    }
}
