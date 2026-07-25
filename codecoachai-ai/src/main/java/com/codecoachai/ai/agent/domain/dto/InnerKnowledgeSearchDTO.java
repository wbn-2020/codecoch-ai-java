package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

/**
 * Internal (service-to-service) knowledge retrieval request.
 *
 * <p>Called from the interview module while building an interview's training context, so it carries
 * the owning {@code userId} explicitly rather than relying on a login session. Knowledge results are
 * scoped to this user; a caller can never read another user's personal knowledge base.
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
