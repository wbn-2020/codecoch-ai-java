package com.codecoachai.ai.service.impl;

import com.codecoachai.ai.client.AiClient;
import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.domain.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateLearningPlanVO;
import com.codecoachai.ai.domain.vo.GenerateQuestionDraftVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
import com.codecoachai.ai.domain.vo.ParseResumeVO;
import com.codecoachai.ai.domain.vo.PracticeReviewVO;
import com.codecoachai.ai.domain.vo.QuestionDraftItemVO;
import com.codecoachai.ai.domain.vo.ResumeOptimizeAiResponseVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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

    private final AiCallLogMapper aiCallLogMapper;
    private final PromptRenderService promptRenderService;
    private final AiProperties aiProperties;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public GenerateInterviewQuestionVO generateQuestion(GenerateInterviewQuestionDTO dto) {
        String scene = isProjectStage(dto.getStageType()) ? SCENE_PROJECT_QUESTION : SCENE_QUESTION;
        long start = System.currentTimeMillis();
        Map<String, String> variables = variables(dto, null);
        PromptRenderResult promptResult = promptRenderService.render(scene, questionPromptContent(scene),
                variables, industryContextBlock(dto == null ? null : dto.getIndustryContext()), null);
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            GenerateInterviewQuestionVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockQuestion(dto, scene)
                    : parseQuestion(rawResponse = aiClient.chat(prompt), scene);
            saveLog(promptResult, toJson(vo), businessId(dto.getQuestionId()), start,
                    null, null, AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
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
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            GenerateQuestionDraftVO vo;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockQuestionDrafts(dto);
                rawResponse = toJson(Map.of("questions", vo.getQuestions()));
            } else {
                rawResponse = aiClient.chat(prompt);
                vo = parseQuestionDrafts(rawResponse, dto);
            }
            Long logId = saveLog(promptResult, rawResponse,
                    dto.getBatchId(), start, null, dto.getAdminUserId(), AiFailureType.NONE);
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, firstText(ex.getMessage(), "AI question generation failed"));
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
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockPracticeReview(dto);
                rawResponse = toJson(vo);
            } else {
                rawResponse = aiClient.chat(promptResult.getRenderedPrompt());
                vo = parsePracticeReview(rawResponse, dto);
            }
            Long logId = saveLog(promptResult, rawResponse, businessId(dto.getRecordId()),
                    start, null, dto.getUserId(), AiFailureType.NONE);
            vo.setAiCallLogId(logId);
            vo.setRawResponse(rawResponse);
            return vo;
        } catch (RuntimeException ex) {
            PracticeReviewVO fallback = mockPracticeReview(dto);
            Long logId = saveLog(promptResult, firstText(rawResponse, ex.getMessage()), businessId(dto.getRecordId()),
                    start, ex.getMessage(), dto.getUserId(), failureType(ex));
            fallback.setAiCallLogId(logId);
            fallback.setRawResponse(firstText(rawResponse, ex.getMessage()));
            return fallback;
        }
    }

    @Override
    public EvaluateAnswerVO evaluate(EvaluateAnswerDTO dto) {
        long start = System.currentTimeMillis();
        Map<String, String> variables = variables(null, dto);
        PromptRenderResult promptResult = promptRenderService.render(SCENE_EVALUATE, defaultEvaluatePrompt(),
                variables, evaluatePromptPrefix(dto), null);
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            EvaluateAnswerVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockEvaluate(dto)
                    : parseEvaluate(rawResponse = aiClient.chat(prompt), dto);
            if (Boolean.TRUE.equals(vo.getFollowUpValid()) && isInvalidFollowUp(vo.getFollowUpQuestion(), dto)) {
                vo.setFollowUpQuestion(buildFallbackFollowUp(dto));
                vo.setFollowUpReason(markFallback(firstText(vo.getFollowUpReason(), "AI 追问无效，使用本地兜底追问")));
                vo.setFollowUpValid(true);
            }
            saveLog(promptResult, mergeRawAndFinal(rawResponse, vo),
                    businessId(dto.getQuestionId()), start, null, null, AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            EvaluateAnswerVO fallback = mockEvaluate(dto);
            saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            return fallback;
        }
    }

    @Override
    public GenerateFollowUpVO generateFollowUp(GenerateFollowUpDTO dto) {
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_FOLLOW_UP, defaultFollowUpPrompt(),
                variables(dto), industryContextBlock(dto == null ? null : dto.getIndustryContext()), null);
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            GenerateFollowUpVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockFollowUp(dto)
                    : parseFollowUp(rawResponse = aiClient.chat(prompt), dto);
            if (isInvalidFollowUp(vo.getFollowUpQuestion(), dto)) {
                vo.setFollowUpQuestion(buildFallbackFollowUp(dto));
                vo.setReason(markFallback(firstText(vo.getReason(), "AI 追问无效，使用本地兜底追问")));
                vo.setRelatedToOriginalQuestion(true);
                vo.setFollowUpValid(true);
            }
            saveLog(promptResult, mergeRawAndFinal(rawResponse, vo),
                    businessId(dto.getQuestionId()), start, null, null, AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            GenerateFollowUpVO fallback = new GenerateFollowUpVO();
            fallback.setFollowUpQuestion(buildFallbackFollowUp(dto));
            fallback.setReason(markFallback("AI 追问调用失败，使用本地兜底追问：" + ex.getMessage()));
            fallback.setRelatedToOriginalQuestion(true);
            fallback.setFollowUpValid(true);
            saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            return fallback;
        }
    }

    @Override
    public GenerateReportVO generateReport(GenerateReportDTO dto) {
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_REPORT, defaultReportPrompt(),
                variables(dto), reportPromptPrefix(dto), null);
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            GenerateReportVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockReport(dto)
                    : parseReport(rawResponse = aiClient.chat(prompt));
            saveLog(promptResult, mergeRawAndFinal(rawResponse, vo),
                    businessId(dto.getInterviewId()), start, null, dto.getUserId(), AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            GenerateReportVO fallback = mockReport(dto);
            saveLog(promptResult, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getInterviewId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            return fallback;
        }
    }

    @Override
    public ParseResumeVO parseResume(ParseResumeDTO dto) {
        validateParseResumeDTO(dto);
        long start = System.currentTimeMillis();
        PromptRenderResult promptResult = promptRenderService.render(SCENE_RESUME_PARSE, resumeParsePromptContent(),
                variables(dto));
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            String structuredJson = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockResumeStructuredJson()
                    : parseResumeStructuredJson(rawResponse = aiClient.chat(prompt));
            ParseResumeVO vo = new ParseResumeVO();
            vo.setStructuredJson(structuredJson);
            saveLog(promptResult, structuredJson,
                    businessId(dto.getAnalysisRecordId()), start, null, dto.getUserId(), AiFailureType.NONE);
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
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            String resultJson = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockResumeOptimizeJson()
                    : parseResumeOptimizeJson(rawResponse = aiClient.chat(prompt));
            Long logId = saveLog(promptResult, resultJson,
                    businessId(dto.getOptimizeRecordId()), start, null, dto.getUserId(), AiFailureType.NONE);
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
        String prompt = promptResult.getRenderedPrompt();
        String rawResponse = null;
        try {
            GenerateLearningPlanVO vo;
            if (Boolean.TRUE.equals(aiProperties.getMockEnabled())) {
                vo = mockLearningPlan(dto);
                rawResponse = toJson(vo);
            } else {
                rawResponse = aiClient.chat(prompt);
                vo = parseLearningPlan(rawResponse, dto);
            }
            Long logId = saveLog(promptResult, rawResponse,
                    businessId(dto.getLearningPlanId()), start, null, dto.getUserId(), AiFailureType.NONE);
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(promptResult, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getLearningPlanId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toBusinessException(ex);
        }
    }

    private void validateParseResumeDTO(ParseResumeDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "request body is required");
        }
        if (dto.getAnalysisRecordId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "analysisRecordId is required");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        if (!StringUtils.hasText(dto.getRawText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "rawText is required");
        }
    }

    private void validateResumeOptimizeDTO(ResumeOptimizeAiRequestDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "request body is required");
        }
        if (dto.getOptimizeRecordId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "optimizeRecordId is required");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        if (dto.getResumeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resumeId is required");
        }
        if (dto.getResume() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume snapshot is required");
        }
    }

    private void validateQuestionDraftDTO(GenerateQuestionDraftDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "request body is required");
        }
        if (!StringUtils.hasText(dto.getBatchId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "batchId is required");
        }
        if (dto.getAdminUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "adminUserId is required");
        }
        int count = dto.getCount() == null ? 5 : dto.getCount();
        if (count < 1 || count > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "count must be between 1 and 20");
        }
    }

    private void validateLearningPlanDTO(GenerateLearningPlanDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "request body is required");
        }
        if (dto.getLearningPlanId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "learningPlanId is required");
        }
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        if (dto.getReportId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "reportId is required");
        }
        if (!StringUtils.hasText(dto.getInterviewSummary()) && !StringUtils.hasText(dto.getWeaknessSummary())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "interview summary or weakness summary is required");
        }
    }

    private BusinessException toBusinessException(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        if (ex instanceof AiProviderException aiProviderException) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR, aiProviderException.getMessage());
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, firstText(ex.getMessage(), "Resume parse failed"));
    }

    private BusinessException toResumeOptimizeBusinessException(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        if (ex instanceof AiProviderException aiProviderException) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR, aiProviderException.getMessage());
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, firstText(ex.getMessage(), "Resume optimize failed"));
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
                + "\n" + projectBlock
                + "\n";
    }

    private String learningPlanPromptContent() {
        return defaultLearningPlanPrompt();
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
        values.put("rawText", dto.getRawText());
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
        values.put("resumeJson", toJson(dto.getResume()));
        values.put("projectsJson", toJson(dto.getProjects()));
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

    private void validatePracticeReviewDTO(PracticeReviewDTO dto) {
        if (dto == null || dto.getRecordId() == null || dto.getQuestionId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "practice record and question are required");
        }
        if (!StringUtils.hasText(dto.getAnswerContent())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "answerContent is required");
        }
        if (dto.getAnswerContent().length() > 5000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "answerContent length must be less than 5000");
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
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI question generation response must be a JSON object");
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

    private String requireText(JsonNode json, String fieldName) {
        String value = json.path(fieldName).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR,
                    "AI question generation item missing field: " + fieldName);
        }
        return value;
    }

    private String requireLearningText(JsonNode json, String fieldName) {
        String value = json.path(fieldName).asText(null);
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
            fallback.setReason("非标准 JSON 响应解析兜底");
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
        } catch (BusinessException ex) {
            GenerateReportVO fallback = mockReport();
            fallback.setSummary(firstText(raw, fallback.getSummary()));
            fallback.setReportContent(firstText(raw, fallback.getReportContent()));
            return fallback;
        }
        GenerateReportVO vo = mockReport();
        if (json.path("totalScore").isNumber()) {
            vo.setTotalScore(json.path("totalScore").asInt());
        }
        vo.setSummary(firstText(json.path("summary").asText(null), vo.getSummary()));
        vo.setStageScores(jsonOrDefault(json.path("stageScores"), vo.getStageScores()));
        vo.setWeakPoints(jsonOrDefault(json.path("weakPoints"), vo.getWeakPoints()));
        vo.setStrengths(jsonOrDefault(json.path("strengths"), vo.getStrengths()));
        vo.setWeaknesses(firstText(json.path("weaknesses").asText(null), vo.getWeaknesses()));
        vo.setMainProblems(jsonOrDefault(json.path("mainProblems"), vo.getMainProblems()));
        vo.setProjectProblems(jsonOrDefault(json.path("projectProblems"), vo.getProjectProblems()));
        vo.setSuggestions(jsonOrDefault(json.path("suggestions"), vo.getSuggestions()));
        vo.setReviewSuggestions(jsonOrDefault(json.path("reviewSuggestions"), vo.getReviewSuggestions()));
        vo.setRecommendedQuestions(jsonOrDefault(json.path("recommendedQuestions"), vo.getRecommendedQuestions()));
        vo.setQaReview(jsonOrDefault(json.path("qaReview"), vo.getQaReview()));
        vo.setReportContent(firstText(json.path("reportContent").asText(null), vo.getSummary()));
        return vo;
    }

    private GenerateLearningPlanVO parseLearningPlan(String raw, GenerateLearningPlanDTO dto) {
        JsonNode json = parseJson(raw);
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI learning plan response must be a JSON object");
        }
        JsonNode stagesNode = json.path("stages");
        if (!stagesNode.isArray() || stagesNode.isEmpty()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI learning plan response missing stages array");
        }
        GenerateLearningPlanVO vo = new GenerateLearningPlanVO();
        vo.setPlanTitle(firstText(json.path("planTitle").asText(null), defaultLearningPlanTitle(dto)));
        vo.setPlanSummary(firstText(json.path("planSummary").asText(null), "Study plan generated from interview report"));
        vo.setDurationDays(json.path("durationDays").isNumber()
                ? normalizeDuration(json.path("durationDays").asInt())
                : normalizeDuration(dto.getExpectedDurationDays()));
        List<GenerateLearningPlanVO.StageVO> stages = new java.util.ArrayList<>();
        for (JsonNode stageNode : stagesNode) {
            if (stageNode == null || !stageNode.isObject()) {
                throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI learning plan stage must be object");
            }
            JsonNode itemsNode = stageNode.path("items");
            if (!itemsNode.isArray() || itemsNode.isEmpty()) {
                throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI learning plan stage missing items array");
            }
            GenerateLearningPlanVO.StageVO stage = new GenerateLearningPlanVO.StageVO();
            stage.setStageNo(stageNode.path("stageNo").isNumber() ? stageNode.path("stageNo").asInt() : stages.size() + 1);
            stage.setStageTitle(firstText(stageNode.path("stageTitle").asText(null), "Stage " + stage.getStageNo()));
            List<GenerateLearningPlanVO.ItemVO> items = new java.util.ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                if (itemNode == null || !itemNode.isObject()) {
                    throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI learning plan item must be object");
                }
                GenerateLearningPlanVO.ItemVO item = new GenerateLearningPlanVO.ItemVO();
                item.setKnowledgePoint(itemNode.path("knowledgePoint").asText(null));
                item.setTaskTitle(requireLearningText(itemNode, "taskTitle"));
                item.setTaskDescription(requireLearningText(itemNode, "taskDescription"));
                item.setTaskType(normalizeTaskType(itemNode.path("taskType").asText(null)));
                item.setPriority(normalizePriority(itemNode.path("priority").asText(null)));
                item.setEstimatedHours(itemNode.path("estimatedHours").isNumber()
                        ? Math.max(1, itemNode.path("estimatedHours").asInt())
                        : 1);
                item.setRelatedQuestionIds(longArray(itemNode.path("relatedQuestionIds")));
                item.setRelatedTags(textArray(itemNode.path("relatedTags")));
                item.setResources(textArray(itemNode.path("resources")));
                items.add(item);
            }
            stage.setItems(items);
            stages.add(stage);
        }
        vo.setStages(stages);
        return vo;
    }

    private String parseResumeStructuredJson(String raw) {
        JsonNode json = parseJson(raw);
        validateResumeStructuredJson(json);
        return json.toString();
    }

    private String parseResumeOptimizeJson(String raw) {
        JsonNode json = parseJson(raw);
        if (json == null || !json.isObject()) {
            throw new AiProviderException(AiFailureType.PARSE_ERROR, "AI resume optimize response must be a JSON object");
        }
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI response is empty");
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
        List<QuestionDraftItemVO> questions = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            QuestionDraftItemVO item = new QuestionDraftItemVO();
            item.setTitle(topic + " 核心面试题 " + i);
            item.setContent("面向" + targetPosition + "，请说明 " + topic + " 的核心原理、典型生产场景、边界问题和工程取舍。");
            item.setReferenceAnswer(topic + " 需要从基本原理、关键机制、常见异常场景、可观测性和生产取舍几个角度回答。");
            item.setAnalysis("优秀回答应覆盖该机制为什么存在、如何工作、什么时候会失效，以及在真实 Java 后端系统中如何验证和排查。");
            item.setDifficulty(difficulty);
            item.setQuestionType(questionType);
            item.setFollowUpQuestions(Boolean.TRUE.equals(dto.getGenerateFollowUps())
                    ? List.of("你会如何在线上验证这个机制是否按预期工作？", "这个机制常见的失效场景有哪些？")
                    : List.of());
            item.setTagSuggestions(Boolean.TRUE.equals(dto.getGenerateTagSuggestions())
                    ? List.of(topic, "Java", "后端")
                    : List.of());
            item.setCategorySuggestion(Boolean.TRUE.equals(dto.getGenerateCategorySuggestion()) ? "Java 后端" : null);
            item.setGroupSuggestion(topic);
            questions.add(item);
        }
        GenerateQuestionDraftVO vo = new GenerateQuestionDraftVO();
        vo.setBatchId(dto.getBatchId());
        vo.setQuestions(questions);
        return vo;
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
        vo.setReason("mock 模式本地相关性追问");
        vo.setRelatedToOriginalQuestion(true);
        vo.setFollowUpValid(true);
        return vo;
    }

    private GenerateReportVO mockReport(GenerateReportDTO dto) {
        GenerateReportVO vo = new GenerateReportVO();
        vo.setTotalScore(82);
        if (applyProjectAwareMockReport(vo)) {
            applyIndustryAwareMockReport(vo, dto);
            return vo;
        }
        vo.setSummary("本场 V1 模拟面试已完成，综合得分 82。总分由回答完整度、关键知识点覆盖、项目表达和工程权衡四个维度综合给出。");
        vo.setStageScores("{\"technical\":82,\"project\":82}");
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
        vo.setPlanSummary("A focused " + duration + "-day plan generated from interview weaknesses, resume signals, and target role.");
        vo.setDurationDays(duration);

        GenerateLearningPlanVO.ItemVO foundations = learningItem("Java core and collection internals",
                "Review HashMap, ArrayList, and common collection trade-offs",
                "Summarize key internals, edge cases, and interview-ready examples.",
                "KNOWLEDGE_REVIEW", "HIGH", 2, List.of("Java", "Collections"));
        GenerateLearningPlanVO.ItemVO concurrency = learningItem("Java concurrency",
                "Practice thread pool and lock scenario questions",
                "Prepare answers that cover parameters, rejection policy, monitoring, and production risks.",
                "INTERVIEW_PRACTICE", "HIGH", 2, List.of("Concurrency", "ThreadPool"));
        GenerateLearningPlanVO.StageVO stageOne = learningStage(1, "Weakness repair", List.of(foundations, concurrency));

        GenerateLearningPlanVO.ItemVO project = learningItem("Project review",
                "Refine one project story with measurable outcomes",
                "Rewrite background, responsibility, technical challenge, trade-off, and result.",
                "PROJECT_REVIEW", "MEDIUM", 2, List.of("Project", "Resume"));
        GenerateLearningPlanVO.ItemVO database = learningItem("Database and cache",
                "Review MySQL index and Redis consistency scenarios",
                "Prepare answers for index failure, slow query diagnosis, cache aside, and consistency boundaries.",
                "CODING_PRACTICE", "MEDIUM", 2, List.of("MySQL", "Redis"));
        GenerateLearningPlanVO.StageVO stageTwo = learningStage(2, "Scenario consolidation", List.of(project, database));

        vo.setStages(List.of(stageOne, stageTwo));
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
        vo.setSummary("本场模拟面试已完成，综合得分 82。总分由回答完整度、关键知识点覆盖、项目表达、技术深度和工程取舍能力综合给出。");
        vo.setStageScores("{\"technical\":82,\"projectExpression\":82,\"architectureThinking\":80}");
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
            vo.setFollowUpReason(markFallback(firstText(vo.getFollowUpReason(), "评分追问缺失或无效，使用本地兜底追问")));
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
        return "[fallback] " + firstText(reason, "使用本地兜底追问");
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
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
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
                字段固定：
                {"totalScore":82,"summary":"总分来源说明","strengths":[],"weakPoints":[],"mainProblems":[],"projectProblems":[],"reviewSuggestions":[],"recommendedQuestions":[],"qaReview":[],"stageScores":{},"reportContent":"报告正文"}
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
                JSON 字段固定：{"score":82,"level":"GOOD","summary":"点评摘要","strengths":["优点"],"weaknesses":["问题"],"improvementSuggestions":["改进建议"],"referenceComparison":"参考答案对比","knowledgeGaps":["知识缺口"],"suggestedFollowUps":["后续练习题"]}
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

    private String defaultLearningPlanTitle(GenerateLearningPlanDTO dto) {
        String target = firstText(dto == null ? null : dto.getTargetPosition(), "Java backend");
        int duration = normalizeDuration(dto == null ? null : dto.getExpectedDurationDays());
        return target + " " + duration + "-day study plan";
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
