package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AdminAgentRunQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AdminAgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentRunFailureDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskSkipDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentRunUserDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
import com.codecoachai.common.core.domain.PageResult;
import java.time.LocalDate;
import java.util.List;

public interface JobCoachAgentService {

    DailyPlanVO generateDailyPlan(Long userId, DailyPlanGenerateDTO dto);

    DailyPlanVO executeDailyPlan(Long userId, Long runId, DailyPlanGenerateDTO dto);

    DailyPlanVO failDailyPlanRun(Long userId, Long runId, AgentRunFailureDTO dto);

    DailyPlanVO latestDailyPlan(Long userId, Long targetJobId, LocalDate date);

    List<AgentTaskVO> todayTasks(Long userId, Long targetJobId, LocalDate date, String status);

    PageResult<AgentTaskVO> pageTasks(Long userId, AgentTaskQueryDTO query);

    AgentTaskVO completeTask(Long userId, Long taskId, AgentTaskCompleteDTO dto);

    AgentTaskVO completeBusinessAction(AgentBusinessActionCompleteDTO dto);

    AgentTaskVO startTask(Long userId, Long taskId);

    AgentTaskVO skipTask(Long userId, Long taskId, AgentTaskSkipDTO dto);

    AgentTaskVO restoreTask(Long userId, Long taskId);

    AgentRunUserDetailVO getRunDetail(Long userId, Long runId);

    AgentRunDetailVO adminGetRunDetail(Long runId);

    PageResult<AgentRunDetailVO> adminPageRuns(AdminAgentRunQueryDTO query);

    PageResult<AgentTaskVO> adminPageTasks(AdminAgentTaskQueryDTO query);
}
