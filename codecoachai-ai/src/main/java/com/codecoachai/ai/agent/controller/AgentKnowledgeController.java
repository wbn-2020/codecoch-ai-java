package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeDocumentCreateDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalCaseQueryDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalCaseSaveDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvalRunRequestDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvaluationDTO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeAskVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeChunkVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeConfigVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentOptionVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVersionVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateCleanupVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateReviewVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvalCaseVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvalRunVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvaluationVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeExactDuplicateGroupVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchTraceVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeStatsVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.ai.agent.service.KnowledgeEvaluationService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import com.codecoachai.common.web.log.OperationLog;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/knowledge")
public class AgentKnowledgeController {

    private static final String VECTOR_JOB_KNOWLEDGE_REBUILD = "KNOWLEDGE_REBUILD";
    private static final String VECTOR_JOB_KNOWLEDGE_RETRY = "KNOWLEDGE_RETRY";
    private static final String VECTOR_SCOPE_KNOWLEDGE = "KNOWLEDGE";
    private static final String VECTOR_SCOPE_FAILED_OR_STALE = "FAILED_OR_STALE";
    private static final String KNOWLEDGE_OP_DUPLICATE_CLEANUP = "KNOWLEDGE_DUPLICATE_CLEANUP";
    private static final String KNOWLEDGE_OP_DELETE_CHUNK = "KNOWLEDGE_DELETE_CHUNK";
    private static final String KNOWLEDGE_OP_DELETE_DOCUMENT = "KNOWLEDGE_DELETE_DOCUMENT";
    private static final String KNOWLEDGE_OP_RESTORE_DOCUMENT_VERSION = "KNOWLEDGE_RESTORE_DOCUMENT_VERSION";
    private static final String KNOWLEDGE_OP_DELETE_EVAL_CASE = "KNOWLEDGE_DELETE_EVAL_CASE";

    private final AgentV4OpsService agentV4OpsService;

    private final KnowledgeEvaluationService knowledgeEvaluationService;

    private final VectorIndexJobService vectorIndexJobService;

    private final V4FeatureGate v4FeatureGate;

    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @ModelAttribute
    public void requireKnowledgeFeatureEnabled() {
        SecurityAssert.requireLoginUserId();
        v4FeatureGate.requireKnowledgeEnabled();
    }

