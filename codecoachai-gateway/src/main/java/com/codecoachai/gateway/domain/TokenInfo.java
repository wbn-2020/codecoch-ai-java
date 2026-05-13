package com.codecoachai.gateway.domain;

import java.util.List;
import lombok.Data;

@Data
public class TokenInfo {

    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
}
