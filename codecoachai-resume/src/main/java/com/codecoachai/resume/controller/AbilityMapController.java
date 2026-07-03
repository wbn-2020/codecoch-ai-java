package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.AbilityMapVO;
import com.codecoachai.resume.service.AbilityMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ability-map")
public class AbilityMapController {

    private final AbilityMapService abilityMapService;

    @GetMapping
    public Result<AbilityMapVO> currentUserAbilityMap() {
        return Result.success(abilityMapService.getCurrentUserAbilityMap());
    }
}
