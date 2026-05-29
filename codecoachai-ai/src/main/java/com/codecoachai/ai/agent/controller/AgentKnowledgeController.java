package com.codecoachai.ai.agent.controller;

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
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeStatsVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.ai.agent.service.KnowledgeEvaluationService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final AgentV4OpsService agentV4OpsService;

    private final KnowledgeEvaluationService knowledgeEvaluationService;
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
    public Result<List<KnowledgeDocumentVO>> listDocuments(@RequestParam(required = false) String title,
                                                          @RequestParam(required = false) String documentType,
                                                          @RequestParam(required = false) String status) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.listKnowledgeDocuments(userId, title, documentType, status));
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

    @PostMapping("/documents/{id}/versions/{versionId}/restore")
    public Result<KnowledgeDocumentVO> restoreDocumentVersion(@PathVariable Long id,
                                                              @PathVariable Long versionId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.restoreKnowledgeDocumentVersion(userId, id, versionId));
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

    @PostMapping("/duplicates/exact/cleanup")
    public Result<KnowledgeDuplicateCleanupVO> cleanupExactDuplicates(
            @RequestParam(defaultValue = "true") Boolean dryRun,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String documentType) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.cleanupExactDuplicateKnowledgeChunks(userId, dryRun, limit, documentId, documentType));
    }

    @DeleteMapping("/chunks/{chunkId}")
    public Result<Void> deleteChunk(@PathVariable Long chunkId) {
        Long userId = SecurityAssert.requireLoginUserId();
        agentV4OpsService.deleteKnowledgeChunk(userId, chunkId);
        return Result.success();
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        agentV4OpsService.deleteKnowledgeDocument(userId, id);
        return Result.success();
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

    @DeleteMapping("/eval/cases/{id}")
    public Result<Void> deleteEvalCase(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        knowledgeEvaluationService.deleteCase(userId, id);
        return Result.success();
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
    public Result<KnowledgeVectorRebuildVO> rebuildVectors(@RequestParam(required = false) Long documentId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.rebuildKnowledgeVectors(userId, documentId));
    }

    @PostMapping("/vectors/retry-failed")
    public Result<KnowledgeVectorRebuildVO> retryFailedVectors(@RequestParam(required = false) Integer limit) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.retryFailedKnowledgeVectors(userId, limit));
    }
}
