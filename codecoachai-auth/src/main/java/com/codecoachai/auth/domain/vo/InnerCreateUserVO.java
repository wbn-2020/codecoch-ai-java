package com.codecoachai.auth.domain.vo;

import lombok.Data;

@Data
public class InnerCreateUserVO {

    private Long userId;
    private String username;
    private String nickname;
}
