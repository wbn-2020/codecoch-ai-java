package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.client.AiClient;
import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.domain.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
import com.codecoachai.ai.domain.vo.ParseResumeVO;
import com.codecoachai.ai.domain.vo.ResumeOptimizeAiResponseVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    private final AiCallLogMapper aiCallLogMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final AiProperties aiProperties;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public GenerateInterviewQuestionVO generateQuestion(GenerateInterviewQuestionDTO dto) {
        String scene = isProjectStage(dto.getStageType()) ? SCENE_PROJECT_QUESTION : SCENE_QUESTION;
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(scene);
        String prompt = render(questionPromptContent(scene, template), variables(dto, null));
        String rawResponse = null;
        try {
            GenerateInterviewQuestionVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockQuestion(dto, scene)
                    : parseQuestion(rawResponse = aiClient.chat(prompt), scene);
            saveLog(scene, template, prompt, toJson(vo), businessId(dto.getQuestionId()), start,
                    null, null, AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            GenerateInterviewQuestionVO fallback = mockQuestion(dto, scene);
            saveLog(scene, template, prompt, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            return fallback;
        }
    }

    @Override
    public EvaluateAnswerVO evaluate(EvaluateAnswerDTO dto) {
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(SCENE_EVALUATE);
        String prompt = render(evaluatePromptContent(template, dto), variables(null, dto));
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
            saveLog(SCENE_EVALUATE, template, prompt, mergeRawAndFinal(rawResponse, vo),
                    businessId(dto.getQuestionId()), start, null, null, AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            EvaluateAnswerVO fallback = mockEvaluate(dto);
            saveLog(SCENE_EVALUATE, template, prompt, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            return fallback;
        }
    }

    @Override
    public GenerateFollowUpVO generateFollowUp(GenerateFollowUpDTO dto) {
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(SCENE_FOLLOW_UP);
        String prompt = render(templateContent(template, defaultFollowUpPrompt()), variables(dto));
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
            saveLog(SCENE_FOLLOW_UP, template, prompt, mergeRawAndFinal(rawResponse, vo),
                    businessId(dto.getQuestionId()), start, null, null, AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            GenerateFollowUpVO fallback = new GenerateFollowUpVO();
            fallback.setFollowUpQuestion(buildFallbackFollowUp(dto));
            fallback.setReason(markFallback("AI 追问调用失败，使用本地兜底追问：" + ex.getMessage()));
            fallback.setRelatedToOriginalQuestion(true);
            fallback.setFollowUpValid(true);
            saveLog(SCENE_FOLLOW_UP, template, prompt, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getQuestionId()), start, ex.getMessage(), null, failureType(ex));
            return fallback;
        }
    }

    @Override
    public GenerateReportVO generateReport(GenerateReportDTO dto) {
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(SCENE_REPORT);
        String prompt = render(reportPromptContent(template, dto), variables(dto));
        String rawResponse = null;
        try {
            GenerateReportVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockReport()
                    : parseReport(rawResponse = aiClient.chat(prompt));
            saveLog(SCENE_REPORT, template, prompt, mergeRawAndFinal(rawResponse, vo),
                    businessId(dto.getInterviewId()), start, null, dto.getUserId(), AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            GenerateReportVO fallback = mockReport();
            saveLog(SCENE_REPORT, template, prompt, mergeRawAndFinal(rawResponse, fallback),
                    businessId(dto.getInterviewId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            return fallback;
        }
    }

    @Override
    public ParseResumeVO parseResume(ParseResumeDTO dto) {
        validateParseResumeDTO(dto);
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(SCENE_RESUME_PARSE);
        String prompt = render(resumeParsePromptContent(template), variables(dto));
        String rawResponse = null;
        try {
            String structuredJson = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockResumeStructuredJson()
                    : parseResumeStructuredJson(rawResponse = aiClient.chat(prompt));
            ParseResumeVO vo = new ParseResumeVO();
            vo.setStructuredJson(structuredJson);
            saveLog(SCENE_RESUME_PARSE, template, prompt, structuredJson,
                    businessId(dto.getAnalysisRecordId()), start, null, dto.getUserId(), AiFailureType.NONE);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(SCENE_RESUME_PARSE, template, prompt, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getAnalysisRecordId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toBusinessException(ex);
        }
    }

    @Override
    public ResumeOptimizeAiResponseVO optimizeResume(ResumeOptimizeAiRequestDTO dto) {
        validateResumeOptimizeDTO(dto);
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(SCENE_RESUME_OPTIMIZE);
        String prompt = render(resumeOptimizePromptContent(template), variables(dto));
        String rawResponse = null;
        try {
            String resultJson = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockResumeOptimizeJson()
                    : parseResumeOptimizeJson(rawResponse = aiClient.chat(prompt));
            Long logId = saveLog(SCENE_RESUME_OPTIMIZE, template, prompt, resultJson,
                    businessId(dto.getOptimizeRecordId()), start, null, dto.getUserId(), AiFailureType.NONE);
            ResumeOptimizeAiResponseVO vo = new ResumeOptimizeAiResponseVO();
            vo.setResultJson(resultJson);
            vo.setAiCallLogId(logId);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(SCENE_RESUME_OPTIMIZE, template, prompt, firstText(rawResponse, ex.getMessage()),
                    businessId(dto.getOptimizeRecordId()), start, ex.getMessage(), dto.getUserId(), failureType(ex));
            throw toResumeOptimizeBusinessException(ex);
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

    private PromptTemplate enabledTemplate(String scene) {
        return promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getScene, scene)
                .eq(PromptTemplate::getStatus, CommonConstants.YES)
                .orderByDesc(PromptTemplate::getUpdatedAt)
                .last("limit 1"));
    }

    private String templateContent(PromptTemplate template, String fallback) {
        if (template == null) {
            return fallback;
        }
        if (StringUtils.hasText(template.getTemplateContent())) {
            return template.getTemplateContent();
        }
        return StringUtils.hasText(template.getContent()) ? template.getContent() : fallback;
    }

    private String questionPromptContent(String scene, PromptTemplate template) {
        if (SCENE_PROJECT_QUESTION.equals(scene)) {
            return templateContent(template, defaultProjectQuestionPrompt());
        }
        return templateContent(template, defaultQuestionPrompt());
    }

    private String evaluatePromptContent(PromptTemplate template, EvaluateAnswerDTO dto) {
        String content = templateContent(template, defaultEvaluatePrompt());
        if (!isProjectStage(dto == null ? null : dto.getStageType(), dto == null ? null : dto.getCurrentStage())
                && !StringUtils.hasText(dto == null ? null : dto.getProjectContent())) {
            return content;
        }
        return """
                这是项目深挖阶段的回答评分。评分必须重点关注：项目理解、技术深度、表达清晰度、问题解决能力、架构思维。
                项目信息：
                {{projectContent}}
                请避免只给通用 Java 评分，必须结合候选人在项目背景、技术架构、数据库设计、核心难点、性能优化、故障排查、技术取舍、个人职责上的表达进行判断。
                """
                + "\n" + content;
    }

    private String reportPromptContent(PromptTemplate template, GenerateReportDTO dto) {
        String content = templateContent(template, defaultReportPrompt());
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
                - 只输出 JSON，不要 Markdown 代码块，不要解释文字。
                """
                + "\n" + projectBlock
                + "\n" + content;
    }

    private String resumeParsePromptContent(PromptTemplate template) {
        return templateContent(template, defaultResumeParsePrompt());
    }

    private String resumeOptimizePromptContent(PromptTemplate template) {
        return templateContent(template, defaultResumeOptimizePrompt());
    }

    private String render(String template, Map<String, String> variables) {
        String prompt = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return prompt;
    }

    private Map<String, String> variables(GenerateInterviewQuestionDTO dto, EvaluateAnswerDTO answerDTO) {
        Map<String, String> values = new LinkedHashMap<>();
        if (dto != null) {
            values.put("targetPosition", dto.getTargetPosition());
            values.put("experienceLevel", dto.getExperienceLevel());
            values.put("industry", dto.getIndustryDirection());
            values.put("industryDirection", dto.getIndustryDirection());
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

    private GenerateReportVO mockReport() {
        GenerateReportVO vo = new GenerateReportVO();
        vo.setTotalScore(82);
        if (applyProjectAwareMockReport(vo)) {
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
        return vo;
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
            return "请结合项目经历【" + project + "】，围绕【" + focus
                    + "】追问一个具体项目深挖问题，要求候选人说明背景、方案、取舍、落地细节和量化结果。";
        }
        return "请结合" + firstText(dto == null ? null : dto.getQuestionTitle(),
                dto == null ? null : dto.getQuestionContent(),
                "当前 Java 后端主题") + "说明核心原理、适用场景和生产实践注意点。";
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

    private int scoreByLength(String answer) {
        int answerLength = answer == null ? 0 : answer.trim().length();
        return Math.min(95, Math.max(55, answerLength / 2));
    }

    private Long saveLog(String scene, PromptTemplate template, String prompt, String response, String businessId,
                         long startMillis, String errorMessage, Long explicitUserId, AiFailureType failureType) {
        AiCallLog log = new AiCallLog();
        long elapsed = System.currentTimeMillis() - startMillis;
        AiFailureType resolvedFailureType = failureType == null ? AiFailureType.UNKNOWN_ERROR : failureType;
        log.setUserId(explicitUserId == null ? LoginUserContext.getUserId() : explicitUserId);
        log.setScene(scene);
        log.setModelName(Boolean.TRUE.equals(aiProperties.getMockEnabled())
                ? aiProperties.getModel() + "(mock)"
                : aiProperties.getModel());
        log.setPromptTemplateId(template == null ? null : template.getId());
        log.setRequestPrompt(prompt);
        log.setResponseContent(response);
        log.setBusinessId(businessId);
        log.setRequestBody(buildRequestMetadata(scene, template, prompt, resolvedFailureType));
        log.setResponseBody(buildResponseMetadata(response, elapsed, errorMessage, resolvedFailureType));
        log.setElapsedMs(elapsed);
        log.setCostMillis(elapsed);
        log.setStatus(errorMessage == null ? CommonConstants.YES : CommonConstants.NO);
        log.setErrorMessage(errorMessage);
        aiCallLogMapper.insert(log);
        return log.getId();
    }

    private String buildRequestMetadata(String scene, PromptTemplate template, String prompt, AiFailureType failureType) {
        Map<String, Object> metadata = baseMetadata(scene, failureType);
        metadata.put("promptTemplateId", template == null ? null : template.getId());
        metadata.put("promptTemplateVersion", template == null ? null : template.getVersion());
        metadata.put("timeoutSeconds", aiProperties.getTimeoutSeconds());
        metadata.put("prompt", prompt);
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
