package com.codecoachai.auth.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerUserAuthVO {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String email;
    private Integer status;
    private Integer mustChangePassword;
    private List<String> roles;
}
