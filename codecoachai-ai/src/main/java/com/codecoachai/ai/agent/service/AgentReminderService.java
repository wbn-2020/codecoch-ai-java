package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.vo.AgentReminderCandidateVO;
import java.time.LocalDate;
import java.util.List;

public interface AgentReminderService {

    List<AgentReminderCandidateVO> listCandidates(Long userId, LocalDate planDate);
}
