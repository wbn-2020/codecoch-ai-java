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
    public static final String PLAN_SUGGESTION_NOT_ACTIONABLE = "PLAN_SUGGESTION_NOT_ACTIONABLE";
    public static final String PLAN_CHANGE_WARNING_NOT_ACKNOWLEDGED = "PLAN_CHANGE_WARNING_NOT_ACKNOWLEDGED";
    public static final String PLAN_CHANGE_FORBIDDEN = "PLAN_CHANGE_FORBIDDEN";
    public static final String PLAN_CHANGE_NOT_FOUND = "PLAN_CHANGE_NOT_FOUND";
    public static final String PLAN_CHANGE_PREVIEW_STALE = "PLAN_CHANGE_PREVIEW_STALE";
    public static final String PLAN_CHANGE_ALREADY_DECIDED = "PLAN_CHANGE_ALREADY_DECIDED";
    public static final String PLAN_CHANGE_CONFIRM_IN_PROGRESS = "PLAN_CHANGE_CONFIRM_IN_PROGRESS";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String PLAN_CHANGE_VALIDATION_FAILED = "PLAN_CHANGE_VALIDATION_FAILED";
    public static final String PLAN_CHANGE_TEMPORARILY_UNAVAILABLE = "PLAN_CHANGE_TEMPORARILY_UNAVAILABLE";

    private AgentErrorCode() {
    }
}
