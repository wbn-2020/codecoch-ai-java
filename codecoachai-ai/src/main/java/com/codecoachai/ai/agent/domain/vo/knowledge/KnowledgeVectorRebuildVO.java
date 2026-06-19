package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeVectorRebuildVO {
    private Boolean vectorEnabled;
    private Boolean embeddingEnabled;
    private Boolean semanticEnabled;
    private String embeddingDisabledReason;
    private Integer documentCount;
    private Integer chunkCount;
    private Integer vectorUpdated;
    private Integer vectorDeleted;
    private Integer duplicateChunkCount;
    private List<Long> failedDocuments;
    private Integer failedDocumentCount;
    private List<String> failedDocumentRefs;
    private List<String> errors;
    private Boolean requiresConfirmation;
    private Boolean dryRun;
    private String operation;
    private Integer requestedLimit;
    private String accessReason;
    private String idempotencyKey;
    private String confirmationMessage;
    private Long jobId;
    private Long vectorJobId;
    private String vectorJobType;
    private String vectorScopeType;
    private String vectorScopeId;
    private String vectorJobStatus;
}
