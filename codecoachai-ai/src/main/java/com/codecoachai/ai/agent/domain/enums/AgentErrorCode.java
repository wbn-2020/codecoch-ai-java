package com.codecoachai.ai.agent.domain.enums;

public final class AgentErrorCode {

    public static final String TARGET_JOB_REQUIRED = "AGENT_TARGET_JOB_REQUIRED";
    public static final String AI_CALL_FAILED = "AGENT_AI_CALL_FAILED";
    public static final String OUTPUT_PARSE_FAILED = "AGENT_OUTPUT_PARSE_FAILED";
    public static final String OUTPUT_VALIDATE_FAILED = "AGENT_OUTPUT_VALIDATE_FAILED";
    public static final String ASYNC_TASK_FAILED = "AGENT_ASYNC_TASK_FAILED";
    public static final String RUN_NOT_FOUND = "AGENT_RUN_NOT_FOUND";
    public static final String RUN_TIMEOUT = "AGENT_RUN_TIMEOUT";
    public static final String TASK_NOT_FOUND = "AGENT_TASK_NOT_FOUND";
    public static final String TASK_STATUS_INVALID = "AGENT_TASK_STATUS_INVALID";

    private AgentErrorCode() {
    }
}
