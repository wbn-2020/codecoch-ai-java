package com.codecoachai.common.mq.payload;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量 AI 出题任务负载。
 * Topic: codecoachai-question  Tag: ai-generate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGeneratePayload {

    private String batchId;
    private Long userId;
    private String topic;
    private String difficulty;
    private Integer count;
    private List<String> tags;
    private String targetPosition;
    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private Integer experienceYears;
    private String experienceLevel;
    private Boolean generateReferenceAnswer;
    private Boolean generateFollowUps;
    private Boolean generateTagSuggestions;
    private Boolean generateCategorySuggestion;
    private String extraRequirements;
}
