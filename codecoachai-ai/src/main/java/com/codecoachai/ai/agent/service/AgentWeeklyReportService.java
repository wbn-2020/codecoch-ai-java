package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportQueryDTO;
import com.codecoachai.ai.agent.domain.dto.weekly.AgentWeeklyReportRefreshDTO;
import com.codecoachai.ai.agent.domain.vo.weekly.AgentWeeklyReportVO;
import java.util.List;

public interface AgentWeeklyReportService {

    AgentWeeklyReportVO current(Long userId, AgentWeeklyReportQueryDTO query);

    AgentWeeklyReportVO generate(Long userId, AgentWeeklyReportGenerateDTO dto);

    AgentWeeklyReportVO detail(Long userId, Long reportId);

    List<AgentWeeklyReportVO> list(Long userId, AgentWeeklyReportQueryDTO query);

    AgentWeeklyReportVO refresh(Long userId, Long reportId, AgentWeeklyReportRefreshDTO dto);
}
