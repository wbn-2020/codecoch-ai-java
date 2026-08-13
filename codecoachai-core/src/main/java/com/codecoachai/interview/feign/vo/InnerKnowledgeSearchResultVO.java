package com.codecoachai.interview.feign.vo;

import lombok.Data;

/**
 * Interview-side view of a personal-knowledge search hit returned by the AI service.
 *
 * <p>Deliberately a lean, summary-level subset of the AI module's {@code KnowledgeSearchResultVO}:
 * only the fields needed to enrich an interview's training context (title, snippet, type, score,
 * source reference). Vector/hash/index internals are intentionally omitted so the interview flow
 * only ever handles desensitized summaries, never raw knowledge internals.
 */
@Data
public class InnerKnowledgeSearchResultVO {

    private Long documentId;
    private Long chunkId;
    private String title;
    private String documentType;
    private String snippet;
    private String sourceRef;
    private Double score;
    private String matchType;
}
