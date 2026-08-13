package com.codecoachai.resume.service.support;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.JobApplicationEventReviewGenerateDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO.ReviewAnalysisVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO.ReviewFactVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO.ReviewGenerationVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO.ReviewSignalVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO.UserInputVO;
import com.codecoachai.resume.feign.dto.GenerateApplicationEventReviewAiDTO;
import com.codecoachai.resume.feign.vo.GenerateApplicationEventReviewAiVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JobApplicationEventReviewPolicy {

    public static final String SCHEMA_VERSION = "APPLICATION_EVENT_REVIEW_V1";
    public static final String GENERATOR_VERSION = "APPLICATION_EVENT_REVIEW_V1";
    private static final String POLICY_VERSION = "APPLICATION_EVENT_REVIEW_POLICY_V2";
    private static final Duration GENERATING_TTL = Duration.ofMinutes(5);
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_LIST_ITEMS = 4;
    private static final int MAX_LIST_ITEM_LENGTH = 300;
    private static final int MAX_SIGNAL_FACT_REFS = 16;
    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCEEDED", "FALLBACK");
    private static final List<String> ALWAYS_PROHIBITED_ASSERTIONS = List.of(
            "录用概率", "通过概率", "候选人排名", "招聘方认为", "面试官认为",
            "一定是因为", "确定是因为", "岗位已关闭", "hc冻结", "已经被淘汰",
            "offer probability", "pass probability", "candidate ranking",
            "recruiter thinks", "interviewer thinks");
    private static final List<String> UNSUPPORTED_EXTERNAL_OUTCOMES = List.of(
            "已通过面试", "面试已通过", "已经通过面试", "已进入下一轮", "已经进入下一轮",
            "已收到offer", "收到offer", "获得offer", "已被录用", "已经录用", "将被录用",
            "岗位已经关闭", "流程已经终止", "流程已终止", "简历未通过", "已被淘汰",
            "passed the interview", "moved to the next round", "received an offer",
            "position is closed", "process has ended", "resume was rejected");
    private static final List<String> UNCERTAINTY_MARKERS = List.of(
            "无法判断", "不能判断", "无法确认", "不能确认", "尚不能确认", "不能证明",
            "不代表", "不等于", "没有证据", "不得推断", "不能推断", "未知",
            "cannot determine", "cannot confirm", "does not prove", "does not mean");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern MOBILE_PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\+?86[-\\s]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern LANDLINE_PHONE_PATTERN =
            Pattern.compile("(?<!\\d)0\\d{2,3}[-\\s]?\\d{7,8}(?!\\d)");
    private static final Pattern INTERNATIONAL_PHONE_PATTERN =
            Pattern.compile("(?<!\\d)\\+\\d[\\d\\s()-]{7,}\\d(?!\\d)");
    private static final Pattern CHINA_ID_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|authorization|token|secret|password)\\s*[:=]\\s*[^,\\s}]+");
    private static final Pattern RECRUITER_REASON_PATTERN = Pattern.compile(
            "(?i)(招聘方|面试官|hr|用人部门|公司).{0,16}"
                    + "(认为|判断|觉得|因为|由于|偏好|不满|担心|淘汰|拒绝原因|决定)");
    private static final Pattern OUTCOME_REASON_PATTERN = Pattern.compile(
            "(?i)((因为|由于).{0,24}(被拒绝|被淘汰|未通过|没有通过)"
                    + "|(被拒绝|被淘汰|未通过|没有通过).{0,12}(因为|原因是))");
    private static final Pattern AUTOMATIC_ACTION_PATTERN = Pattern.compile(
            "(?i)自动(?:为你)?(?:投递|发送|联系|跟进|提交|修改|调整|创建|安排|执行|更新|撤回|报名"
                    + "|apply|send|contact|follow up|submit|modify|create|schedule|execute|update)");
    private static final Pattern SYSTEM_ACTION_ASSERTION_PATTERN = Pattern.compile(
            "(?i)(系统|平台|助手|codecoachai|我|我们)(已|已经|将|会|正在)?(?:为你)?"
                    + "(投递|发送|联系|跟进|提交|修改|调整|创建|安排|执行|更新|撤回|报名)");
    private static final Pattern SPECULATIVE_REJECTION_REASON_PATTERN = Pattern.compile(
            "(?i)((学历|年龄|能力|性格|薪资|竞争力|经验|背景).{0,12}"
                    + "(导致|造成|因此|所以).{0,12}(拒绝|淘汰|未通过)"
                    + "|(拒绝|淘汰|未通过).{0,12}(源于|来自).{0,12}"
                    + "(学历|年龄|能力|性格|薪资|竞争力|经验|背景))");

    private final ObjectMapper objectMapper;

    public String requireScenario(String eventType) {
        String normalized = normalizeCode(eventType);
        return switch (normalized) {
            case "INTERVIEW_COMPLETED", "INTERVIEW_FEEDBACK_REVIEW" -> "INTERVIEW_COMPLETED";
            case "REJECTION", "REJECTED", "REJECTION_REVIEW" -> "REJECTION";
            case "NO_RESPONSE_REVIEW" -> "NO_RESPONSE";
            default -> throw new BusinessException(
                    ErrorCode.PARAM_ERROR, "当前投递事件类型不支持生成 AI 复盘");
        };
    }

    public String resolveEventScope(String scenario, Map<String, Object> root) {
        if (!"INTERVIEW_COMPLETED".equals(scenario)) {
            return "REAL_JOB";
        }
        String source = text(root.get("source"));
        if ("interview-report".equalsIgnoreCase(source)
                || root.get("reportId") != null
                || root.get("interviewId") != null) {
            return "SIMULATION";
        }
        if (StringUtils.hasText(source)
                && !"manual".equalsIgnoreCase(source)
                && !"user".equalsIgnoreCase(source)) {
            return "UNKNOWN";
        }
        return "REAL_JOB";
    }

    public UserInputVO mergeUserInput(
            JobApplicationEventStructuredReviewVO existing,
            JobApplicationEventReviewGenerateDTO request) {
        UserInputVO oldInput = existing == null ? null : existing.getUserInput();
        UserInputVO input = new UserInputVO();
        input.setOwner("USER");
        boolean preserveExisting = request != null
                && Boolean.TRUE.equals(request.getForce())
                && oldInput != null;

        List<String> observed = preserveExisting
                ? factContents(oldInput.getObservedFacts())
                : request != null && request.getObservedFacts() != null
                ? request.getObservedFacts()
                : factContents(oldInput == null ? null : oldInput.getObservedFacts());
        int nextId = 1;
        for (String value : cleanTextList(observed, 10, 300)) {
            input.getObservedFacts().add(fact("U" + nextId++, value, "USER", "USER_OBSERVATION"));
        }

        String externalFeedback = preserveExisting
                ? factContent(oldInput.getExternalFeedback())
                : request != null && request.getExternalFeedback() != null
                ? cleanText(request.getExternalFeedback(), 2000)
                : factContent(oldInput == null ? null : oldInput.getExternalFeedback());
        if (StringUtils.hasText(externalFeedback)) {
            input.setExternalFeedback(fact(
                    "U" + nextId,
                    externalFeedback,
                    "USER",
                    hasStrongExternalFeedbackText(externalFeedback)
                            ? "USER_REPORTED_EXTERNAL_FEEDBACK"
                            : "USER_REPORTED_NO_EXTERNAL_FEEDBACK"));
        }
        input.setSelfReflection(preserveExisting
                ? cleanText(oldInput.getSelfReflection(), 2000)
                : request != null && request.getSelfReflection() != null
                ? cleanText(request.getSelfReflection(), 2000)
                : cleanText(oldInput == null ? null : oldInput.getSelfReflection(), 2000));
        return input;
    }

    public List<ReviewFactVO> buildSystemFacts(
            JobApplication application,
            JobApplicationEvent event,
            Map<String, Object> root,
            String eventScope) {
        List<ReviewFactVO> facts = new ArrayList<>();
        String eventTime = event.getEventTime() == null ? "未知时间" : EVENT_TIME_FORMAT.format(event.getEventTime());
        facts.add(fact("S1",
                "系统记录的事件类型为 " + normalizeCode(event.getEventType())
                        + "，事件时间为 " + eventTime + "。",
                "SYSTEM", "EVENT_RECORD"));

        String applicationSummary = "投递状态为 " + firstText(application.getStatus(), "未知")
                + "，来源为 " + firstText(application.getSource(), "未知")
                + "，目标为 " + firstText(application.getCompanyName(), "未知公司")
                + " / " + firstText(application.getJobTitle(), "未知岗位") + "。";
        facts.add(fact("S2", applicationSummary, "SYSTEM", "APPLICATION_RECORD"));

        if ("SIMULATION".equals(eventScope)) {
            String reportId = text(root.get("reportId"));
            String interviewId = text(root.get("interviewId"));
            String metadata = "该事件关联 CodeCoachAI 模拟面试报告";
            if (StringUtils.hasText(reportId)) {
                metadata += "，报告 ID 为 " + reportId;
            }
            if (StringUtils.hasText(interviewId)) {
                metadata += "，面试 ID 为 " + interviewId;
            }
            facts.add(fact("S3", metadata + "。", "SYSTEM", "INTERVIEW_REPORT_METADATA"));
        }
        return facts;
    }

    public String inputFingerprint(GenerateApplicationEventReviewAiDTO aiRequest) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("aiRequest", aiRequest);
        snapshot.put("generatorVersion", GENERATOR_VERSION);
        snapshot.put("policyVersion", POLICY_VERSION);
        return "sha256:" + ResumeGenerationHashUtils.sha256(objectMapper, snapshot);
    }

    public boolean shouldReuse(
            JobApplicationEventStructuredReviewVO existing,
            String fingerprint,
            boolean force,
            LocalDateTime now) {
        if (force || existing == null || existing.getGeneration() == null) {
            return false;
        }
        ReviewGenerationVO generation = existing.getGeneration();
        if (!Objects.equals(fingerprint, generation.getInputFingerprint())) {
            return false;
        }
        String status = normalizeCode(generation.getStatus());
        if (TERMINAL_STATUSES.contains(status)) {
            return true;
        }
        return "GENERATING".equals(status)
                && generation.getStartedAt() != null
                && Duration.between(generation.getStartedAt(), now).compareTo(GENERATING_TTL) < 0;
    }

    public JobApplicationEventStructuredReviewVO claim(
            JobApplicationEventStructuredReviewVO existing,
            String scenario,
            String eventScope,
            UserInputVO input,
            List<ReviewFactVO> systemFacts,
            String fingerprint,
            String requestId,
            LocalDateTime now) {
        JobApplicationEventStructuredReviewVO review = new JobApplicationEventStructuredReviewVO();
        review.setSchemaVersion(SCHEMA_VERSION);
        review.setScenario(scenario);
        review.setEventScope(eventScope);
        review.setUserInput(input);
        review.setSystemFacts(systemFacts);
        review.setAnalysis(existing == null ? null : existing.getAnalysis());

        ReviewGenerationVO generation = new ReviewGenerationVO();
        generation.setOwner("SYSTEM");
        generation.setStatus("GENERATING");
        generation.setFallback(false);
        generation.setInputFingerprint(fingerprint);
        generation.setRequestId(requestId);
        generation.setGeneratorVersion(GENERATOR_VERSION);
        generation.setStartedAt(now);
        generation.setConfidenceLevel(calculateConfidence(review, false));
        generation.setConfidenceBasis(confidenceBasis(review, false));
        review.setGeneration(generation);
        return review;
    }

    public GenerateApplicationEventReviewAiDTO toAiRequest(
            Long userId,
            JobApplication application,
            JobApplicationEvent event,
            String scenario,
            String eventScope,
            UserInputVO input,
            List<ReviewFactVO> systemFacts,
            Map<String, Object> legacyRoot) {
        GenerateApplicationEventReviewAiDTO dto = new GenerateApplicationEventReviewAiDTO();
        dto.setUserId(userId);
        dto.setEventId(event.getId());
        dto.setApplicationId(application.getId());
        dto.setTargetJobId(application.getTargetJobId());
        dto.setScenario(scenario);
        dto.setEventScope(eventScope);
        dto.setJobTitle(cleanText(firstText(application.getJobTitle(), application.getCompanyName()), 300));
        dto.setApplicationSource(cleanText(application.getSource(), 120));
        dto.setApplicationStatus(normalizeCode(application.getStatus()));
        dto.setEventType(normalizeCode(event.getEventType()));
        dto.setEventTime(event.getEventTime() == null ? null : EVENT_TIME_FORMAT.format(event.getEventTime()));
        dto.setEventSummary(cleanText(event.getSummary(), 1000));
        dto.setSelfReflection(input == null ? null : input.getSelfReflection());
        dto.setLegacyHypotheses(legacyHypotheses(legacyRoot));
        dto.setConfidenceCeiling(confidenceCeiling(scenario, eventScope));

        if (input != null) {
            for (ReviewFactVO fact : input.getObservedFacts()) {
                dto.getFacts().add(toAiFact(fact));
            }
            if (input.getExternalFeedback() != null) {
                dto.getFacts().add(toAiFact(input.getExternalFeedback()));
            }
        }
        if (systemFacts != null) {
            for (ReviewFactVO fact : systemFacts) {
                dto.getFacts().add(toAiFact(fact));
            }
        }
        return dto;
    }

    public JobApplicationEventStructuredReviewVO complete(
            JobApplicationEventStructuredReviewVO claimed,
            GenerateApplicationEventReviewAiVO aiResponse,
            String fallbackReason,
            LocalDateTime generatedAt) {
        ReviewAnalysisVO fallbackAnalysis = fallbackAnalysis(claimed.getScenario(), claimed.getEventScope());
        ReviewAnalysisVO safeAiAnalysis = sanitizeAiAnalysis(aiResponse, claimed);
        boolean fallback = StringUtils.hasText(fallbackReason) || !usable(safeAiAnalysis);
        String effectiveFallbackReason = fallback
                ? firstText(fallbackReason, "AI_INVALID_OUTPUT")
                : null;
        ReviewAnalysisVO analysis = fallback
                ? fallbackAnalysis
                : fillRuleDefaults(safeAiAnalysis, fallbackAnalysis);
        claimed.setAnalysis(analysis);

        ReviewGenerationVO generation = claimed.getGeneration();
        generation.setStatus(fallback ? "FALLBACK" : "SUCCEEDED");
        generation.setFallback(fallback);
        generation.setFallbackReason(effectiveFallbackReason);
        generation.setAiCallLogId(fallback || aiResponse == null ? null : aiResponse.getAiCallLogId());
        generation.setGeneratedAt(generatedAt);
        generation.setConfidenceLevel(calculateConfidence(claimed, fallback));
        generation.setConfidenceBasis(confidenceBasis(claimed, fallback));
        return claimed;
    }

    private ReviewAnalysisVO sanitizeAiAnalysis(
            GenerateApplicationEventReviewAiVO response,
            JobApplicationEventStructuredReviewVO review) {
        ReviewAnalysisVO analysis = new ReviewAnalysisVO();
        analysis.setOwner("AI");
        if (response == null) {
            return analysis;
        }
        analysis.setSummary(safeAiText(response.getSummary(), MAX_SUMMARY_LENGTH, review, List.of()));
        analysis.setLimits(safeAiTextList(response.getLimits(), review));
        analysis.setAdjustments(safeAiTextList(response.getAdjustments(), review));
        analysis.setNextActions(safeAiTextList(response.getNextActions(), review));

        Set<String> validFactIds = validFactIds(review);
        if (response.getSignals() != null && response.getSignals().size() <= MAX_LIST_ITEMS) {
            for (GenerateApplicationEventReviewAiVO.Signal source : response.getSignals()) {
                if (source == null || analysis.getSignals().size() >= 4) {
                    continue;
                }
                if (source.getFactRefs() == null
                        || source.getFactRefs().isEmpty()
                        || source.getFactRefs().size() > MAX_SIGNAL_FACT_REFS) {
                    continue;
                }
                List<String> refs = source.getFactRefs().stream()
                        .map(this::cleanCode)
                        .filter(validFactIds::contains)
                        .distinct()
                        .toList();
                if (refs.isEmpty()) {
                    continue;
                }
                String content = safeAiText(
                        source.getContent(), MAX_LIST_ITEM_LENGTH, review, refs);
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                ReviewSignalVO signal = new ReviewSignalVO();
                signal.setOwner("AI");
                signal.setContent(content);
                signal.setFactRefs(refs);
                signal.setConfidenceLevel(signalConfidence(source.getConfidenceLevel(), refs, review));
                analysis.getSignals().add(signal);
            }
        }
        return analysis;
    }

    private ReviewAnalysisVO fillRuleDefaults(
            ReviewAnalysisVO safeAiAnalysis,
            ReviewAnalysisVO fallbackAnalysis) {
        safeAiAnalysis.setSummary(firstText(
                safeAiAnalysis.getSummary(),
                fallbackAnalysis.getSummary()));
        if (safeAiAnalysis.getLimits().isEmpty()) {
            safeAiAnalysis.setLimits(new ArrayList<>(fallbackAnalysis.getLimits()));
        }
        if (safeAiAnalysis.getAdjustments().isEmpty()) {
            safeAiAnalysis.setAdjustments(new ArrayList<>(fallbackAnalysis.getAdjustments()));
        }
        if (safeAiAnalysis.getNextActions().isEmpty()) {
            safeAiAnalysis.setNextActions(new ArrayList<>(fallbackAnalysis.getNextActions()));
        }
        return safeAiAnalysis;
    }

    private ReviewAnalysisVO fallbackAnalysis(String scenario, String eventScope) {
        ReviewAnalysisVO analysis = new ReviewAnalysisVO();
        analysis.setOwner("RULE");
        switch (scenario) {
            case "INTERVIEW_COMPLETED" -> {
                analysis.setSummary("当前面试事件可以支持对准备过程和表达方式做有限复盘。");
                analysis.setLimits(List.of(
                        "缺少招聘方直接反馈，无法判断真实面试结果和决策原因。",
                        "SIMULATION".equals(eventScope)
                                ? "当前证据来自 CodeCoachAI 模拟面试报告，不能代表真实招聘结果。"
                                : "当前仅有用户记录和系统事件元数据，结论应保持克制。"));
                analysis.setAdjustments(List.of(
                        "只选择一个知识点或表达缺口复练，并为本轮设置明确完成标准。"));
                analysis.setNextActions(List.of(
                        "补记问题、回答、明确反馈和一个可验证的改进动作。"));
            }
            case "REJECTION" -> {
                analysis.setSummary("拒绝结果已经明确，但单次结果不能证明具体淘汰原因。");
                analysis.setLimits(List.of(
                        "除非招聘方明确说明，否则无法判断本次招聘决策原因。"));
                analysis.setAdjustments(List.of(
                        "只从关键词、项目证据或投递渠道中选择一个变量做验证。"));
                analysis.setNextActions(List.of(
                        "记录拒绝时间和渠道，并定义一个小范围的下一轮实验。"));
            }
            default -> {
                analysis.setSummary("无反馈只是弱流程信号，不等于已经被拒绝。");
                analysis.setLimits(List.of(
                        "沉默既不能证明已经被拒绝，也不能证明岗位仍在推进。"));
                analysis.setAdjustments(List.of(
                        "保留一次轻量跟进，并设置明确的停止条件。"));
                analysis.setNextActions(List.of(
                        "检查投递渠道和跟进间隔，并把精力转向其他活跃岗位。"));
            }
        }
        return analysis;
    }

    private String calculateConfidence(JobApplicationEventStructuredReviewVO review, boolean fallback) {
        if (fallback || review == null
                || "NO_RESPONSE".equals(review.getScenario())
                || "UNKNOWN".equals(review.getEventScope())) {
            return "LOW";
        }
        UserInputVO input = review.getUserInput();
        int observations = input == null ? 0 : input.getObservedFacts().size();
        boolean externalFeedbackProvided = input != null && input.getExternalFeedback() != null;
        boolean strongExternalFeedback = hasStrongExternalFeedback(review);
        if (externalFeedbackProvided && !strongExternalFeedback) {
            return "LOW";
        }
        if ("REJECTION".equals(review.getScenario())) {
            return "LOW";
        }
        if (strongExternalFeedback
                && observations >= 3
                && "REAL_JOB".equals(review.getEventScope())) {
            return "HIGH";
        }
        if ((strongExternalFeedback && observations >= 1)
                || observations >= 3
                || "SIMULATION".equals(review.getEventScope())) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> confidenceBasis(JobApplicationEventStructuredReviewVO review, boolean fallback) {
        List<String> basis = new ArrayList<>();
        int observations = review == null || review.getUserInput() == null
                ? 0 : review.getUserInput().getObservedFacts().size();
        if (observations > 0) {
            basis.add("当前复盘包含 " + observations + " 条用户直接观察。");
        } else {
            basis.add("当前没有用户直接观察。");
        }
        if (review != null && "SIMULATION".equals(review.getEventScope())) {
            basis.add("当前存在系统模拟面试报告引用。");
        }
        if (review != null && review.getUserInput() != null
                && review.getUserInput().getExternalFeedback() != null) {
            basis.add(hasStrongExternalFeedback(review)
                    ? "用户转述了明确的外部反馈。"
                    : "用户仅说明尚未收到明确外部反馈，不能作为强证据。");
        } else {
            basis.add("当前没有可核验的外部反馈。");
        }
        if (fallback) {
            basis.add("AI 生成不可用，本次内容由确定性规则降级生成。");
        }
        return basis;
    }

    private String signalConfidence(
            String requested,
            List<String> refs,
            JobApplicationEventStructuredReviewVO review) {
        if ("LOW".equals(confidenceCeiling(review.getScenario(), review.getEventScope()))
                || "LOW".equalsIgnoreCase(requested)) {
            return "LOW";
        }
        boolean onlySelfReported = refs.stream().allMatch(ref -> ref.startsWith("U"));
        return onlySelfReported ? "LOW" : "MEDIUM";
    }

    private boolean usable(ReviewAnalysisVO analysis) {
        return analysis != null && (StringUtils.hasText(analysis.getSummary())
                || hasText(analysis.getLimits())
                || hasText(analysis.getAdjustments())
                || hasText(analysis.getNextActions())
                || analysis.getSignals() != null && analysis.getSignals().stream()
                        .anyMatch(signal -> signal != null && StringUtils.hasText(signal.getContent())));
    }

    private Set<String> validFactIds(JobApplicationEventStructuredReviewVO review) {
        Set<String> ids = new LinkedHashSet<>();
        if (review.getUserInput() != null) {
            review.getUserInput().getObservedFacts().stream()
                    .map(ReviewFactVO::getId)
                    .map(this::cleanCode)
                    .filter(StringUtils::hasText)
                    .forEach(ids::add);
            if (review.getUserInput().getExternalFeedback() != null) {
                String feedbackId = cleanCode(review.getUserInput().getExternalFeedback().getId());
                if (StringUtils.hasText(feedbackId)) {
                    ids.add(feedbackId);
                }
            }
        }
        review.getSystemFacts().stream()
                .map(ReviewFactVO::getId)
                .map(this::cleanCode)
                .filter(StringUtils::hasText)
                .forEach(ids::add);
        return ids;
    }

    private GenerateApplicationEventReviewAiDTO.FactDTO toAiFact(ReviewFactVO source) {
        GenerateApplicationEventReviewAiDTO.FactDTO target =
                new GenerateApplicationEventReviewAiDTO.FactDTO();
        target.setId(source.getId());
        target.setContent(source.getContent());
        target.setOwner(source.getOwner());
        target.setSourceType(source.getSourceType());
        return target;
    }

    private List<String> legacyHypotheses(Map<String, Object> root) {
        List<String> values = new ArrayList<>();
        appendLegacy(values, root.get("assumptions"));
        appendLegacy(values, root.get("facts"));
        return cleanTextList(values, 8, 300);
    }

    private void appendLegacy(List<String> target, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> appendLegacy(target, item));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendLegacy(target, map.get("content"));
            appendLegacy(target, map.get("text"));
            appendLegacy(target, map.get("hypothesis"));
            appendLegacy(target, map.get("description"));
            return;
        }
        if (!(value instanceof CharSequence sequence)) {
            return;
        }
        String text = sequence.toString().trim();
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (!looksLikeJsonContainer(text)) {
            target.add(text);
            return;
        }
        try {
            appendLegacy(target, objectMapper.readTree(text));
        } catch (Exception ignored) {
            // Malformed serialized business JSON is not promoted to evidence.
        }
    }

    private void appendLegacy(List<String> target, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> appendLegacy(target, item));
            return;
        }
        if (node.isObject()) {
            appendLegacy(target, node.get("content"));
            appendLegacy(target, node.get("text"));
            appendLegacy(target, node.get("hypothesis"));
            appendLegacy(target, node.get("description"));
            return;
        }
        if (node.isTextual()) {
            appendLegacy(target, node.asText());
        }
    }

    private boolean looksLikeJsonContainer(String value) {
        return value.startsWith("{") || value.startsWith("[");
    }

    private String confidenceCeiling(String scenario, String eventScope) {
        if ("NO_RESPONSE".equals(scenario)
                || "REJECTION".equals(scenario)
                || "UNKNOWN".equals(eventScope)) {
            return "LOW";
        }
        return "HIGH";
    }

    private List<String> safeAiTextList(
            List<String> values,
            JobApplicationEventStructuredReviewVO review) {
        if (values == null || values.isEmpty() || values.size() > MAX_LIST_ITEMS) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String safe = safeAiText(value, MAX_LIST_ITEM_LENGTH, review, List.of());
            if (StringUtils.hasText(safe)) {
                result.add(safe);
            }
        }
        return new ArrayList<>(result);
    }

    private String safeAiText(
            String value,
            int maxLength,
            JobApplicationEventStructuredReviewVO review,
            List<String> factRefs) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String safe = WHITESPACE_PATTERN.matcher(value.trim()).replaceAll(" ");
        if (safe.length() > maxLength
                || !containsChinese(safe)
                || containsPii(safe)
                || containsUnsafeSemantics(safe, review, factRefs)) {
            return null;
        }
        return safe;
    }

    private List<String> cleanTextList(Collection<String> values, int limit, int itemLimit) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = cleanText(value, itemLimit);
            if (StringUtils.hasText(cleaned)) {
                result.add(cleaned);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(result);
    }

    private boolean containsChinese(String value) {
        return value.codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.HAN.equals(
                        Character.UnicodeScript.of(codePoint)));
    }

    private boolean containsPii(String value) {
        return EMAIL_PATTERN.matcher(value).find()
                || MOBILE_PHONE_PATTERN.matcher(value).find()
                || LANDLINE_PHONE_PATTERN.matcher(value).find()
                || INTERNATIONAL_PHONE_PATTERN.matcher(value).find()
                || CHINA_ID_PATTERN.matcher(value).find()
                || SECRET_PATTERN.matcher(value).find();
    }

    private boolean containsUnsafeSemantics(
            String value,
            JobApplicationEventStructuredReviewVO review,
            List<String> factRefs) {
        String[] clauses = value.split("[。！？!?；;\\r\\n]+");
        for (String clause : clauses) {
            if (containsUnsafeClause(clause, review, factRefs)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsUnsafeClause(
            String value,
            JobApplicationEventStructuredReviewVO review,
            List<String> factRefs) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (ALWAYS_PROHIBITED_ASSERTIONS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        if (AUTOMATIC_ACTION_PATTERN.matcher(normalized).find()
                && !containsAny(normalized, "不得自动", "不能自动", "不会自动", "不应自动", "请勿自动")) {
            return true;
        }
        if (SYSTEM_ACTION_ASSERTION_PATTERN.matcher(normalized).find()
                && !containsAny(normalized, "不会", "不能", "不得", "不应", "尚未", "没有", "并未", "未曾")) {
            return true;
        }
        boolean groundedExternalFeedback =
                referencesStrongExternalFeedback(review, factRefs);
        if ((RECRUITER_REASON_PATTERN.matcher(normalized).find()
                || OUTCOME_REASON_PATTERN.matcher(normalized).find()
                || SPECULATIVE_REJECTION_REASON_PATTERN.matcher(normalized).find())
                && !groundedExternalFeedback) {
            return true;
        }
        boolean uncertain = UNCERTAINTY_MARKERS.stream().anyMatch(normalized::contains);
        if (!uncertain && UNSUPPORTED_EXTERNAL_OUTCOMES.stream().anyMatch(normalized::contains)) {
            return true;
        }
        return !uncertain
                && !"REJECTION".equals(review.getScenario())
                && containsAny(normalized, "已被拒绝", "已经被拒绝", "拒绝结果已经明确");
    }

    private boolean referencesStrongExternalFeedback(
            JobApplicationEventStructuredReviewVO review,
            List<String> factRefs) {
        if (!hasStrongExternalFeedback(review)
                || factRefs == null
                || factRefs.isEmpty()) {
            return false;
        }
        String feedbackId = cleanCode(review.getUserInput().getExternalFeedback().getId());
        return factRefs.stream().map(this::cleanCode).anyMatch(feedbackId::equals);
    }

    private boolean hasStrongExternalFeedback(JobApplicationEventStructuredReviewVO review) {
        return review != null
                && review.getUserInput() != null
                && review.getUserInput().getExternalFeedback() != null
                && hasStrongExternalFeedbackText(
                        review.getUserInput().getExternalFeedback().getContent());
    }

    private boolean hasStrongExternalFeedbackText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = WHITESPACE_PATTERN.matcher(value.trim().toLowerCase(Locale.ROOT))
                .replaceAll("");
        if (Set.of("无", "暂无", "没有", "未知", "不清楚", "none", "n/a", "na")
                .contains(normalized)) {
            return false;
        }
        if (containsAny(normalized, "但", "不过", "然而", "同时")
                || containsAny(normalized, "明确指出", "明确表示", "具体反馈", "建议改进")) {
            return true;
        }
        boolean feedbackTarget = containsAny(
                normalized, "反馈", "评价", "意见", "原因", "回复", "说明",
                "feedback", "reason", "response", "comment");
        boolean negativeCue = containsAny(
                normalized, "没有", "未收到", "尚未", "暂无", "无反馈", "无明确",
                "未给出", "未提供", "未包含", "未说明", "没有给出", "没有提供",
                "没有包含", "没有说明", "未获", "未回复", "未反馈", "未评价", "未告知",
                "didnot", "didn't", "hasnot", "havenot", "nofeedback", "noreason",
                "noexplicitfeedback", "noclearfeedback", "withoutfeedback", "withoutareason");
        return !(feedbackTarget && negativeCue);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private ReviewFactVO fact(String id, String content, String owner, String sourceType) {
        ReviewFactVO fact = new ReviewFactVO();
        fact.setId(id);
        fact.setContent(content);
        fact.setOwner(owner);
        fact.setSourceType(sourceType);
        return fact;
    }

    private List<String> factContents(List<ReviewFactVO> facts) {
        if (facts == null) {
            return null;
        }
        return facts.stream().map(ReviewFactVO::getContent).toList();
    }

    private String factContent(ReviewFactVO fact) {
        return fact == null ? null : fact.getContent();
    }

    private boolean hasText(Collection<String> values) {
        return values != null && values.stream().anyMatch(StringUtils::hasText);
    }

    private String cleanText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String cleanCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeCode(String value) {
        return cleanCode(value);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
