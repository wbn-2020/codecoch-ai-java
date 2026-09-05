package com.codecoachai.interview.feign.dto;

import lombok.Data;

/**
 * Internal knowledge retrieval request sent to the AI service while building an interview's
 * training context. Carries the owning {@code userId} explicitly (service-to-service call, no
 * login session); results are always scoped to that user.
 */
@Data
public class InnerKnowledgeSearchDTO {

    private Long userId;
    private String keyword;
    private Integer limit;
    private Double minScore;
    private Long documentId;
    private String documentType;
}
