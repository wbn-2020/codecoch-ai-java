package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 按 JD 文本做规则版冷启动推荐的请求体。
 *
 * <p>用于新用户零数据场景：不依赖能力画像、匹配报告或学习计划，
 * 直接用 JD 文本命中关键词后从正式题库取题。</p>
 */
@Data
public class QuestionRecommendationJdDTO {

    /** 目标岗位 ID（可选，仅用于推荐来源透传） */
    private Long targetJobId;

    /** JD 原文或岗位关键词文本；为空时退化为按权重取高频题 */
    @Size(max = 20000, message = "岗位描述过长")
    private String jdText;

    /** 期望题量 */
    private Integer limit;
}
