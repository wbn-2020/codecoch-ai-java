package com.codecoachai.ai.agent.service.support;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;

public class AgentPlanChangeConflictException extends BusinessException {

    public AgentPlanChangeConflictException(String message) {
        super(ErrorCode.STALE_SOURCE_VERSION, message);
    }
}
