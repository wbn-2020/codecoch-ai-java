package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.InnerAbilityProfileSummaryVO;
import com.codecoachai.resume.service.AbilityMapService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ability-map")
public class InnerAbilityMapController {

    private final AbilityMapService abilityMapService;

    @GetMapping("/users/{userId}/profile-summary")
    public Result<List<InnerAbilityProfileSummaryVO>> profileSummary(@PathVariable Long userId,
                                                                     @RequestParam(required = false) List<String> skillCodes) {
        return Result.success(abilityMapService.listProfileSummary(userId, skillCodes));
    }
}
