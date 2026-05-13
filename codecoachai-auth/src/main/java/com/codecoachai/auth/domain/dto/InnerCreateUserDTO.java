package com.codecoachai.auth.domain.dto;

import lombok.Data;

@Data
public class InnerCreateUserDTO {

    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
}
