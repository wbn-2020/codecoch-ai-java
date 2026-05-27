package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AgentFeedbackCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeDocumentCreateDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeAskVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsJobLogVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsMetricDefinitionVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionResultVO;
import com.codecoachai.common.core.domain.PageResult;
import java.util.List;

public interface AgentV4OpsService {

    AgentFeedbackVO createFeedback(Long userId, AgentFeedbackCreateDTO dto);

    PageResult<AgentFeedbackVO> pageFeedback(Long userId, Long taskId, Long runId, String feedbackType,
                                             Long pageNo, Long pageSize);

    AgentFeedbackStatsVO feedbackStats(Integer days);

    KnowledgeDocumentVO createKnowledgeDocument(Long userId, KnowledgeDocumentCreateDTO dto);

    List<KnowledgeDocumentVO> listKnowledgeDocuments(Long userId);

    KnowledgeDocumentVO getKnowledgeDocument(Long userId, Long id);

    void deleteKnowledgeDocument(Long userId, Long id);

    List<KnowledgeSearchResultVO> searchKnowledge(Long userId, String keyword, Integer limit);

    KnowledgeAskVO askKnowledge(Long userId, KnowledgeAskDTO dto);

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
