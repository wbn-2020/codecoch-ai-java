package com.codecoachai.auth.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerUserBasicVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private Integer status;
    private Integer mustChangePassword;
    private List<String> roles;
}
