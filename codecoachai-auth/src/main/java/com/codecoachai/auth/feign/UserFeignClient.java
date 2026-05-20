package com.codecoachai.auth.feign;

import com.codecoachai.auth.domain.dto.InnerCreateUserDTO;
import com.codecoachai.auth.domain.dto.InnerResetPasswordDTO;
import com.codecoachai.auth.domain.vo.InnerCreateUserVO;
import com.codecoachai.auth.domain.vo.InnerUserAuthVO;
import com.codecoachai.auth.domain.vo.InnerUserBasicVO;
import com.codecoachai.auth.domain.vo.InnerUserRoleVO;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.feign.config.OpenFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-user", contextId = "userFeignClient", configuration = OpenFeignConfig.class)
public interface UserFeignClient {

    @GetMapping("/inner/users/by-username")
    Result<InnerUserAuthVO> getByUsername(@RequestParam("username") String username);

    @GetMapping("/inner/users/by-email")
    Result<InnerUserAuthVO> getByEmail(@RequestParam("email") String email);

    @PostMapping("/inner/users")
    Result<InnerCreateUserVO> createUser(@RequestBody InnerCreateUserDTO dto);

    @GetMapping("/inner/users/{id}/roles")
    Result<InnerUserRoleVO> getUserRoles(@PathVariable("id") Long id);

    @GetMapping("/inner/users/{id}")
    Result<InnerUserBasicVO> getInnerUser(@PathVariable("id") Long id);

    @PostMapping("/inner/users/{id}/reset-password")
    Result<Void> resetPassword(@PathVariable("id") Long id, @RequestBody InnerResetPasswordDTO dto);
}
