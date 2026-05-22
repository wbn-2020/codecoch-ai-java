package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.vo.growth.GrowthOverviewVO;
import com.codecoachai.ai.agent.domain.vo.growth.ReadinessScoreRecordVO;
import com.codecoachai.ai.agent.domain.vo.growth.SkillGrowthSnapshotVO;
import com.codecoachai.ai.agent.service.AgentGrowthService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/growth")
public class GrowthAliasController {

    private final AgentGrowthService agentGrowthService;

    @GetMapping("/profile/overview")
    public Result<GrowthOverviewVO> overview() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.growthOverview(userId));
    }

    @GetMapping("/skills/trend")
    public Result<List<SkillGrowthSnapshotVO>> skillTrend(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.skillTrend(userId, days));
    }

    @GetMapping("/readiness-score")
    public Result<List<ReadinessScoreRecordVO>> readinessScore(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.readinessTrend(userId, days));
    }
}
