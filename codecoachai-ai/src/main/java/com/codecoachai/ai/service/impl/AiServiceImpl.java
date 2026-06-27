package com.codecoachai.ai.service.impl;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.AnalyzeResumeJobMatchDTO;
import com.codecoachai.ai.domain.dto.AnalyzeSkillGapDTO;
import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionRecommendationDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.ai.domain.dto.ParseJobDescriptionDTO;
import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.domain.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.domain.vo.AnalyzeResumeJobMatchVO;
import com.codecoachai.ai.domain.vo.AnalyzeSkillGapVO;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateLearningPlanVO;
import com.codecoachai.ai.domain.vo.GenerateQuestionRecommendationVO;
import com.codecoachai.ai.domain.vo.GenerateQuestionDraftVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
import com.codecoachai.ai.domain.vo.ParseJobDescriptionVO;
import com.codecoachai.ai.domain.vo.ParseResumeVO;
import com.codecoachai.ai.domain.vo.PracticeReviewVO;
import com.codecoachai.ai.domain.vo.QuestionDraftItemVO;
import com.codecoachai.ai.domain.vo.QuestionRecommendationItemVO;
import com.codecoachai.ai.domain.vo.ResumeOptimizeAiResponseVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.ai.security.AiPiiMasker;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final String SCENE_QUESTION = "INTERVIEW_QUESTION_GENERATE";
    private static final String SCENE_PROJECT_QUESTION = "PROJECT_DEEP_DIVE_QUESTION";
    private static final String SCENE_EVALUATE = "INTERVIEW_ANSWER_EVALUATE";
    private static final String SCENE_FOLLOW_UP = "INTERVIEW_FOLLOW_UP_GENERATE";
    private static final String SCENE_REPORT = "INTERVIEW_REPORT_GENERATE";
    private static final String SCENE_RESUME_PARSE = "RESUME_STRUCTURED_PARSE";
    private static final String SCENE_RESUME_OPTIMIZE = "RESUME_OPTIMIZE";
    private static final String SCENE_AI_QUESTION_GENERATE = "AI_QUESTION_GENERATE";
    private static final String SCENE_LEARNING_PLAN_GENERATE = "LEARNING_PLAN_GENERATE";
    private static final String SCENE_PRACTICE_REVIEW = "PRACTICE_ANSWER_REVIEW";
    private static final String SCENE_JOB_DESCRIPTION_PARSE = "JOB_DESCRIPTION_PARSE";
    private static final String SCENE_RESUME_JOB_MATCH = "RESUME_JOB_MATCH";
    private static final String TRUST_VERIFIED = "VERIFIED";
    private static final String TRUST_PARTIAL = "PARTIAL";
    private static final String SCENE_SKILL_GAP_ANALYZE = "SKILL_GAP_ANALYZE";
    private static final String SCENE_TARGETED_STUDY_PLAN_GENERATE = "TARGETED_STUDY_PLAN_GENERATE";
    private static final List<String> RESUME_MATCH_FACT_TERMS = List.of(
            "PostgreSQL", "MongoDB", "Jenkins", "GitHub Actions", "GitHub Action", "AWS", "Kubernetes",
            "K8s", "WebFlux", "Docker", "RabbitMQ", "RocketMQ", "Kafka", "Nacos", "Seata",
            "Elasticsearch", "ElasticSearch", "Flink", "ClickHouse", "CI/CD", "CICD",
            "阿里云", "腾讯云", "华为云", "百度云", "Azure", "GCP", "云原生", "容器化",
            "阿里", "腾讯", "字节", "美团", "百度", "京东", "华为", "蚂蚁", "小米", "滴滴",
            "空窗", "6个月", "六个月");
    private static final Pattern UNSUPPORTED_NUMERIC_FACT_PATTERN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?\\s*(?:%|ms|毫秒|秒|s|qps|tps|w|万|k|人|台|倍|个月|年|小时|天)|[一二三四五六七八九十百千万]+(?:人|台|万|个月|年|小时|天))");
    private static final String SCENE_TARGETED_QUESTION_RECOMMEND = "TARGETED_QUESTION_RECOMMEND";

    private final AiCallLogMapper aiCallLogMapper;
    private final PromptRenderService promptRenderService;
    private final AiCallLogService aiCallLogService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public GenerateInterviewQuestionVO generateQuestion(GenerateInterviewQuestionDTO dto) {
        String scene = isProjectStage(dto.getStageType()) ? SCENE_PROJECT_QUESTION : SCENE_QUESTION;
        long start = System.currentTimeMillis();
        Map<String, String> variables = variables(dto, null);
        PromptRenderResult promptResult = promptRenderService.render(scene, questionPromptContent(scene),
                variables, industryContextBlock(dto == null ? null : dto.getIndustryContext()), null);
        String rawResponse = null;
        try {
            GenerateInterviewQuestionVO vo;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockQuestion(dto, scene);
                saveLog(promptResult, toJson(vo), businessId(dto.getQuestionId()), start,
                        null, null, AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, null, businessId(dto.getQuestionId()));
                rawResponse = routeResult.getContent();
                vo = parseQuestion(rawResponse, scene);
            }
            return vo;
        } catch (RuntimeException ex) {
            if (!mockEnabled()) {
                throw ex;
            }
            GenerateInterviewQuestionVO fallback = mockQuestion(dto, scene);
            saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            return fallback;
        }
    }

    @Override
    public GenerateQuestionDraftVO generateQuestionDrafts(GenerateQuestionDraftDTO dto) {
        validateQuestionDraftDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_AI_QUESTION_GENERATE,
                questionDraftPromptContent(), variables(dto));
        String rawResponse = null;
        try {
            GenerateQuestionDraftVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockQuestionDrafts(dto);
                rawResponse = toJson(Map.of("questions", vo.getQuestions()));
                logId = saveLog(promptResult, rawResponse,
                        dto.getBatchId(), start, null, dto.getAdminUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getAdminUserId(), dto.getBatchId());
                rawResponse = routeResult.getContent();
                vo = parseQuestionDrafts(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            vo.setBatchId(dto.getBatchId());
            vo.setAiCallLogId(logId);
            vo.setRawResponse(rawResponse);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    dto.getBatchId(), start, ex.getMessage(), dto.getAdminUserId(), failureType(ex));
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目生成失败，请稍后重试");
        }
    }

    @Override
    public GenerateQuestionRecommendationVO generateQuestionRecommendations(GenerateQuestionRecommendationDTO dto) {
        validateQuestionRecommendationDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_TARGETED_QUESTION_RECOMMEND,
                questionRecommendationPromptContent(), variables(dto));
        String rawResponse = null;
        try {
            GenerateQuestionRecommendationVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockQuestionRecommendations(dto);
                rawResponse = toJson(vo);
                logId = saveLog(promptResult, rawResponse, businessId(dto.getBatchId()),
                        start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getBatchId()));
                rawResponse = routeResult.getContent();
                vo = parseQuestionRecommendations(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            vo.setBatchId(dto.getBatchId());
            vo.setAiCallLogId(logId);
            vo.setRawResponse(rawResponse);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()), businessId(dto.getBatchId()),
                    start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toBusinessException(ex);
        }
    }

    @Override
    public PracticeReviewVO reviewPractice(PracticeReviewDTO dto) {
        validatePracticeReviewDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_PRACTICE_REVIEW,
                defaultPracticeReviewPrompt(), variables(dto));
        String rawResponse = null;
        try {
            PracticeReviewVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockPracticeReview(dto);
                rawResponse = toJson(vo);
                logId = saveLog(promptResult, rawResponse, businessId(dto.getRecordId()),
                        start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getRecordId()));
                rawResponse = routeResult.getContent();
                vo = parsePracticeReview(rawResponse, dto);
                validatePracticeReviewQuality(vo, dto);
                logId = routeResult.getAiCallLogId();
            }
            vo.setAiCallLogId(logId);
            vo.setRawResponse(rawResponse);
            return vo;
        } catch (RuntimeException ex) {
            Long logId = saveLog(promptResult, firstText(rawResponse, ex.getMessage()), businessId(dto.getRecordId()),
                    start, ex.getMessage(), dto.getUserId(), failureType(ex));
            if (!mockEnabled()) {
                throw toBusinessException(ex);
            }
            PracticeReviewVO fallback = mockPracticeReview(dto);
            fallback.setAiCallLogId(logId);
            fallback.setRawResponse(null);
            fallback.setSummary("本次点评不够贴合题目，已生成基础中文建议。建议先对照参考答案补齐核心概念、适用场景和边界条件。");
            fallback.setComment(fallback.getSummary());
            return fallback;
        }
    }

    @Override
    public EvaluateAnswerVO evaluate(EvaluateAnswerDTO dto) {
        long start = System.currentTimeMillis();
        Map<String, String> variables = variables(null, dto);
        PromptRenderResult promptResult = promptRenderService.render(SCENE_EVALUATE, defaultEvaluatePrompt(),
                variables, evaluatePromptPrefix(dto), null);
        String rawResponse = null;
        try {
            EvaluateAnswerVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockEvaluate(dto);
                logId = saveLog(promptResult, toJson(vo),
                        businessId(dto.getQuestionId()), start, null, null, AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, null, businessId(dto.getQuestionId()));
                rawResponse = routeResult.getContent();
                vo = parseEvaluate(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            if (Boolean.TRUE.equals(vo.getFollowUpValid()) && isInvalidFollowUp(vo.getFollowUpQuestion(), dto)) {
                vo.setFollowUpQuestion(buildFallbackFollowUp(dto));
                vo.setFollowUpReason(markFallback(firstText(vo.getFollowUpReason(), "追问内容不够贴合，已改用通用追问")));
                vo.setFollowUpValid(true);
            }
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            if (!mockEnabled()) {
                throw ex;
            }
            EvaluateAnswerVO fallback = mockEvaluate(dto);
            Long logId = saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            fallback.setAiCallLogId(logId);
            return fallback;
        }
    }

    @Override
    public EvaluateAnswerVO evaluateStream(EvaluateAnswerDTO dto, java.util.function.Consumer<String> tokenConsumer) {
        long start = System.currentTimeMillis();
        Map<String, String> variables = variables(null, dto);
        PromptRenderResult promptResult = promptRenderService.render(SCENE_EVALUATE, defaultEvaluatePrompt(),
                variables, evaluatePromptPrefix(dto), null);
        String rawResponse = null;
        try {
            EvaluateAnswerVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockEvaluate(dto);
                rawResponse = toJson(vo);
                emitMockTokens(rawResponse, tokenConsumer);
                logId = saveLog(promptResult, rawResponse,
                        businessId(dto.getQuestionId()), start, null, null, AiFailureType.NONE);
            } else {
                RouteResult routeResult = callStreamAndLog(promptResult, null, businessId(dto.getQuestionId()), tokenConsumer);
                rawResponse = routeResult.getContent();
                vo = parseEvaluate(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            if (Boolean.TRUE.equals(vo.getFollowUpValid()) && isInvalidFollowUp(vo.getFollowUpQuestion(), dto)) {
                vo.setFollowUpQuestion(buildFallbackFollowUp(dto));
                vo.setFollowUpReason(markFallback(firstText(vo.getFollowUpReason(), "杩介棶鍐呭涓嶅璐村悎锛屽凡鏀圭敤閫氱敤杩介棶")));
                vo.setFollowUpValid(true);
            }
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            if (!mockEnabled()) {
                throw ex;
            }
            EvaluateAnswerVO fallback = mockEvaluate(dto);
            Long logId = saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            fallback.setAiCallLogId(logId);
            emitMockTokens(toJson(fallback), tokenConsumer);
            return fallback;
        }
    }

    @Override
    public GenerateFollowUpVO generateFollowUp(GenerateFollowUpDTO dto) {
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_FOLLOW_UP, defaultFollowUpPrompt(),
                variables(dto), industryContextBlock(dto == null ? null : dto.getIndustryContext()), null);
        String rawResponse = null;
        try {
            GenerateFollowUpVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockFollowUp(dto);
                logId = saveLog(promptResult, toJson(vo),
                        businessId(dto.getQuestionId()), start, null, null, AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, null, businessId(dto.getQuestionId()));
                rawResponse = routeResult.getContent();
                vo = parseFollowUp(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            if (isInvalidFollowUp(vo.getFollowUpQuestion(), dto)) {
                vo.setFollowUpQuestion(buildFallbackFollowUp(dto));
                vo.setReason(markFallback(firstText(vo.getReason(), "追问内容不够贴合，已改用通用追问")));
                vo.setRelatedToOriginalQuestion(true);
                vo.setFollowUpValid(true);
            }
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            if (!mockEnabled()) {
                throw ex;
            }
            GenerateFollowUpVO fallback = new GenerateFollowUpVO();
            fallback.setFollowUpQuestion(buildFallbackFollowUp(dto));
            fallback.setReason(markFallback("追问内容暂时不够贴合，已改用通用追问"));
            fallback.setRelatedToOriginalQuestion(true);
            fallback.setFollowUpValid(true);
            Long logId = saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            fallback.setAiCallLogId(logId);
            return fallback;
        }
    }

    @Override
    public GenerateReportVO generateReport(GenerateReportDTO dto) {
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_REPORT, defaultReportPrompt(),
                variables(dto), reportPromptPrefix(dto), null);
        String rawResponse = null;
        try {
            GenerateReportVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockReport(dto);
                logId = saveLog(promptResult, toJson(vo),
                        businessId(dto.getInterviewId()), start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getInterviewId()));
                rawResponse = routeResult.getContent();
                vo = parseReport(rawResponse);
                logId = routeResult.getAiCallLogId();
            }
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            if (!mockEnabled()) {
                throw ex;
            }
            GenerateReportVO fallback = mockReport(dto);
            Long logId = saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getInterviewId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            fallback.setAiCallLogId(logId);
            return fallback;
        }
    }

    @Override
    public ParseResumeVO parseResume(ParseResumeDTO dto) {
        validateParseResumeDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_RESUME_PARSE, resumeParsePromptContent(),
                variables(dto));
        String rawResponse = null;
        try {
            String structuredJson;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                structuredJson = mockResumeStructuredJson();
                saveLog(promptResult, structuredJson,
                        businessId(dto.getAnalysisRecordId()), start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getAnalysisRecordId()));
                rawResponse = routeResult.getContent();
                structuredJson = parseResumeStructuredJson(rawResponse);
            }
            ParseResumeVO vo = new ParseResumeVO();
            vo.setStructuredJson(structuredJson);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getAnalysisRecordId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toBusinessException(ex);
        }
    }

    @Override
    public ResumeOptimizeAiResponseVO optimizeResume(ResumeOptimizeAiRequestDTO dto) {
        validateResumeOptimizeDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_RESUME_OPTIMIZE,
                resumeOptimizePromptContent(), variables(dto));
        String rawResponse = null;
        try {
            String resultJson;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                resultJson = mockResumeOptimizeJson();
                logId = saveLog(promptResult, resultJson,
                        businessId(dto.getOptimizeRecordId()), start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getOptimizeRecordId()));
                rawResponse = routeResult.getContent();
                resultJson = parseResumeOptimizeJson(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            ResumeOptimizeAiResponseVO vo = new ResumeOptimizeAiResponseVO();
            vo.setResultJson(resultJson);
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getOptimizeRecordId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toResumeOptimizeBusinessException(ex);
        }
    }

    @Override
    public GenerateLearningPlanVO generateLearningPlan(GenerateLearningPlanDTO dto) {
        validateLearningPlanDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_LEARNING_PLAN_GENERATE,
                learningPlanPromptContent(), variables(dto));
        String rawResponse = null;
        try {
            GenerateLearningPlanVO vo;
            Long logId;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockLearningPlan(dto);
                rawResponse = toJson(vo);
                logId = saveLog(promptResult, rawResponse,
                        businessId(dto.getLearningPlanId()), start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getLearningPlanId()));
                rawResponse = routeResult.getContent();
                vo = parseLearningPlan(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            Long logId = saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getLearningPlanId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            if (!mockEnabled()) {
                throw toBusinessException(ex);
            }
            GenerateLearningPlanVO fallback = mockLearningPlan(dto);
            fallback.setPlanSummary("学习计划内容暂时不够完整，系统已生成基础训练计划。你可以先按该计划训练，稍后再重新生成。");
            fallback.setAiCallLogId(logId);
            return fallback;
        }
    }

    @Override
    public GenerateLearningPlanVO generateTargetedStudyPlan(GenerateTargetedStudyPlanDTO dto) {
        validateTargetedStudyPlanDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_TARGETED_STUDY_PLAN_GENERATE,
                targetedStudyPlanPromptContent(), variables(dto));
        String rawResponse = null;
        try {
            GenerateLearningPlanVO vo;
            Long logId;
            String businessId = businessId(firstLong(dto.getLearningPlanId(), dto.getSkillProfileId()));
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockTargetedStudyPlan(dto);
                rawResponse = toJson(vo);
                logId = saveLog(promptResult, rawResponse, businessId,
                        start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId);
                rawResponse = routeResult.getContent();
                vo = parseTargetedStudyPlan(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            Long logId = saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(firstLong(dto.getLearningPlanId(), dto.getSkillProfileId())),
                    start, ex.getMessage(), dto.getUserId(), failureType(ex));
            if (!mockEnabled()) {
                throw toBusinessException(ex);
            }
            GenerateLearningPlanVO fallback = mockTargetedStudyPlan(dto);
            fallback.setPlanSummary("针对性学习计划内容暂时不够完整，系统已按当前短板生成基础训练计划。你可以先执行任务，稍后再重新生成。");
            fallback.setAiCallLogId(logId);
            return fallback;
        }
    }

    @Override
    public ParseJobDescriptionVO parseJobDescription(ParseJobDescriptionDTO dto) {
        validateParseJobDescriptionDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_JOB_DESCRIPTION_PARSE,
                jobDescriptionParsePromptContent(), variables(dto));
        String rawResponse = null;
        try {
            Long logId;
            String resultJson;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                resultJson = mockJobDescriptionParseJson(dto);
                logId = saveLog(promptResult, resultJson,
                        businessId(dto.getTargetJobId()), start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                AiCallContext ctx = new AiCallContext();
                ctx.setScene(SCENE_JOB_DESCRIPTION_PARSE);
                ctx.setPrompt(promptResult.getRenderedPrompt());
                ctx.setUserId(dto.getUserId());
                RouteResult routeResult = aiCallLogService.callAndLog(ctx);
                rawResponse = routeResult.getContent();
                resultJson = parseJobDescriptionJson(rawResponse);
                logId = routeResult.getAiCallLogId();
            }
            ParseJobDescriptionVO vo = new ParseJobDescriptionVO();
            vo.setResultJson(resultJson);
            vo.setAiCallLogId(logId);
            vo.setRawResponse(firstText(rawResponse, resultJson));
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getTargetJobId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toJobDescriptionParseBusinessException(ex);
        }
    }

    @Override
    public AnalyzeResumeJobMatchVO analyzeResumeJobMatch(AnalyzeResumeJobMatchDTO dto) {
        validateAnalyzeResumeJobMatchDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_RESUME_JOB_MATCH,
                resumeJobMatchPromptContent(), variables(dto));
        String rawResponse = null;
        try {
            Long logId;
            String resultJson;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                resultJson = mockResumeJobMatchJson();
                logId = saveLog(promptResult, resultJson,
                        businessId(dto.getReportId()), start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getReportId()));
                rawResponse = routeResult.getContent();
                resultJson = parseResumeJobMatchJson(rawResponse, dto);
                logId = routeResult.getAiCallLogId();
            }
            AnalyzeResumeJobMatchVO vo = new AnalyzeResumeJobMatchVO();
            vo.setResultJson(resultJson);
            vo.setAiCallLogId(logId);
            vo.setRawResponse(firstText(rawResponse, resultJson));
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getReportId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toBusinessException(ex);
        }
    }

    @Override
    public AnalyzeSkillGapVO analyzeSkillGap(AnalyzeSkillGapDTO dto) {
        validateAnalyzeSkillGapDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_SKILL_GAP_ANALYZE,
                skillGapAnalyzePromptContent(), variables(dto));
        String rawResponse = null;
        try {
            Long logId;
            String resultJson;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                resultJson = mockSkillGapAnalyzeJson();
                logId = saveLog(promptResult, resultJson,
                        businessId(dto.getProfileId()), start, null, dto.getUserId(), AiFailureType.NONE);
            } else {
                RouteResult routeResult = callAndLog(promptResult, dto.getUserId(), businessId(dto.getProfileId()));
                rawResponse = routeResult.getContent();
                resultJson = parseSkillGapAnalyzeJson(rawResponse);
                logId = routeResult.getAiCallLogId();
            }
            AnalyzeSkillGapVO vo = new AnalyzeSkillGapVO();
            vo.setResultJson(resultJson);
            vo.setAiCallLogId(logId);
            vo.setRawResponse(firstText(rawResponse, resultJson));
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getProfileId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toBusinessException(ex);
        }
    }

    private void validateParseResumeDTO(ParseResumeDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getAnalysisRecordId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历解析记录不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (!StringUtils.hasText(dto.getRawText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历内容不能为空");
        }
    }

    private void validateParseJobDescriptionDTO(ParseJobDescriptionDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getTargetJobId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (!StringUtils.hasText(dto.getJobTitle())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位名称不能为空");
        }
        if (!StringUtils.hasText(dto.getJdText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位描述不能为空");
        }
    }

    private void validateAnalyzeResumeJobMatchDTO(AnalyzeResumeJobMatchDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getReportId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (dto.getResumeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历不能为空");
        }
        if (dto.getTargetJobId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不能为空");
        }
        if (!StringUtils.hasText(dto.getResumeAnalysisJson())
                && !StringUtils.hasText(dto.getResumeSnapshotJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历分析结果或简历快照不能为空");
        }
        if (!StringUtils.hasText(dto.getJobDescriptionAnalysisJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位分析结果不能为空");
        }
    }

    private void validateAnalyzeSkillGapDTO(AnalyzeSkillGapDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getProfileId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像不能为空");
        }
        if (dto.getMatchReportId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (dto.getTargetJobId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不能为空");
        }
        if (!StringUtils.hasText(dto.getMatchReportJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告内容不能为空");
        }
        if (!StringUtils.hasText(dto.getGapsJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力短板内容不能为空");
        }
    }

    private void validateResumeOptimizeDTO(ResumeOptimizeAiRequestDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getOptimizeRecordId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历建议记录不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (dto.getResumeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历不能为空");
        }
        if (dto.getResume() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历快照不能为空");
        }
    }

    private void validateQuestionDraftDTO(GenerateQuestionDraftDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (!StringUtils.hasText(dto.getBatchId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "题目生成批次不能为空");
        }
        if (dto.getAdminUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员信息不能为空");
        }
        int count = dto.getCount() == null ? 5 : dto.getCount();
        if (count < 1 || count > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "题目数量需在 1 到 20 道之间");
        }
    }

    private void validateQuestionRecommendationDTO(GenerateQuestionRecommendationDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getBatchId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "推荐题批次不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        int count = dto.getQuestionCount() == null ? 5 : dto.getQuestionCount();
        if (count < 1 || count > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "题目数量需在 1 到 20 道之间");
        }
        if (!StringUtils.hasText(dto.getSkillGapsJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力短板内容不能为空");
        }
    }

    private void validateLearningPlanDTO(GenerateLearningPlanDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getLearningPlanId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (dto.getReportId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试报告不能为空");
        }
        if (!StringUtils.hasText(dto.getInterviewSummary()) && !StringUtils.hasText(dto.getWeaknessSummary())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试总结或薄弱点总结不能为空");
        }
    }

    private void validateTargetedStudyPlanDTO(GenerateTargetedStudyPlanDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求内容不能为空");
        }
        if (dto.getLearningPlanId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划不能为空");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户信息不能为空");
        }
        if (dto.getSkillProfileId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像不能为空");
        }
        if (!StringUtils.hasText(dto.getSkillGapsJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力短板内容不能为空");
        }
        if (dto.getAvailableDays() == null || dto.getAvailableDays() < 1 || dto.getAvailableDays() > 60) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "可学习天数需在 1 到 60 天之间");
        }
        if (dto.getDailyMinutes() == null || dto.getDailyMinutes() < 15 || dto.getDailyMinutes() > 480) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "每天学习时长需在 15 到 480 分钟之间");
        }
    }

    private BusinessException toBusinessException(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        if (ex instanceof AiProviderException) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 服务暂时不可用，请稍后重试");
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 服务暂时不可用，请稍后重试");
    }

    private boolean mockEnabled() {
        return Boolean.TRUE.equals(aiProperties.getMockEnabled());
    }

    private BusinessException toResumeOptimizeBusinessException(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        if (ex instanceof AiProviderException) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR, "简历建议生成失败，请稍后重试");
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "简历建议生成失败，请稍后重试");
    }

    private BusinessException toJobDescriptionParseBusinessException(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        if (ex instanceof AiProviderException) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR, "岗位分析生成失败，请稍后重试");
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "岗位分析生成失败，请稍后重试");
    }

    private boolean isProjectStage(String stageType) {
        return isProjectStage(stageType, null);
    }

    private boolean isProjectStage(String stageType, String stageName) {
        String value = (firstText(stageType, stageName, "")).toUpperCase();
        return value.startsWith("PROJECT")
                || value.contains("TECH_SELECTION")
                || value.contains("CORE_FLOW")
                || value.contains("OPTIMIZATION")
                || value.contains("SCALABILITY");
    }

    private String questionPromptContent(String scene) {
        if (SCENE_PROJECT_QUESTION.equals(scene)) {
            return defaultProjectQuestionPrompt();
        }
        return defaultQuestionPrompt();
    }

    private String evaluatePromptPrefix(EvaluateAnswerDTO dto) {
        String industryBlock = industryContextBlock(dto == null ? null : dto.getIndustryContext());
        if (!isProjectStage(dto == null ? null : dto.getStageType(), dto == null ? null : dto.getCurrentStage())
                && !StringUtils.hasText(dto == null ? null : dto.getProjectContent())) {
            return industryBlock;
        }
        return """
                这是项目深挖阶段的回答评分。评分必须重点关注：项目理解、技术深度、表达清晰度、问题解决能力、架构思维。
                项目信息：
                {{projectContent}}
                请避免只给通用 Java 评分，必须结合候选人在项目背景、技术架构、数据库设计、核心难点、性能优化、故障排查、技术取舍、个人职责上的表达进行判断。
                """
                + "\n" + industryBlock
                + "\n";
    }

    private String reportPromptPrefix(GenerateReportDTO dto) {
        String industryBlock = industryContextBlock(dto == null ? null : dto.getIndustryContext());
        String jdContextBlock = hasReportJdContext(dto)
                ? """
                JD target context:
                - targetJobId: {{targetJobId}}
                - skillProfileId: {{skillProfileId}}
                - matchReportId: {{matchReportId}}
                - skillGapContext: {{skillGapContext}}
                Use this context to make weakPoints, reviewSuggestions, recommendedQuestions, and reportContent reflect JD-specific missing skills, coverage gaps, and follow-up practice topics. Do not expose raw prompt, raw resume, raw JD, or raw AI output.
                """
                : "";
        String projectBlock = StringUtils.hasText(dto == null ? null : dto.getProjectContent())
                ? """
                项目深挖要求：
                - 报告必须包含项目表达评价、技术深度评价、项目薄弱点、改进建议。
                - 项目信息：
                {{projectContent}}
                - 请将项目深挖问题写入 projectProblems，并在 reportContent 中体现项目表达与技术深度分析。
                """
                : "";
        return """
                学习反馈闭环要求：
                - weakPoints 必须输出结构化薄弱点，覆盖 Java 基础、数据库、并发、缓存、架构设计、项目表达中实际暴露的问题。
                - reviewSuggestions/suggestions 必须输出可执行复习建议，包含薄弱知识点、复习方向、练习题方向、下一轮面试建议。
                - recommendedQuestions 必须输出推荐练习题或练习方向，优先围绕薄弱点和面试阶段。
                - reportContent 必须适合前端直接展示，明确回答：哪里弱、为什么弱、应该复习什么、应该练哪些题、下一轮重点练什么。
                - 如果存在行业上下文，必须在 reportContent 或 reviewSuggestions 中体现行业匹配、行业短板、行业迁移能力建议。
                - 只输出 JSON，不要 Markdown 代码块，不要解释文字。
                """
                + "\n" + industryBlock
                + "\n" + jdContextBlock
                + "\n" + projectBlock
                + "\n";
    }

    private boolean hasReportJdContext(GenerateReportDTO dto) {
        return dto != null
                && (dto.getTargetJobId() != null
                || dto.getSkillProfileId() != null
                || dto.getMatchReportId() != null
                || StringUtils.hasText(dto.getSkillGapContext()));
    }

    private String learningPlanPromptContent() {
        return defaultLearningPlanPrompt();
    }

    private String targetedStudyPlanPromptContent() {
        return defaultTargetedStudyPlanPrompt();
    }

    private String questionRecommendationPromptContent() {
        return defaultQuestionRecommendationPrompt();
    }

    private String industryContextBlock(String industryContext) {
        if (!StringUtils.hasText(industryContext)) {
            return "";
        }
        return """
                行业场景上下文：
                {{industryContext}}
                行业上下文使用边界：
                1. 行业模板只是场景化提问参考，不是候选人真实经历事实。
                2. 如果候选人项目没有对应行业背景，只能以假设场景或迁移能力方式追问。
                3. 禁止伪造候选人做过某行业项目、公司、职责、指标或业务成果。
                4. 评估时要区分真实经历不足、行业迁移能力不足和技术能力不足。
                """;
    }

    private String resumeParsePromptContent() {
        return defaultResumeParsePrompt();
    }

    private String resumeOptimizePromptContent() {
        return defaultResumeOptimizePrompt();
    }

    private String jobDescriptionParsePromptContent() {
        return defaultJobDescriptionParsePrompt();
    }

    private String resumeJobMatchPromptContent() {
        return defaultResumeJobMatchPrompt();
    }

    private String skillGapAnalyzePromptContent() {
        return defaultSkillGapAnalyzePrompt();
    }

    private String questionDraftPromptContent() {
        return defaultQuestionDraftPrompt();
    }

    private Map<String, String> variables(GenerateInterviewQuestionDTO dto, EvaluateAnswerDTO answerDTO) {
        Map<String, String> values = new LinkedHashMap<>();
        if (dto != null) {
            values.put("targetPosition", dto.getTargetPosition());
            values.put("experienceLevel", dto.getExperienceLevel());
            values.put("industry", dto.getIndustryDirection());
            values.put("industryDirection", dto.getIndustryDirection());
            values.put("industryContext", dto.getIndustryContext());
            values.put("difficulty", dto.getDifficulty());
            values.put("interviewerStyle", dto.getInterviewerStyle());
            values.put("stageName", firstText(dto.getCurrentStage(), dto.getStageType()));
            values.put("currentStage", firstText(dto.getCurrentStage(), dto.getStageType()));
            values.put("stageType", dto.getStageType());
            values.put("focusPoints", dto.getFocusPoints());
            values.put("currentQuestion", dto.getQuestionTitle());
            values.put("questionContent", dto.getQuestionContent());
            values.put("resumeContent", firstText(dto.getResumeContent(), dto.getResumeSummary()));
            values.put("projectExperience", dto.getProjectContent());
            values.put("projectContent", dto.getProjectContent());
            values.put("historySummary", dto.getHistorySummary());
        }
        if (answerDTO != null) {
            values.put("stageName", answerDTO.getCurrentStage());
            values.put("currentStage", answerDTO.getCurrentStage());
            values.put("currentQuestion", answerDTO.getQuestionTitle());
            values.put("rootQuestionContent", answerDTO.getRootQuestionContent());
            values.put("currentQuestionContent", answerDTO.getCurrentQuestionContent());
            values.put("questionContent", firstText(answerDTO.getQuestionContent(), answerDTO.getCurrentQuestionContent()));
            values.put("userAnswer", answerDTO.getAnswerContent());
            values.put("answerContent", answerDTO.getAnswerContent());
            values.put("referenceAnswer", answerDTO.getReferenceAnswer());
            values.put("historySummary", answerDTO.getHistorySummary());
            values.put("industryContext", answerDTO.getIndustryContext());
            values.put("aiComment", "");
            values.put("followUpCount", String.valueOf(safeInt(answerDTO.getFollowUpCount())));
            values.put("maxFollowUpCount", String.valueOf(maxFollowUp(answerDTO)));
            values.put("knowledgePoints", answerDTO.getKnowledgePoints());
            values.put("stageType", answerDTO.getStageType());
            values.put("projectContent", answerDTO.getProjectContent());
            values.put("projectExperience", answerDTO.getProjectContent());
        }
        return values;
    }

    private Map<String, String> variables(GenerateFollowUpDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("stageName", dto.getCurrentStage());
        values.put("currentStage", dto.getCurrentStage());
        values.put("currentQuestion", dto.getQuestionTitle());
        values.put("rootQuestionContent", dto.getRootQuestionContent());
        values.put("currentQuestionContent", dto.getCurrentQuestionContent());
        values.put("questionContent", firstText(dto.getQuestionContent(), dto.getCurrentQuestionContent()));
        values.put("referenceAnswer", dto.getReferenceAnswer());
        values.put("userAnswer", dto.getAnswerContent());
        values.put("answerContent", dto.getAnswerContent());
        values.put("historySummary", dto.getHistorySummary());
        values.put("industryContext", dto.getIndustryContext());
        values.put("aiComment", dto.getComment());
        values.put("followUpCount", String.valueOf(safeInt(dto.getFollowUpCount())));
        values.put("maxFollowUpCount", String.valueOf(maxFollowUp(dto)));
        values.put("knowledgePoints", dto.getKnowledgePoints());
        return values;
    }

    private Map<String, String> variables(GenerateReportDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("targetJobId", dto.getTargetJobId() == null ? "" : String.valueOf(dto.getTargetJobId()));
        values.put("skillProfileId", dto.getSkillProfileId() == null ? "" : String.valueOf(dto.getSkillProfileId()));
        values.put("matchReportId", dto.getMatchReportId() == null ? "" : String.valueOf(dto.getMatchReportId()));
        values.put("skillGapContext", dto.getSkillGapContext());
        values.put("targetPosition", dto.getTargetPosition());
        values.put("experienceLevel", dto.getExperienceLevel());
        values.put("industry", dto.getIndustryDirection());
        values.put("industryDirection", dto.getIndustryDirection());
        values.put("industryContext", dto.getIndustryContext());
        values.put("difficulty", dto.getDifficulty());
        values.put("resumeContent", dto.getResumeContent());
        values.put("projectExperience", dto.getProjectContent());
        values.put("projectContent", dto.getProjectContent());
        values.put("historySummary", dto.getMessages() == null ? "" : String.join("\n", dto.getMessages()));
        return values;
    }

    private Map<String, String> variables(ParseResumeDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("analysisRecordId", dto.getAnalysisRecordId() == null ? "" : String.valueOf(dto.getAnalysisRecordId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("originalFilename", dto.getOriginalFilename());
        values.put("fileExt", dto.getFileExt());
        values.put("rawText", AiPiiMasker.maskResumeJson(dto.getRawText()));
        return values;
    }

    private Map<String, String> variables(ResumeOptimizeAiRequestDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("optimizeRecordId", dto.getOptimizeRecordId() == null ? "" : String.valueOf(dto.getOptimizeRecordId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("resumeId", dto.getResumeId() == null ? "" : String.valueOf(dto.getResumeId()));
        values.put("targetPosition", dto.getTargetPosition());
        values.put("experienceYears", dto.getExperienceYears() == null ? "" : String.valueOf(dto.getExperienceYears()));
        values.put("industryDirection", dto.getIndustryDirection());
        values.put("targetCompany", dto.getTargetCompany());
        values.put("extraRequirements", dto.getExtraRequirements());
        values.put("optimizeFocus", dto.getOptimizeFocus());
        values.put("resumeJson", AiPiiMasker.maskResumeJson(toJson(dto.getResume())));
        values.put("projectsJson", AiPiiMasker.maskResumeJson(toJson(dto.getProjects())));
        return values;
    }

    private Map<String, String> variables(ParseJobDescriptionDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("targetJobId", dto.getTargetJobId() == null ? "" : String.valueOf(dto.getTargetJobId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("jobTitle", dto.getJobTitle());
        values.put("companyName", dto.getCompanyName());
        values.put("jobLevel", dto.getJobLevel());
        values.put("jdText", dto.getJdText());
        values.put("jdSource", dto.getJdSource());
        values.put("userTargetDirection", dto.getUserTargetDirection());
        return values;
    }

    private Map<String, String> variables(AnalyzeResumeJobMatchDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("reportId", dto.getReportId() == null ? "" : String.valueOf(dto.getReportId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("resumeId", dto.getResumeId() == null ? "" : String.valueOf(dto.getResumeId()));
        values.put("resumeVersionId", dto.getResumeVersionId() == null ? "" : String.valueOf(dto.getResumeVersionId()));
        values.put("targetJobId", dto.getTargetJobId() == null ? "" : String.valueOf(dto.getTargetJobId()));
        values.put("jdAnalysisId", dto.getJdAnalysisId() == null ? "" : String.valueOf(dto.getJdAnalysisId()));
        values.put("resumeAnalysisJson", AiPiiMasker.maskResumeJson(dto.getResumeAnalysisJson()));
        values.put("resumeSnapshotJson", AiPiiMasker.maskResumeJson(dto.getResumeSnapshotJson()));
        values.put("jobDescriptionAnalysisJson", dto.getJobDescriptionAnalysisJson());
        values.put("targetJobJson", dto.getTargetJobJson());
        values.put("userExperienceYears", dto.getUserExperienceYears());
        return values;
    }

    private Map<String, String> variables(AnalyzeSkillGapDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("profileId", dto.getProfileId() == null ? "" : String.valueOf(dto.getProfileId()));
        values.put("matchReportId", dto.getMatchReportId() == null ? "" : String.valueOf(dto.getMatchReportId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("resumeId", dto.getResumeId() == null ? "" : String.valueOf(dto.getResumeId()));
        values.put("targetJobId", dto.getTargetJobId() == null ? "" : String.valueOf(dto.getTargetJobId()));
        values.put("jdAnalysisId", dto.getJdAnalysisId() == null ? "" : String.valueOf(dto.getJdAnalysisId()));
        values.put("targetJobJson", dto.getTargetJobJson());
        values.put("jobDescriptionAnalysisJson", dto.getJobDescriptionAnalysisJson());
        values.put("matchReportJson", dto.getMatchReportJson());
        values.put("matchDetailsJson", dto.getMatchDetailsJson());
        values.put("gapsJson", dto.getGapsJson());
        values.put("recommendedLearningTopicsJson", dto.getRecommendedLearningTopicsJson());
        values.put("recommendedInterviewTopicsJson", dto.getRecommendedInterviewTopicsJson());
        values.put("resumeAnalysisJson", AiPiiMasker.maskResumeJson(dto.getResumeAnalysisJson()));
        values.put("resumeSnapshotJson", AiPiiMasker.maskResumeJson(dto.getResumeSnapshotJson()));
        return values;
    }

    private Map<String, String> variables(GenerateQuestionDraftDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("batchId", dto.getBatchId());
        values.put("targetPosition", dto.getTargetPosition());
        values.put("technologyStack", dto.getTechnologyStack());
        values.put("knowledgePoint", dto.getKnowledgePoint());
        values.put("questionType", firstText(dto.getQuestionType(), "SHORT_ANSWER"));
        values.put("difficulty", firstText(dto.getDifficulty(), "MEDIUM"));
        values.put("experienceYears", dto.getExperienceYears() == null ? "" : String.valueOf(dto.getExperienceYears()));
        values.put("count", String.valueOf(dto.getCount() == null ? 5 : dto.getCount()));
        values.put("generateReferenceAnswer", String.valueOf(!Boolean.FALSE.equals(dto.getGenerateReferenceAnswer())));
        values.put("generateFollowUps", String.valueOf(Boolean.TRUE.equals(dto.getGenerateFollowUps())));
        values.put("generateTagSuggestions", String.valueOf(Boolean.TRUE.equals(dto.getGenerateTagSuggestions())));
        values.put("generateCategorySuggestion", String.valueOf(Boolean.TRUE.equals(dto.getGenerateCategorySuggestion())));
        values.put("extraRequirements", dto.getExtraRequirements());
        return values;
    }

    private Map<String, String> variables(GenerateQuestionRecommendationDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("batchId", dto.getBatchId() == null ? "" : String.valueOf(dto.getBatchId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("sourceType", dto.getSourceType());
        values.put("sourceId", dto.getSourceId() == null ? "" : String.valueOf(dto.getSourceId()));
        values.put("targetJobId", dto.getTargetJobId() == null ? "" : String.valueOf(dto.getTargetJobId()));
        values.put("matchReportId", dto.getMatchReportId() == null ? "" : String.valueOf(dto.getMatchReportId()));
        values.put("skillProfileId", dto.getSkillProfileId() == null ? "" : String.valueOf(dto.getSkillProfileId()));
        values.put("studyPlanId", dto.getStudyPlanId() == null ? "" : String.valueOf(dto.getStudyPlanId()));
        values.put("strategy", firstText(dto.getStrategy(), "GAP_PRIORITY"));
        values.put("questionCount", String.valueOf(dto.getQuestionCount() == null ? 5 : dto.getQuestionCount()));
        values.put("difficultyPreference", firstText(dto.getDifficultyPreference(), "MEDIUM"));
        values.put("targetJobJson", dto.getTargetJobJson());
        values.put("matchReportJson", dto.getMatchReportJson());
        values.put("skillProfileJson", dto.getSkillProfileJson());
        values.put("skillGapsJson", dto.getSkillGapsJson());
        values.put("studyPlanJson", dto.getStudyPlanJson());
        values.put("studyTasksJson", dto.getStudyTasksJson());
        return values;
    }

    private Map<String, String> variables(PracticeReviewDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("recordId", dto.getRecordId() == null ? "" : String.valueOf(dto.getRecordId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("questionId", dto.getQuestionId() == null ? "" : String.valueOf(dto.getQuestionId()));
        values.put("questionTitle", dto.getQuestionTitle());
        values.put("questionContent", dto.getQuestionContent());
        values.put("questionType", dto.getQuestionType());
        values.put("difficulty", dto.getDifficulty());
        values.put("technologyStack", dto.getTechnologyStack());
        values.put("knowledgePoint", dto.getKnowledgePoint());
        values.put("referenceAnswer", dto.getReferenceAnswer());
        values.put("analysis", dto.getAnalysis());
        values.put("answerContent", dto.getAnswerContent());
        values.put("userAnswer", dto.getAnswerContent());
        values.put("answerDurationSeconds", dto.getAnswerDurationSeconds() == null ? "" : String.valueOf(dto.getAnswerDurationSeconds()));
        values.put("targetPosition", dto.getTargetPosition());
        values.put("experienceLevel", dto.getExperienceLevel());
        return values;
    }

    private Map<String, String> variables(GenerateLearningPlanDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("learningPlanId", dto.getLearningPlanId() == null ? "" : String.valueOf(dto.getLearningPlanId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("reportId", dto.getReportId() == null ? "" : String.valueOf(dto.getReportId()));
        values.put("sessionId", dto.getSessionId() == null ? "" : String.valueOf(dto.getSessionId()));
        values.put("targetPosition", dto.getTargetPosition());
        values.put("industryDirection", dto.getIndustryDirection());
        values.put("experienceLevel", dto.getExperienceLevel());
        values.put("interviewSummary", dto.getInterviewSummary());
        values.put("weaknessSummary", dto.getWeaknessSummary());
        values.put("questionPerformanceSummary", dto.getQuestionPerformanceSummary());
        values.put("resumeWeaknessSummary", dto.getResumeWeaknessSummary());
        values.put("expectedDurationDays", dto.getExpectedDurationDays() == null ? "14" : String.valueOf(dto.getExpectedDurationDays()));
        values.put("extraRequirements", dto.getExtraRequirements());
        return values;
    }

    private Map<String, String> variables(GenerateTargetedStudyPlanDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("learningPlanId", dto.getLearningPlanId() == null ? "" : String.valueOf(dto.getLearningPlanId()));
        values.put("userId", dto.getUserId() == null ? "" : String.valueOf(dto.getUserId()));
        values.put("targetJobId", dto.getTargetJobId() == null ? "" : String.valueOf(dto.getTargetJobId()));
        values.put("skillProfileId", dto.getSkillProfileId() == null ? "" : String.valueOf(dto.getSkillProfileId()));
        values.put("matchReportId", dto.getMatchReportId() == null ? "" : String.valueOf(dto.getMatchReportId()));
        values.put("targetJobJson", dto.getTargetJobJson());
        values.put("skillProfileJson", dto.getSkillProfileJson());
        values.put("skillGapsJson", dto.getSkillGapsJson());
        values.put("availableDays", dto.getAvailableDays() == null ? "14" : String.valueOf(dto.getAvailableDays()));
        values.put("dailyMinutes", dto.getDailyMinutes() == null ? "60" : String.valueOf(dto.getDailyMinutes()));
        values.put("startDate", dto.getStartDate() == null ? "" : dto.getStartDate().toString());
        values.put("existingStudyPlansJson", dto.getExistingStudyPlansJson());
        values.put("planTitle", dto.getPlanTitle());
        return values;
    }

    private PracticeReviewVO parsePracticeReview(String raw, PracticeReviewDTO dto) {
        JsonNode json = parseJson(raw);
        PracticeReviewVO vo = new PracticeReviewVO();
        vo.setRecordId(dto.getRecordId());
        vo.setQuestionId(dto.getQuestionId());
        int score = clampScore(json.path("score").asInt(scoreByLength(dto.getAnswerContent())));
        vo.setScore(score);
        vo.setLevel(firstText(json.path("level").asText(null), levelByScore(score)));
        vo.setMasteryStatus(firstText(json.path("masteryStatus").asText(null), masteryByScore(score)));
        vo.setSummary(firstText(json.path("summary").asText(null), json.path("comment").asText(null), json.path("aiComment").asText(null)));
        vo.setComment(vo.getSummary());
        vo.setStrengths(textArray(json.path("strengths")));
        vo.setWeaknesses(textArray(json.path("weaknesses")));
        vo.setImprovementSuggestions(textArray(json.path("improvementSuggestions")));
        vo.setSuggestions(firstText(json.path("suggestions").asText(null), json.path("advice").asText(null),
                String.join("\n", vo.getImprovementSuggestions())));
        vo.setReferenceComparison(json.path("referenceComparison").asText(null));
        vo.setKnowledgeGaps(textArray(json.path("knowledgeGaps")));
        vo.setSuggestedFollowUps(textArray(json.path("suggestedFollowUps")));
        vo.setKnowledgePoints(firstText(json.path("knowledgePoints").asText(null), json.path("knowledgePoint").asText(null),
                String.join(",", vo.getKnowledgeGaps())));
        return vo;
    }

    private PracticeReviewVO mockPracticeReview(PracticeReviewDTO dto) {
        PracticeReviewVO vo = new PracticeReviewVO();
        int score = scoreByLength(dto == null ? null : dto.getAnswerContent());
        vo.setRecordId(dto == null ? null : dto.getRecordId());
        vo.setQuestionId(dto == null ? null : dto.getQuestionId());
        vo.setScore(score);
        vo.setLevel(levelByScore(score));
        vo.setMasteryStatus(masteryByScore(score));
        vo.setSummary(score >= 75
                ? "回答覆盖了主要结论，建议继续补充原理边界、适用场景和生产实践细节。"
                : "回答偏概括，建议先对齐核心概念，再按原理、场景、优缺点和排查步骤展开。");
        vo.setComment(vo.getSummary());
        vo.setStrengths(List.of(score >= 75 ? "覆盖了主要知识点" : "已给出基础回答方向"));
        vo.setWeaknesses(List.of(score >= 75 ? "边界条件和落地细节仍可补充" : "核心概念、原理和场景展开不足"));
        vo.setImprovementSuggestions(List.of("对照参考答案补齐遗漏点", "按原理、场景、优缺点和排查步骤组织答案"));
        vo.setSuggestions("对照参考答案补齐遗漏点，使用 2-3 个关键词组织答案，并加入一个真实项目或线上问题示例。");
        vo.setReferenceComparison("请对照参考答案检查核心概念、关键步骤和边界条件是否覆盖。");
        vo.setKnowledgeGaps(List.of(firstText(dto == null ? null : dto.getKnowledgePoint(), dto == null ? null : dto.getQuestionTitle(), "Java 后端基础知识")));
        vo.setSuggestedFollowUps(List.of("请结合一个生产问题说明该知识点的排查思路。"));
        vo.setKnowledgePoints(firstText(dto == null ? null : dto.getQuestionTitle(), "Java 后端基础知识"));
        return vo;
    }

    private void validatePracticeReviewQuality(PracticeReviewVO vo, PracticeReviewDTO dto) {
        String reviewText = normalizeEvidenceText(
                vo == null ? null : vo.getSummary(),
                vo == null ? null : vo.getComment(),
                vo == null ? null : vo.getSuggestions(),
                vo == null ? null : vo.getReferenceComparison(),
                vo == null ? null : String.join(" ", vo.getStrengths() == null ? List.of() : vo.getStrengths()),
                vo == null ? null : String.join(" ", vo.getWeaknesses() == null ? List.of() : vo.getWeaknesses()),
                vo == null ? null : String.join(" ", vo.getImprovementSuggestions() == null ? List.of() : vo.getImprovementSuggestions()),
                vo == null ? null : String.join(" ", vo.getKnowledgeGaps() == null ? List.of() : vo.getKnowledgeGaps()));
        if (!hasEnoughChinese(reviewText)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI practice review must be Chinese");
        }
        if (!hasTopicOverlap(reviewText, dto)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI practice review is unrelated to the question");
        }
    }

    private boolean hasEnoughChinese(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        int chinese = 0;
        int latin = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                chinese++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
            }
        }
        return chinese >= 12 && chinese >= Math.max(6, latin / 2);
    }

    private boolean hasTopicOverlap(String reviewText, PracticeReviewDTO dto) {
        List<String> tokens = topicTokens(dto);
        if (tokens.isEmpty()) {
            return true;
        }
        String normalizedReview = normalizeEvidenceText(reviewText);
        long matched = tokens.stream().filter(token -> containsEvidenceTerm(normalizedReview, token)).count();
        int requiredMatches = tokens.size() >= 3 ? 2 : 1;
        return matched >= requiredMatches;
    }

    private List<String> topicTokens(PracticeReviewDTO dto) {
        String source = normalizeEvidenceText(
                dto == null ? null : dto.getQuestionTitle(),
                dto == null ? null : dto.getQuestionContent(),
                dto == null ? null : dto.getKnowledgePoint());
        if (!StringUtils.hasText(source)) {
            return List.of();
        }
        List<String> tokens = new java.util.ArrayList<>();
        Matcher matcher = Pattern.compile("[a-zA-Z][a-zA-Z0-9+#.-]{2,}|[\\u4e00-\\u9fff]{2,}").matcher(source);
        while (matcher.find()) {
            String token = matcher.group();
            if (!isGenericTopicToken(token)) {
                tokens.add(token);
            }
        }
        return tokens.stream().distinct().limit(12).toList();
    }

    private boolean isGenericTopicToken(String token) {
        if (!StringUtils.hasText(token)) {
            return true;
        }
        String value = token.toLowerCase();
        return List.of("java", "spring", "mysql", "redis", "题目", "问题", "答案", "分析",
                "实现", "说明", "什么", "如何", "为什么", "核心", "主要", "用户", "参考",
                "面试", "后台", "项目", "系统", "场景", "技术").contains(value);
    }

    private void validatePracticeReviewDTO(PracticeReviewDTO dto) {
        if (dto == null || dto.getRecordId() == null || dto.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "练习记录和题目信息不能为空");
        }
        if (!StringUtils.hasText(dto.getAnswerContent())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "回答内容不能为空");
        }
        if (dto.getAnswerContent().length() > 5000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "回答内容不能超过 5000 字");
        }
    }

    private String levelByScore(int score) {
        if (score >= 90) {
            return "EXCELLENT";
        }
        if (score >= 75) {
            return "GOOD";
        }
        if (score >= 60) {
            return "NORMAL";
        }
        return "WEAK";
    }

    private String masteryByScore(int score) {
        if (score >= 80) {
            return "MASTERED";
        }
        if (score >= 60) {
            return "FAMILIAR";
        }
        return "NOT_MASTERED";
    }

    private GenerateInterviewQuestionVO parseQuestion(String raw, String scene) {
        JsonNode json;
        try {
            json = parseJson(raw);
        } catch (BusinessException ex) {
            GenerateInterviewQuestionVO fallback = new GenerateInterviewQuestionVO();
            String content = cleanQuestionText(firstText(extractLabelValue(raw, "questionText"),
                    extractLabelValue(raw, "questionContent"),
                    raw,
                    "请结合当前阶段说明核心原理、适用场景和生产实践注意点。"));
            fallback.setQuestionContent(content);
            fallback.setQuestionText(content);
            fallback.setScene(scene);
            return fallback;
        }
        GenerateInterviewQuestionVO vo = new GenerateInterviewQuestionVO();
        String content = cleanQuestionText(firstText(json.path("questionContent").asText(null),
                json.path("questionText").asText(null),
                json.path("question").asText(null),
                json.path("content").asText(null),
                raw));
        vo.setQuestionContent(content);
        vo.setQuestionText(content);
        vo.setScene(scene);
        return vo;
    }

    private GenerateQuestionDraftVO parseQuestionDrafts(String raw, GenerateQuestionDraftDTO dto) {
        JsonNode json = parseJson(raw);
        if (json != null && json.isArray()) {
            json = objectMapper.createObjectNode().set("questions", json);
        }
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI question generation response must be a JSON object or array");
        }
        JsonNode questionsNode = json.path("questions");
        if (!questionsNode.isArray()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI question generation response missing questions array");
        }
        int requestedCount = dto.getCount() == null ? 5 : dto.getCount();
        if (questionsNode.isEmpty() || questionsNode.size() > requestedCount) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI question generation response question count invalid");
        }
        List<QuestionDraftItemVO> questions = new java.util.ArrayList<>();
        for (JsonNode item : questionsNode) {
            if (item == null || !item.isObject()) {
                throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI question generation item must be object");
            }
            QuestionDraftItemVO question = new QuestionDraftItemVO();
            question.setTitle(requireText(item, "title"));
            question.setContent(requireText(item, "content"));
            question.setReferenceAnswer(requireText(item, "referenceAnswer"));
            question.setAnalysis(requireText(item, "analysis"));
            question.setDifficulty(firstText(item.path("difficulty").asText(null), dto.getDifficulty(), "MEDIUM"));
            question.setQuestionType(firstText(item.path("questionType").asText(null), dto.getQuestionType(), "SHORT_ANSWER"));
            question.setFollowUpQuestions(textArray(item.path("followUpQuestions")));
            question.setTagSuggestions(textArray(item.path("tagSuggestions")));
            question.setCategorySuggestion(item.path("categorySuggestion").asText(null));
            question.setGroupSuggestion(item.path("groupSuggestion").asText(null));
            questions.add(question);
        }
        GenerateQuestionDraftVO vo = new GenerateQuestionDraftVO();
        vo.setBatchId(dto.getBatchId());
        vo.setQuestions(questions);
        return vo;
    }

    private GenerateQuestionRecommendationVO parseQuestionRecommendations(String raw,
                                                                          GenerateQuestionRecommendationDTO dto) {
        JsonNode json = parseJson(raw);
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI question recommendation response must be a JSON object");
        }
        JsonNode questionsNode = json.path("questions");
        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI question recommendation response missing questions array");
        }
        List<QuestionRecommendationItemVO> questions = new java.util.ArrayList<>();
        int limit = dto.getQuestionCount() == null ? 5 : dto.getQuestionCount();
        for (JsonNode item : questionsNode) {
            if (questions.size() >= limit) {
                break;
            }
            QuestionRecommendationItemVO question = new QuestionRecommendationItemVO();
            question.setTitle(requireRecommendationText(item, "title"));
            question.setContent(requireRecommendationText(item, "content"));
            question.setQuestionType(firstText(item.path("questionType").asText(null), "SHORT_ANSWER"));
            question.setDifficulty(firstText(item.path("difficulty").asText(null),
                    dto.getDifficultyPreference(), "MEDIUM"));
            question.setSkillCode(item.path("skillCode").asText(null));
            question.setSkillName(firstText(item.path("skillName").asText(null), item.path("knowledgePoint").asText(null)));
            question.setGapSeverity(firstText(item.path("gapSeverity").asText(null), item.path("severity").asText(null)));
            question.setRecommendReason(requireRecommendationText(item, "recommendReason"));
            question.setAnswerHint(firstText(item.path("answerHint").asText(null), item.path("referenceAnswer").asText(null)));
            question.setEvaluatePoints(firstText(item.path("evaluatePoints").asText(null), item.path("analysis").asText(null)));
            questions.add(question);
        }
        if (questions.isEmpty()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI question recommendation response contains no valid questions");
        }
        GenerateQuestionRecommendationVO vo = new GenerateQuestionRecommendationVO();
        vo.setBatchId(dto.getBatchId());
        vo.setQuestions(questions);
        return vo;
    }

    private String requireText(JsonNode json, String fieldName) {
        String value = json.path(fieldName).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI question generation item missing field: " + fieldName);
        }
        return value;
    }

    private String requireRecommendationText(JsonNode json, String fieldName) {
        String value = json.path(fieldName).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI question recommendation item missing field: " + fieldName);
        }
        return value;
    }

    private String requireLearningText(JsonNode json, String fieldName) {
        String value = textField(json, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI learning plan item missing field: " + fieldName);
        }
        return value;
    }

    private String requireLearningText(JsonNode json, String fieldName, String aliasFieldName) {
        String value = firstText(textField(json, fieldName), textField(json, aliasFieldName));
        if (!StringUtils.hasText(value)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI learning plan item missing field: " + fieldName);
        }
        return value;
    }

    private String requireLearningText(JsonNode json, String fieldName, String... aliasFieldNames) {
        String value = textField(json, fieldName);
        if (!StringUtils.hasText(value) && aliasFieldNames != null) {
            for (String alias : aliasFieldNames) {
                value = textField(json, alias);
                if (StringUtils.hasText(value)) {
                    break;
                }
            }
        }
        if (!StringUtils.hasText(value)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI learning plan item missing field: " + fieldName);
        }
        return value;
    }

    private List<String> textArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText(null);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<Long> longArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Long> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (item.isIntegralNumber()) {
                values.add(item.asLong());
            } else {
                String value = item.asText(null);
                if (StringUtils.hasText(value)) {
                    try {
                        values.add(Long.valueOf(value));
                    } catch (NumberFormatException ignored) {
                        // Ignore non-numeric related question ids from the model.
                    }
                }
            }
        }
        return values;
    }

    private String normalizeTaskType(String taskType) {
        String value = StringUtils.hasText(taskType) ? taskType.trim().toUpperCase() : "KNOWLEDGE_REVIEW";
        if (!List.of("KNOWLEDGE_REVIEW", "CODING_PRACTICE", "PROJECT_REVIEW", "INTERVIEW_PRACTICE",
                "RESUME_IMPROVEMENT").contains(value)) {
            return "KNOWLEDGE_REVIEW";
        }
        return value;
    }

    private String normalizePriority(String priority) {
        String value = StringUtils.hasText(priority) ? priority.trim().toUpperCase() : "MEDIUM";
        if (!List.of("HIGH", "MEDIUM", "LOW").contains(value)) {
            return "MEDIUM";
        }
        return value;
    }

    private int normalizeDuration(Integer durationDays) {
        if (durationDays == null) {
            return 14;
        }
        if (durationDays <= 7) {
            return 7;
        }
        if (durationDays <= 14) {
            return 14;
        }
        return 30;
    }

    private EvaluateAnswerVO parseEvaluate(String raw, EvaluateAnswerDTO dto) {
        JsonNode json;
        try {
            json = parseJson(raw);
        } catch (BusinessException ex) {
            EvaluateAnswerVO fallback = new EvaluateAnswerVO();
            int score = scoreByLength(dto.getAnswerContent());
            fallback.setScore(score);
            fallback.setComment(firstText(extractLabelValue(raw, "comment"),
                    raw,
                    score >= 75 ? "回答覆盖了主要知识点，建议补充项目落地细节。" : "回答偏简略，建议补充原理、边界条件和实践案例。"));
            fallback.setNextAction(decideNextAction(extractLabelValue(raw, "nextAction"), score, dto.getFollowUpCount(), maxFollowUp(dto)));
            fallback.setKnowledgePoints(firstText(dto.getQuestionTitle(), "Java 后端基础"));
            fillFollowUpFromEvaluation(fallback, dto, firstText(
                    extractLabelValue(raw, "followUpQuestion"),
                    extractLabelValue(raw, "questionContent")));
            return fallback;
        }
        EvaluateAnswerVO vo = new EvaluateAnswerVO();
        vo.setScore(clampScore(json.path("score").isNumber() ? json.path("score").asInt() : scoreByLength(dto.getAnswerContent())));
        vo.setComment(firstText(json.path("comment").asText(null), json.path("aiComment").asText(null),
                vo.getScore() >= 75 ? "回答覆盖了主要知识点，建议补充项目落地细节。" : "回答偏简略，建议补充原理、边界条件和实践案例。"));
        vo.setNextAction(decideNextAction(json.path("nextAction").asText(null), vo.getScore(), dto.getFollowUpCount(), maxFollowUp(dto)));
        vo.setKnowledgePoints(firstText(json.path("knowledgePoints").asText(null), json.path("knowledgePoint").asText(null)));
        fillFollowUpFromEvaluation(vo, dto, firstText(json.path("followUpQuestion").asText(null), json.path("questionContent").asText(null)));
        return vo;
    }

    private GenerateFollowUpVO parseFollowUp(String raw, GenerateFollowUpDTO dto) {
        JsonNode json;
        try {
            json = parseJson(raw);
        } catch (BusinessException ex) {
            GenerateFollowUpVO fallback = new GenerateFollowUpVO();
            fallback.setFollowUpQuestion(firstText(extractLabelValue(raw, "followUpQuestion"),
                    extractLabelValue(raw, "questionContent"),
                    raw,
                    buildFallbackFollowUp(dto)));
            fallback.setReason("已整理为可继续练习的追问");
            fallback.setRelatedToOriginalQuestion(!isInvalidFollowUp(fallback.getFollowUpQuestion(), dto));
            fallback.setFollowUpValid(!isInvalidFollowUp(fallback.getFollowUpQuestion(), dto));
            return fallback;
        }
        GenerateFollowUpVO vo = new GenerateFollowUpVO();
        vo.setFollowUpQuestion(firstText(json.path("followUpQuestion").asText(null), json.path("questionContent").asText(null),
                json.path("questionText").asText(null), buildFallbackFollowUp(dto)));
        vo.setReason(json.path("reason").asText(null));
        vo.setRelatedToOriginalQuestion(json.path("relatedToOriginalQuestion").isBoolean()
                ? json.path("relatedToOriginalQuestion").asBoolean()
                : !isInvalidFollowUp(vo.getFollowUpQuestion(), dto));
        vo.setFollowUpValid(!isInvalidFollowUp(vo.getFollowUpQuestion(), dto));
        return vo;
    }

    private GenerateReportVO parseReport(String raw) {
        JsonNode json;
        try {
            json = parseJson(raw);
        } catch (RuntimeException ex) {
            GenerateReportVO fallback = new GenerateReportVO();
            fallback.setReportContent(StringUtils.hasText(raw) ? raw.trim() : null);
            return fallback;
        }
        GenerateReportVO vo = new GenerateReportVO();
        if (json == null || !json.isObject()) {
            vo.setReportContent(jsonOrDefault(json, null));
            return vo;
        }
        JsonNode score = firstNode(json, "totalScore", "overallScore", "score", "finalScore");
        Integer parsedScore = null;
        if (score != null && score.isNumber()) {
            parsedScore = clampScore(score.asInt());
        } else if (score != null && score.isTextual()) {
            parsedScore = parseScore(score.asText(null));
        }
        if (parsedScore != null && parsedScore > 0) {
            vo.setTotalScore(parsedScore);
        }
        vo.setSummary(firstText(jsonText(json, "summary", "overallSummary", "conclusion", "comment", "overview"),
                summarizeReportContent(jsonOrDefault(firstNode(json, "reportContent", "content", "report", "markdown"), null))));
        vo.setStageScores(jsonOrDefault(firstNode(json, "stageScores", "stageReports", "dimensionScores", "scores"), null));
        vo.setWeakPoints(jsonOrDefault(firstNode(json, "weakPoints", "weaknessPoints", "weaknessTags", "knowledgeGaps"), null));
        vo.setStrengths(jsonOrDefault(firstNode(json, "strengths", "advantages", "highlights"), null));
        vo.setWeaknesses(firstText(jsonText(json, "weaknesses", "weaknessSummary", "problemsSummary"),
                jsonOrDefault(firstNode(json, "weaknessPoints", "knowledgeGaps"), null)));
        vo.setMainProblems(jsonOrDefault(firstNode(json, "mainProblems", "problems", "keyProblems", "issues"), null));
        vo.setProjectProblems(jsonOrDefault(firstNode(json, "projectProblems", "projectIssues", "projectWeaknesses"), null));
        vo.setSuggestions(jsonOrDefault(firstNode(json, "suggestions", "advice", "improvementSuggestions"), null));
        vo.setReviewSuggestions(jsonOrDefault(firstNode(json, "reviewSuggestions", "studySuggestions", "learningSuggestions", "nextSteps"), null));
        vo.setRecommendedQuestions(jsonOrDefault(firstNode(json, "recommendedQuestions", "recommendQuestions", "practiceQuestions", "questionRecommendations"), null));
        vo.setQaReview(jsonOrDefault(firstNode(json, "qaReview", "questionReviews", "answerReviews", "qaReviews"), null));
        vo.setReportContent(firstText(jsonText(json, "reportContent", "content", "report", "markdown"),
                vo.getSummary()));
        return vo;
    }

    private JsonNode firstNode(JsonNode json, String... fieldNames) {
        if (json == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode node = json.path(fieldName);
            if (isPresentNode(node)) {
                return node;
            }
            String snakeCase = camelToSnake(fieldName);
            if (!snakeCase.equals(fieldName)) {
                node = json.path(snakeCase);
                if (isPresentNode(node)) {
                    return node;
                }
            }
        }
        return null;
    }

    private boolean isPresentNode(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private String camelToSnake(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String jsonText(JsonNode json, String... fieldNames) {
        JsonNode node = firstNode(json, fieldNames);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText(null) : node.toString();
    }

    private String textField(JsonNode json, String... fieldNames) {
        JsonNode node = firstNode(json, fieldNames);
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asText(null) : null;
    }

    private Integer parseScore(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(value);
        return matcher.find() ? clampScore(Integer.parseInt(matcher.group())) : null;
    }

    private String summarizeReportContent(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replaceAll("\\s+", " ");
        return text.length() <= 160 ? text : text.substring(0, 160);
    }

    private GenerateLearningPlanVO parseLearningPlan(String raw, GenerateLearningPlanDTO dto) {
        JsonNode json = parseJson(raw);
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI learning plan response must be a JSON object");
        }
        JsonNode stagesNode = normalizeLearningStagesNode(json);
        if (stagesNode == null || !stagesNode.isArray() || stagesNode.isEmpty()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI learning plan response missing stages array");
        }
        GenerateLearningPlanVO vo = new GenerateLearningPlanVO();
        vo.setPlanTitle(firstText(textField(json, "planTitle", "title", "name"), defaultLearningPlanTitle(dto)));
        vo.setPlanSummary(firstText(textField(json, "planSummary", "summary", "description"),
                "根据面试报告生成的学习计划"));
        Integer durationDays = readInteger(firstNode(json, "durationDays", "duration", "days"));
        vo.setDurationDays(durationDays == null
                ? normalizeDuration(dto.getExpectedDurationDays())
                : normalizeDuration(durationDays));
        List<GenerateLearningPlanVO.StageVO> stages = new java.util.ArrayList<>();
        for (JsonNode stageNode : stagesNode) {
            GenerateLearningPlanVO.StageVO stage = new GenerateLearningPlanVO.StageVO();
            boolean stageObject = stageNode != null && stageNode.isObject();
            Integer stageNo = stageObject ? readInteger(firstNode(stageNode, "stageNo", "no", "order")) : null;
            stage.setStageNo(stageNo == null ? stages.size() + 1 : stageNo);
            stage.setStageTitle(firstText(
                    stageObject ? textField(stageNode, "stageTitle", "title", "name", "phaseName") : textValue(stageNode),
                    "阶段 " + stage.getStageNo()));
            List<GenerateLearningPlanVO.ItemVO> items = new java.util.ArrayList<>();
            JsonNode itemsNode = stageObject
                    ? firstNode(stageNode, "items", "tasks", "taskItems", "dailyTasks", "planItems")
                    : null;
            if (itemsNode != null && itemsNode.isObject() && !itemsNode.isEmpty()) {
                itemsNode = normalizeLearningItemMap(itemsNode);
            }
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode itemNode : itemsNode) {
                    GenerateLearningPlanVO.ItemVO item = normalizeLearningPlanItem(
                            itemNode, stage.getStageTitle(), stage.getStageNo(), items.size() + 1);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
            if (items.isEmpty()) {
                items.add(fallbackLearningPlanItem(stageObject ? stageNode : null, stage.getStageTitle(),
                        stage.getStageNo(), 1));
            }
            stage.setItems(items);
            stages.add(stage);
        }
        vo.setStages(stages);
        return vo;
    }

    private JsonNode normalizeLearningStagesNode(JsonNode json) {
        JsonNode stagesNode = firstNode(json, "stages", "stageList", "phases", "phaseList");
        if (stagesNode != null && stagesNode.isArray() && !stagesNode.isEmpty()) {
            return stagesNode;
        }
        if (stagesNode != null && stagesNode.isObject() && !stagesNode.isEmpty()) {
            ArrayNode normalizedStages = objectMapper.createArrayNode();
            stagesNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    return;
                }
                ObjectNode stage = value.isObject() ? ((ObjectNode) value).deepCopy() : objectMapper.createObjectNode();
                if (!StringUtils.hasText(textField(stage, "stageTitle", "title", "name", "phaseName"))) {
                    stage.put("stageTitle", entry.getKey());
                }
                if (value.isArray()) {
                    stage.set("items", value);
                } else if (value.isValueNode() && !StringUtils.hasText(textField(stage, "description", "content"))) {
                    stage.put("description", firstText(textValue(value), entry.getKey()));
                }
                normalizedStages.add(stage);
            });
            if (!normalizedStages.isEmpty()) {
                return normalizedStages;
            }
        }
        JsonNode rootItems = firstNode(json, "items", "tasks", "taskItems", "dailyTasks", "planItems");
        if (rootItems != null && rootItems.isObject() && !rootItems.isEmpty()) {
            rootItems = normalizeLearningItemMap(rootItems);
        }
        if (rootItems != null && rootItems.isArray() && !rootItems.isEmpty()) {
            ObjectNode fallbackStage = objectMapper.createObjectNode();
            fallbackStage.put("stageNo", 1);
            fallbackStage.put("stageTitle", firstText(textField(json, "stageTitle", "title", "name"), "基础训练"));
            fallbackStage.set("items", rootItems);
            ArrayNode fallbackStages = objectMapper.createArrayNode();
            fallbackStages.add(fallbackStage);
            return fallbackStages;
        }
        return stagesNode;
    }

    private ArrayNode normalizeLearningItemMap(JsonNode itemsNode) {
        ArrayNode normalizedItems = objectMapper.createArrayNode();
        itemsNode.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isObject()) {
                ObjectNode item = ((ObjectNode) value).deepCopy();
                if (!StringUtils.hasText(textField(item, "taskTitle", "title", "taskName", "name"))) {
                    item.put("taskTitle", entry.getKey());
                }
                normalizedItems.add(item);
                return;
            }
            ObjectNode item = objectMapper.createObjectNode();
            item.put("taskTitle", entry.getKey());
            if (value.isValueNode()) {
                item.put("taskDescription", firstText(textValue(value), entry.getKey()));
            }
            normalizedItems.add(item);
        });
        return normalizedItems;
    }

    private GenerateLearningPlanVO.ItemVO normalizeLearningPlanItem(JsonNode itemNode, String stageTitle,
                                                                    int stageNo, int itemIndex) {
        if (itemNode == null || itemNode.isNull()) {
            return null;
        }
        if (!itemNode.isObject()) {
            String text = textValue(itemNode);
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return fallbackLearningPlanItem(null, firstText(text, stageTitle), stageNo, itemIndex);
        }
        GenerateLearningPlanVO.ItemVO item = new GenerateLearningPlanVO.ItemVO();
        item.setDayOffset(readInteger(firstNode(itemNode, "dayOffset", "day", "dayNo")));
        item.setKnowledgePoint(firstText(textField(itemNode, "knowledgePoint", "skillName", "topic", "knowledge"),
                stageTitle, "Java 后端能力"));
        item.setSkillName(firstText(textField(itemNode, "skillName", "skill", "knowledgePoint"),
                item.getKnowledgePoint()));
        item.setSourceGapId(textField(itemNode, "sourceGapId", "gapId"));
        item.setTaskTitle(firstText(textField(itemNode, "taskTitle", "title", "taskName", "name"),
                item.getKnowledgePoint() + "专项训练"));
        item.setTaskDescription(firstText(textField(itemNode, "taskDescription", "description", "detail", "content"),
                defaultLearningTaskDescription(item.getKnowledgePoint())));
        item.setTaskType(normalizeTaskType(firstText(textField(itemNode, "taskType", "type", "taskCategory"))));
        item.setPriority(normalizePriority(textField(itemNode, "priority")));
        Integer minutes = readInteger(firstNode(itemNode, "estimatedMinutes", "durationMinutes", "minutes"));
        Integer hours = readInteger(firstNode(itemNode, "estimatedHours", "durationHours", "hours"));
        item.setEstimatedHours(hours == null ? Math.max(1, minutes == null ? 1 : (minutes + 59) / 60) : Math.max(1, hours));
        item.setEstimatedMinutes(minutes == null ? item.getEstimatedHours() * 60 : Math.max(1, minutes));
        item.setAcceptance(firstText(textField(itemNode, "acceptance", "acceptanceCriteria", "expectedOutcome"),
                defaultLearningAcceptance(item.getKnowledgePoint())));
        item.setRelatedQuestionIds(longArray(firstNode(itemNode, "relatedQuestionIds", "questionIds")));
        item.setRelatedTags(textArray(firstNode(itemNode, "relatedTags", "tags")));
        item.setResources(textArray(firstNode(itemNode, "resources", "resourceList")));
        if (item.getDayOffset() == null) {
            item.setDayOffset(Math.max(1, stageNo + itemIndex - 1));
        }
        return item;
    }

    private GenerateLearningPlanVO.ItemVO fallbackLearningPlanItem(JsonNode source, String stageTitle,
                                                                   int stageNo, int itemIndex) {
        String knowledgePoint = firstText(
                source == null ? null : textField(source, "knowledgePoint", "skillName", "topic", "name", "title"),
                stageTitle,
                "Java 后端能力");
        GenerateLearningPlanVO.ItemVO item = new GenerateLearningPlanVO.ItemVO();
        item.setDayOffset(Math.max(1, stageNo + itemIndex - 1));
        item.setKnowledgePoint(knowledgePoint);
        item.setSkillName(knowledgePoint);
        item.setSourceGapId(source == null ? null : textField(source, "sourceGapId", "gapId"));
        item.setTaskTitle(firstText(source == null ? null : textField(source, "taskTitle", "title", "taskName", "name"),
                knowledgePoint + "专项训练"));
        item.setTaskDescription(firstText(source == null ? null : textField(source, "taskDescription", "description", "detail", "content"),
                defaultLearningTaskDescription(knowledgePoint)));
        item.setTaskType(normalizeTaskType(source == null ? null : textField(source, "taskType", "type", "taskCategory")));
        item.setPriority(normalizePriority(source == null ? null : textField(source, "priority")));
        Integer minutes = source == null ? null : readInteger(firstNode(source, "estimatedMinutes", "durationMinutes", "minutes"));
        Integer hours = source == null ? null : readInteger(firstNode(source, "estimatedHours", "durationHours", "hours"));
        item.setEstimatedHours(hours == null ? Math.max(1, minutes == null ? 1 : (minutes + 59) / 60) : Math.max(1, hours));
        item.setEstimatedMinutes(minutes == null ? item.getEstimatedHours() * 60 : Math.max(1, minutes));
        item.setAcceptance(firstText(source == null ? null : textField(source, "acceptance", "acceptanceCriteria", "expectedOutcome"),
                defaultLearningAcceptance(knowledgePoint)));
        item.setRelatedQuestionIds(source == null ? List.of() : longArray(firstNode(source, "relatedQuestionIds", "questionIds")));
        item.setRelatedTags(source == null ? List.of(knowledgePoint) : textArray(firstNode(source, "relatedTags", "tags")));
        item.setResources(source == null ? List.of() : textArray(firstNode(source, "resources", "resourceList")));
        return item;
    }

    private String defaultLearningTaskDescription(String knowledgePoint) {
        return "围绕" + firstText(knowledgePoint, "当前短板") + "整理关键概念、项目证据和面试表达。";
    }

    private String defaultLearningAcceptance(String knowledgePoint) {
        return "能结合项目或练习记录讲清" + firstText(knowledgePoint, "该能力点") + "的核心思路。";
    }

    private GenerateLearningPlanVO parseTargetedStudyPlan(String raw, GenerateTargetedStudyPlanDTO dto) {
        GenerateLearningPlanDTO fallback = new GenerateLearningPlanDTO();
        fallback.setLearningPlanId(dto.getLearningPlanId());
        fallback.setUserId(dto.getUserId());
        fallback.setReportId(firstLong(dto.getMatchReportId(), dto.getSkillProfileId()));
        fallback.setTargetPosition(dto.getPlanTitle());
        fallback.setExpectedDurationDays(dto.getAvailableDays());
        GenerateLearningPlanVO vo = parseLearningPlan(raw, fallback);
        vo.setPlanTitle(firstText(vo.getPlanTitle(), dto.getPlanTitle(), defaultTargetedStudyPlanTitle(dto)));
        vo.setDurationDays(dto.getAvailableDays());
        return vo;
    }

    private String parseResumeStructuredJson(String raw) {
        JsonNode json = parseJson(raw);
        validateResumeStructuredJson(json);
        return json.toString();
    }

    private String parseResumeOptimizeJson(String raw, ResumeOptimizeAiRequestDTO dto) {
        JsonNode json = parseJson(raw);
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI resume optimize response must be a JSON object");
        }
        ObjectNode normalized = ((ObjectNode) json).deepCopy();
        markUnsupportedResumeOptimizeSuggestions(normalized, dto);
        return normalized.toString();
    }

    private void markUnsupportedResumeOptimizeSuggestions(ObjectNode root, ResumeOptimizeAiRequestDTO dto) {
        String evidence = normalizeEvidenceText(
                dto == null ? null : toJson(dto.getResume()),
                dto == null ? null : toJson(dto.getProjects()),
                dto == null ? null : dto.getTargetPosition(),
                dto == null ? null : dto.getIndustryDirection(),
                dto == null ? null : dto.getOptimizeFocus());
        JsonNode suggestions = root.path("rewriteSuggestions");
        if (!suggestions.isArray()) {
            return;
        }
        ArrayNode riskWarnings = root.path("riskWarnings").isArray()
                ? (ArrayNode) root.path("riskWarnings")
                : objectMapper.createArrayNode();
        for (JsonNode item : suggestions) {
            if (!(item instanceof ObjectNode suggestion)) {
                continue;
            }
            String after = normalizeEvidenceText(
                    suggestion.path("after").asText(null),
                    suggestion.path("newValue").asText(null),
                    suggestion.path("value").asText(null),
                    suggestion.path("optimized").asText(null),
                    suggestion.path("suggested").asText(null));
            String unsupported = firstUnsupportedEvidenceTerm(after, evidence);
            if (!StringUtils.hasText(unsupported)) {
                continue;
            }
            suggestion.put("fabricationRisk", true);
            suggestion.put("unsupportedFact", unsupported);
            ObjectNode warning = objectMapper.createObjectNode();
            warning.put("type", "UNSUPPORTED_FACT");
            warning.put("description", "优化建议包含输入简历/项目中未提供的事实：" + unsupported);
            warning.put("suggestion", "请把该内容作为待补充材料确认，不要直接写入简历草稿。");
            riskWarnings.add(warning);
        }
        root.set("riskWarnings", riskWarnings);
    }

    private String firstUnsupportedEvidenceTerm(String output, String evidence) {
        if (!StringUtils.hasText(output)) {
            return null;
        }
        for (String term : RESUME_MATCH_FACT_TERMS) {
            if (containsEvidenceTerm(output, term) && !containsEvidenceTerm(evidence, term)) {
                return term;
            }
        }
        Matcher matcher = UNSUPPORTED_NUMERIC_FACT_PATTERN.matcher(output);
        while (matcher.find()) {
            String term = matcher.group().replaceAll("\\s+", "");
            if (StringUtils.hasText(term) && !containsEvidenceTerm(evidence, term)) {
                return term;
            }
        }
        return null;
    }

    private String parseJobDescriptionJson(String raw) {
        JsonNode json = parseJson(raw);
        validateJobDescriptionJson(json);
        return json.toString();
    }

    private String parseResumeJobMatchJson(String raw, AnalyzeResumeJobMatchDTO dto) {
        JsonNode parsed;
        try {
            parsed = parseJson(raw);
        } catch (RuntimeException ex) {
            return fallbackResumeJobMatchJson("匹配报告内容暂时无法整理，请重新生成或补充资料后再用于训练。", ex);
        }
        JsonNode json = normalizeResumeJobMatchJson(parsed);
        try {
            validateResumeJobMatchJson(json, dto);
        } catch (AiProviderException ex) {
            if (isUnsupportedResumeMatchEvidenceError(ex)) {
                throw ex;
            }
            return fallbackResumeJobMatchJson(
                    "匹配报告内容结构不完整，请结合简历和岗位描述明细复核后重新生成。", ex);
        }
        return json.toString();
    }

    private JsonNode normalizeResumeJobMatchJson(JsonNode json) {
        JsonNode rootNode = unwrapResumeMatchRoot(json);
        if (!(rootNode instanceof ObjectNode root)) {
            return rootNode;
        }
        ObjectNode normalized = root.deepCopy();
        ArrayNode schemaWarnings = objectMapper.createArrayNode();
        if (rootNode != json) {
            markResumeMatchSchemaWarning(schemaWarnings, "root", "AI 返回包含外层包装，系统已展开为匹配报告");
        }
        Integer overallScore = readScore(firstNode(normalized, "overallScore", "overall_score",
                "matchScore", "match_score", "matchingScore", "matching_score", "score", "totalScore",
                "total_score", "综合得分", "匹配分", "综合匹配度"));
        if (overallScore == null) {
            overallScore = averageDimensionScore(firstNode(normalized, "dimensionScores", "scores", "scoreDetails",
                    "dimensionScore", "dimension_score", "维度得分", "维度评分"));
        }
        if (overallScore == null) {
            overallScore = 0;
        }
        normalized.put("overallScore", clampScore(overallScore));

        JsonNode rawDimensionScores = firstNode(normalized, "dimensionScores", "scores", "scoreDetails",
                "dimensionScore", "dimension_score", "维度得分", "维度评分");
        ObjectNode dimensionScores = rawDimensionScores != null && rawDimensionScores.isObject()
                ? ((ObjectNode) rawDimensionScores).deepCopy()
                : objectMapper.createObjectNode();
        for (String dimension : List.of("techStack", "projectExperience", "businessFit", "communication")) {
            List<String> scoreFieldNames = new ArrayList<>();
            scoreFieldNames.add(dimension);
            scoreFieldNames.add(dimension + "Score");
            scoreFieldNames.addAll(resumeMatchDimensionAliases(dimension));
            Integer score = readScore(firstNode(dimensionScores, scoreFieldNames.toArray(String[]::new)));
            if (score == null) {
                score = readDimensionScore(rawDimensionScores, dimension);
            }
            if (score == null) {
                score = readScore(firstNode(normalized, scoreFieldNames.toArray(String[]::new)));
            }
            dimensionScores.put(dimension, clampScore(score == null ? overallScore : score));
        }
        normalized.set("dimensionScores", dimensionScores);

        normalizeArrayField(normalized, "strengths", true, "advantages", "highlights", "matchStrengths",
                "matchedPoints", "matchedSkills", "优势", "匹配优势", "亮点");
        normalizeArrayField(normalized, "gaps", true, "weaknesses", "skillGaps", "mismatchPoints",
                "missingSkills", "gapItems", "shortcomings", "差距", "短板", "能力短板", "不足项");
        normalizeArrayField(normalized, "resumeRisks", false, "risks", "riskPoints", "resumeIssues",
                "riskWarnings", "风险", "简历风险");
        normalizeArrayField(normalized, "optimizationSuggestions", false,
                "suggestions", "improvementSuggestions", "resumeOptimizationSuggestions",
                "recommendations", "nextSteps", "优化建议", "改进建议");
        normalizeArrayField(normalized, "recommendedLearningTopics", true,
                "learningTopics", "studyTopics", "recommendedSkills",
                "learningSuggestions", "studyRecommendations", "学习主题", "学习建议");
        normalizeArrayField(normalized, "recommendedInterviewTopics", true,
                "interviewTopics", "practiceTopics", "recommendedQuestions",
                "interviewSuggestions", "interviewFocus", "面试主题", "面试建议", "面试重点");
        normalizeResumeMatchStrengthItems(normalized, schemaWarnings);
        normalizeResumeMatchGapItems(normalized, schemaWarnings);
        if (!StringUtils.hasText(normalized.path("summary").asText(null))) {
            markResumeMatchSchemaWarning(schemaWarnings, "summary", "摘要内容已整理为可展示内容");
            String summary = textField(normalized, "summary", "overallSummary", "matchSummary", "comment",
                    "conclusion", "overallEvaluation", "summaryText", "整体评价", "匹配结论");
            normalized.put("summary", StringUtils.hasText(summary)
                    ? summary
                    : "已整理出部分匹配结果，来源仍需复核，请结合明细确认后再继续训练。");
        }
        if (!schemaWarnings.isEmpty()) {
            normalized.set("schemaWarnings", schemaWarnings);
            if (!StringUtils.hasText(normalized.path("trustStatus").asText(null))) {
                normalized.put("trustStatus", TRUST_PARTIAL);
            }
        } else if (!StringUtils.hasText(normalized.path("trustStatus").asText(null))) {
            normalized.put("trustStatus", TRUST_VERIFIED);
        }
        return normalized;
    }

    private JsonNode unwrapResumeMatchRoot(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return json;
        }
        JsonNode parsedText = parseTextualJsonNode(json);
        if (parsedText != json) {
            return unwrapResumeMatchRoot(parsedText);
        }
        if (json.isArray() && json.size() == 1) {
            return unwrapResumeMatchRoot(json.get(0));
        }
        if (!json.isObject() || looksLikeResumeMatchObject(json)) {
            return json;
        }
        JsonNode wrapper = firstNode(json, "resultJson", "result", "data", "report", "matchReport",
                "resumeJobMatch", "resumeJobMatchReport", "matchResult", "analysisResult", "content");
        if (wrapper == null) {
            return json;
        }
        JsonNode parsedWrapper = parseTextualJsonNode(wrapper);
        JsonNode candidate = parsedWrapper != wrapper ? parsedWrapper : wrapper;
        return candidate == json ? json : unwrapResumeMatchRoot(candidate);
    }

    private JsonNode parseTextualJsonNode(JsonNode node) {
        if (node == null || !node.isTextual() || !StringUtils.hasText(node.asText(null))) {
            return node;
        }
        try {
            return objectMapper.readTree(extractJson(node.asText()));
        } catch (Exception ignored) {
            return node;
        }
    }

    private boolean looksLikeResumeMatchObject(JsonNode json) {
        return json != null && json.isObject()
                && firstNode(json, "overallScore", "overall_score", "matchScore", "match_score",
                "matchingScore", "score", "dimensionScores", "scores", "scoreDetails", "strengths",
                "advantages", "highlights", "gaps", "weaknesses", "skillGaps", "missingSkills",
                "summary", "overallSummary", "matchSummary", "整体评价", "匹配结论") != null;
    }

    private void normalizeArrayField(ObjectNode root, String fieldName, boolean allowTextItem, String... aliases) {
        List<String> fieldNames = new ArrayList<>();
        fieldNames.add(fieldName);
        if (aliases != null) {
            fieldNames.addAll(List.of(aliases));
        }
        JsonNode node = firstNode(root, fieldNames.toArray(String[]::new));
        if (node != null && node.isArray()) {
            root.set(fieldName, node);
            return;
        }
        ArrayNode array = objectMapper.createArrayNode();
        if (node != null && node.isObject()) {
            array.add(node);
        } else if (node != null && allowTextItem && StringUtils.hasText(node.asText(null))) {
            array.add(node.asText());
        }
        root.set(fieldName, array);
    }

    private void normalizeResumeMatchStrengthItems(ObjectNode root, ArrayNode schemaWarnings) {
        JsonNode strengths = root.path("strengths");
        ArrayNode normalized = objectMapper.createArrayNode();
        if (strengths.isArray()) {
            int index = 0;
            for (JsonNode item : strengths) {
                ObjectNode normalizedItem = normalizeResumeMatchStrengthItem(item, schemaWarnings, index);
                if (normalizedItem != null) {
                    normalized.add(normalizedItem);
                }
                index++;
            }
        }
        root.set("strengths", normalized);
    }

    private ObjectNode normalizeResumeMatchStrengthItem(JsonNode item, ArrayNode schemaWarnings, int index) {
        String fieldPath = "strengths[" + index + "]";
        ObjectNode normalized = item instanceof ObjectNode objectItem
                ? objectItem.deepCopy()
                : objectMapper.createObjectNode();
        String title = item != null && item.isObject()
                ? textField(item, "title", "name", "point", "summary", "advantage", "strength")
                : item == null ? null : item.asText(null);
        String evidence = item != null && item.isObject()
                ? textField(item, "evidence", "reason", "basis", "source", "detail", "description")
                : null;
        if (!StringUtils.hasText(title) && !StringUtils.hasText(evidence)) {
            markResumeMatchSchemaWarning(schemaWarnings, fieldPath, "空优势项已跳过");
            return null;
        }
        if (!StringUtils.hasText(title)) {
            markResumeMatchSchemaWarning(schemaWarnings, fieldPath + ".title", "缺少标题，已补充默认标题");
            title = "待复核匹配优势";
        }
        if (!StringUtils.hasText(evidence)) {
            markResumeMatchSchemaWarning(schemaWarnings, fieldPath + ".evidence", "缺少来源说明，已标记后续确认");
            evidence = "AI 返回该优势但缺少结构化来源说明，已标记为待人工复核。";
        }
        normalized.put("title", title);
        normalized.put("evidence", evidence);
        JsonNode relatedSkills = firstNode(normalized, "relatedSkills", "skills", "skillNames", "skill");
        normalized.set("relatedSkills", normalizeResumeMatchTextArray(relatedSkills));
        return normalized;
    }

    private void normalizeResumeMatchGapItems(ObjectNode root, ArrayNode schemaWarnings) {
        JsonNode gaps = root.path("gaps");
        ArrayNode normalized = objectMapper.createArrayNode();
        if (gaps.isArray()) {
            int index = 0;
            for (JsonNode item : gaps) {
                ObjectNode normalizedItem = normalizeResumeMatchGapItem(item, schemaWarnings, index);
                if (normalizedItem != null) {
                    normalized.add(normalizedItem);
                }
                index++;
            }
        }
        root.set("gaps", normalized);
    }

    private ObjectNode normalizeResumeMatchGapItem(JsonNode item, ArrayNode schemaWarnings, int index) {
        String fieldPath = "gaps[" + index + "]";
        ObjectNode normalized = item instanceof ObjectNode objectItem
                ? objectItem.deepCopy()
                : objectMapper.createObjectNode();
        String textItem = item != null && item.isTextual() ? item.asText(null) : null;
        String skillName = item != null && item.isObject()
                ? textField(item, "skillName", "skill", "knowledgePoint", "topic", "name")
                : null;
        String description = item != null && item.isObject()
                ? textField(item, "description", "gapDescription", "weakness", "problem", "summary")
                : textItem;
        String evidence = item != null && item.isObject()
                ? textField(item, "evidence", "reason", "basis", "source", "detail")
                : null;
        if (!StringUtils.hasText(skillName)
                && !StringUtils.hasText(description)
                && !StringUtils.hasText(evidence)) {
            markResumeMatchSchemaWarning(schemaWarnings, fieldPath, "空短板项已跳过");
            return null;
        }
        if (!StringUtils.hasText(skillName)) {
            markResumeMatchSchemaWarning(schemaWarnings, fieldPath + ".skillName", "缺少技能名称，已补充默认名称");
            skillName = "待确认技能";
        }
        if (!StringUtils.hasText(description)) {
            markResumeMatchSchemaWarning(schemaWarnings, fieldPath + ".description", "缺少描述，已补充默认描述");
            description = "AI 返回该差距但缺少结构化描述，已标记为待人工复核。";
        }
        if (!StringUtils.hasText(evidence)) {
            markResumeMatchSchemaWarning(schemaWarnings, fieldPath + ".evidence", "缺少来源说明，已标记后续确认");
            evidence = "AI 返回该差距但缺少结构化来源说明，已标记为待人工复核。";
        }
        normalized.put("skillName", skillName);
        normalized.put("description", description);
        normalized.put("evidence", evidence);
        normalized.put("severity", normalizeResumeMatchSeverity(
                textField(normalized, "severity", "level", "priority", "gapSeverity")));
        JsonNode actions = firstNode(normalized, "recommendedActions", "actions", "suggestions", "nextActions");
        normalized.set("recommendedActions", normalizeResumeMatchTextArray(actions));
        return normalized;
    }

    private String normalizeResumeMatchSeverity(String value) {
        if (!StringUtils.hasText(value)) {
            return "MEDIUM";
        }
        String upper = value.trim().toUpperCase();
        if (upper.contains("HIGH") || upper.contains("P0") || upper.contains("P1")) {
            return "HIGH";
        }
        if (upper.contains("LOW")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private ArrayNode normalizeResumeMatchTextArray(JsonNode node) {
        ArrayNode array = objectMapper.createArrayNode();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return array;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item == null || item.isNull()
                        ? null
                        : item.isValueNode() ? item.asText(null) : item.toString();
                if (StringUtils.hasText(text)) {
                    array.add(text);
                }
            }
            return array;
        }
        String text = node.isValueNode() ? node.asText(null) : node.toString();
        if (StringUtils.hasText(text)) {
            array.add(text);
        }
        return array;
    }

    private void markResumeMatchSchemaWarning(ArrayNode warnings, String fieldPath, String message) {
        if (warnings == null) {
            return;
        }
        ObjectNode warning = objectMapper.createObjectNode();
        warning.put("field", fieldPath);
        warning.put("message", message);
        warnings.add(warning);
    }

    private String fallbackResumeJobMatchJson(String summary, RuntimeException ex) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("overallScore", 0);
        ObjectNode dimensionScores = objectMapper.createObjectNode();
        for (String dimension : List.of("techStack", "projectExperience", "businessFit", "communication")) {
            dimensionScores.put(dimension, 0);
        }
        root.set("dimensionScores", dimensionScores);
        root.set("strengths", objectMapper.createArrayNode());
        ArrayNode gaps = objectMapper.createArrayNode();
        ObjectNode gap = objectMapper.createObjectNode();
        gap.put("skillName", "待复核匹配结论");
        gap.put("severity", "HIGH");
        gap.put("description", firstText(summary, "匹配报告资料来源不完整，请重新生成或补齐资料后复核。"));
        gap.put("evidence", "AI 输出未通过结构化校验，系统已将异常内容标记为待复核。");
        ArrayNode actions = objectMapper.createArrayNode();
        actions.add("检查简历项目经历、目标岗位分析结果后重新生成匹配报告。");
        actions.add("在报告详情查看生成记录，确认是否为内容结构异常或资料来源不完整。");
        gap.set("recommendedActions", actions);
        gaps.add(gap);
        root.set("gaps", gaps);
        root.set("resumeRisks", objectMapper.createArrayNode());
        root.set("optimizationSuggestions", objectMapper.createArrayNode()
                .add("当前资料来源不完整，建议先补充简历项目经历、岗位描述和关键技能后重新生成。"));
        root.set("recommendedLearningTopics", objectMapper.createArrayNode()
                .add("先练习目标岗位核心 Java 后端基础与项目表达。"));
        root.set("recommendedInterviewTopics", objectMapper.createArrayNode()
                .add("围绕当前简历项目准备 1 场基础追问面试。"));
        root.put("summary", firstText(summary, "匹配报告资料来源不完整，请重新生成或补齐资料后复核。"));
        root.put("trustStatus", "FALLBACK");
        root.put("fallback", true);
        ArrayNode warnings = objectMapper.createArrayNode();
        markResumeMatchSchemaWarning(warnings, "rawResponse", firstText(ex == null ? null : ex.getMessage(), "匹配报告内容暂时不可解析，已标记为来源待复核"));
        root.set("schemaWarnings", warnings);
        return root.toString();
    }

    private boolean isUnsupportedResumeMatchEvidenceError(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("unsupported fact") || lower.contains("cannot be based on missing evidence");
    }

    private List<String> resumeMatchDimensionAliases(String dimension) {
        if ("techStack".equals(dimension)) {
            return List.of("tech_stack", "techStackScore", "technicalSkills", "technical_skills",
                    "techSkills", "tech_skills", "technology", "technologyScore", "technicalScore",
                    "skillScore", "skillsScore", "技术栈", "技术匹配", "技术能力");
        }
        if ("projectExperience".equals(dimension)) {
            return List.of("project_experience", "projectExperienceScore", "project", "projects",
                    "projectScore", "project_score", "experience", "experienceScore", "experience_score",
                    "项目经验", "项目经历", "项目匹配");
        }
        if ("businessFit".equals(dimension)) {
            return List.of("business_fit", "businessFitScore", "business", "domainFit", "domain_fit",
                    "jobFit", "job_fit", "positionFit", "position_fit", "businessScore",
                    "业务契合", "岗位契合", "业务匹配");
        }
        if ("communication".equals(dimension)) {
            return List.of("communication_score", "communicationSkill", "communication_skill",
                    "expression", "presentation", "communicationScore", "沟通表达", "表达能力", "沟通能力");
        }
        return List.of();
    }

    private Integer averageDimensionScore(JsonNode dimensionScores) {
        if (dimensionScores == null) {
            return null;
        }
        int total = 0;
        int count = 0;
        for (String dimension : List.of("techStack", "projectExperience", "businessFit", "communication")) {
            List<String> scoreFieldNames = new ArrayList<>();
            scoreFieldNames.add(dimension);
            scoreFieldNames.add(dimension + "Score");
            scoreFieldNames.addAll(resumeMatchDimensionAliases(dimension));
            Integer score = dimensionScores.isObject()
                    ? readScore(firstNode(dimensionScores, scoreFieldNames.toArray(String[]::new)))
                    : readDimensionScore(dimensionScores, dimension);
            if (score != null) {
                total += clampScore(score);
                count++;
            }
        }
        return count == 0 ? null : Math.round((float) total / count);
    }

    private Integer readDimensionScore(JsonNode dimensionScores, String dimension) {
        if (dimensionScores == null || !dimensionScores.isArray()) {
            return null;
        }
        for (JsonNode item : dimensionScores) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String name = textField(item, "dimension", "name", "label", "type", "category", "key", "skill");
            if (!matchesResumeMatchDimension(name, dimension)) {
                continue;
            }
            Integer score = readScore(firstNode(item, "score", "value", "matchScore", "match_score",
                    "overallScore", "overall_score", "points", "得分", "分数"));
            if (score != null) {
                return score;
            }
        }
        return null;
    }

    private boolean matchesResumeMatchDimension(String value, String dimension) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(dimension)) {
            return false;
        }
        String normalizedValue = normalizeResumeMatchDimensionName(value);
        List<String> candidates = new ArrayList<>();
        candidates.add(dimension);
        candidates.add(dimension + "Score");
        candidates.addAll(resumeMatchDimensionAliases(dimension));
        for (String candidate : candidates) {
            if (normalizedValue.equals(normalizeResumeMatchDimensionName(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeResumeMatchDimensionName(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[\\s_\\-:/：]+", "");
    }

    private Integer readScore(JsonNode node) {
        Integer score = readInteger(node);
        if (score != null) {
            return score;
        }
        if (node != null && node.isNumber()) {
            return (int) Math.round(node.asDouble());
        }
        if (node != null && node.isTextual()) {
            Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(node.asText());
            if (matcher.find()) {
                try {
                    return (int) Math.round(Double.parseDouble(matcher.group()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        if (node != null && node.isObject()) {
            for (String field : List.of("score", "value", "totalScore", "matchScore")) {
                score = readScore(node.path(field));
                if (score != null) {
                    return score;
                }
            }
        }
        return null;
    }

    private String parseSkillGapAnalyzeJson(String raw) {
        JsonNode json = parseJson(raw);
        validateSkillGapAnalyzeJson(json);
        return json.toString();
    }

    private void validateResumeStructuredJson(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI resume parse response must be a JSON object");
        }
        requireJsonField(json, "basicInfo");
        requireJsonField(json, "targetPosition");
        requireJsonField(json, "skills");
        requireJsonField(json, "workExperiences");
        requireJsonField(json, "projectExperiences");
        requireJsonField(json, "educationExperiences");
        JsonNode projects = json.path("projectExperiences");
        if (projects.isArray()) {
            for (JsonNode project : projects) {
                if (project != null && project.isObject()) {
                    requireJsonField(project, "techStack");
                    requireJsonField(project, "responsibilities");
                    requireJsonField(project, "technicalDifficulties");
                    requireJsonField(project, "achievements");
                }
            }
        }
    }

    private void validateJobDescriptionJson(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "岗位分析结果暂时无法整理");
        }
        requireJobDescriptionField(json, "responsibilities");
        requireJobDescriptionField(json, "requiredSkills");
        requireJobDescriptionField(json, "summary");
    }

    private void validateResumeJobMatchJson(JsonNode json, AnalyzeResumeJobMatchDTO dto) {
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume job match response must be a JSON object");
        }
        requireResumeJobMatchField(json, "overallScore");
        requireResumeJobMatchField(json, "dimensionScores");
        requireResumeJobMatchField(json, "strengths");
        requireResumeJobMatchField(json, "gaps");
        requireResumeJobMatchField(json, "resumeRisks");
        requireResumeJobMatchField(json, "optimizationSuggestions");
        requireResumeJobMatchField(json, "recommendedLearningTopics");
        requireResumeJobMatchField(json, "recommendedInterviewTopics");
        requireResumeJobMatchField(json, "summary");
        requireResumeJobMatchScore(json, "overallScore");
        JsonNode dimensionScores = requireResumeJobMatchObject(json, "dimensionScores");
        for (String dimension : List.of("techStack", "projectExperience", "businessFit", "communication")) {
            requireResumeJobMatchScore(dimensionScores, dimension);
        }
        validateResumeJobMatchStrengths(requireResumeJobMatchArray(json, "strengths"));
        validateResumeJobMatchGaps(requireResumeJobMatchArray(json, "gaps"));
        requireResumeJobMatchArray(json, "resumeRisks");
        requireResumeJobMatchArray(json, "optimizationSuggestions");
        requireResumeJobMatchArray(json, "recommendedLearningTopics");
        requireResumeJobMatchArray(json, "recommendedInterviewTopics");
        requireResumeJobMatchText(json, "summary");
        validateResumeJobMatchEvidence(json, dto);
    }

    private void validateSkillGapAnalyzeJson(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI skill gap analyze response must be a JSON object");
        }
        requireSkillGapAnalyzeField(json, "profileSummary");
        requireSkillGapAnalyzeField(json, "overallLevel");
        requireSkillGapAnalyzeField(json, "overallScore");
        requireSkillGapAnalyzeField(json, "skillGaps");
        JsonNode gaps = json.path("skillGaps");
        if (!gaps.isArray() || gaps.isEmpty()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI skill gap analyze response must contain skillGaps");
        }
    }

    private void requireJobDescriptionField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "岗位分析结果缺少必要字段：" + fieldName);
        }
    }

    private void requireResumeJobMatchField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume job match response missing field: " + fieldName);
        }
    }

    private JsonNode requireResumeJobMatchObject(JsonNode json, String fieldName) {
        JsonNode node = json.path(fieldName);
        if (!node.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume job match response field must be an object: " + fieldName);
        }
        return node;
    }

    private JsonNode requireResumeJobMatchArray(JsonNode json, String fieldName) {
        JsonNode node = json.path(fieldName);
        if (!node.isArray()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume job match response field must be an array: " + fieldName);
        }
        return node;
    }

    private void requireResumeJobMatchScore(JsonNode json, String fieldName) {
        Integer score = readScore(json.path(fieldName));
        if (score == null || score < 0 || score > 100) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume job match response score must be 0-100: " + fieldName);
        }
    }

    private void requireResumeJobMatchText(JsonNode json, String fieldName) {
        if (!StringUtils.hasText(json.path(fieldName).asText(null))) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume job match response text field is empty: " + fieldName);
        }
    }

    private void validateResumeJobMatchStrengths(JsonNode strengths) {
        for (JsonNode item : strengths) {
            if (item.isObject()) {
                requireResumeJobMatchText(item, "evidence");
            }
        }
    }

    private void validateResumeJobMatchGaps(JsonNode gaps) {
        for (JsonNode item : gaps) {
            if (item.isObject()) {
                requireResumeJobMatchText(item, "skillName");
                requireResumeJobMatchText(item, "description");
                requireResumeJobMatchText(item, "evidence");
            }
        }
    }

    private void validateResumeJobMatchEvidence(JsonNode json, AnalyzeResumeJobMatchDTO dto) {
        String resumeEvidence = normalizeEvidenceText(
                dto == null ? null : dto.getResumeAnalysisJson(),
                dto == null ? null : dto.getResumeSnapshotJson());
        String allEvidence = normalizeEvidenceText(
                dto == null ? null : dto.getResumeAnalysisJson(),
                dto == null ? null : dto.getResumeSnapshotJson(),
                dto == null ? null : dto.getJobDescriptionAnalysisJson(),
                dto == null ? null : dto.getTargetJobJson());
        String output = normalizeEvidenceText(json.toString());
        for (String term : RESUME_MATCH_FACT_TERMS) {
            if (containsEvidenceTerm(output, term) && !containsEvidenceTerm(allEvidence, term)) {
                throw new AiProviderException(AiFailureType.PARSE_ERROR,
                        "AI resume job match response contains unsupported fact: " + term);
            }
        }

        JsonNode strengths = json.path("strengths");
        if (strengths.isArray()) {
            for (JsonNode item : strengths) {
                validateStrengthEvidence(item, resumeEvidence);
            }
        }
    }

    private void validateStrengthEvidence(JsonNode item, String resumeEvidence) {
        if (item == null || !item.isObject()) {
            return;
        }
        String evidence = item.path("evidence").asText(null);
        if (containsAny(evidence, "未提供", "没有直接证据", "无直接证据", "不足")) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume job match strength cannot be based on missing evidence");
        }
        String strengthText = normalizeEvidenceText(item.toString());
        for (String term : RESUME_MATCH_FACT_TERMS) {
            if (containsEvidenceTerm(strengthText, term) && !containsEvidenceTerm(resumeEvidence, term)) {
                throw new AiProviderException(AiFailureType.PARSE_ERROR,
                        "AI resume job match strength contains unsupported resume fact: " + term);
            }
        }
    }

    private String normalizeEvidenceText(String... values) {
        if (values == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                builder.append(value).append('\n');
            }
        }
        return builder.toString().toLowerCase().replaceAll("\\s+", "");
    }

    private boolean containsEvidenceTerm(String text, String term) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(term)) {
            return false;
        }
        return text.contains(term.toLowerCase().replaceAll("\\s+", ""));
    }

    private Integer readInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private void requireSkillGapAnalyzeField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI skill gap analyze response missing field: " + fieldName);
        }
    }

    private void requireJsonField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI resume parse response missing field: " + fieldName);
        }
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(extractJson(raw));
        } catch (Exception ex) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI response is not valid JSON", null, ex);
        }
    }

    private String extractJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "智能生成结果为空，请稍后重试");
        }
        String text = raw.trim();
        int codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int firstLineEnd = text.indexOf('\n', codeStart);
            int codeEnd = text.lastIndexOf("```");
            if (firstLineEnd >= 0 && codeEnd > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, codeEnd).trim();
            }
        }
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }
        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1);
        }
        return text;
    }

    private GenerateInterviewQuestionVO mockQuestion(GenerateInterviewQuestionDTO dto, String scene) {
        GenerateInterviewQuestionVO vo = new GenerateInterviewQuestionVO();
        String projectAwareContent = mockQuestionContent(dto, scene);
        if (StringUtils.hasText(projectAwareContent)) {
            vo.setQuestionContent(projectAwareContent);
            vo.setQuestionText(projectAwareContent);
            vo.setScene(scene);
            return vo;
        }
        String content = "请结合" + firstText(dto.getQuestionTitle(), dto.getQuestionContent(), "当前 Java 后端主题")
                + "说明核心原理、适用场景和生产实践注意点。";
        vo.setQuestionContent(content);
        vo.setQuestionText(content);
        vo.setScene(scene);
        return vo;
    }

    private GenerateQuestionDraftVO mockQuestionDrafts(GenerateQuestionDraftDTO dto) {
        int count = dto.getCount() == null ? 5 : dto.getCount();
        String topic = firstText(dto.getKnowledgePoint(), dto.getTechnologyStack(), "Java 后端");
        String targetPosition = firstText(dto.getTargetPosition(), "Java 后端开发工程师");
        String difficulty = firstText(dto.getDifficulty(), "MEDIUM");
        String questionType = firstText(dto.getQuestionType(), "SHORT_ANSWER");
        String[] titleTemplates = {
                "%s 的核心原理和适用场景是什么？",
                "%s 在生产环境中常见的失效场景有哪些？",
                "如何排查 %s 相关的线上问题？",
                "%s 的关键参数或边界条件如何设计？",
                "请结合项目说明 %s 的工程取舍",
                "%s 与相邻技术方案应该如何对比？",
                "如何验证 %s 方案是否达到预期效果？",
                "%s 在高并发场景下要注意什么？"
        };
        List<QuestionDraftItemVO> questions = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            QuestionDraftItemVO item = new QuestionDraftItemVO();
            String title = String.format(titleTemplates[(i - 1) % titleTemplates.length], topic);
            if (i > titleTemplates.length) {
                title = title + "（场景 " + i + "）";
            }
            item.setTitle(title);
            item.setContent("面向" + targetPosition + "，请围绕“" + title + "”说明核心原理、典型生产场景、边界问题和工程取舍。");
            item.setReferenceAnswer(topic + " 需要结合具体问题，从基本原理、关键机制、常见异常场景、可观测性和生产取舍几个角度回答。");
            item.setAnalysis("优秀回答应覆盖该机制为什么存在、如何工作、什么时候会失效，以及在真实 Java 后端系统中如何验证和排查。");
            item.setDifficulty(difficulty);
            item.setQuestionType(questionType);
            item.setFollowUpQuestions(Boolean.TRUE.equals(dto.getGenerateFollowUps())
                    ? List.of("你会如何在线上验证这个机制是否按预期工作？", "这个机制常见的失效场景有哪些？")
                    : List.of());
            item.setTagSuggestions(Boolean.TRUE.equals(dto.getGenerateTagSuggestions())
                    ? List.of(topic, "Java", "后端")
                    : List.of());
            item.setCategorySuggestion(Boolean.TRUE.equals(dto.getGenerateCategorySuggestion())
                    ? inferQuestionCategorySuggestion(topic)
                    : null);
            item.setGroupSuggestion(inferQuestionGroupSuggestion(topic));
            questions.add(item);
        }
        GenerateQuestionDraftVO vo = new GenerateQuestionDraftVO();
        vo.setBatchId(dto.getBatchId());
        vo.setQuestions(questions);
        return vo;
    }

    private String inferQuestionCategorySuggestion(String topic) {
        String text = topic == null ? "" : topic.toLowerCase();
        if (text.contains("mysql") || text.contains("索引") || text.contains("sql")) {
            return "MySQL";
        }
        if (text.contains("redis") || text.contains("缓存")) {
            return "Redis";
        }
        if (text.contains("jvm") || text.contains("gc") || text.contains("垃圾回收")) {
            return "JVM";
        }
        if (text.contains("并发") || text.contains("多线程") || text.contains("juc") || text.contains("线程")) {
            return "并发";
        }
        if (text.contains("hashmap") || text.contains("collection") || text.contains("集合")) {
            return "集合";
        }
        if (text.contains("spring")) {
            return "Spring Boot";
        }
        return "Java基础";
    }

    private String inferQuestionGroupSuggestion(String topic) {
        String text = topic == null ? "" : topic.toLowerCase();
        if (text.contains("hashmap")) {
            return "HashMap";
        }
        if (text.contains("jvm") || text.contains("gc") || text.contains("垃圾回收")) {
            return "JVM GC";
        }
        if (text.contains("线程池") || text.contains("threadpool")) {
            return "线程池";
        }
        if (text.contains("并发") || text.contains("多线程") || text.contains("juc") || text.contains("线程")) {
            return "多线程基础";
        }
        if (text.contains("mysql") || text.contains("索引")) {
            return "MySQL索引";
        }
        if (text.contains("redis") || text.contains("缓存")) {
            return "Redis缓存一致性";
        }
        return topic;
    }

    private GenerateQuestionRecommendationVO mockQuestionRecommendations(GenerateQuestionRecommendationDTO dto) {
        int count = dto.getQuestionCount() == null ? 5 : dto.getQuestionCount();
        String difficulty = firstText(dto.getDifficultyPreference(), "MEDIUM");
        List<String> skills = extractSkillNames(dto.getSkillGapsJson());
        if (skills.isEmpty()) {
            skills = List.of("Redis", "Spring Boot", "MySQL");
        }
        List<QuestionRecommendationItemVO> questions = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String skill = skills.get((i - 1) % skills.size());
            QuestionRecommendationItemVO item = new QuestionRecommendationItemVO();
            item.setTitle(skill + " targeted gap practice question " + i);
            item.setContent("Explain a production scenario for " + skill
                    + ", including core mechanism, failure cases, troubleshooting, and tradeoffs.");
            item.setQuestionType("SHORT_ANSWER");
            item.setDifficulty(difficulty);
            item.setSkillName(skill);
            item.setGapSeverity("HIGH");
            item.setRecommendReason("Recommended because this skill is marked as a target-job gap.");
            item.setAnswerHint("Cover principle, scenario, boundary condition, and one project example.");
            item.setEvaluatePoints("Mechanism accuracy; production scenario; risk handling; project evidence.");
            questions.add(item);
        }
        GenerateQuestionRecommendationVO vo = new GenerateQuestionRecommendationVO();
        vo.setBatchId(dto.getBatchId());
        vo.setQuestions(questions);
        return vo;
    }

    private List<String> extractSkillNames(String skillGapsJson) {
        List<String> skills = new java.util.ArrayList<>();
        for (JsonNode gap : readArrayNodes(skillGapsJson)) {
            String skill = firstText(gap.path("skillName").asText(null), gap.path("name").asText(null));
            if (StringUtils.hasText(skill) && !skills.contains(skill)) {
                skills.add(skill);
            }
        }
        return skills;
    }

    private EvaluateAnswerVO mockEvaluate(EvaluateAnswerDTO dto) {
        int score = scoreByLength(dto.getAnswerContent());
        EvaluateAnswerVO vo = new EvaluateAnswerVO();
        vo.setScore(score);
        if (applyProjectAwareMockEvaluation(vo, dto, score)) {
            return vo;
        }
        vo.setComment(score >= 75 ? "回答覆盖了主要知识点，建议补充项目落地细节。" : "回答偏简略，建议补充原理、边界条件和实践案例。");
        vo.setNextAction(score < 75 && safeInt(dto.getFollowUpCount()) < maxFollowUp(dto) ? "FOLLOW_UP" : "NEXT_QUESTION");
        vo.setKnowledgePoints(firstText(dto.getQuestionTitle(), "Java 后端基础"));
        fillFollowUpFromEvaluation(vo, dto, null);
        return vo;
    }

    private GenerateFollowUpVO mockFollowUp(GenerateFollowUpDTO dto) {
        GenerateFollowUpVO vo = new GenerateFollowUpVO();
        vo.setFollowUpQuestion(buildFallbackFollowUp(dto));
        vo.setReason("本地模拟追问，用于管理端测试");
        vo.setRelatedToOriginalQuestion(true);
        vo.setFollowUpValid(true);
        return vo;
    }

    private GenerateReportVO mockReport(GenerateReportDTO dto) {
        GenerateReportVO vo = new GenerateReportVO();
        vo.setTotalScore(null);
        if (applyProjectAwareMockReport(vo)) {
            applyIndustryAwareMockReport(vo, dto);
            return vo;
        }
        vo.setSummary("本场模拟面试已生成基础参考报告，但未获得可信 AI 评分。请继续答题或重新生成报告后再查看总分。");
        vo.setStageScores("{}");
        vo.setWeakPoints("[\"源码细节\",\"执行计划\",\"缓存一致性边界\"]");
        vo.setStrengths("[\"能围绕 Java 后端常见题目给出基本结论\",\"能结合 Spring、MySQL、Redis 说明常见处理思路\"]");
        vo.setWeaknesses("部分回答停留在结论层，对源码细节、执行计划字段、缓存一致性边界和线上排查步骤展开不足。");
        vo.setMainProblems("[\"源码细节展开不足\",\"线上排查步骤不够完整\"]");
        vo.setProjectProblems("[\"项目指标量化不足\",\"优化前后对比不足\"]");
        vo.setSuggestions("[\"复盘集合、并发、事务、索引和缓存高频题\",\"准备 2-3 个带指标的项目优化案例\"]");
        vo.setReviewSuggestions(vo.getSuggestions());
        vo.setRecommendedQuestions("[]");
        vo.setQaReview("[]");
        vo.setReportContent(vo.getSummary());
        applyIndustryAwareMockReport(vo, dto);
        return vo;
    }

    private GenerateReportVO mockReport() {
        return mockReport(null);
    }

    private GenerateLearningPlanVO mockLearningPlan(GenerateLearningPlanDTO dto) {
        GenerateLearningPlanVO vo = new GenerateLearningPlanVO();
        int duration = normalizeDuration(dto == null ? null : dto.getExpectedDurationDays());
        vo.setPlanTitle(defaultLearningPlanTitle(dto));
        vo.setPlanSummary("基于面试短板、简历信号和目标岗位生成的 " + duration + " 天基础训练计划。");
        vo.setDurationDays(duration);

        GenerateLearningPlanVO.ItemVO foundations = learningItem("Java 集合与核心语法",
                "复习 HashMap、ArrayList 和常见集合权衡",
                "总结核心原理、边界条件和可用于面试表达的例子。",
                "KNOWLEDGE_REVIEW", "HIGH", 2, List.of("Java", "Collections"));
        GenerateLearningPlanVO.ItemVO concurrency = learningItem("Java 并发",
                "练习线程池与锁相关场景题",
                "准备覆盖核心参数、拒绝策略、监控指标和生产风险的回答。",
                "INTERVIEW_PRACTICE", "HIGH", 2, List.of("Concurrency", "ThreadPool"));
        GenerateLearningPlanVO.StageVO stageOne = learningStage(1, "短板修复", List.of(foundations, concurrency));

        GenerateLearningPlanVO.ItemVO project = learningItem("项目复盘",
                "打磨一个有真实结果支撑的项目故事",
                "按背景、职责、技术难点、取舍和结果重新组织表达。",
                "PROJECT_REVIEW", "MEDIUM", 2, List.of("Project", "Resume"));
        GenerateLearningPlanVO.ItemVO database = learningItem("数据库与缓存",
                "复习 MySQL 索引和 Redis 一致性场景",
                "准备索引失效、慢查询排查、Cache Aside 和一致性边界的回答。",
                "CODING_PRACTICE", "MEDIUM", 2, List.of("MySQL", "Redis"));
        GenerateLearningPlanVO.StageVO stageTwo = learningStage(2, "场景巩固", List.of(project, database));

        vo.setStages(List.of(stageOne, stageTwo));
        return vo;
    }

    private GenerateLearningPlanVO mockTargetedStudyPlan(GenerateTargetedStudyPlanDTO dto) {
        GenerateLearningPlanVO vo = new GenerateLearningPlanVO();
        int duration = dto == null || dto.getAvailableDays() == null ? 14 : dto.getAvailableDays();
        int minutes = dto == null || dto.getDailyMinutes() == null ? 60 : dto.getDailyMinutes();
        vo.setPlanTitle(defaultTargetedStudyPlanTitle(dto));
        vo.setPlanSummary("基于当前技能短板生成的 " + duration + " 天针对性训练计划。");
        vo.setDurationDays(duration);

        List<JsonNode> gaps = readArrayNodes(dto == null ? null : dto.getSkillGapsJson());
        if (gaps.isEmpty()) {
            GenerateLearningPlanVO.ItemVO item = learningItem("Redis",
                    "复习 Redis 缓存穿透、击穿和雪崩",
                    "总结成因、防护策略和可结合项目表达的例子。",
                    "KNOWLEDGE_REVIEW", "HIGH", Math.max(1, minutes / 60), List.of("Redis"));
            item.setDayOffset(1);
            item.setSkillName("Redis");
            item.setSourceGapId(null);
            item.setEstimatedMinutes(minutes);
            item.setAcceptance("Explain at least two cache protection strategies with project examples.");
            vo.setStages(List.of(learningStage(1, "针对性短板修复", List.of(item))));
            return vo;
        }

        List<GenerateLearningPlanVO.ItemVO> items = new java.util.ArrayList<>();
        int day = 1;
        for (JsonNode gap : gaps.stream().limit(5).toList()) {
            String skillName = firstText(gap.path("skillName").asText(null), "Java 后端能力");
            GenerateLearningPlanVO.ItemVO item = learningItem(skillName,
                    "强化 " + skillName + " 面试表达",
                    firstText(gap.path("gapDescription").asText(null),
                            "复习薄弱概念，总结典型场景，并准备项目证据。"),
                    "KNOWLEDGE_REVIEW",
                    firstText(gap.path("severity").asText(null), "HIGH"),
                    Math.max(1, minutes / 60),
                    List.of(skillName));
            item.setDayOffset(day++);
            item.setSkillName(skillName);
            item.setSourceGapId(gap.path("id").asText(null));
            item.setEstimatedMinutes(minutes);
            item.setAcceptance("能结合一个真实项目或排障案例讲清 " + skillName + "。");
            items.add(item);
        }
        vo.setStages(List.of(learningStage(1, "针对性短板修复", items)));
        return vo;
    }

    private GenerateLearningPlanVO.StageVO learningStage(int stageNo, String stageTitle,
                                                        List<GenerateLearningPlanVO.ItemVO> items) {
        GenerateLearningPlanVO.StageVO stage = new GenerateLearningPlanVO.StageVO();
        stage.setStageNo(stageNo);
        stage.setStageTitle(stageTitle);
        stage.setItems(items);
        return stage;
    }

    private GenerateLearningPlanVO.ItemVO learningItem(String knowledgePoint, String taskTitle,
                                                      String taskDescription, String taskType,
                                                      String priority, int estimatedHours,
                                                      List<String> tags) {
        GenerateLearningPlanVO.ItemVO item = new GenerateLearningPlanVO.ItemVO();
        item.setKnowledgePoint(knowledgePoint);
        item.setTaskTitle(taskTitle);
        item.setTaskDescription(taskDescription);
        item.setTaskType(taskType);
        item.setPriority(priority);
        item.setEstimatedHours(estimatedHours);
        item.setEstimatedMinutes(estimatedHours * 60);
        item.setRelatedQuestionIds(List.of());
        item.setRelatedTags(tags);
        item.setResources(List.of());
        return item;
    }

    private String mockResumeStructuredJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("basicInfo", Map.of(
                "name", "张三",
                "phone", "13800000000",
                "email", "zhangsan@example.com",
                "location", "上海"
        ));
        json.put("targetPosition", "Java 后端开发工程师");
        json.put("skills", List.of("Java", "Spring Boot", "MySQL", "Redis", "微服务"));
        json.put("workExperiences", List.of(Map.of(
                "company", "示例科技有限公司",
                "position", "Java 后端开发工程师",
                "period", "2022.07-至今",
                "description", "负责核心业务系统后端研发、接口设计和性能优化。"
        )));
        json.put("projectExperiences", List.of(Map.of(
                "projectName", "在线面试训练平台",
                "period", "2023.01-2024.12",
                "description", "面向求职用户的刷题、模拟面试和报告生成平台。",
                "techStack", List.of("Spring Cloud", "MyBatis-Plus", "MySQL", "Redis"),
                "responsibilities", List.of("负责简历与面试核心服务设计", "实现 AI 面试报告生成链路"),
                "technicalDifficulties", List.of("跨服务调用稳定性", "AI 返回 JSON 格式约束"),
                "achievements", List.of("完成核心闭环交付", "提升面试报告生成稳定性")
        )));
        json.put("educationExperiences", List.of(Map.of(
                "school", "示例大学",
                "degree", "本科",
                "major", "计算机科学与技术",
                "period", "2018.09-2022.06"
        )));
        return toJson(json);
    }

    private String mockResumeOptimizeJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("overallScore", 82);
        json.put("overallComment", "简历整体结构完整，但项目难点和量化结果表达不足。");
        json.put("targetPositionMatch", Map.of(
                "score", 80,
                "comment", "与目标岗位匹配度较高，可继续补充可验证的技术深度。",
                "matchedPoints", List.of("Spring Boot", "MySQL", "Redis"),
                "missingPoints", List.of("JVM 调优", "分布式事务")
        ));
        json.put("sectionScores", Map.of(
                "skillStack", 78,
                "projectExperience", 82,
                "responsibilityClarity", 75,
                "technicalDepth", 76,
                "quantifiedResults", 60
        ));
        json.put("problems", List.of(Map.of(
                "type", "PROJECT_DESCRIPTION_TOO_GENERAL",
                "title", "项目描述偏泛",
                "description", "项目职责描述停留在功能开发层面，缺少技术难点和解决方案。",
                "severity", "MEDIUM"
        )));
        json.put("rewriteSuggestions", List.of(Map.of(
                "section", "project",
                "projectName", "电商订单系统",
                "before", "负责订单模块开发。",
                "after", "负责订单创建、状态流转和支付回调处理，重点处理重复回调下的幂等控制。",
                "reason", "增强职责边界和技术问题表达。",
                "fabricationRisk", false
        )));
        json.put("riskWarnings", List.of(Map.of(
                "type", "OVER_PACKAGING",
                "description", "如果没有真实压测数据，不建议写具体性能提升百分比。",
                "suggestion", "可改为优化慢查询和缓存命中策略，降低接口响应耗时。"
        )));
        json.put("possibleInterviewQuestions", List.of(Map.of(
                "question", "支付回调重复通知时如何保证幂等？",
                "reason", "简历中提到了支付回调处理。"
        )));
        json.put("nextActions", List.of(
                "补充每个项目的业务背景。",
                "明确个人负责模块。",
                "补充真实可验证的技术难点。"
        ));
        return toJson(json);
    }

    private String mockJobDescriptionParseJson(ParseJobDescriptionDTO dto) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("jobTitle", firstText(dto == null ? null : dto.getJobTitle(), "Java Backend Engineer"));
        json.put("companyName", dto == null ? null : dto.getCompanyName());
        json.put("jobLevel", firstText(dto == null ? null : dto.getJobLevel(), "Mid-level"));
        json.put("responsibilities", List.of(
                "Build and maintain Java backend services",
                "Design APIs and improve service reliability"
        ));
        json.put("requiredSkills", List.of(
                Map.of("name", "Spring Boot", "category", "Framework", "requiredLevel", 4, "weight", 90,
                        "evidence", "JD expects Java backend service development"),
                Map.of("name", "MySQL", "category", "Database", "requiredLevel", 3, "weight", 75,
                        "evidence", "Backend roles usually require relational database design and SQL tuning")
        ));
        json.put("bonusSkills", List.of(
                Map.of("name", "Redis", "category", "Cache", "requiredLevel", 3, "weight", 60,
                        "evidence", "High-concurrency backend systems benefit from cache design")
        ));
        json.put("techStackKeywords", List.of("Java", "Spring Boot", "MySQL", "Redis"));
        json.put("businessKeywords", List.of("Backend service", "API design", "Reliability"));
        json.put("experienceRequirement", "Experience building Java backend applications.");
        json.put("projectExperienceRequirement", "Show at least one production-like backend project with clear responsibilities.");
        json.put("interviewFocusPoints", List.of(
                Map.of("topic", "Spring Boot service design", "reason", "Core backend framework capability"),
                Map.of("topic", "Database schema and SQL optimization", "reason", "Common Java backend interview focus")
        ));
        json.put("skillWeights", Map.of("Spring Boot", 90, "MySQL", 75, "Redis", 60));
        json.put("summary", "This role focuses on Java backend development, API design, database usage, and production reliability.");
        return toJson(json);
    }

    private String mockResumeJobMatchJson() {
        Map<String, Object> dimensionScores = new LinkedHashMap<>();
        dimensionScores.put("techStack", 82);
        dimensionScores.put("projectExperience", 75);
        dimensionScores.put("businessFit", 68);
        dimensionScores.put("communication", 80);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("overallScore", 78);
        json.put("dimensionScores", dimensionScores);
        json.put("strengths", List.of(
                Map.of("title", "Spring Boot 项目经历与目标岗位相关",
                        "evidence", "简历快照中出现 Java 后端项目经历，并包含 Spring Boot 相关技术栈。",
                        "relatedSkills", List.of("Spring Boot", "Java"))
        ));
        json.put("gaps", List.of(
                Map.of("skillName", "Redis",
                        "category", "中间件",
                        "severity", "HIGH",
                        "targetLevel", 4,
                        "currentLevel", 2,
                        "description", "简历中缓存一致性、分布式锁等 Redis 项目证据不足。",
                        "evidence", "JD 要求高并发后端能力；简历未提供 Redis 场景的直接实践细节。",
                        "recommendedActions", List.of("补充 Redis 项目证据", "练习 Redis 场景题"))
        ));
        json.put("resumeRisks", List.of(
                Map.of("riskType", "PROJECT_DEPTH",
                        "description", "项目描述需要补充技术难点、排障过程和可量化结果。")
        ));
        json.put("optimizationSuggestions", List.of(
                Map.of("section", "项目经历",
                        "suggestion", "补充缓存设计、故障处理步骤和优化前后的指标变化。")
        ));
        json.put("recommendedLearningTopics", List.of("Redis 缓存一致性", "MySQL 索引优化"));
        json.put("recommendedInterviewTopics", List.of("分布式锁", "接口幂等"));
        json.put("summary", "综合匹配度中等。面试前优先补齐 Redis 与项目深度表达证据。");
        return toJson(json);
    }

    private String mockSkillGapAnalyzeJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("profileSummary",
                "候选人的 Java 与 Spring Boot 基础较稳定，但 Redis 场景和项目深度证据仍需要补齐。");
        json.put("overallLevel", 2);
        json.put("overallScore", 68);
        List<Map<String, Object>> skillGaps = new java.util.ArrayList<>();
        Map<String, Object> redisGap = new LinkedHashMap<>();
        redisGap.put("skillName", "Redis");
        redisGap.put("category", "中间件");
        redisGap.put("targetLevel", 4);
        redisGap.put("currentLevel", 2);
        redisGap.put("gapLevel", 2);
        redisGap.put("confidence", 0.82);
        redisGap.put("severity", "HIGH");
        redisGap.put("evidenceSources", List.of("RESUME_JOB_MATCH"));
        redisGap.put("gapDescription", "简历缺少缓存一致性和分布式锁的项目证据。");
        redisGap.put("recommendedActions", List.of("复习缓存一致性常见方案",
                "练习 Redis 场景题",
                "进行一次 Redis 专项模拟面试"));
        redisGap.put("priority", 1);
        skillGaps.add(redisGap);

        Map<String, Object> projectDepthGap = new LinkedHashMap<>();
        projectDepthGap.put("skillName", "项目深度表达");
        projectDepthGap.put("category", "项目经历");
        projectDepthGap.put("targetLevel", 4);
        projectDepthGap.put("currentLevel", 3);
        projectDepthGap.put("gapLevel", 1);
        projectDepthGap.put("confidence", 0.76);
        projectDepthGap.put("severity", "MEDIUM");
        projectDepthGap.put("evidenceSources", List.of("RESUME_JOB_MATCH"));
        projectDepthGap.put("gapDescription", "项目描述需要补充可量化结果和排障细节。");
        projectDepthGap.put("recommendedActions", List.of("补充可量化项目结果",
                "准备线上排障案例"));
        projectDepthGap.put("priority", 2);
        skillGaps.add(projectDepthGap);
        json.put("skillGaps", skillGaps);
        json.put("nextPrioritySkills", List.of("Redis", "项目深度表达"));
        json.put("nextActions", List.of("完成 Redis 缓存一致性复盘",
                "练习 5 道 Redis 场景题",
                "用可量化结果改写一段项目经历"));
        return toJson(json);
    }

    private String mockQuestionContent(GenerateInterviewQuestionDTO dto, String scene) {
        if (SCENE_PROJECT_QUESTION.equals(scene)) {
            String project = summarize(firstText(dto == null ? null : dto.getProjectContent(),
                    dto == null ? null : dto.getResumeContent(),
                    "候选人项目经历"), 120);
            String focus = firstText(dto == null ? null : dto.getFocusPoints(),
                    "项目背景、技术架构、数据库设计、核心难点、性能优化、故障排查、技术取舍和个人职责");
            String industry = StringUtils.hasText(dto == null ? null : dto.getIndustryContext())
                    ? "，并以行业场景作为参考，不能把行业模板当成候选人真实项目经历"
                    : "";
            return "请结合项目经历【" + project + "】，围绕【" + focus
                    + "】追问一个具体项目深挖问题" + industry + "，要求候选人说明背景、方案、取舍、落地细节和量化结果。";
        }
        String industry = StringUtils.hasText(dto == null ? null : dto.getIndustryContext())
                ? "，可结合行业场景考察迁移能力，但不要伪造候选人真实经历"
                : "";
        return "请结合" + firstText(dto == null ? null : dto.getQuestionTitle(),
                dto == null ? null : dto.getQuestionContent(),
                "当前 Java 后端主题") + "说明核心原理、适用场景和生产实践注意点" + industry + "。";
    }

    private boolean applyProjectAwareMockEvaluation(EvaluateAnswerVO vo, EvaluateAnswerDTO dto, int score) {
        boolean projectStage = isProjectStage(dto == null ? null : dto.getStageType(), dto == null ? null : dto.getCurrentStage())
                || StringUtils.hasText(dto == null ? null : dto.getProjectContent());
        if (!projectStage) {
            return false;
        }
        vo.setComment(score >= 75
                ? "项目回答覆盖了主要背景和方案，建议继续补充架构取舍、故障排查过程、性能指标和个人贡献边界。"
                : "项目回答偏概括，建议按背景、职责、技术架构、数据库设计、核心难点、优化结果和复盘改进展开。");
        vo.setKnowledgePoints("项目理解、技术深度、表达清晰度、问题解决能力、架构思维");
        vo.setNextAction(score < 75 && safeInt(dto == null ? null : dto.getFollowUpCount()) < maxFollowUp(dto)
                ? "FOLLOW_UP"
                : "NEXT_QUESTION");
        fillFollowUpFromEvaluation(vo, dto, null);
        return true;
    }

    private boolean applyProjectAwareMockReport(GenerateReportVO vo) {
        vo.setTotalScore(null);
        vo.setSummary("本场模拟面试已生成项目表达基础参考报告，但未获得可信 AI 评分。请继续答题或重新生成报告后再查看总分。");
        vo.setStageScores("{}");
        vo.setWeakPoints("[\"源码细节\",\"执行计划\",\"缓存一致性边界\",\"项目指标量化\"]");
        vo.setStrengths("[\"能围绕 Java 后端常见题目给出基本结论\",\"能结合 Spring、MySQL、Redis 说明常见处理思路\",\"能够描述项目背景和核心职责\"]");
        vo.setWeaknesses("部分回答停留在结论层，对源码细节、数据库执行计划、缓存一致性边界和线上排查步骤展开不足；项目表达还需要补充量化指标、技术取舍和故障复盘。");
        vo.setMainProblems("[\"源码细节展开不足\",\"线上排查步骤不够完整\",\"项目方案缺少取舍说明\"]");
        vo.setProjectProblems("[\"项目表达缺少背景-职责-方案-结果的完整链路\",\"技术深度需要补充数据库设计、性能优化和故障排查细节\",\"优化前后缺少量化对比\"]");
        vo.setSuggestions("[\"复盘集合、并发、事务、索引和缓存高频题\",\"准备 2-3 个带指标的项目优化案例\",\"按背景、架构、职责、难点、取舍、结果组织项目回答\"]");
        vo.setReviewSuggestions(vo.getSuggestions());
        vo.setRecommendedQuestions("[\"你在项目中负责的核心模块如何设计？\",\"数据库表和索引为什么这样设计？\",\"遇到线上故障时你如何定位和复盘？\"]");
        vo.setQaReview("[]");
        vo.setReportContent(vo.getSummary());
        return true;
    }

    private void applyIndustryAwareMockReport(GenerateReportVO vo, GenerateReportDTO dto) {
        if (!StringUtils.hasText(dto == null ? null : dto.getIndustryContext())) {
            return;
        }
        String direction = firstText(dto.getIndustryDirection(), "目标行业");
        String industryFeedback = "行业反馈：" + direction
                + "场景会重点关注业务链路、数据一致性、异常补偿、权限边界和可观测性。本次面试可继续补充真实项目与该行业场景的匹配点；如果过往项目不属于该行业，应按假设场景说明迁移方案，而不是包装成真实经历。";
        vo.setSummary(firstText(vo.getSummary(), "") + " 已结合" + direction + "行业场景给出反馈。");
        vo.setWeakPoints(appendJsonArrayText(vo.getWeakPoints(), "行业场景迁移能力"));
        vo.setProjectProblems(appendJsonArrayText(vo.getProjectProblems(), "行业业务链路和风险点结合不足"));
        vo.setReviewSuggestions(appendJsonArrayText(vo.getReviewSuggestions(), industryFeedback));
        vo.setSuggestions(appendJsonArrayText(vo.getSuggestions(), industryFeedback));
        vo.setReportContent(firstText(vo.getReportContent(), vo.getSummary()) + "\n" + industryFeedback);
    }

    private String appendJsonArrayText(String jsonArray, String item) {
        if (!StringUtils.hasText(jsonArray)) {
            return toJson(List.of(item));
        }
        try {
            JsonNode node = objectMapper.readTree(jsonArray);
            if (node.isArray()) {
                List<String> values = new java.util.ArrayList<>();
                node.forEach(value -> values.add(value.asText()));
                values.add(item);
                return toJson(values);
            }
        } catch (Exception ignored) {
            // Keep existing non-JSON report text and append a readable item.
        }
        return jsonArray + "\n" + item;
    }

    private int scoreByLength(String answer) {
        int answerLength = answer == null ? 0 : answer.trim().length();
        return Math.min(95, Math.max(55, answerLength / 2));
    }

    private Long saveLog(PromptRenderResult promptResult, String response, String businessId,
                         long startMillis, String errorMessage, Long explicitUserId, AiFailureType failureType) {
        AiCallLog log = new AiCallLog();
        long elapsed = System.currentTimeMillis() - startMillis;
        AiFailureType resolvedFailureType = failureType == null ? AiFailureType.UNKNOWN_ERROR : failureType;
        String requestId = UUID.randomUUID().toString();
        String traceId = currentTraceId();
        log.setUserId(explicitUserId == null ? LoginUserContext.getUserId() : explicitUserId);
        log.setScene(promptResult.getScene());
        log.setModelName(Boolean.TRUE.equals(aiProperties.getMockEnabled())
                ? aiProperties.getModel() + "(mock)"
                : aiProperties.getModel());
        log.setPromptTemplateId(promptResult.getPromptTemplateId());
        log.setPromptTemplateVersionId(promptResult.getPromptTemplateVersionId());
        log.setPromptVersion(promptResult.getPromptVersion());
        log.setRequestId(requestId);
        log.setTraceId(traceId);
        log.setInputVariablesJson(promptResult.getInputVariablesJson());
        log.setModelParamsJson(promptResult.getModelParamsJson());
        log.setPromptHash(promptResult.getPromptHash());
        log.setResponseFormat("JSON");
        log.setRequestPrompt(promptResult.getRenderedPrompt());
        log.setResponseContent(response);
        log.setBusinessId(businessId);
        log.setRequestBody(buildRequestMetadata(promptResult, resolvedFailureType, requestId, traceId));
        log.setResponseBody(buildResponseMetadata(response, elapsed, errorMessage, resolvedFailureType));
        log.setElapsedMs(elapsed);
        log.setCostMillis(elapsed);
        log.setSuccess(errorMessage == null ? CommonConstants.YES : CommonConstants.NO);
        log.setPromptTokens(null);
        log.setCompletionTokens(null);
        log.setTotalTokens(null);
        log.setStatus(errorMessage == null ? CommonConstants.YES : CommonConstants.NO);
        log.setErrorMessage(errorMessage);
        try {
            aiCallLogMapper.insert(log);
            return log.getId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private RouteResult callAndLog(PromptRenderResult promptResult, Long userId, String businessId) {
        AiCallContext ctx = new AiCallContext();
        String requestId = UUID.randomUUID().toString();
        String traceId = currentTraceId();
        ctx.setScene(promptResult.getScene());
        ctx.setPrompt(promptResult.getRenderedPrompt());
        ctx.setUserId(userId);
        ctx.setBusinessId(businessId);
        ctx.setRequestId(requestId);
        ctx.setPromptTemplateId(promptResult.getPromptTemplateId());
        ctx.setPromptTemplateVersionId(promptResult.getPromptTemplateVersionId());
        ctx.setPromptVersion(promptResult.getPromptVersion());
        ctx.setInputVariablesJson(promptResult.getInputVariablesJson());
        ctx.setModelParamsJson(promptResult.getModelParamsJson());
        ctx.setPromptHash(promptResult.getPromptHash());
        ctx.setResponseFormat("JSON");
        ctx.setRequestBody(buildRequestMetadata(promptResult, AiFailureType.NONE, requestId, traceId));
        return aiCallLogService.callAndLog(ctx);
    }

    private RouteResult callStreamAndLog(PromptRenderResult promptResult, Long userId, String businessId,
                                         java.util.function.Consumer<String> tokenConsumer) {
        AiCallContext ctx = new AiCallContext();
        String requestId = UUID.randomUUID().toString();
        String traceId = currentTraceId();
        ctx.setScene(promptResult.getScene());
        ctx.setPrompt(promptResult.getRenderedPrompt());
        ctx.setUserId(userId);
        ctx.setBusinessId(businessId);
        ctx.setRequestId(requestId);
        ctx.setPromptTemplateId(promptResult.getPromptTemplateId());
        ctx.setPromptTemplateVersionId(promptResult.getPromptTemplateVersionId());
        ctx.setPromptVersion(promptResult.getPromptVersion());
        ctx.setInputVariablesJson(promptResult.getInputVariablesJson());
        ctx.setModelParamsJson(promptResult.getModelParamsJson());
        ctx.setPromptHash(promptResult.getPromptHash());
        ctx.setResponseFormat("JSON");
        ctx.setRequestBody(buildRequestMetadata(promptResult, AiFailureType.NONE, requestId, traceId));
        return aiCallLogService.callStreamAndLog(ctx, tokenConsumer);
    }

    private void emitMockTokens(String content, java.util.function.Consumer<String> tokenConsumer) {
        if (tokenConsumer == null || !StringUtils.hasText(content)) {
            return;
        }
        int chunkSize = 24;
        for (int start = 0; start < content.length(); start += chunkSize) {
            tokenConsumer.accept(content.substring(start, Math.min(content.length(), start + chunkSize)));
        }
    }

    private String buildRequestMetadata(PromptRenderResult promptResult, AiFailureType failureType,
                                        String requestId, String traceId) {
        Map<String, Object> metadata = baseMetadata(promptResult.getScene(), failureType);
        metadata.put("promptTemplateId", promptResult.getPromptTemplateId());
        metadata.put("promptTemplateVersionId", promptResult.getPromptTemplateVersionId());
        metadata.put("promptTemplateVersion", promptResult.getPromptVersion());
        metadata.put("promptVersion", promptResult.getPromptVersion());
        metadata.put("fallbackUsed", promptResult.getFallbackUsed());
        metadata.put("inputVariables", promptResult.getInputVariablesJson());
        metadata.put("modelParams", promptResult.getModelParamsJson());
        metadata.put("promptHash", promptResult.getPromptHash());
        metadata.put("requestId", requestId);
        metadata.put("traceId", traceId);
        metadata.put("timeoutSeconds", aiProperties.getTimeoutSeconds());
        metadata.put("prompt", promptResult.getRenderedPrompt());
        return toJson(metadata);
    }

    private String buildResponseMetadata(String response, long elapsed, String errorMessage, AiFailureType failureType) {
        Map<String, Object> metadata = baseMetadata(null, failureType);
        metadata.put("success", errorMessage == null);
        metadata.put("latencyMs", elapsed);
        metadata.put("promptTokens", null);
        metadata.put("completionTokens", null);
        metadata.put("totalTokens", null);
        metadata.put("errorMessage", errorMessage);
        metadata.put("response", response);
        return toJson(metadata);
    }

    private Map<String, Object> baseMetadata(String scene, AiFailureType failureType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (scene != null) {
            metadata.put("scene", scene);
        }
        metadata.put("provider", aiProperties.getProvider());
        metadata.put("model", aiProperties.getModel());
        metadata.put("mockMode", Boolean.TRUE.equals(aiProperties.getMockEnabled()));
        metadata.put("failureType", failureType == null ? AiFailureType.UNKNOWN_ERROR.name() : failureType.name());
        metadata.put("retryCount", 0);
        return metadata;
    }

    private String currentTraceId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(HeaderConstants.TRACE_ID);
        }
        return null;
    }

    private AiFailureType failureType(RuntimeException ex) {
        if (ex instanceof AiProviderException aiProviderException) {
            return aiProviderException.getFailureType();
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("not valid json") || message.contains("parse")) {
            return AiFailureType.PARSE_ERROR;
        }
        if (message.contains("empty")) {
            return AiFailureType.EMPTY_RESPONSE;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return AiFailureType.TIMEOUT;
        }
        if (message.contains("base-url") || message.contains("api-key") || message.contains("disabled")) {
            return AiFailureType.CONFIG_ERROR;
        }
        return AiFailureType.UNKNOWN_ERROR;
    }

    private String jsonOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isTextual()) {
            return firstText(node.asText(null), fallback);
        }
        return node.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String businessId(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private void fillFollowUpFromEvaluation(EvaluateAnswerVO vo, EvaluateAnswerDTO dto, String candidateQuestion) {
        if (!"FOLLOW_UP".equals(vo.getNextAction())) {
            vo.setFollowUpQuestion("");
            vo.setFollowUpReason(firstText(vo.getFollowUpReason(), ""));
            vo.setFollowUpValid(false);
            return;
        }
        String question = firstText(candidateQuestion, vo.getFollowUpQuestion());
        if (isInvalidFollowUp(question, dto)) {
            question = buildFallbackFollowUp(dto);
            vo.setFollowUpReason(markFallback(firstText(vo.getFollowUpReason(), "追问内容不够贴合，已改用通用追问")));
        }
        vo.setFollowUpQuestion(question);
        vo.setFollowUpValid(true);
    }

    private String decideNextAction(String candidate, int score, Integer followUpCount, int maxFollowUpCount) {
        String action = candidate == null ? "" : candidate.trim().toUpperCase();
        boolean legal = "FOLLOW_UP".equals(action)
                || "NEXT_QUESTION".equals(action)
                || "NEXT_STAGE".equals(action)
                || "FINISH".equals(action);
        if (!legal) {
            action = score < 75 ? "FOLLOW_UP" : "NEXT_QUESTION";
        }
        if ("FOLLOW_UP".equals(action) && safeInt(followUpCount) >= maxFollowUpCount) {
            return "NEXT_QUESTION";
        }
        return action;
    }

    private boolean isInvalidFollowUp(String question, EvaluateAnswerDTO dto) {
        return isInvalidFollowUp(question,
                dto == null ? null : dto.getRootQuestionContent(),
                dto == null ? null : dto.getCurrentQuestionContent(),
                dto == null ? null : dto.getAnswerContent());
    }

    private boolean isInvalidFollowUp(String question, GenerateFollowUpDTO dto) {
        return isInvalidFollowUp(question,
                dto == null ? null : dto.getRootQuestionContent(),
                dto == null ? null : dto.getCurrentQuestionContent(),
                dto == null ? null : dto.getAnswerContent());
    }

    private boolean isInvalidFollowUp(String question, String rootQuestion, String currentQuestion, String answer) {
        if (!StringUtils.hasText(question) || question.trim().length() < 12) {
            return true;
        }
        String value = question.trim();
        String lower = value.toLowerCase();
        String[] banned = {"假设原问题", "如果你有具体", "请提供具体", "由于没有", "无法生成", "用户增长", "团队协作", "市场运营"};
        for (String item : banned) {
            if (value.contains(item)) {
                return true;
            }
        }
        boolean looksLikeQuestion = value.endsWith("?") || value.endsWith("？")
                || value.contains("请") || value.contains("如何") || value.contains("为什么")
                || value.contains("能否") || value.contains("是否");
        if (!looksLikeQuestion) {
            return true;
        }
        boolean hasTechContext = containsAny(lower,
                "java", "jvm", "spring", "mysql", "redis",
                "线程", "并发", "集合", "事务", "索引", "缓存", "锁", "gc", "接口", "数据库");
        boolean relatedToContext = sharesKeyword(value, rootQuestion) || sharesKeyword(value, currentQuestion) || sharesKeyword(value, answer);
        return !hasTechContext && !relatedToContext;
    }

    private String buildFallbackFollowUp(EvaluateAnswerDTO dto) {
        String root = firstText(dto == null ? null : dto.getRootQuestionContent(),
                dto == null ? null : dto.getCurrentQuestionContent(),
                dto == null ? null : dto.getQuestionContent(),
                dto == null ? null : dto.getQuestionTitle(),
                "当前 Java 技术问题");
        String answer = summarize(firstText(dto == null ? null : dto.getAnswerContent(), "回答较简略"), 80);
        String missing = summarize(firstText(
                dto == null ? null : dto.getKnowledgePoints(),
                dto == null ? null : dto.getReferenceAnswer(),
                "核心原理、边界条件和项目实践"), 90);
        return "你刚才的回答是：" + answer + "。请继续围绕「" + summarize(root, 80) + "」补充说明：" + missing + "。";
    }

    private String buildFallbackFollowUp(GenerateFollowUpDTO dto) {
        String root = firstText(dto == null ? null : dto.getRootQuestionContent(),
                dto == null ? null : dto.getCurrentQuestionContent(),
                dto == null ? null : dto.getQuestionContent(),
                dto == null ? null : dto.getQuestionTitle(),
                "当前 Java 技术问题");
        String answer = summarize(firstText(dto == null ? null : dto.getAnswerContent(), "回答较简略"), 80);
        String missing = summarize(firstText(
                dto == null ? null : dto.getKnowledgePoints(),
                dto == null ? null : dto.getReferenceAnswer(),
                "核心原理、边界条件和项目实践"), 90);
        return "你刚才的回答是：" + answer + "。请继续围绕「" + summarize(root, 80) + "」补充说明：" + missing + "。";
    }

    private String markFallback(String reason) {
        return firstText(reason, "已为你生成一条更贴近当前回答的追问");
    }

    private String mergeRawAndFinal(String rawResponse, Object finalResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return toJson(finalResponse);
        }
        return "{\"rawResponse\":" + quoteJson(rawResponse) + ",\"finalResponse\":" + toJson(finalResponse) + "}";
    }

    private String quoteJson(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
    }

    private String cleanQuestionText(String raw) {
        String value = firstText(raw, "请结合当前阶段说明核心原理、适用场景和生产实践注意点。").trim();
        value = firstText(extractLabelValue(value, "questionContent"), extractLabelValue(value, "questionText"), value);
        value = value.replaceAll("(?i)^scene\\s*[:：]\\s*[^\\n]+\\n?", "");
        value = value.replaceAll("(?i)^question(Text|Content)?\\s*[:：]\\s*", "");
        value = value.replace("```json", "").replace("```", "").trim();
        return value;
    }

    private String extractLabelValue(String raw, String label) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(label)) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?i)" + Pattern.quote(label) + "\\s*[:：]\\s*\"?([^\"\\n\\r]+)");
        Matcher matcher = pattern.matcher(raw);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean sharesKeyword(String question, String context) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(context)) {
            return false;
        }
        String q = question.toLowerCase();
        String[] separators = context.toLowerCase().replaceAll("[，。！？、；：,.!?;:()（）\\[\\]{}\"']", " ").split("\\s+");
        for (String token : separators) {
            if (token.length() >= 2 && q.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String summarize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "...";
    }

    private int clampScore(int score) {
        return Math.min(100, Math.max(0, score));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int maxFollowUp(EvaluateAnswerDTO dto) {
        return dto == null || dto.getMaxFollowUpCount() == null || dto.getMaxFollowUpCount() <= 0 ? 2 : dto.getMaxFollowUpCount();
    }

    private int maxFollowUp(GenerateFollowUpDTO dto) {
        return dto == null || dto.getMaxFollowUpCount() == null || dto.getMaxFollowUpCount() <= 0 ? 2 : dto.getMaxFollowUpCount();
    }

    private String defaultQuestionPrompt() {
        return """
                你是资深 Java 面试官。请基于当前阶段生成一个干净的中文技术面试问题。
                当前阶段：{{stageName}}
                阶段类型：{{stageType}}
                阶段重点：{{focusPoints}}
                目标岗位：{{targetPosition}}
                难度：{{difficulty}}
                题库候选题：{{questionContent}}
                历史摘要：{{historySummary}}
                要求：只能围绕当前阶段重点提问，不要跳到无关主题。只输出 JSON，不要 Markdown，不要代码块，不要解释文字。
                JSON 字段固定：{"questionContent":"问题内容"}
                """;
    }

    private String defaultProjectQuestionPrompt() {
        return """
                你是资深 Java 项目面试官。请基于候选人简历项目经历生成一个中文项目深挖问题。
                当前阶段：{{stageName}}
                阶段类型：{{stageType}}
                阶段重点：{{focusPoints}}
                目标岗位：{{targetPosition}}
                难度：{{difficulty}}
                简历摘要：{{resumeContent}}
                项目经历：
                {{projectContent}}
                历史摘要：{{historySummary}}
                提问要求：
                1. 必须围绕项目背景、技术架构、数据库设计、核心难点、性能优化、故障排查、技术取舍、个人职责之一展开。
                2. 必须要求候选人说明具体落地细节、取舍原因、风险边界或量化结果。
                3. 不要泛泛询问“介绍一下项目”，不要跳到无关八股题。
                4. 只输出 JSON，不要 Markdown，不要代码块，不要解释文字。
                JSON 字段固定：{"questionContent":"问题内容"}
                """;
    }

    private String defaultEvaluatePrompt() {
        return """
                你是资深 Java 面试官。请一次性完成评分、点评、流程决策，并在需要时生成一个追问。
                原始主问题：
                {{rootQuestionContent}}
                当前问题：
                {{currentQuestionContent}}
                参考答案：
                {{referenceAnswer}}
                候选人回答：
                {{userAnswer}}
                当前阶段：{{stageName}}
                历史摘要：{{historySummary}}
                当前追问次数：{{followUpCount}}
                最大追问次数：{{maxFollowUpCount}}
                要求：
                1. score 必须是 0-100 整数。
                2. nextAction 只能是 FOLLOW_UP、NEXT_QUESTION、NEXT_STAGE、FINISH。
                3. 如果回答明显很短、错误或泛泛而谈，可以 FOLLOW_UP。
                4. 如果 followUpCount >= maxFollowUpCount，禁止 FOLLOW_UP。
                5. nextAction=FOLLOW_UP 时，followUpQuestion 必须紧扣原始主问题和候选人回答，必须是 Java 技术面试追问。
                6. 不允许出现“假设原问题”“如果你有具体问题请提供”“由于没有上下文”等话术。
                7. 不允许输出 Markdown、代码块或解释文字。
                只输出 JSON：
                {"score":80,"comment":"中文点评","nextAction":"FOLLOW_UP","followUpQuestion":"追问内容","followUpReason":"追问原因","knowledgePoints":"相关知识点"}
                """;
    }

    private String defaultFollowUpPrompt() {
        return """
                你是资深 Java 面试官。请基于以下上下文生成一个追问。
                原始主问题：
                {{rootQuestionContent}}
                当前问题：
                {{currentQuestionContent}}
                参考答案：
                {{referenceAnswer}}
                候选人回答：
                {{userAnswer}}
                AI 评分点评：
                {{aiComment}}
                当前阶段：
                {{stageName}}
                历史摘要：
                {{historySummary}}
                追问要求：
                1. 追问必须紧扣原始主问题和候选人回答，不能换题。
                2. 必须指出候选人回答中具体缺失或错误的点。
                3. 只生成一个更深入的问题。
                4. 不要重复原问题。
                5. 不要编造“假设原问题”。
                6. 不要说“请提供具体问题”。
                7. 不要输出解释文字。
                8. 不允许跳到团队协作、用户增长、市场运营等非 Java 技术面试主题。
                只返回 JSON：{"followUpQuestion":"追问内容","reason":"追问原因","relatedToOriginalQuestion":true}
                """;
    }

    private String defaultReportPrompt() {
        return """
                你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。
                只输出 JSON，不要 Markdown，不要代码块，不要解释文字。
                totalScore 必须基于真实问答计算；无有效回答、题目明细缺失或无法评分时返回 null，不要生成固定兜底分数。
                qaReview 必须逐题对应真实回答，不能用空数组伪装成功报告。
                字段固定：
                {"totalScore":null,"summary":"评分依据或不可评分原因","strengths":[],"weakPoints":[],"mainProblems":[],"projectProblems":[],"reviewSuggestions":[],"recommendedQuestions":[],"qaReview":[],"stageScores":{},"reportContent":"报告正文"}
                """;
    }

    private String defaultLearningPlanPrompt() {
        return """
                You are a senior Java backend interview coach. Generate a practical study plan in Chinese.
                Use only the given interview report, weakness summary, question performance, resume weakness, target position, and industry context.
                targetPosition: {{targetPosition}}
                industryDirection: {{industryDirection}}
                experienceLevel: {{experienceLevel}}
                expectedDurationDays: {{expectedDurationDays}}
                interviewSummary:
                {{interviewSummary}}
                weaknessSummary:
                {{weaknessSummary}}
                questionPerformanceSummary:
                {{questionPerformanceSummary}}
                resumeWeaknessSummary:
                {{resumeWeaknessSummary}}
                extraRequirements:
                {{extraRequirements}}

                Output only one JSON object. Do not output Markdown. Do not output code fences. Do not add explanations.
                Top-level fields must be planTitle, planSummary, durationDays, stages.
                stages must be an array. Each stage must contain stageNo, stageTitle, items.
                items must be an array. Each item must contain knowledgePoint, taskTitle, taskDescription, taskType, priority, estimatedHours, relatedTags, resources.
                taskType must be one of KNOWLEDGE_REVIEW, CODING_PRACTICE, PROJECT_REVIEW, INTERVIEW_PRACTICE, RESUME_IMPROVEMENT.
                priority must be one of HIGH, MEDIUM, LOW.
                Use 2 to 4 stages and 2 to 5 items per stage.
                Example:
                {"planTitle":"Java backend 14 day plan","planSummary":"Focus on weak points and interview practice.","durationDays":14,"stages":[{"stageNo":1,"stageTitle":"Foundation repair","items":[{"knowledgePoint":"Java Collections","taskTitle":"Review HashMap resize mechanism","taskDescription":"Summarize core flow, edge cases, and interview expression.","taskType":"KNOWLEDGE_REVIEW","priority":"HIGH","estimatedHours":2,"relatedTags":["Java"],"resources":[]}]}]}
                """;
    }

    private String defaultQuestionDraftPrompt() {
        return """
                你是 Java 后端面试题生成助手。
                请根据输入的 targetPosition、technologyStack、knowledgePoint、questionType、difficulty、experienceYears、count 生成面试题草稿。
                如果 targetPosition 为空，请生成通用 Java 后端面试题。

                输入：
                - targetPosition: {{targetPosition}}
                - technologyStack: {{technologyStack}}
                - knowledgePoint: {{knowledgePoint}}
                - questionType: {{questionType}}
                - difficulty: {{difficulty}}
                - experienceYears: {{experienceYears}}
                - count: {{count}}
                - generateReferenceAnswer: {{generateReferenceAnswer}}
                - generateFollowUps: {{generateFollowUps}}
                - generateTagSuggestions: {{generateTagSuggestions}}
                - generateCategorySuggestion: {{generateCategorySuggestion}}
                - extraRequirements: {{extraRequirements}}

                输出要求：
                1. 只能输出 JSON object。
                2. 不要输出 Markdown。
                3. 不要输出代码块。
                4. 顶层固定为 {"questions":[...]}，不要直接输出 JSON array。
                5. questions 数量必须大于 0 且不得超过 count。
                6. 每题必须包含 title、content、referenceAnswer、analysis。
                7. difficulty 和 questionType 优先使用输入值。
                8. 不要生成虚假候选人经历。
                9. 不要把题目判定为重复题。
                10. 不要输出题库去重判断。
                11. 不要输出 SAME_INTENT、FOLLOW_UP、RELATED、ADVANCED 题目关系。
                12. 如果输入不足，生成通用 Java 后端面试题，但仍要围绕技术点。

                JSON 格式：
                {"questions":[{"title":"题目标题","content":"题目内容","referenceAnswer":"参考答案","analysis":"答案解析","difficulty":"MEDIUM","questionType":"SHORT_ANSWER","followUpQuestions":["追问题"],"tagSuggestions":["标签建议"],"categorySuggestion":"分类建议","groupSuggestion":"问题组建议"}]}
                """;
    }

    private String defaultJobDescriptionParsePrompt() {
        return """
                You are a senior Java backend career coach. Parse the target job JD into structured JSON.
                targetJobId: {{targetJobId}}
                userId: {{userId}}
                jobTitle: {{jobTitle}}
                companyName: {{companyName}}
                jobLevel: {{jobLevel}}
                jdSource: {{jdSource}}
                userTargetDirection: {{userTargetDirection}}
                JD:
                {{jdText}}

                Output only one JSON object. Do not output Markdown, code fences, or explanations.
                Top-level fields must be:
                jobTitle, companyName, jobLevel, responsibilities, requiredSkills, bonusSkills,
                techStackKeywords, businessKeywords, experienceRequirement, projectExperienceRequirement,
                interviewFocusPoints, skillWeights, summary.
                responsibilities, requiredSkills, bonusSkills, techStackKeywords, businessKeywords, and interviewFocusPoints must be arrays.
                requiredSkills and bonusSkills items should contain name, category, requiredLevel, weight, and evidence.
                interviewFocusPoints items should contain topic and reason.
                skillWeights should be an object keyed by skill name.
                Do not invent company facts beyond the JD. If a field is not available, use an empty string, empty array, or empty object.
                Example:
                {"jobTitle":"Java Backend Engineer","companyName":"","jobLevel":"Mid-level","responsibilities":[],"requiredSkills":[{"name":"Spring Boot","category":"Framework","requiredLevel":4,"weight":90,"evidence":"JD requires Spring Boot experience"}],"bonusSkills":[],"techStackKeywords":[],"businessKeywords":[],"experienceRequirement":"","projectExperienceRequirement":"","interviewFocusPoints":[],"skillWeights":{},"summary":""}
                """;
    }

    private String defaultResumeJobMatchPrompt() {
        return """
                你是资深 Java 后端求职教练。请基于简历与目标岗位生成匹配分析 JSON。
                reportId: {{reportId}}
                userId: {{userId}}
                resumeId: {{resumeId}}
                targetJobId: {{targetJobId}}
                jdAnalysisId: {{jdAnalysisId}}
                userExperienceYears: {{userExperienceYears}}
                targetJob:
                {{targetJobJson}}
                resumeAnalysisJson:
                {{resumeAnalysisJson}}
                resumeSnapshotJson:
                {{resumeSnapshotJson}}
                jobDescriptionAnalysisJson:
                {{jobDescriptionAnalysisJson}}

                只输出一个 JSON 对象，不要输出 Markdown、代码块或额外解释。
                所有给用户看的标题、描述、摘要、建议必须使用中文。
                严格事实约束：
                1. 只能使用 resumeAnalysisJson、resumeSnapshotJson、jobDescriptionAnalysisJson 和 targetJob 中出现的信息。
                2. 不得编造候选人项目、公司、年限、空窗、技术栈、云平台、CI/CD、数据库、框架、指标或职责。
                3. 如果简历没有直接证据，不要写成优势；请写入 gaps 或 resumeRisks，并在 evidence 中说明“简历未提供直接证据”。
                4. strengths、gaps、resumeRisks、optimizationSuggestions 中的每一项都必须能追溯到简历或 JD 证据。
                5. evidence 字段必须引用或概括输入中的具体事实；不能只写泛泛判断。
                分数必须是 0 到 100 的整数；证据弱或缺失时必须降低相关维度分。
                顶层字段固定为：
                overallScore, dimensionScores, strengths, gaps, resumeRisks, optimizationSuggestions,
                recommendedLearningTopics, recommendedInterviewTopics, summary.
                dimensionScores 必须包含 techStack, projectExperience, businessFit, communication。
                strengths 每项必须包含 title, evidence, relatedSkills。
                gaps 每项必须包含 skillName, category, severity, targetLevel, currentLevel, description,
                evidence, recommendedActions.
                resumeRisks 每项必须包含 riskType 和 description。
                optimizationSuggestions 每项必须包含 section 和 suggestion。
                recommendedLearningTopics 和 recommendedInterviewTopics 必须是字符串数组。
                示例：
                {"overallScore":78,"dimensionScores":{"techStack":82,"projectExperience":75,"businessFit":68,"communication":80},"strengths":[{"title":"Spring Boot 项目经历与目标岗位相关","evidence":"简历项目经历中出现 Spring Boot 后端开发内容。","relatedSkills":["Spring Boot"]}],"gaps":[{"skillName":"Redis","category":"中间件","severity":"HIGH","targetLevel":4,"currentLevel":2,"description":"简历未提供 Redis 缓存一致性或分布式锁的直接项目证据。","evidence":"JD 要求 Redis；简历未提供 Redis 场景细节。","recommendedActions":["补充 Redis 项目证据","练习 Redis 场景题"]}],"resumeRisks":[{"riskType":"PROJECT_DEPTH","description":"项目描述缺少可量化结果和故障处理过程。"}],"optimizationSuggestions":[{"section":"项目经历","suggestion":"补充缓存设计、排障过程和优化指标。"}],"recommendedLearningTopics":["Redis 缓存一致性"],"recommendedInterviewTopics":["分布式锁"],"summary":"综合匹配度中等，优先补齐 Redis 与项目深度证据。"}
                """;
    }

    private String defaultSkillGapAnalyzePrompt() {
        return """
                你是资深 Java 后端求职教练。请根据匹配报告生成目标岗位能力画像 JSON。
                profileId: {{profileId}}
                matchReportId: {{matchReportId}}
                userId: {{userId}}
                resumeId: {{resumeId}}
                targetJobId: {{targetJobId}}
                jdAnalysisId: {{jdAnalysisId}}
                targetJob:
                {{targetJobJson}}
                jobDescriptionAnalysis:
                {{jobDescriptionAnalysisJson}}
                resumeAnalysis:
                {{resumeAnalysisJson}}
                resumeSnapshot:
                {{resumeSnapshotJson}}
                resumeJobMatchReport:
                {{matchReportJson}}
                resumeJobMatchDetails:
                {{matchDetailsJson}}
                matchGaps:
                {{gapsJson}}
                recommendedLearningTopics:
                {{recommendedLearningTopicsJson}}
                recommendedInterviewTopics:
                {{recommendedInterviewTopicsJson}}

                只输出一个 JSON 对象，不要输出 Markdown、代码块或解释文字。
                所有给用户看的摘要、短板说明、建议动作必须使用中文，可保留 Redis、Spring Boot、Kafka、MySQL 等技术名。
                不要编造简历和匹配报告证据之外的候选人经历；如果证据不足，请写成能力短板或风险。
                顶层字段固定为：
                profileSummary, overallLevel, overallScore, skillGaps, nextPrioritySkills, nextActions.
                overallLevel 必须是 1 到 5 的整数；overallScore 必须是 0 到 100 的整数。
                skillGaps 必须是非空数组。每个 skillGaps item 必须包含：
                skillName, category, targetLevel, currentLevel, gapLevel, confidence, severity,
                evidenceSources, gapDescription, recommendedActions, priority.
                severity 只能是 HIGH、MEDIUM、LOW。
                evidenceSources 和 recommendedActions 必须是字符串数组。
                优先引用 RESUME_JOB_MATCH 证据；建议动作要能落到学习、刷题、简历优化或定向模拟面试。
                示例：
                {"profileSummary":"Java 和 Spring Boot 基础较稳定，Redis 场景证据仍有明显缺口。","overallLevel":2,"overallScore":68,"skillGaps":[{"skillName":"Redis","category":"中间件","targetLevel":4,"currentLevel":2,"gapLevel":2,"confidence":0.82,"severity":"HIGH","evidenceSources":["RESUME_JOB_MATCH"],"gapDescription":"简历缺少缓存一致性项目证据。","recommendedActions":["复习缓存一致性方案","练习 Redis 场景题"],"priority":1}],"nextPrioritySkills":["Redis"],"nextActions":["完成 Redis 缓存一致性复盘"]}
                """;
    }

    private String defaultPracticeReviewPrompt() {
        return """
                你是资深 Java 后端面试刷题教练。请基于题目、参考答案、答案解析和用户答案生成简答题 AI 点评。
                练习记录 ID：{{recordId}}
                题目 ID：{{questionId}}
                题目标题：{{questionTitle}}
                题型：{{questionType}}
                难度：{{difficulty}}
                知识点：{{knowledgePoint}}
                经验级别：{{experienceLevel}}
                答题耗时秒数：{{answerDurationSeconds}}
                题目内容：
                {{questionContent}}
                参考答案：
                {{referenceAnswer}}
                答案解析：
                {{analysis}}
                用户答案：
                {{userAnswer}}
                要求：
                1. 只基于用户答案点评，不编造用户没有表达的经历。
                2. score 必须是 0-100 整数。
                3. level 只能是 EXCELLENT、GOOD、NORMAL、WEAK。
                4. summary 简要说明整体表现。
                5. strengths、weaknesses、improvementSuggestions、knowledgeGaps、suggestedFollowUps 必须是字符串数组。
                6. referenceComparison 对比用户答案和参考答案的关键差异。
                只输出 JSON，不要 Markdown，不要代码块。
                JSON 字段固定：{"score":76,"level":"GOOD","summary":"点评摘要","strengths":["优点"],"weaknesses":["问题"],"improvementSuggestions":["改进建议"],"referenceComparison":"参考答案对比","knowledgeGaps":["知识缺口"],"suggestedFollowUps":["后续练习题"]}
                """;
    }

    private String defaultResumeParsePrompt() {
        return """
                你是资深 Java 后端招聘简历解析助手。请从候选人简历原文中抽取结构化 JSON。
                文件名：{{originalFilename}}
                文件类型：{{fileExt}}
                简历原文：
                {{rawText}}

                要求：
                1. 只输出 JSON object，不要 Markdown，不要代码块，不要解释文字。
                2. 不要编造简历原文中没有的信息；无法识别的字段使用空字符串或空数组。
                3. JSON 顶层字段固定为：
                   - basicInfo：基本信息，包含 name、phone、email、location。
                   - targetPosition：求职岗位。
                   - skills：技能栈数组。
                   - workExperiences：工作经历数组。
                   - projectExperiences：项目经历数组。
                   - educationExperiences：教育经历数组。
                4. projectExperiences 中每个项目必须包含：
                   projectName、period、description、techStack、responsibilities、technicalDifficulties、achievements。
                5. techStack、responsibilities、technicalDifficulties、achievements 使用数组。
                输出示例：
                {"basicInfo":{"name":"","phone":"","email":"","location":""},"targetPosition":"","skills":[],"workExperiences":[],"projectExperiences":[{"projectName":"","period":"","description":"","techStack":[],"responsibilities":[],"technicalDifficulties":[],"achievements":[]}],"educationExperiences":[]}
                """;
    }

    private String defaultResumeOptimizePrompt() {
        return """
                你是资深 Java 后端招聘简历优化顾问。请基于用户已经确认的正式简历和项目经历，输出简历优化建议 JSON。
                优化记录 ID：{{optimizeRecordId}}
                用户 ID：{{userId}}
                简历 ID：{{resumeId}}
                目标岗位：{{targetPosition}}
                工作年限：{{experienceYears}}
                行业方向：{{industryDirection}}
                目标公司：{{targetCompany}}
                优化重点：{{optimizeFocus}}
                额外要求：
                {{extraRequirements}}
                简历信息：
                {{resumeJson}}
                项目经历：
                {{projectsJson}}

                安全边界：
                1. 只能基于已有 resume / resume_project 内容做表达优化。
                2. 禁止伪造公司、学历、岗位、项目、年限、职责、技术成果、业务指标。
                3. 如果原文没有数据支撑，不得生成具体数字。
                4. 如果发现过度包装风险，需要指出风险，而不是替用户补造。
                5. 允许优化表达方式、结构层次、职责边界、技术难点描述和面试追问准备。
                6. 不允许帮用户编造不存在的经历。

                只输出 JSON object，不要 Markdown，不要代码块，不要解释文字。JSON 字段固定为：
                {
                  "overallScore":82,
                  "overallComment":"简历整体评价",
                  "targetPositionMatch":{"score":80,"comment":"岗位匹配评价","matchedPoints":[],"missingPoints":[]},
                  "sectionScores":{"skillStack":78,"projectExperience":82,"responsibilityClarity":75,"technicalDepth":76,"quantifiedResults":60},
                  "problems":[{"type":"PROJECT_DESCRIPTION_TOO_GENERAL","title":"问题标题","description":"问题描述","severity":"MEDIUM"}],
                  "rewriteSuggestions":[{"section":"project","projectName":"项目名","before":"原表达","after":"优化表达","reason":"优化原因","fabricationRisk":false}],
                  "riskWarnings":[{"type":"OVER_PACKAGING","description":"风险描述","suggestion":"修改建议"}],
                  "possibleInterviewQuestions":[{"question":"可能追问","reason":"追问原因"}],
                  "nextActions":["下一步动作"]
                }
                """;
    }

    private String defaultTargetedStudyPlanPrompt() {
        return """
                You are a senior Java backend career coach. Generate a gap-driven study plan for the target job.
                learningPlanId: {{learningPlanId}}
                userId: {{userId}}
                targetJobId: {{targetJobId}}
                skillProfileId: {{skillProfileId}}
                matchReportId: {{matchReportId}}
                requestedPlanTitle: {{planTitle}}
                availableDays: {{availableDays}}
                dailyMinutes: {{dailyMinutes}}
                startDate: {{startDate}}
                targetJob:
                {{targetJobJson}}
                skillProfile:
                {{skillProfileJson}}
                selectedSkillGaps:
                {{skillGapsJson}}
                existingStudyPlans:
                {{existingStudyPlansJson}}

                Output only one JSON object. Do not output Markdown, code fences, or explanations.
                The plan must directly address the selected skill gaps and must not invent candidate experience.
                Top-level fields must be planTitle, planSummary, durationDays, stages.
                stages must be an array. Each stage must contain stageNo, stageTitle, items.
                items must be an array. Each item must contain dayOffset, title or taskTitle, description or taskDescription,
                skillName, sourceGapId, priority, estimatedMinutes, acceptance, relatedTags, and resources.
                taskType must be one of KNOWLEDGE_REVIEW, CODING_PRACTICE, PROJECT_REVIEW, INTERVIEW_PRACTICE, RESUME_IMPROVEMENT.
                priority must be one of HIGH, MEDIUM, LOW.
                sourceGapId must use the original selected skill gap id as a string.
                Example:
                {"planTitle":"Java backend Redis gap repair plan","planSummary":"Repair Redis first, then consolidate MQ.","durationDays":14,"stages":[{"stageNo":1,"stageTitle":"Redis repair","items":[{"dayOffset":1,"skillName":"Redis","sourceGapId":"12","taskTitle":"Redis cache penetration, breakdown and avalanche","taskDescription":"Summarize causes, protections, and project expression.","taskType":"KNOWLEDGE_REVIEW","priority":"HIGH","estimatedMinutes":60,"acceptance":"Can explain at least two cache protection strategies with project examples.","relatedTags":["Redis"],"resources":[]}]}]}
                """;
    }

    private String defaultQuestionRecommendationPrompt() {
        return """
                You are a senior Java backend interview training coach. Generate target-job question recommendations.
                batchId: {{batchId}}
                userId: {{userId}}
                sourceType: {{sourceType}}
                sourceId: {{sourceId}}
                targetJobId: {{targetJobId}}
                matchReportId: {{matchReportId}}
                skillProfileId: {{skillProfileId}}
                studyPlanId: {{studyPlanId}}
                strategy: {{strategy}}
                questionCount: {{questionCount}}
                difficultyPreference: {{difficultyPreference}}
                targetJob:
                {{targetJobJson}}
                matchReport:
                {{matchReportJson}}
                skillProfile:
                {{skillProfileJson}}
                skillGaps:
                {{skillGapsJson}}
                studyPlan:
                {{studyPlanJson}}
                studyTasks:
                {{studyTasksJson}}

                Output only one JSON object. Do not output Markdown, code fences, or explanations.
                Top-level field must be questions.
                questions must be an array with exactly questionCount items when possible.
                Each item must contain title, content, questionType, difficulty, skillName, gapSeverity,
                recommendReason, answerHint, and evaluatePoints.
                questionType should be SHORT_ANSWER unless a coding or scenario question is clearly better.
                difficulty must follow difficultyPreference unless the gap severity requires adjustment.
                Recommendations must directly map to skillGaps or studyTasks and must not invent candidate experience.
                Example:
                {"questions":[{"title":"Redis cache penetration production handling","content":"Explain how to prevent Redis cache penetration in a Java backend service and how you would verify the fix.","questionType":"SHORT_ANSWER","difficulty":"MEDIUM","skillName":"Redis","gapSeverity":"HIGH","recommendReason":"Redis is a high-severity target-job gap.","answerHint":"Cover Bloom filter, null cache, parameter validation, monitoring, and project tradeoffs.","evaluatePoints":"Mechanism accuracy; production scenario; boundary cases; monitoring and fallback."}]}
                """;
    }

    private String defaultLearningPlanTitle(GenerateLearningPlanDTO dto) {
        String target = firstText(dto == null ? null : dto.getTargetPosition(), "Java backend");
        int duration = normalizeDuration(dto == null ? null : dto.getExpectedDurationDays());
        return target + " " + duration + "-day study plan";
    }

    private String defaultTargetedStudyPlanTitle(GenerateTargetedStudyPlanDTO dto) {
        if (dto != null && StringUtils.hasText(dto.getPlanTitle())) {
            return dto.getPlanTitle();
        }
        int duration = dto == null || dto.getAvailableDays() == null ? 14 : dto.getAvailableDays();
        return "Gap-driven " + duration + "-day study plan";
    }

    private Long firstLong(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<JsonNode> readArrayNodes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<JsonNode> values = new java.util.ArrayList<>();
            node.forEach(values::add);
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
