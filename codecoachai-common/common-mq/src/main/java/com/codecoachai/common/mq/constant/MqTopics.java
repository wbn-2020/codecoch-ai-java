package com.codecoachai.common.mq.constant;

/**
 * RocketMQ Topic 与 Tag 统一常量。
 * Topic 命名：codecoachai-{业务域}
 * Tag 命名：小写 + 短横线
 */
public final class MqTopics {

    private MqTopics() {}

    // ========== 简历相关 ==========
    public static final String RESUME = "codecoachai-resume";
    public static final String RESUME_TAG_PARSE = "parse";
    public static final String RESUME_TAG_OPTIMIZE = "optimize";

    // ========== 面试相关 ==========
    public static final String INTERVIEW = "codecoachai-interview";
    public static final String INTERVIEW_TAG_REPORT = "report";
    public static final String INTERVIEW_TAG_FINISH_EVENT = "finish-event";

    // ========== 题库相关 ==========
    public static final String QUESTION = "codecoachai-question";
    public static final String QUESTION_TAG_AI_GENERATE = "ai-generate";
    public static final String QUESTION_TAG_DUPLICATE_CHECK = "duplicate-check";

    // ========== 搜索索引同步 ==========
    public static final String SEARCH_SYNC = "codecoachai-search";
    public static final String SEARCH_TAG_QUESTION = "question";
    public static final String SEARCH_TAG_RESUME = "resume";
    public static final String SEARCH_TAG_INTERVIEW = "interview";

    // ========== 通知 ==========
    public static final String NOTIFY = "codecoachai-notify";
    public static final String NOTIFY_TAG_PUSH = "push";

    // ========== 求职匹配 ==========
    public static final String JOB_MATCH = "codecoachai-job-match";
    public static final String JOB_MATCH_TAG_ANALYZE = "analyze";

    // ========== 学习计划 ==========
    public static final String STUDY_PLAN = "codecoachai-study-plan";
    public static final String STUDY_PLAN_TAG_GENERATE = "generate";

    /**
     * 拼接 destination：{topic}:{tag}
     */
    public static String dest(String topic, String tag) {
        return topic + ":" + tag;
    }
}
