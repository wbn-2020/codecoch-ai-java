package com.codecoachai.auth.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerUserRoleVO {

    private Long userId;
    private List<String> roles;
}
