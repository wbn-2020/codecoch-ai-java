package com.codecoachai.system.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class RoleMenuAssignDTO {

    private List<Long> menuIds;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
