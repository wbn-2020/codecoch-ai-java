package com.codecoachai.auth.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class CurrentUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private List<String> roles;
    private List<String> permissions;
}
