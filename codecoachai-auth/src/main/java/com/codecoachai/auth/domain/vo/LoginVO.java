package com.codecoachai.auth.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class LoginVO {

    private String token;
    private String tokenName;
    private String expireTime;
    private CurrentUserVO userInfo;
    private List<String> roles;
}
