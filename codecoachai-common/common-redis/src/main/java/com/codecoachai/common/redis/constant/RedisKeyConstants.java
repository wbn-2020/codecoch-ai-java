package com.codecoachai.common.redis.constant;

public final class RedisKeyConstants {

    private static final String NS = "codecoachai:";

    // ========== 登录 ==========
    public static final String LOGIN_TOKEN_PREFIX = NS + "login:token:";
    public static final String USER_ROLES_PREFIX = NS + "user:roles:";

    // ========== 缓存 ==========
    public static final String QUESTION_DETAIL_PREFIX = NS + "q:detail:";
    public static final String QUESTION_CATEGORY_TREE = NS + "q:category:tree";
    public static final String QUESTION_TAG_LIST = NS + "q:tag:list";
    public static final String QUESTION_HOT_LIST_PREFIX = NS + "q:hot:list:";
    public static final String PROMPT_TPL_PREFIX = NS + "prompt:tpl:";

    // ========== 面试 ==========
    public static final String INTERVIEW_CTX_PREFIX = NS + "interview:ctx:";

    // ========== AI 配额 ==========
    public static final String AI_QUOTA_DAY_PREFIX = NS + "ai:quota:day:";
    public static final String AI_QUOTA_MINUTE_PREFIX = NS + "ai:quota:min:";
    public static final String AI_TOKENS_IN_PREFIX = NS + "ai:tokens:in:";
    public static final String AI_TOKENS_OUT_PREFIX = NS + "ai:tokens:out:";

    // ========== 分布式锁 ==========
    public static final String LOCK_REPORT_PREFIX = NS + "lock:report:";
    public static final String LOCK_RESUME_PARSE_PREFIX = NS + "lock:resume:parse:";
    public static final String LOCK_RESUME_OPTIMIZE_PREFIX = NS + "lock:resume:optimize:";
    public static final String LOCK_PLAN_GEN_PREFIX = NS + "lock:plan:gen:";
    public static final String LOCK_QUESTION_GEN_PREFIX = NS + "lock:question:gen:";

    // ========== STS 凭证 ==========
    public static final String STS_TOKEN_PREFIX = NS + "sts:token:";

    // ========== MQ 消费幂等 ==========
    public static final String MQ_CONSUMED_PREFIX = NS + "mq:consumed:";

    private RedisKeyConstants() {
    }

    public static String questionDetailKey(Object id) {
        return QUESTION_DETAIL_PREFIX + id;
    }

    public static String questionHotListKey(Object categoryId) {
        return QUESTION_HOT_LIST_PREFIX + categoryId;
    }

    public static String promptTplKey(String code) {
        return PROMPT_TPL_PREFIX + code;
    }

    public static String interviewCtxKey(Object sessionId) {
        return INTERVIEW_CTX_PREFIX + sessionId;
    }

    public static String aiQuotaDayKey(Object userId, String date) {
        return AI_QUOTA_DAY_PREFIX + userId + ":" + date;
    }

    public static String aiQuotaMinuteKey(Object userId) {
        return AI_QUOTA_MINUTE_PREFIX + userId;
    }

    public static String aiTokensInKey(Object userId, String date) {
        return AI_TOKENS_IN_PREFIX + userId + ":" + date;
    }

    public static String aiTokensOutKey(Object userId, String date) {
        return AI_TOKENS_OUT_PREFIX + userId + ":" + date;
    }

    public static String lockReportKey(Object sessionId) {
        return LOCK_REPORT_PREFIX + sessionId;
    }

    public static String lockResumeParseKey(Object resumeId) {
        return LOCK_RESUME_PARSE_PREFIX + resumeId;
    }

    public static String mqConsumedKey(String messageId) {
        return MQ_CONSUMED_PREFIX + messageId;
    }
}
