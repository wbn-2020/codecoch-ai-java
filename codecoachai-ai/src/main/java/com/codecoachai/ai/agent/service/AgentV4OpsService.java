package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AgentFeedbackCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeDocumentCreateDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvaluationDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeAskVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeChunkVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeConfigVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentOptionVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVersionVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateCleanupVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateReviewVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvaluationVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeExactDuplicateGroupVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchTraceVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeStatsVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsJobLogVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsMetricDefinitionVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionResultVO;
import com.codecoachai.common.core.domain.PageResult;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface AgentV4OpsService {

    AgentFeedbackVO createFeedback(Long userId, AgentFeedbackCreateDTO dto);

    PageResult<AgentFeedbackVO> pageFeedback(Long userId, Long taskId, Long runId, String feedbackType,
                                             Long pageNo, Long pageSize);

    AgentFeedbackStatsVO feedbackStats(Integer days);

    KnowledgeDocumentVO createKnowledgeDocument(Long userId, KnowledgeDocumentCreateDTO dto);

    KnowledgeDocumentVO updateKnowledgeDocument(Long userId, Long documentId, KnowledgeDocumentCreateDTO dto);

    KnowledgeDocumentVO uploadKnowledgeDocument(Long userId, MultipartFile file, String documentType);

    PageResult<KnowledgeDocumentVO> pageKnowledgeDocuments(Long userId, String title, String documentType, String status,
                                                           Long pageNo, Long pageSize);

    List<KnowledgeDocumentVO> listKnowledgeDocuments(Long userId, String title, String documentType, String status);

    List<String> listKnowledgeDocumentTypes(Long userId);

    List<KnowledgeDocumentOptionVO> listKnowledgeDocumentOptions(Long userId);

    KnowledgeStatsVO getKnowledgeStats(Long userId);

    KnowledgeConfigVO getKnowledgeConfig(Long userId);

    KnowledgeDocumentVO getKnowledgeDocument(Long userId, Long id);

    List<KnowledgeDocumentVersionVO> listKnowledgeDocumentVersions(Long userId, Long documentId);

    KnowledgeDocumentVO restoreKnowledgeDocumentVersion(Long userId, Long documentId, Long versionId);

    List<KnowledgeChunkVO> listKnowledgeChunks(Long userId, Long documentId);

    KnowledgeChunkVO getKnowledgeChunk(Long userId, Long chunkId);

    List<KnowledgeSearchResultVO> listSimilarKnowledgeChunks(Long userId, Long chunkId, Integer limit);

    KnowledgeDuplicateReviewVO reviewDuplicateKnowledgeChunks(Long userId, Integer limit, Double threshold);

    List<KnowledgeExactDuplicateGroupVO> listExactDuplicateKnowledgeChunks(Long userId, Integer limit,
                                                                          Long documentId, String documentType);

    KnowledgeDuplicateCleanupVO cleanupExactDuplicateKnowledgeChunks(Long userId, Boolean dryRun, Integer limit,
                                                                     Long documentId, String documentType);

    void deleteKnowledgeChunk(Long userId, Long chunkId);

    void deleteKnowledgeDocument(Long userId, Long id);

    List<KnowledgeSearchResultVO> searchKnowledge(Long userId, String keyword, Integer limit, Double minScore,
                                                  Long documentId, String documentType);

    KnowledgeSearchTraceVO traceKnowledgeSearch(Long userId, String keyword, Integer limit, Double minScore,
                                                Long documentId, String documentType);

    KnowledgeAskVO askKnowledge(Long userId, KnowledgeAskDTO dto);

    /**
     * 流式问答：先回调检索到的 references，再逐 token 回调答案增量，最后回调引用校验结果。
     * 过程异常通过 listener.onError 暴露，不向外抛出（便于 SSE 控制层统一处理）。
     */
    void askKnowledgeStream(Long userId, KnowledgeAskDTO dto, KnowledgeAskStreamListener listener);

    /** 流式问答事件回调。 */
    interface KnowledgeAskStreamListener {
        void onReferences(List<KnowledgeSearchResultVO> references);

        void onToken(String delta);

        void onCitation(KnowledgeAskVO result);

        void onDone(Long aiCallLogId);

        void onError(String message);
    }

    KnowledgeEvaluationVO evaluateKnowledge(Long userId, KnowledgeEvaluationDTO dto);

    KnowledgeVectorRebuildVO rebuildKnowledgeVectors(Long userId, Long documentId);

    KnowledgeVectorRebuildVO retryFailedKnowledgeVectors(Long userId, Integer limit);

    KnowledgeVectorRebuildVO rebuildAllKnowledgeVectors(Integer limit);

    KnowledgeVectorRebuildVO retryAllFailedKnowledgeVectors(Integer limit);

    List<AnalyticsMetricDefinitionVO> listMetrics(String category, Integer enabled);

    AnalyticsMetricDefinitionVO saveMetric(AdminAnalyticsMetricSaveDTO dto);

    PageResult<AnalyticsJobLogVO> pageJobs(String jobCode, String status, Long pageNo, Long pageSize);

    AnalyticsJobLogVO rerunJob(Long jobId);

    AnalyticsJobLogVO runDailyPlanBatch(AnalyticsJobRunDTO dto);

    List<PromptRegressionCaseVO> listPromptCases(String promptType, Integer enabled);

    PromptRegressionCaseVO savePromptCase(PromptRegressionCaseSaveDTO dto);

    PromptRegressionResultVO runPromptCase(Long caseId, Long promptVersionId);

    List<PromptRegressionResultVO> listPromptResults(Long caseId);
}
