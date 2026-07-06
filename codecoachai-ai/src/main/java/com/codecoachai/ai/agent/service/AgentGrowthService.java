package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentMemoryCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewGenerateDTO;
import com.codecoachai.ai.agent.domain.vo.growth.GrowthOverviewVO;
import com.codecoachai.ai.agent.domain.vo.growth.ReadinessScoreRecordVO;
import com.codecoachai.ai.agent.domain.vo.growth.SkillGrowthSnapshotVO;
import com.codecoachai.ai.agent.domain.vo.memory.AgentMemoryVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewVO;
import com.codecoachai.common.core.domain.PageResult;
import java.util.List;

public interface AgentGrowthService {
    AgentReviewVO generateReview(Long userId, AgentReviewGenerateDTO dto);
    List<AgentReviewVO> listReviews(Long userId, Long targetJobId);
    GrowthOverviewVO growthOverview(Long userId);
    List<SkillGrowthSnapshotVO> skillTrend(Long userId, Integer days);
    List<ReadinessScoreRecordVO> readinessTrend(Long userId, Integer days);
    PageResult<AgentMemoryVO> pageMemories(Long userId, AgentMemoryQueryDTO query);
    AgentMemoryVO createMemory(Long userId, AgentMemoryCreateDTO dto);
    AgentMemoryVO confirmMemory(Long userId, Long id);
    AgentMemoryVO setMemoryEnabled(Long userId, Long id, boolean enabled);
    void deleteMemory(Long userId, Long id);
}
