package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AgentMemoryCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewGenerateDTO;
import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.vo.growth.GrowthOverviewVO;
import com.codecoachai.ai.agent.domain.vo.growth.ReadinessScoreRecordVO;
import com.codecoachai.ai.agent.domain.vo.growth.SkillGrowthSnapshotVO;
import com.codecoachai.ai.agent.domain.vo.memory.AgentMemoryVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewVO;
import com.codecoachai.ai.agent.service.AgentGrowthService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentGrowthController {

    private final AgentGrowthService agentGrowthService;

    private final V4FeatureGate v4FeatureGate;

    @ModelAttribute
    public void requireGrowthFeatureEnabled() {
        SecurityAssert.requireLoginUserId();
        v4FeatureGate.requireGrowthEnabled();
    }

    @PostMapping("/job-coach/review")
    public Result<AgentReviewVO> generateReview(@RequestBody(required = false) AgentReviewGenerateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.generateReview(userId, dto));
    }

    @GetMapping("/reviews")
    public Result<List<AgentReviewVO>> reviews(@RequestParam(required = false) Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.listReviews(userId, targetJobId));
    }

    @GetMapping("/memories")
    public Result<PageResult<AgentMemoryVO>> memories(@ModelAttribute AgentMemoryQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.pageMemories(userId, query));
    }

    @PostMapping("/memories")
    public Result<AgentMemoryVO> createMemory(@RequestBody AgentMemoryCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.createMemory(userId, dto));
    }

    @PostMapping("/memories/{id}/enable")
    public Result<AgentMemoryVO> enableMemory(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.setMemoryEnabled(userId, id, true));
    }

    @PostMapping("/memories/{id}/confirm")
    public Result<AgentMemoryVO> confirmMemory(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.confirmMemory(userId, id));
    }

    @PostMapping("/memories/{id}/disable")
    public Result<AgentMemoryVO> disableMemory(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.setMemoryEnabled(userId, id, false));
    }

    @DeleteMapping("/memories/{id}")
    public Result<Void> deleteMemory(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        agentGrowthService.deleteMemory(userId, id);
        return Result.success();
    }

    @GetMapping("/growth/profile/overview")
    public Result<GrowthOverviewVO> growthOverview() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.growthOverview(userId));
    }

    @GetMapping("/growth/skills/trend")
    public Result<List<SkillGrowthSnapshotVO>> skillTrend(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.skillTrend(userId, days));
    }

    @GetMapping("/growth/readiness/trend")
    public Result<List<ReadinessScoreRecordVO>> readinessTrend(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentGrowthService.readinessTrend(userId, days));
    }
}