    @PostMapping("/documents")
    public Result<KnowledgeDocumentVO> createDocument(@RequestBody KnowledgeDocumentCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.createKnowledgeDocument(userId, dto));
    }

    @PutMapping("/documents/{id}")
    public Result<KnowledgeDocumentVO> updateDocument(@PathVariable Long id,
                                                      @RequestBody KnowledgeDocumentCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.updateKnowledgeDocument(userId, id, dto));
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentVO> uploadDocument(@RequestPart("file") MultipartFile file,
                                                      @RequestParam(required = false) String documentType) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.uploadKnowledgeDocument(userId, file, documentType));
    }

    @GetMapping("/documents")
    public Result<PageResult<KnowledgeDocumentVO>> listDocuments(@RequestParam(required = false) String title,
                                                                 @RequestParam(required = false) String documentType,
                                                                 @RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) Long pageNo,
                                                                 @RequestParam(required = false) Long pageSize) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.pageKnowledgeDocuments(userId, title, documentType, status, pageNo, pageSize));
    }

    @GetMapping("/documents/types")
    public Result<List<String>> documentTypes() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listKnowledgeDocumentTypes(userId));
    }

    @GetMapping("/documents/options")
    public Result<List<KnowledgeDocumentOptionVO>> documentOptions() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listKnowledgeDocumentOptions(userId));
    }

    @GetMapping("/stats")
    public Result<KnowledgeStatsVO> stats() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.getKnowledgeStats(userId));
    }

    @GetMapping("/config")
    public Result<KnowledgeConfigVO> config() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.getKnowledgeConfig(userId));
    }

    @GetMapping("/documents/{id}")
    public Result<KnowledgeDocumentVO> document(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.getKnowledgeDocument(userId, id));
    }

    @GetMapping("/documents/{id}/versions")
    public Result<List<KnowledgeDocumentVersionVO>> documentVersions(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listKnowledgeDocumentVersions(userId, id));
    }

    @OperationLog(module = "knowledge", action = "RESTORE_KNOWLEDGE_DOCUMENT_VERSION", description = "Restore personal knowledge document version", logArgs = false, logResponse = false)
    @PostMapping("/documents/{id}/versions/{versionId}/restore")
    public Result<KnowledgeDocumentVO> restoreDocumentVersion(@PathVariable Long id,
                                                              @PathVariable Long versionId,
                                                              @RequestParam(required = false) Boolean confirm,
                                                              @RequestParam(required = false) Boolean dryRun,
                                                              @RequestParam(required = false) String reason,
                                                              @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String lockKey = operationConfirmationGuard.requireConfirmed(
                KNOWLEDGE_OP_RESTORE_DOCUMENT_VERSION + ":" + userId + ":" + id + ":" + versionId,
                confirm,
                dryRun,
                reason,
                idempotencyKey);
        try {
            return Result.success(agentV4OpsService.restoreKnowledgeDocumentVersion(userId, id, versionId));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @GetMapping("/documents/{id}/chunks")
    public Result<List<KnowledgeChunkVO>> chunks(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listKnowledgeChunks(userId, id));
    }

    @GetMapping("/chunks/{chunkId}")
    public Result<KnowledgeChunkVO> chunk(@PathVariable Long chunkId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.getKnowledgeChunk(userId, chunkId));
    }

    @GetMapping("/chunks/{chunkId}/similar")
    public Result<List<KnowledgeSearchResultVO>> similarChunks(@PathVariable Long chunkId,
                                                               @RequestParam(required = false) Integer limit) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listSimilarKnowledgeChunks(userId, chunkId, limit));
    }

    @GetMapping("/duplicates/review")
    public Result<KnowledgeDuplicateReviewVO> duplicateReview(@RequestParam(required = false) Integer limit,
                                                             @RequestParam(required = false) Double threshold) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.reviewDuplicateKnowledgeChunks(userId, limit, threshold));
    }

    @GetMapping("/duplicates/exact")
    public Result<List<KnowledgeExactDuplicateGroupVO>> exactDuplicates(@RequestParam(required = false) Integer limit,
                                                                       @RequestParam(required = false) Long documentId,
                                                                       @RequestParam(required = false) String documentType) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listExactDuplicateKnowledgeChunks(userId, limit, documentId, documentType));
    }

    @OperationLog(module = "knowledge", action = "CLEANUP_EXACT_DUPLICATE_CHUNKS", description = "Cleanup exact duplicate knowledge chunks", logArgs = false, logResponse = false)
    @PostMapping("/duplicates/exact/cleanup")
    public Result<KnowledgeDuplicateCleanupVO> cleanupExactDuplicates(
            @RequestParam(defaultValue = "true") Boolean dryRun,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) Boolean confirm,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String lockKey = null;
        if (Boolean.FALSE.equals(dryRun)) {
            lockKey = operationConfirmationGuard.requireConfirmed(
                    KNOWLEDGE_OP_DUPLICATE_CLEANUP + ":" + userId,
                    confirm,
                    dryRun,
                    reason,
                    idempotencyKey);
        }
        try {
            return Result.success(agentV4OpsService.cleanupExactDuplicateKnowledgeChunks(userId, dryRun, limit, documentId, documentType));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @OperationLog(module = "knowledge", action = "DELETE_KNOWLEDGE_CHUNK", description = "Delete personal knowledge chunk", logArgs = false, logResponse = false)
    @DeleteMapping("/chunks/{chunkId}")
    public Result<Void> deleteChunk(@PathVariable Long chunkId,
                                    @RequestParam(required = false) Boolean confirm,
                                    @RequestParam(required = false) Boolean dryRun,
                                    @RequestParam(required = false) String reason,
                                    @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String lockKey = operationConfirmationGuard.requireConfirmed(
                KNOWLEDGE_OP_DELETE_CHUNK + ":" + userId + ":" + chunkId,
                confirm,
                dryRun,
                reason,
                idempotencyKey);
        try {
            agentV4OpsService.deleteKnowledgeChunk(userId, chunkId);
            return Result.success();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @OperationLog(module = "knowledge", action = "DELETE_KNOWLEDGE_DOCUMENT", description = "Delete personal knowledge document", logArgs = false, logResponse = false)
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id,
                                       @RequestParam(required = false) Boolean confirm,
                                       @RequestParam(required = false) Boolean dryRun,
                                       @RequestParam(required = false) String reason,
                                       @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String lockKey = operationConfirmationGuard.requireConfirmed(
                KNOWLEDGE_OP_DELETE_DOCUMENT + ":" + userId + ":" + id,
                confirm,
                dryRun,
                reason,
                idempotencyKey);
        try {
            agentV4OpsService.deleteKnowledgeDocument(userId, id);
            return Result.success();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @GetMapping("/search")
    public Result<List<KnowledgeSearchResultVO>> search(@RequestParam String keyword,
                                                        @RequestParam(required = false) Integer limit,
                                                        @RequestParam(required = false) Double minScore,
                                                        @RequestParam(required = false) Long documentId,
                                                        @RequestParam(required = false) String documentType) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.searchKnowledge(userId, keyword, limit, minScore, documentId, documentType));
    }

    @GetMapping("/search/trace")
    public Result<KnowledgeSearchTraceVO> searchTrace(@RequestParam String keyword,
                                                      @RequestParam(required = false) Integer limit,
                                                      @RequestParam(required = false) Double minScore,
                                                      @RequestParam(required = false) Long documentId,
                                                      @RequestParam(required = false) String documentType) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.traceKnowledgeSearch(userId, keyword, limit, minScore, documentId, documentType));
    }

    @PostMapping("/ask")
    public Result<KnowledgeAskVO> ask(@RequestBody KnowledgeAskDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.askKnowledge(userId, dto));
    }

    @PostMapping("/evaluate")
    public Result<KnowledgeEvaluationVO> evaluate(@RequestBody KnowledgeEvaluationDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.evaluateKnowledge(userId, dto));
    }


    @GetMapping("/eval/cases")
    public Result<PageResult<KnowledgeEvalCaseVO>> pageEvalCases(KnowledgeEvalCaseQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(knowledgeEvaluationService.pageCases(userId, query));
    }

    @PostMapping("/eval/cases")
    public Result<KnowledgeEvalCaseVO> saveEvalCase(@RequestBody KnowledgeEvalCaseSaveDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(knowledgeEvaluationService.saveCase(userId, dto));
    }

    @OperationLog(module = "knowledge", action = "DELETE_KNOWLEDGE_EVAL_CASE", description = "Delete personal knowledge evaluation case", logArgs = false, logResponse = false)
    @DeleteMapping("/eval/cases/{id}")
    public Result<Void> deleteEvalCase(@PathVariable Long id,
                                       @RequestParam(required = false) Boolean confirm,
                                       @RequestParam(required = false) Boolean dryRun,
                                       @RequestParam(required = false) String reason,
                                       @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String lockKey = operationConfirmationGuard.requireConfirmed(
                KNOWLEDGE_OP_DELETE_EVAL_CASE + ":" + userId + ":" + id,
                confirm,
                dryRun,
                reason,
                idempotencyKey);
        try {
            knowledgeEvaluationService.deleteCase(userId, id);
            return Result.success();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @PostMapping("/eval/runs")
    public Result<KnowledgeEvalRunVO> runEval(@RequestBody(required = false) KnowledgeEvalRunRequestDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(knowledgeEvaluationService.run(userId, dto));
    }

    @GetMapping("/eval/runs")
    public Result<PageResult<KnowledgeEvalRunVO>> pageEvalRuns(@RequestParam(required = false) Long pageNo,
                                                               @RequestParam(required = false) Long pageSize) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(knowledgeEvaluationService.pageRuns(userId, pageNo, pageSize));
    }

    @GetMapping("/eval/runs/{id}")
    public Result<KnowledgeEvalRunVO> getEvalRun(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(knowledgeEvaluationService.getRun(userId, id));
    }

    @PostMapping("/vectors/rebuild")
    public Result<KnowledgeVectorRebuildVO> rebuildVectors(@RequestParam(required = false) Long documentId,
                                                           @RequestParam(required = false) Boolean confirm,
                                                           @RequestParam(required = false) String reason,
                                                           @RequestParam(required = false) Boolean dryRun,
                                                           @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String cleanReason = cleanReason(reason);
        String cleanIdempotencyKey = cleanIdempotencyKey(idempotencyKey);
        String scopeId = documentId == null ? null : String.valueOf(documentId);
        if (requiresMaintenancePreview(confirm, cleanReason, dryRun, cleanIdempotencyKey)) {
            return Result.success(knowledgeVectorPreview(VECTOR_JOB_KNOWLEDGE_REBUILD, null, scopeId, cleanReason, cleanIdempotencyKey));
        }
        String lockKey = acquireMaintenanceIdempotencyKey(userId, VECTOR_JOB_KNOWLEDGE_REBUILD,
                cleanReason, cleanIdempotencyKey);
        Long jobId = vectorIndexJobService.start(VECTOR_JOB_KNOWLEDGE_REBUILD, VECTOR_SCOPE_KNOWLEDGE, scopeId, null);
        try {
            KnowledgeVectorRebuildVO result = agentV4OpsService.rebuildKnowledgeVectors(userId, documentId);
            String status = finishKnowledgeVectorJob(jobId, result);
            attachKnowledgeVectorJob(result, jobId, VECTOR_JOB_KNOWLEDGE_REBUILD, VECTOR_SCOPE_KNOWLEDGE, scopeId, status);
            attachKnowledgeMaintenanceConfirmation(result, VECTOR_JOB_KNOWLEDGE_REBUILD, null, cleanReason, cleanIdempotencyKey);
            return Result.success(result);
        } catch (Exception ex) {
            releaseMaintenanceIdempotencyKey(lockKey);
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    @PostMapping("/vectors/retry-failed")
    public Result<KnowledgeVectorRebuildVO> retryFailedVectors(@RequestParam(required = false) Integer limit,
                                                               @RequestParam(required = false) Boolean confirm,
                                                               @RequestParam(required = false) String reason,
                                                               @RequestParam(required = false) Boolean dryRun,
                                                               @RequestParam(required = false) String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        String cleanReason = cleanReason(reason);
        String cleanIdempotencyKey = cleanIdempotencyKey(idempotencyKey);
        if (requiresMaintenancePreview(confirm, cleanReason, dryRun, cleanIdempotencyKey)) {
            return Result.success(knowledgeVectorPreview(VECTOR_JOB_KNOWLEDGE_RETRY, limit, VECTOR_SCOPE_FAILED_OR_STALE, cleanReason, cleanIdempotencyKey));
        }
        String lockKey = acquireMaintenanceIdempotencyKey(userId, VECTOR_JOB_KNOWLEDGE_RETRY,
                cleanReason, cleanIdempotencyKey);
        Long jobId = vectorIndexJobService.start(VECTOR_JOB_KNOWLEDGE_RETRY, VECTOR_SCOPE_KNOWLEDGE,
                VECTOR_SCOPE_FAILED_OR_STALE, limit);
        try {
            KnowledgeVectorRebuildVO result = agentV4OpsService.retryFailedKnowledgeVectors(userId, limit);
            String status = finishKnowledgeVectorJob(jobId, result);
            attachKnowledgeVectorJob(result, jobId, VECTOR_JOB_KNOWLEDGE_RETRY, VECTOR_SCOPE_KNOWLEDGE,
                    VECTOR_SCOPE_FAILED_OR_STALE, status);
            attachKnowledgeMaintenanceConfirmation(result, VECTOR_JOB_KNOWLEDGE_RETRY, limit, cleanReason, cleanIdempotencyKey);
            return Result.success(result);
        } catch (Exception ex) {
            releaseMaintenanceIdempotencyKey(lockKey);
            vectorIndexJobService.fail(jobId, ex);
            throw ex;
        }
    }

    private boolean requiresMaintenancePreview(Boolean confirm, String reason, Boolean dryRun, String idempotencyKey) {
        return Boolean.TRUE.equals(dryRun)
                || !Boolean.TRUE.equals(confirm)
                || !StringUtils.hasText(reason)
                || !StringUtils.hasText(idempotencyKey);
    }

    private KnowledgeVectorRebuildVO knowledgeVectorPreview(String operation, Integer limit, String scopeId,
                                                           String reason, String idempotencyKey) {
        KnowledgeVectorRebuildVO result = new KnowledgeVectorRebuildVO();
        result.setRequiresConfirmation(true);
        result.setDryRun(true);
        result.setOperation(operation);
        result.setRequestedLimit(limit);
        result.setAccessReason(reason);
        result.setIdempotencyKey(idempotencyKey);
        result.setVectorScopeType(VECTOR_SCOPE_KNOWLEDGE);
        result.setVectorScopeId(scopeId);
        result.setVectorJobStatus("PREVIEW");
        result.setConfirmationMessage("知识库索引维护需要 confirm=true、reason 和 idempotencyKey，当前请求未执行。");
        return result;
    }

    private void attachKnowledgeMaintenanceConfirmation(KnowledgeVectorRebuildVO result, String operation,
                                                        Integer limit, String reason, String idempotencyKey) {
        if (result == null) {
            return;
        }
        result.setRequiresConfirmation(false);
        result.setDryRun(false);
        result.setOperation(operation);
        result.setRequestedLimit(limit);
        result.setAccessReason(reason);
        result.setIdempotencyKey(idempotencyKey);
    }

    private String acquireMaintenanceIdempotencyKey(Long userId, String operation, String reason, String idempotencyKey) {
        return operationConfirmationGuard.requireConfirmed("knowledge-vector-maintenance:" + operation + ":" + userId,
                true, false, reason, idempotencyKey);
    }

    private void releaseMaintenanceIdempotencyKey(String lockKey) {
        operationConfirmationGuard.release(lockKey);
    }

    private String cleanReason(String reason) {
        return operationConfirmationGuard.cleanReason(reason);
    }

    private String cleanIdempotencyKey(String idempotencyKey) {
        return operationConfirmationGuard.cleanIdempotencyKey(idempotencyKey);
    }

    private String finishKnowledgeVectorJob(Long jobId, KnowledgeVectorRebuildVO result) {
        long total = numberValue(result == null ? null : result.getChunkCount());
        long success = numberValue(result == null ? null : result.getVectorUpdated());
        long failed = result == null || result.getFailedDocuments() == null ? 0L : result.getFailedDocuments().size();
        long updated = numberValue(result == null ? null : result.getVectorUpdated());
        long deleted = numberValue(result == null ? null : result.getVectorDeleted());
        String error = result == null || result.getErrors() == null || result.getErrors().isEmpty()
                ? null : String.join("; ", result.getErrors().stream().limit(5).toList());
        String status = failed > 0 || error != null ? "FAILED" : "SUCCESS";
        vectorIndexJobService.finish(jobId, status, Map.of(), total, success, failed, updated, deleted, error);
        return status;
    }

    private void attachKnowledgeVectorJob(KnowledgeVectorRebuildVO result, Long jobId, String jobType,
                                          String scopeType, String scopeId, String status) {
        if (result == null || jobId == null) {
            return;
        }
        result.setJobId(jobId);
        result.setVectorJobId(jobId);
        result.setVectorJobType(jobType);
        result.setVectorScopeType(scopeType);
        result.setVectorScopeId(scopeId);
        result.setVectorJobStatus(status);
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
