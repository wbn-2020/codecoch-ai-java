package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.AnalyzeResumeJobMatchDTO;
import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.ai.domain.dto.ParseJobDescriptionDTO;
import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.domain.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceImplFailureHandlingTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private PromptRenderService promptRenderService;
    @Mock
    private AiCallLogService aiCallLogService;

    private AiServiceImpl service;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setMockEnabled(false);
        aiProperties.setProvider("openai-compatible");
        aiProperties.setModel("deepseek-chat");
        when(promptRenderService.render(any(String.class), any(String.class), anyMap()))
                .thenAnswer(invocation -> PromptRenderResult.builder()
                        .scene(invocation.getArgument(0))
                        .renderedPrompt("rendered prompt")
                        .inputVariablesJson("{}")
                        .modelParamsJson("{}")
                        .promptHash("hash")
                        .fallbackUsed(false)
                        .build());
        service = new AiServiceImpl(
                aiCallLogMapper,
                promptRenderService,
                aiCallLogService,
                aiProperties,
                new ObjectMapper());
    }

    @Test
    void reviewPracticeDoesNotReturnFallbackWhenRealAiFails() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenThrow(new AiProviderException(AiFailureType.TIMEOUT, "provider timeout"));

        assertThrows(BusinessException.class, () -> service.reviewPractice(practiceReviewDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void reviewPracticeAcceptsChineseProviderReviewAndNormalizesLocalizedLevel() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "score": 85,
                  "level": "良好",
                  "summary": "回答覆盖了自调用、异常处理和传播行为等事务失效场景，并给出了可执行的排查方向。",
                  "strengths": [
                    "准确指出同类内部自调用会绕过代理",
                    "说明了受检异常未配置 rollbackFor 的影响"
                  ],
                  "weaknesses": [
                    "可以进一步区分不同事务传播行为的边界"
                  ],
                  "improvementSuggestions": [
                    "补充 REQUIRES_NEW 与 NESTED 的具体差异"
                  ],
                  "referenceComparison": "与参考答案的核心结论一致，且补充了事务同步状态和数据库提交日志的排查思路。",
                  "knowledgeGaps": [
                    "事务传播行为的细粒度差异"
                  ],
                  "suggestedFollowUps": [
                    "请说明自调用场景下 REQUIRES_NEW 为什么不会生效。"
                  ]
                }
                """);
        routeResult.setAiCallLogId(915L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = assertDoesNotThrow(() -> service.reviewPractice(springTransactionPracticeReviewDTO()));

        assertEquals(85, result.getScore());
        assertEquals("GOOD", result.getLevel());
        assertEquals("MASTERED", result.getMasteryStatus());
        assertEquals(915L, result.getAiCallLogId());
        assertTrue(result.getSummary().contains("事务失效"));
    }

    @Test
    void reviewPracticeStillRejectsUnrelatedChineseContent() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "score": 85,
                  "level": "GOOD",
                  "summary": "回答介绍了垃圾回收器的分代设计和停顿时间，整体结构比较完整。",
                  "strengths": ["说明了年轻代和老年代的职责"],
                  "weaknesses": ["缺少垃圾回收日志示例"],
                  "improvementSuggestions": ["补充 G1 收集器的 Region 设计"],
                  "referenceComparison": "与垃圾回收参考材料基本一致。",
                  "knowledgeGaps": ["垃圾回收日志分析"],
                  "suggestedFollowUps": ["请说明 G1 的 Mixed GC。"]
                }
                """);
        routeResult.setAiCallLogId(916L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        assertThrows(BusinessException.class,
                () -> service.reviewPractice(springTransactionPracticeReviewDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void generateLearningPlanDoesNotReturnFallbackWhenRealAiFails() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenThrow(new AiProviderException(AiFailureType.TIMEOUT, "provider timeout"));

        assertThrows(BusinessException.class, () -> service.generateLearningPlan(learningPlanDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void generateTargetedStudyPlanDoesNotReturnFallbackWhenRealAiFails() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenThrow(new AiProviderException(AiFailureType.TIMEOUT, "provider timeout"));

        assertThrows(BusinessException.class, () -> service.generateTargetedStudyPlan(targetedStudyPlanDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void generateQuestionDraftsDoesNotReturnMockWhenRealAiResponseCannotBeParsed() {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("not-json-from-provider");
        routeResult.setAiCallLogId(909L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        assertThrows(BusinessException.class, () -> service.generateQuestionDrafts(questionDraftDTO()));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void parseJobDescriptionRequestsJsonAndNormalizesWrappedAliases() throws Exception {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "data": {
                    "jobResponsibilities": ["负责核心交易服务的接口设计与稳定性治理"],
                    "mustHaveSkills": ["Java", "Spring Boot", "MySQL"],
                    "overview": "面向高并发业务场景招聘 Java 后端工程师。"
                  }
                }
                """);
        routeResult.setAiCallLogId(911L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = service.parseJobDescription(jobDescriptionDTO());
        var json = new ObjectMapper().readTree(result.getResultJson());
        ArgumentCaptor<AiCallContext> contextCaptor = ArgumentCaptor.forClass(AiCallContext.class);
        verify(aiCallLogService).callAndLog(contextCaptor.capture());

        assertEquals("JSON", contextCaptor.getValue().getResponseFormat());
        assertEquals("200", contextCaptor.getValue().getBusinessId());
        assertEquals("负责核心交易服务的接口设计与稳定性治理", json.path("responsibilities").get(0).asText());
        assertEquals("Java", json.path("requiredSkills").get(0).asText());
        assertEquals("面向高并发业务场景招聘 Java 后端工程师。", json.path("summary").asText());
        assertEquals(911L, result.getAiCallLogId());
    }

    @Test
    void parseJobDescriptionRejectsInferredKubernetesWhenJdOnlyMentionsContainerizationAndNacos() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(jobDescriptionRouteResult("Kubernetes"));
        ParseJobDescriptionDTO dto = jobDescriptionDTO();
        dto.setJdText("负责 Java 服务的容器化部署与 Nacos 配置管理。");
        dto.setUserTargetDirection("Java 后端，期望学习 Kubernetes");

        assertThrows(BusinessException.class, () -> service.parseJobDescription(dto));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void parseJobDescriptionRejectsInferredK8sWhenJdOnlyMentionsContainerizationAndNacos() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(jobDescriptionRouteResult("k8s"));
        ParseJobDescriptionDTO dto = jobDescriptionDTO();
        dto.setJdText("负责 Java 服务的容器化部署与 Nacos 配置管理。");

        assertThrows(BusinessException.class, () -> service.parseJobDescription(dto));

        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void parseJobDescriptionAllowsK8sWhenJdExplicitlyMentionsKubernetes() {
        when(aiCallLogService.callAndLog(any(AiCallContext.class)))
                .thenReturn(jobDescriptionRouteResult("K8s"));
        ParseJobDescriptionDTO dto = jobDescriptionDTO();
        dto.setJdText("负责 Java 服务的容器化部署，要求具备 Kubernetes 集群运维经验。");

        var result = assertDoesNotThrow(() -> service.parseJobDescription(dto));

        assertEquals(915L, result.getAiCallLogId());
        assertTrue(result.getResultJson().contains("K8s"));
    }

    @Test
    void optimizeResumeAcceptsDoubleEncodedJsonAndNormalizesAliases() throws Exception {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                "{\\"score\\":86,\\"summary\\":\\"建议补充项目难点与可验证结果。\\",\\"suggestions\\":[],\\"actionItems\\":[\\"补充真实项目指标\\"]}"
                """);
        routeResult.setAiCallLogId(912L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = service.optimizeResume(resumeOptimizeDTO());
        var json = new ObjectMapper().readTree(result.getResultJson());

        assertEquals(86, json.path("overallScore").asInt());
        assertEquals("建议补充项目难点与可验证结果。", json.path("overallComment").asText());
        assertTrue(json.path("rewriteSuggestions").isArray());
        assertEquals("补充真实项目指标", json.path("nextActions").get(0).asText());
        assertEquals(912L, result.getAiCallLogId());
    }

    @Test
    void analyzeResumeJobMatchAcceptsKubernetesAndK8sAsEquivalentEvidence() throws Exception {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "overallScore": 84,
                  "dimensionScores": {
                    "techStack": 88,
                    "projectExperience": 82,
                    "businessFit": 80,
                    "communication": 86
                  },
                  "strengths": [
                    {
                      "title": "K8s 容器化交付经验",
                      "evidence": "简历项目技术栈明确包含 Kubernetes。",
                      "relatedSkills": ["K8s"]
                    }
                  ],
                  "gaps": [
                    {
                      "skillName": "Redis",
                      "category": "middleware",
                      "severity": "MEDIUM",
                      "targetLevel": 4,
                      "currentLevel": 3,
                      "description": "需要补充缓存一致性的设计取舍。",
                      "evidence": "岗位要求 Redis，简历已有相关技术栈但缺少方案细节。",
                      "recommendedActions": ["补充真实缓存一致性案例"]
                    }
                  ],
                  "resumeRisks": [],
                  "optimizationSuggestions": ["补充 Kubernetes 发布与回滚的真实细节。"],
                  "recommendedLearningTopics": ["Redis 缓存一致性"],
                  "recommendedInterviewTopics": ["K8s 发布与故障恢复"],
                  "summary": "技术栈与目标岗位整体匹配。",
                  "schemaWarnings": [{"fieldPath": "model", "message": "模型自行声明的告警"}],
                  "trustStatus": "FALLBACK",
                  "fallback": true
                }
                """);
        routeResult.setAiCallLogId(913L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);
        AnalyzeResumeJobMatchDTO dto = resumeJobMatchDTO();
        dto.setResumeSnapshotJson("""
                {"skills":["Java","Spring Boot","Kubernetes"],"projects":[{"techStack":"Kubernetes"}]}
                """);

        var result = service.analyzeResumeJobMatch(dto);
        var json = new ObjectMapper().readTree(result.getResultJson());

        assertEquals("VERIFIED", json.path("trustStatus").asText(), json::toPrettyString);
        assertFalse(json.path("fallback").asBoolean(false));
        assertTrue(json.path("schemaWarnings").isEmpty(), json::toPrettyString);
        assertEquals(913L, result.getAiCallLogId());
    }

    @Test
    void analyzeResumeJobMatchSanitizesUnsupportedEvidenceWithoutDiscardingReport() throws Exception {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "overallScore": 80,
                  "dimensionScores": {
                    "techStack": 80,
                    "projectExperience": 75,
                    "businessFit": 78,
                    "communication": 82
                  },
                  "strengths": [
                    {
                      "title": "Cloud platform experience",
                      "evidence": "The resume shows AWS production experience.",
                      "relatedSkills": ["AWS"]
                    }
                  ],
                  "gaps": [
                    {
                      "skillName": "Redis",
                      "category": "middleware",
                      "severity": "MEDIUM",
                      "targetLevel": 3,
                      "currentLevel": 2,
                      "description": "Needs deeper cache consistency evidence.",
                      "evidence": "JD asks for Redis; resume only mentions basic backend work.",
                      "recommendedActions": ["Add Redis project evidence"]
                    }
                  ],
                  "resumeRisks": [],
                  "optimizationSuggestions": [],
                  "recommendedLearningTopics": ["Redis cache consistency"],
                  "recommendedInterviewTopics": ["Redis scenarios"],
                  "summary": "Mostly aligned."
                }
                """);
        routeResult.setAiCallLogId(910L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);

        var result = assertDoesNotThrow(() -> service.analyzeResumeJobMatch(resumeJobMatchDTO()));
        var json = new ObjectMapper().readTree(result.getResultJson());

        assertEquals(80, json.path("overallScore").asInt());
        assertEquals("PARTIAL", json.path("trustStatus").asText(), json::toPrettyString);
        assertFalse(json.path("fallback").asBoolean(false));
        assertTrue(json.path("strengths").isEmpty(), json::toPrettyString);
        assertFalse(result.getResultJson().contains("AWS"), json::toPrettyString);
        assertTrue(json.path("schemaWarnings").isArray());
        assertEquals(910L, result.getAiCallLogId());
    }

    @Test
    void analyzeResumeJobMatchDoesNotTreatContainerizationAsKubernetesEvidence() throws Exception {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "overallScore": 76,
                  "dimensionScores": {
                    "techStack": 78,
                    "projectExperience": 74,
                    "businessFit": 75,
                    "communication": 77
                  },
                  "strengths": [
                    {
                      "title": "K8s 容器编排经验",
                      "evidence": "简历项目技术栈包含 Kubernetes。",
                      "relatedSkills": ["K8s"]
                    }
                  ],
                  "gaps": [
                    {
                      "skillName": "Redis",
                      "category": "middleware",
                      "severity": "MEDIUM",
                      "targetLevel": 4,
                      "currentLevel": 3,
                      "description": "需要补充缓存一致性的设计取舍。",
                      "evidence": "岗位要求 Redis，简历缺少方案细节。",
                      "recommendedActions": ["补充真实缓存一致性案例"]
                    }
                  ],
                  "resumeRisks": [],
                  "optimizationSuggestions": ["补充 Kubernetes 发布与回滚的真实细节。"],
                  "recommendedLearningTopics": ["Redis 缓存一致性"],
                  "recommendedInterviewTopics": ["K8s 发布与故障恢复"],
                  "summary": "K8s 技术栈与目标岗位整体匹配。"
                }
                """);
        routeResult.setAiCallLogId(914L);
        when(aiCallLogService.callAndLog(any(AiCallContext.class))).thenReturn(routeResult);
        AnalyzeResumeJobMatchDTO dto = resumeJobMatchDTO();
        dto.setResumeSnapshotJson("""
                {"skills":["Java","Spring Boot","容器化"],"projects":[{"delivery":"Docker 容器化交付"}]}
                """);

        var result = service.analyzeResumeJobMatch(dto);
        var json = new ObjectMapper().readTree(result.getResultJson());
        String normalizedJson = result.getResultJson().toLowerCase();

        assertEquals("PARTIAL", json.path("trustStatus").asText(), json::toPrettyString);
        assertFalse(json.path("fallback").asBoolean(false));
        assertTrue(json.path("strengths").isEmpty(), json::toPrettyString);
        assertTrue(json.path("optimizationSuggestions").isEmpty(), json::toPrettyString);
        assertTrue(json.path("recommendedInterviewTopics").isEmpty(), json::toPrettyString);
        assertFalse(normalizedJson.contains("k8s"), json::toPrettyString);
        assertFalse(normalizedJson.contains("kubernetes"), json::toPrettyString);
        assertEquals(914L, result.getAiCallLogId());
    }

    private PracticeReviewDTO practiceReviewDTO() {
        PracticeReviewDTO dto = new PracticeReviewDTO();
        dto.setUserId(10L);
        dto.setRecordId(20L);
        dto.setQuestionId(30L);
        dto.setQuestionTitle("Redis cache penetration");
        dto.setQuestionContent("How do you prevent cache penetration?");
        dto.setKnowledgePoint("Redis");
        dto.setAnswerContent("Use parameter validation, null cache, and Bloom filters.");
        return dto;
    }

    private PracticeReviewDTO springTransactionPracticeReviewDTO() {
        PracticeReviewDTO dto = new PracticeReviewDTO();
        dto.setUserId(27L);
        dto.setRecordId(55L);
        dto.setQuestionId(9700403L);
        dto.setQuestionTitle("Spring 事务在什么情况下会失效？");
        dto.setQuestionContent("请结合代理、自调用、异常类型和传播行为说明常见事务失效原因。");
        dto.setQuestionType("SHORT_ANSWER");
        dto.setDifficulty("MEDIUM");
        dto.setKnowledgePoint("回答应覆盖代理边界和真实提交行为，而不是只列举注解。");
        dto.setReferenceAnswer("自调用绕过代理、非 public 方法、异常被吞、受检异常未配置回滚和传播级别使用不当都可能导致事务失效。");
        dto.setAnalysis("回答应覆盖代理边界和真实提交行为，而不是只列举注解。");
        dto.setAnswerContent("Spring 事务依赖 AOP 代理。常见失效包括自调用绕过代理、异常被吞、受检异常未配置 rollbackFor，以及传播行为选择不当。");
        dto.setAnswerDurationSeconds(210);
        dto.setExperienceLevel("3-5年");
        return dto;
    }

    private GenerateLearningPlanDTO learningPlanDTO() {
        GenerateLearningPlanDTO dto = new GenerateLearningPlanDTO();
        dto.setLearningPlanId(40L);
        dto.setUserId(10L);
        dto.setReportId(50L);
        dto.setTargetPosition("Java backend engineer");
        dto.setInterviewSummary("Need stronger Redis and transaction answers.");
        dto.setWeaknessSummary("Redis cache penetration and transaction isolation.");
        dto.setExpectedDurationDays(7);
        return dto;
    }

    private GenerateTargetedStudyPlanDTO targetedStudyPlanDTO() {
        GenerateTargetedStudyPlanDTO dto = new GenerateTargetedStudyPlanDTO();
        dto.setLearningPlanId(60L);
        dto.setUserId(10L);
        dto.setSkillProfileId(70L);
        dto.setTargetJobId(80L);
        dto.setSkillGapsJson("[{\"skillName\":\"Redis\",\"severity\":\"HIGH\",\"gapDescription\":\"Cache penetration\"}]");
        dto.setAvailableDays(7);
        dto.setDailyMinutes(60);
        dto.setStartDate(LocalDate.of(2026, 6, 17));
        dto.setPlanTitle("Redis improvement plan");
        return dto;
    }

    private GenerateQuestionDraftDTO questionDraftDTO() {
        GenerateQuestionDraftDTO dto = new GenerateQuestionDraftDTO();
        dto.setBatchId("batch-parse-failure");
        dto.setAdminUserId(10L);
        dto.setTargetPosition("Java backend engineer");
        dto.setTechnologyStack("Java, Spring Cloud, Redis");
        dto.setKnowledgePoint("Redis cache penetration");
        dto.setQuestionType("SHORT_ANSWER");
        dto.setDifficulty("MEDIUM");
        dto.setCount(3);
        return dto;
    }

    private ParseJobDescriptionDTO jobDescriptionDTO() {
        ParseJobDescriptionDTO dto = new ParseJobDescriptionDTO();
        dto.setTargetJobId(200L);
        dto.setUserId(10L);
        dto.setJobTitle("Java 后端工程师");
        dto.setCompanyName("上海星云数据科技有限公司");
        dto.setJdText("负责核心交易服务研发，要求熟悉 Java、Spring Boot 与 MySQL。");
        return dto;
    }

    private RouteResult jobDescriptionRouteResult(String technology) {
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {
                  "responsibilities": ["负责 Java 服务交付"],
                  "requiredSkills": [
                    {
                      "name": "%s",
                      "category": "deployment",
                      "requiredLevel": 3,
                      "weight": 70,
                      "evidence": "模型输出的技术要求"
                    }
                  ],
                  "techStackKeywords": ["%s"],
                  "summary": "岗位要求具备 %s 相关能力。"
                }
                """.formatted(technology, technology, technology));
        routeResult.setAiCallLogId(915L);
        return routeResult;
    }

    private ResumeOptimizeAiRequestDTO resumeOptimizeDTO() {
        ResumeOptimizeAiRequestDTO dto = new ResumeOptimizeAiRequestDTO();
        dto.setOptimizeRecordId(300L);
        dto.setUserId(10L);
        dto.setResumeId(20L);
        dto.setTargetPosition("Java 后端工程师");
        ResumeOptimizeAiRequestDTO.ResumeSnapshot snapshot = new ResumeOptimizeAiRequestDTO.ResumeSnapshot();
        snapshot.setSkillStack("Java, Spring Boot, MySQL");
        snapshot.setSummary("具备 Java 后端服务开发经验。");
        dto.setResume(snapshot);
        return dto;
    }

    private AnalyzeResumeJobMatchDTO resumeJobMatchDTO() {
        AnalyzeResumeJobMatchDTO dto = new AnalyzeResumeJobMatchDTO();
        dto.setReportId(90L);
        dto.setUserId(10L);
        dto.setResumeId(20L);
        dto.setTargetJobId(30L);
        dto.setJdAnalysisId(40L);
        dto.setResumeSnapshotJson("{\"skills\":[\"Java\",\"Spring Boot\"],\"projects\":[\"Order service\"]}");
        dto.setJobDescriptionAnalysisJson("{\"requiredSkills\":[\"Java\",\"Redis\"],\"summary\":\"Backend role\"}");
        dto.setTargetJobJson("{\"jobTitle\":\"Java backend engineer\"}");
        return dto;
    }
}
