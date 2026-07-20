package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentWeekPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.context.AgentReviewPlanWeekResult;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeItem;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeSet;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentPlanAdjustmentVO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentPlanInfluenceVO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentWeekPlanVO;
import java.time.LocalDate;
import java.util.List;

public interface AgentWeekPlanService {

    AgentWeekPlanVO current(Long userId, Long targetJobId, LocalDate date);

    AgentWeekPlanVO generate(Long userId, AgentWeekPlanGenerateDTO dto);

    AgentWeekPlanVO refresh(Long userId, Long weekPlanId);

    AgentWeekPlanVO detail(Long userId, Long weekPlanId);

    List<AgentPlanAdjustmentVO> adjustments(Long userId, Long weekPlanId);

    List<AgentPlanInfluenceVO> influences(Long userId, Long weekPlanId);

    void recordTaskAdjustment(Long userId, AgentTask before, AgentTask after, String adjustmentType, String reason);

    AgentReviewPlanWeekResult recordPendingReviewChange(Long userId,
                                                        AgentPlanChangeSet changeSet,
                                                        List<AgentPlanChangeItem> items);

    AgentReviewPlanWeekResult rebuildAfterReviewChange(Long userId,
                                                       AgentPlanChangeSet changeSet,
                                                       List<AgentPlanChangeItem> items);
}
