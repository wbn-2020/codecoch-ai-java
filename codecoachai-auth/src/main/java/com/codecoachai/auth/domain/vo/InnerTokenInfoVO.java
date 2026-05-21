package com.codecoachai.auth.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerTokenInfoVO {

    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
    private List<String> permissions;
}
