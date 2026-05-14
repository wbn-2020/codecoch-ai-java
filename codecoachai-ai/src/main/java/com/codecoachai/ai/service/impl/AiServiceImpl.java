package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.client.AiClient;
import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
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
import java.util.Map;
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
        String prompt = render(templateContent(template, defaultQuestionPrompt()), variables(dto, null));
        String rawResponse = null;
        try {
            GenerateInterviewQuestionVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockQuestion(dto, scene)
                    : parseQuestion(rawResponse = aiClient.chat(prompt), scene);
            saveLog(scene, template, prompt, toJson(vo), businessId(dto.getQuestionId()), start, null);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(scene, template, prompt, rawResponse, businessId(dto.getQuestionId()), start, ex.getMessage());
            throw businessAiException(ex);
        }
    }

    @Override
    public EvaluateAnswerVO evaluate(EvaluateAnswerDTO dto) {
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(SCENE_EVALUATE);
        String prompt = render(templateContent(template, defaultEvaluatePrompt()), variables(null, dto));
        String rawResponse = null;
        try {
            EvaluateAnswerVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockEvaluate(dto)
                    : parseEvaluate(rawResponse = aiClient.chat(prompt), dto);
            saveLog(SCENE_EVALUATE, template, prompt, toJson(vo), businessId(dto.getQuestionId()), start, null);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(SCENE_EVALUATE, template, prompt, rawResponse, businessId(dto.getQuestionId()), start, ex.getMessage());
            throw businessAiException(ex);
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
            saveLog(SCENE_FOLLOW_UP, template, prompt, toJson(vo), businessId(dto.getQuestionId()), start, null);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(SCENE_FOLLOW_UP, template, prompt, rawResponse, businessId(dto.getQuestionId()), start, ex.getMessage());
            throw businessAiException(ex);
        }
    }

    @Override
    public GenerateReportVO generateReport(GenerateReportDTO dto) {
        long start = System.currentTimeMillis();
        PromptTemplate template = enabledTemplate(SCENE_REPORT);
        String prompt = render(templateContent(template, defaultReportPrompt()), variables(dto));
        String rawResponse = null;
        try {
            GenerateReportVO vo = Boolean.TRUE.equals(aiProperties.getMockEnabled())
                    ? mockReport()
                    : parseReport(rawResponse = aiClient.chat(prompt));
            saveLog(SCENE_REPORT, template, prompt, toJson(vo), businessId(dto.getInterviewId()), start, null);
            return vo;
        } catch (RuntimeException ex) {
            saveLog(SCENE_REPORT, template, prompt, rawResponse, businessId(dto.getInterviewId()), start, ex.getMessage());
            throw businessAiException(ex);
        }
    }

    private boolean isProjectStage(String stageType) {
        String value = stageType == null ? "" : stageType.toUpperCase();
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
            values.put("questionContent", answerDTO.getQuestionContent());
            values.put("userAnswer", answerDTO.getAnswerContent());
            values.put("referenceAnswer", answerDTO.getReferenceAnswer());
            values.put("historySummary", answerDTO.getHistorySummary());
        }
        return values;
    }

    private Map<String, String> variables(GenerateFollowUpDTO dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("stageName", dto.getCurrentStage());
        values.put("currentStage", dto.getCurrentStage());
        values.put("currentQuestion", dto.getQuestionTitle());
        values.put("questionContent", dto.getQuestionContent());
        values.put("userAnswer", dto.getAnswerContent());
        values.put("historySummary", dto.getHistorySummary());
        values.put("aiComment", dto.getComment());
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

    private GenerateInterviewQuestionVO parseQuestion(String raw, String scene) {
        JsonNode json = parseJson(raw);
        GenerateInterviewQuestionVO vo = new GenerateInterviewQuestionVO();
        String content = firstText(json.path("questionContent").asText(null), json.path("questionText").asText(null), raw);
        vo.setQuestionContent(content);
        vo.setQuestionText(content);
        vo.setScene(scene);
        return vo;
    }

    private EvaluateAnswerVO parseEvaluate(String raw, EvaluateAnswerDTO dto) {
        JsonNode json = parseJson(raw);
        EvaluateAnswerVO vo = new EvaluateAnswerVO();
        vo.setScore(json.path("score").isNumber() ? json.path("score").asInt() : scoreByLength(dto.getAnswerContent()));
        vo.setComment(firstText(json.path("comment").asText(null), json.path("aiComment").asText(null), raw));
        vo.setNextAction(firstText(json.path("nextAction").asText(null), vo.getScore() < 70 ? "FOLLOW_UP" : "NEXT_QUESTION"));
        vo.setKnowledgePoints(json.path("knowledgePoints").asText(null));
        return vo;
    }

    private GenerateFollowUpVO parseFollowUp(String raw, GenerateFollowUpDTO dto) {
        JsonNode json = parseJson(raw);
        GenerateFollowUpVO vo = new GenerateFollowUpVO();
        vo.setFollowUpQuestion(firstText(json.path("followUpQuestion").asText(null), json.path("questionContent").asText(null),
                "请进一步说明：" + firstText(dto.getQuestionTitle(), "当前问题")));
        return vo;
    }

    private GenerateReportVO parseReport(String raw) {
        JsonNode json = parseJson(raw);
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

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(extractJson(raw));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI response is not valid JSON");
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
        vo.setComment(score >= 75 ? "回答覆盖了主要知识点，建议补充项目落地细节。" : "回答偏简略，建议补充原理、边界条件和实践案例。");
        vo.setNextAction(score < 75 && (dto.getFollowUpCount() == null || dto.getFollowUpCount() < 2) ? "FOLLOW_UP" : "NEXT_QUESTION");
        vo.setKnowledgePoints(firstText(dto.getQuestionTitle(), "Java 后端基础"));
        return vo;
    }

    private GenerateFollowUpVO mockFollowUp(GenerateFollowUpDTO dto) {
        GenerateFollowUpVO vo = new GenerateFollowUpVO();
        vo.setFollowUpQuestion("请进一步结合生产项目说明：" + firstText(dto.getQuestionTitle(), dto.getQuestionContent(), "当前问题"));
        return vo;
    }

    private GenerateReportVO mockReport() {
        GenerateReportVO vo = new GenerateReportVO();
        vo.setTotalScore(82);
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

    private int scoreByLength(String answer) {
        int answerLength = answer == null ? 0 : answer.trim().length();
        return Math.min(95, Math.max(55, answerLength / 2));
    }

    private void saveLog(String scene, PromptTemplate template, String prompt, String response, String businessId,
                         long startMillis, String errorMessage) {
        AiCallLog log = new AiCallLog();
        log.setUserId(LoginUserContext.getUserId());
        log.setScene(scene);
        log.setModelName(Boolean.TRUE.equals(aiProperties.getMockEnabled())
                ? aiProperties.getModel() + "(mock)"
                : aiProperties.getModel());
        log.setPromptTemplateId(template == null ? null : template.getId());
        log.setRequestPrompt(prompt);
        log.setResponseContent(response);
        log.setBusinessId(businessId);
        log.setRequestBody(prompt);
        log.setResponseBody(response);
        long elapsed = System.currentTimeMillis() - startMillis;
        log.setElapsedMs(elapsed);
        log.setCostMillis(elapsed);
        log.setStatus(errorMessage == null ? CommonConstants.YES : CommonConstants.NO);
        log.setErrorMessage(errorMessage);
        aiCallLogMapper.insert(log);
    }

    private BusinessException businessAiException(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "AI service failed: " + ex.getMessage());
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

    private String defaultQuestionPrompt() {
        return "你是 Java 面试官。请基于当前阶段 {{stageName}}、目标岗位 {{targetPosition}}、难度 {{difficulty}} 和题库问题 {{questionContent}} 生成一道中文面试问题。只返回 JSON：{\"questionContent\":\"问题内容\"}";
    }

    private String defaultEvaluatePrompt() {
        return "你是 Java 面试官。请根据问题 {{questionContent}}、参考答案 {{referenceAnswer}} 和候选人回答 {{userAnswer}} 给出中文评分。只返回 JSON：{\"score\":80,\"comment\":\"点评\",\"nextAction\":\"NEXT_QUESTION\",\"knowledgePoints\":\"知识点\"}";
    }

    private String defaultFollowUpPrompt() {
        return "你是 Java 面试官。请基于问题 {{questionContent}}、回答 {{userAnswer}} 和点评 {{aiComment}} 生成一个中文追问。只返回 JSON：{\"followUpQuestion\":\"追问内容\"}";
    }

    private String defaultReportPrompt() {
        return "你是 Java 面试教练。请基于面试记录 {{historySummary}} 生成中文结构化报告。只返回 JSON：{\"totalScore\":82,\"summary\":\"总分来源说明\",\"strengths\":[],\"weakPoints\":[],\"mainProblems\":[],\"projectProblems\":[],\"reviewSuggestions\":[],\"recommendedQuestions\":[],\"qaReview\":[],\"stageScores\":{},\"reportContent\":\"报告正文\"}";
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
