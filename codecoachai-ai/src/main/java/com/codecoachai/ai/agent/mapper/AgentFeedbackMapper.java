package com.codecoachai.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.ai.agent.domain.entity.AgentFeedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentFeedbackMapper extends BaseMapper<AgentFeedback> {
}
