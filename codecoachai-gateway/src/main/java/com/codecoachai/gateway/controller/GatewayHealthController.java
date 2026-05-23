package com.codecoachai.gateway.controller;

import com.codecoachai.common.core.domain.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayHealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("codecoachai-gateway");
    }
}
