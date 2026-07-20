package com.codecoachai.resume.careercalendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobReadinessSnapshot;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.InterviewEvidenceFeignClient;
import com.codecoachai.resume.feign.dto.GenerateInterviewPreparationAiDTO;
import com.codecoachai.resume.feign.vo.GenerateInterviewPreparationAiVO;
import com.codecoachai.resume.feign.vo.InterviewWeaknessSummaryVO;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobReadinessSnapshotMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.service.support.ResumeGenerationHashUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerInterviewPreparationServiceImpl implements CareerInterviewPreparationService {

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "INTERVIEW",
            "INTERVIEW_SCHEDULED",
            "PHONE_SCREEN",
            "TECHNICAL_INTERVIEW",
            "HR_INTERVIEW",
            "FINAL_INTERVIEW");
    private static final DateTimeFormatter LOCAL_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_REQUIREMENTS = 20;
    private static final int MAX_PROJECTS = 10;
    private static final int MAX_WEAKNESSES = 8;
    private static final int MAX_CLAIM_WAIT_ATTEMPTS = 40;
    private static final long CLAIM_WAIT_MILLIS = 50L;
    private static final Duration GENERATING_TTL = Duration.ofMinutes(5);
    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FALLBACK = "FALLBACK";
    private static final String STATUS_STALE = "STALE";
    private static final String INPUT_POLICY_VERSION = "CAREER_INTERVIEW_PREPARATION_INPUT_V2";
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINA_MOBILE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern CHINA_ID_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?token|password|secret)\\s*[:=]\\s*[^\\s,;，；]+");
    private static final List<String> PROHIBITED_AI_SEMANTICS = List.of(
            "真题", "原题", "必考", "必问", "一定会问", "肯定会问", "面试官会问",
            "真实面试题", "预测题", "内部题库", "题库命中", "泄题",
            "保证通过", "确保通过", "录用概率", "通过概率", "淘汰概率", "候选人排名",
            "招聘方认为", "面试官认为", "该公司使用", "该公司采用", "公司的技术栈是",
            "面试轮次为", "面试轮次是", "内幕消息",
            "will ask", "real interview question", "guaranteed to pass");

    private final CareerCalendarEventMapper eventMapper;
    private final JobApplicationMapper applicationMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper analysisMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final JobReadinessSnapshotMapper readinessSnapshotMapper;
    private final AiFeignClient aiFeignClient;
    private final Optional<InterviewEvidenceFeignClient> interviewEvidenceFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public CareerInterviewPreparationVO get(Long eventId) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerCalendarEvent event = ownedInterviewEvent(userId, eventId);
        CareerInterviewPreparationVO existing = readPreparation(event);
        if (existing == null
                || !Set.of(STATUS_READY, STATUS_FALLBACK)
                        .contains(normalizeCode(event.getPreparationStatus()))) {
            return existing;
        }
        try {
            PreparationContext context = loadContext(
                    userId, event, normalizeTimeBudget(existing.getTimeBudgetMinutes()));
            String currentSourceHash = sourceHash(toAiRequest(context));
            if (Objects.equals(currentSourceHash, event.getPreparationSourceHash())) {
                return existing;
            }
            eventMapper.markPreparationStale(event.getId(), userId);
            existing.setStatus(STATUS_STALE);
            markStale(existing, "关联岗位、简历、项目或准备度证据已更新，请重新生成面试准备包。");
            return existing;
        } catch (RuntimeException ex) {
            log.warn("Skip interview preparation staleness check because evidence is unavailable, calendarEventId={}",
                    eventId, ex);
            return existing;
        }
    }

    @Override
    public CareerInterviewPreparationVO generate(
            Long eventId,
            CareerInterviewPreparationGenerateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerCalendarEvent event = ownedInterviewEvent(userId, eventId);
        int timeBudget = normalizeTimeBudget(request == null ? null : request.getTimeBudgetMinutes());
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());
        PreparationContext context = loadContext(userId, event, timeBudget);
        GenerateInterviewPreparationAiDTO aiRequest = toAiRequest(context);
        String sourceHash = sourceHash(aiRequest);
        aiRequest.setSourceHash(sourceHash);

        CareerInterviewPreparationVO existing = readPreparation(event);
        if (!force && reusable(event, existing, sourceHash)) {
            return existing;
        }
        PreparationClaim claim = claimPreparation(userId, event, sourceHash);
        if (!claim.claimed()) {
            return claim.winner();
        }

        GenerateInterviewPreparationAiVO aiResponse = null;
        boolean aiSafetyAdjusted = false;
        String fallbackReason = null;
        if (context.application() == null) {
            fallbackReason = "APPLICATION_CONTEXT_MISSING";
        } else {
            try {
                GenerateInterviewPreparationAiVO rawAiResponse = FeignResultUtils.unwrap(
                        aiFeignClient.generateInterviewPreparation(aiRequest));
                AiSanitizationResult sanitization = sanitizeAiResponse(rawAiResponse);
                aiResponse = sanitization.response();
                aiSafetyAdjusted = sanitization.rejectedCount() > 0;
                if (rawAiResponse != null && Boolean.TRUE.equals(rawAiResponse.getFallback())) {
                    fallbackReason = "AI_RULE_FALLBACK";
                } else if (!usable(aiResponse)) {
                    fallbackReason = "AI_INVALID_OUTPUT";
                }
            } catch (RuntimeException ex) {
                fallbackReason = "AI_FEIGN_ERROR";
                log.warn("Interview preparation AI generation failed, calendarEventId={}", eventId, ex);
            }
        }

        LocalDateTime generatedAt = LocalDateTime.now().withNano(0);
        CareerInterviewPreparationVO prepared = buildPreparation(
                context, aiResponse, fallbackReason, sourceHash, generatedAt, aiSafetyAdjusted);
        String preparationJson = writeJson(prepared);
        int affectedRows = eventMapper.compareAndSetPreparation(
                event.getId(),
                userId,
                claim.eventUpdatedAt(),
                STATUS_GENERATING,
                sourceHash,
                claim.claimedAt(),
                preparationJson,
                prepared.getStatus(),
                prepared.getAiCallLogId(),
                generatedAt,
                sourceHash);
        if (affectedRows == 1) {
            return prepared;
        }

        CareerCalendarEvent winner = ownedInterviewEvent(userId, eventId);
        CareerInterviewPreparationVO winnerPreparation = readPreparation(winner);
        if (reusable(winner, winnerPreparation, sourceHash)) {
            return winnerPreparation;
        }
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "面试准备包正在并发更新，请稍后重试");
    }

    private PreparationContext loadContext(Long userId, CareerCalendarEvent event, int timeBudget) {
        List<String> warnings = new ArrayList<>();
        JobApplication application = null;
        if (event.getApplicationId() != null) {
            JobApplication candidate = applicationMapper.selectById(event.getApplicationId());
            if (candidate != null
                    && Objects.equals(userId, candidate.getUserId())
                    && !Objects.equals(candidate.getDeleted(), CommonConstants.YES)) {
                application = candidate;
            } else {
                warnings.add("关联投递不存在或当前不可用。");
            }
        } else {
            warnings.add("当前事件未关联投递，无法基于具体 JD 和简历生成强建议。");
        }

        TargetJob targetJob = ownedTargetJob(userId, application == null ? null : application.getTargetJobId());
        JobDescriptionAnalysis analysis = latestAnalysis(userId, targetJob == null ? null : targetJob.getId());
        boolean jdParseComplete = jdParseComplete(targetJob, analysis);
        ResumeVersion resumeVersion = ownedResumeVersion(
                userId, application == null ? null : application.getResumeVersionId());
        List<ProjectEvidence> projects = projectEvidence(userId, application, targetJob);
        JobReadinessSnapshot readiness = latestReadiness(
                userId, targetJob == null ? null : targetJob.getId());

        if (targetJob == null) {
            warnings.add("当前没有可用的目标岗位证据。");
        } else if (!jdParseComplete) {
            warnings.add(jdParseWarning(targetJob, analysis));
        }
        if (resumeVersion == null) {
            warnings.add("当前没有可用的绑定简历版本。");
        }
        if (projects.isEmpty()) {
            warnings.add("当前没有可用的项目证据。");
        }
        if (readiness == null) {
            warnings.add("当前没有可用的岗位准备度快照。");
        } else if (Objects.equals(readiness.getFallback(), CommonConstants.YES)
                || "LOW".equals(normalizeCode(readiness.getConfidenceLevel()))) {
            warnings.add("岗位准备度快照为低置信度或降级结果，本次建议不会标记为高置信度。");
        }

        List<String> recentWeaknesses = recentInterviewWeaknesses(userId, warnings);
        List<String> requirements = jobRequirements(analysis, jdParseComplete, warnings);
        List<String> projectSummaries = projects.stream()
                .map(this::projectSummary)
                .filter(StringUtils::hasText)
                .limit(MAX_PROJECTS)
                .toList();
        String resumeVersionSummary = resumeVersionSummary(resumeVersion, warnings);
        List<String> readinessGaps = readinessGaps(readiness, warnings);
        List<String> sourceWarnings = deduplicate(warnings, 12, 500);
        List<String> evidenceSources = evidenceSources(event, application, targetJob,
                jdParseComplete ? analysis : null, resumeVersion, projects, readiness, recentWeaknesses);
        return new PreparationContext(
                userId,
                event,
                application,
                targetJob,
                analysis,
                resumeVersion,
                projects,
                readiness,
                jdParseComplete,
                timeBudget,
                requirements,
                resumeVersionSummary,
                projectSummaries,
                readinessGaps,
                recentWeaknesses,
                List.of(),
                sourceWarnings,
                evidenceSources);
    }

    private GenerateInterviewPreparationAiDTO toAiRequest(PreparationContext context) {
        GenerateInterviewPreparationAiDTO dto = new GenerateInterviewPreparationAiDTO();
        dto.setUserId(context.userId());
        dto.setCalendarEventId(context.event().getId());
        dto.setApplicationId(context.application() == null ? null : context.application().getId());
        dto.setTimeBudgetMinutes(context.timeBudget());
        dto.setEventTitle(cleanText(context.event().getTitle(), 500));
        dto.setEventDescription(cleanText(context.event().getDescription(), 2000));
        dto.setEventType(normalizeCode(context.event().getEventType()));
        dto.setEventLocalTime(eventLocalTime(context.event()));
        dto.setTimezone(context.event().getTimezone());
        dto.setLocation(cleanText(context.event().getLocation(), 300));
        dto.setCompanyName(cleanText(firstText(
                context.application() == null ? null : context.application().getCompanyName(),
                context.targetJob() == null ? null : context.targetJob().getCompanyName()), 300));
        dto.setJobTitle(cleanText(firstText(
                context.application() == null ? null : context.application().getJobTitle(),
                context.targetJob() == null ? null : context.targetJob().getJobTitle()), 300));
        dto.setTargetJobSummary(targetJobSummary(
                context.targetJob(), context.analysis(), context.jdParseComplete()));
        dto.setJobRequirements(context.requirements());
        dto.setResumeVersionSummary(context.resumeVersionSummary());
        dto.setProjectEvidence(context.projectSummaries());
        dto.setReadinessGaps(context.readinessGaps());
        dto.setRecentInterviewWeaknesses(context.recentWeaknesses());
        dto.setConfirmedMemories(context.confirmedMemories());
        dto.setSourceWarnings(context.sourceWarnings());
        return dto;
    }

    private CareerInterviewPreparationVO buildPreparation(
            PreparationContext context,
            GenerateInterviewPreparationAiVO aiResponse,
            String fallbackReason,
            String sourceHash,
            LocalDateTime generatedAt,
            boolean aiSafetyAdjusted) {
        boolean fallback = StringUtils.hasText(fallbackReason);
        CareerInterviewPreparationVO vo = new CareerInterviewPreparationVO();
        vo.setCalendarEventId(context.event().getId());
        vo.setApplicationId(context.event().getApplicationId());
        vo.setTimeBudgetMinutes(context.timeBudget());
        vo.setSummary(firstText(
                 fallback ? null : cleanText(aiResponse == null ? null : aiResponse.getSummary(), 1000),
                 ruleSummary(context, fallbackReason)));
        vo.setFacts(contextFacts(context));
        vo.setLimits(limits(context, fallback ? null : aiResponse, aiSafetyAdjusted));
        vo.setFocusAreas(fillToCount(
                fallback ? List.of() : values(aiResponse == null ? null : aiResponse.getFocusAreas(), 8, 500),
                ruleFocusAreas(context),
                focusCount(context.timeBudget())));
        vo.setProjectStories(fillToCount(
                fallback ? List.of() : values(aiResponse == null ? null : aiResponse.getProjectStories(), 3, 1000),
                ruleProjectStories(context),
                projectCount(context.timeBudget())));
        vo.setPracticeQuestions(fillToCount(
                fallback ? List.of() : values(
                        aiResponse == null ? null : aiResponse.getPracticeQuestions(), 8, 500),
                rulePracticeQuestions(context),
                questionCount(context.timeBudget())));
        vo.setChecklist(fillToCount(
                fallback ? List.of() : values(aiResponse == null ? null : aiResponse.getChecklist(), 10, 500),
                ruleChecklist(context),
                checklistCount(context.timeBudget())));
        vo.setSchedule(fillToCount(
                fallback ? List.of() : values(aiResponse == null ? null : aiResponse.getSchedule(), 6, 500),
                ruleSchedule(context.timeBudget()),
                ruleSchedule(context.timeBudget()).size()));
        vo.setNextActions(fillToCount(
                fallback ? List.of() : values(aiResponse == null ? null : aiResponse.getNextActions(), 4, 500),
                ruleNextActions(context),
                 Math.min(4, context.timeBudget() == 30 ? 2 : 3)));
        vo.setEvidenceSources(context.evidenceSources());
        vo.setConfidenceLevel(confidence(context, fallback, aiSafetyAdjusted));
        vo.setFallback(fallback);
        vo.setAiCallLogId(fallback || aiResponse == null ? null : aiResponse.getAiCallLogId());
        vo.setSourceHash(sourceHash);
        vo.setStatus(fallback ? STATUS_FALLBACK : STATUS_READY);
        vo.setGeneratedAt(generatedAt);
        vo.setStale(false);
        vo.setStaleReason(null);
        return vo;
    }

    private String sourceHash(GenerateInterviewPreparationAiDTO dto) {
        ObjectNode normalizedInput = objectMapper.valueToTree(dto);
        normalizedInput.remove("sourceHash");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("normalizedAiInput", normalizedInput);
        snapshot.put("inputPolicyVersion", INPUT_POLICY_VERSION);
        return ResumeGenerationHashUtils.sha256(objectMapper, snapshot);
    }

    private CareerCalendarEvent ownedInterviewEvent(Long userId, Long eventId) {
        if (eventId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日历事件 ID 不能为空");
        }
        CareerCalendarEvent event = eventMapper.selectById(eventId);
        if (event == null
                || !Objects.equals(userId, event.getUserId())
                || Objects.equals(event.getDeleted(), CommonConstants.YES)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日历事件不存在或无权访问");
        }
        if (!SUPPORTED_EVENT_TYPES.contains(normalizeCode(event.getEventType()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只有面试类日历事件可以生成准备包");
        }
        return event;
    }

    private TargetJob ownedTargetJob(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return null;
        }
        TargetJob job = targetJobMapper.selectById(targetJobId);
        return job != null
                && Objects.equals(userId, job.getUserId())
                && !Objects.equals(job.getDeleted(), CommonConstants.YES)
                ? job : null;
    }

    private ResumeVersion ownedResumeVersion(Long userId, Long resumeVersionId) {
        if (resumeVersionId == null) {
            return null;
        }
        ResumeVersion version = resumeVersionMapper.selectById(resumeVersionId);
        return version != null
                && Objects.equals(userId, version.getUserId())
                && !Objects.equals(version.getDeleted(), CommonConstants.YES)
                ? version : null;
    }

    private JobDescriptionAnalysis latestAnalysis(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return null;
        }
        return analysisMapper.selectOne(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getUserId, userId)
                .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                .orderByDesc(JobDescriptionAnalysis::getId)
                .last("limit 1"));
    }

    private JobReadinessSnapshot latestReadiness(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return null;
        }
        return readinessSnapshotMapper.selectOne(new LambdaQueryWrapper<JobReadinessSnapshot>()
                .eq(JobReadinessSnapshot::getUserId, userId)
                .eq(JobReadinessSnapshot::getTargetJobId, targetJobId)
                .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO)
                .orderByDesc(JobReadinessSnapshot::getGeneratedAt)
                .orderByDesc(JobReadinessSnapshot::getId)
                .last("limit 1"));
    }

    private List<ProjectEvidence> projectEvidence(
            Long userId,
            JobApplication application,
            TargetJob targetJob) {
        if (application == null || targetJob == null || targetJob.getId() == null) {
            return List.of();
        }
        LambdaQueryWrapper<ProjectEvidence> wrapper = new LambdaQueryWrapper<ProjectEvidence>()
                .eq(ProjectEvidence::getUserId, userId)
                .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                .eq(ProjectEvidence::getTargetJobId, targetJob.getId())
                .orderByDesc(ProjectEvidence::getCompletenessScore)
                .orderByDesc(ProjectEvidence::getUpdatedAt)
                .orderByDesc(ProjectEvidence::getId)
                .last("limit " + MAX_PROJECTS);
        List<ProjectEvidence> values = projectEvidenceMapper.selectList(wrapper);
        return values == null ? List.of() : values;
    }

    private List<String> recentInterviewWeaknesses(Long userId, List<String> warnings) {
        if (interviewEvidenceFeignClient.isEmpty()) {
            warnings.add("近期模拟面试弱项证据暂不可用。");
            return List.of();
        }
        try {
            InterviewWeaknessSummaryVO summary = FeignResultUtils.unwrap(
                    interviewEvidenceFeignClient.get().weaknessSummary(userId, 90));
            if (summary == null || summary.getTopWeaknesses() == null) {
                return List.of();
            }
            return deduplicate(summary.getTopWeaknesses().stream()
                    .filter(Objects::nonNull)
                    .map(item -> firstText(item.getName(), item.getCategory()))
                    .filter(StringUtils::hasText)
                    .toList(), MAX_WEAKNESSES, 500);
        } catch (RuntimeException ex) {
            warnings.add("近期模拟面试弱项证据加载失败，本次准备包已部分降级。");
            log.warn("Failed to load interview weakness summary for preparation, userId={}", userId, ex);
            return List.of();
        }
    }

    private boolean jdParseComplete(TargetJob targetJob, JobDescriptionAnalysis analysis) {
        return targetJob != null
                && analysis != null
                && JobDescriptionParseStatus.PARSED.getCode().equals(normalizeCode(targetJob.getParseStatus()))
                && JobDescriptionParseStatus.PARSED.getCode().equals(normalizeCode(analysis.getParseStatus()));
    }

    private String jdParseWarning(TargetJob targetJob, JobDescriptionAnalysis analysis) {
        String targetStatus = normalizeCode(targetJob == null ? null : targetJob.getParseStatus());
        String analysisStatus = normalizeCode(analysis == null ? null : analysis.getParseStatus());
        if (JobDescriptionParseStatus.FAILED.getCode().equals(targetStatus)
                || JobDescriptionParseStatus.FAILED.getCode().equals(analysisStatus)) {
            return "岗位 JD 解析失败，本次已省略结构化 JD 证据。";
        }
        return "岗位 JD 解析尚未完成，本次已省略结构化 JD 证据。";
    }

    private List<String> jobRequirements(
            JobDescriptionAnalysis analysis,
            boolean jdParseComplete,
            List<String> warnings) {
        if (!jdParseComplete || analysis == null) {
            return List.of();
        }
        List<String> requirements = new ArrayList<>();
        requirements.addAll(readStringArray(
                analysis.getRequiredSkillsJson(), "岗位 JD 必备技能", warnings, MAX_REQUIREMENTS));
        requirements.addAll(readStringArray(
                analysis.getResponsibilitiesJson(), "岗位 JD 职责", warnings, MAX_REQUIREMENTS));
        requirements.addAll(readStringArray(
                analysis.getInterviewFocusJson(), "岗位 JD 面试关注点", warnings, MAX_REQUIREMENTS));
        return deduplicate(requirements, MAX_REQUIREMENTS, 500);
    }

    private List<String> readinessGaps(JobReadinessSnapshot readiness, List<String> warnings) {
        if (readiness == null) {
            return List.of();
        }
        List<String> gaps = new ArrayList<>();
        gaps.addAll(readinessSummaryGaps(readiness.getSummaryJson(), warnings));
        gaps.addAll(readinessMatrixGaps(readiness.getMatrixJson(), warnings));
        gaps.addAll(readinessDimensionGaps(readiness.getDimensionJson(), warnings));
        if (gaps.isEmpty() && readiness.getMissingCount() != null && readiness.getMissingCount() > 0) {
            gaps.add("岗位准备度快照中有 " + readiness.getMissingCount() + " 项要求缺少证据。");
        }
        if (gaps.isEmpty() && readiness.getWeakCount() != null && readiness.getWeakCount() > 0) {
            gaps.add("岗位准备度快照中有 " + readiness.getWeakCount() + " 项要求证据偏弱。");
        }
        return deduplicate(gaps, MAX_WEAKNESSES, 500);
    }

    private String targetJobSummary(
            TargetJob targetJob,
            JobDescriptionAnalysis analysis,
            boolean jdParseComplete) {
        if (targetJob == null) {
            return null;
        }
        return cleanText(String.join("; ", nonBlank(java.util.Arrays.asList(
                targetJob.getCompanyName(),
                targetJob.getJobTitle(),
                targetJob.getJobLevel(),
                jdParseComplete && analysis != null ? analysis.getSummary() : null))), 2000);
    }

    private String resumeVersionSummary(ResumeVersion version, List<String> warnings) {
        if (version == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add("简历版本：" + firstText(version.getVersionName(),
                version.getVersionNo() == null ? null : "V" + version.getVersionNo()));
        if (StringUtils.hasText(version.getSnapshotJson())) {
            try {
                JsonNode root = objectMapper.readTree(version.getSnapshotJson());
                if (!root.isObject()) {
                    warnings.add("绑定简历版本快照格式不符合白名单，已省略快照内容。");
                } else {
                    addWhitelistedText(parts, root.get("targetPosition"), "绑定简历目标岗位", warnings);
                    addWhitelistedText(parts, root.get("summary"), "绑定简历摘要", warnings);
                    addWhitelistedTextOrArray(parts, root.get("skillStack"), "绑定简历技能栈", warnings);
                }
            } catch (Exception ex) {
                warnings.add("绑定简历版本快照解析失败，已省略快照内容。");
            }
        }
        return cleanText(String.join("; ", nonBlank(parts)), 2000);
    }

    private String projectSummary(ProjectEvidence project) {
        return truncate(String.join("; ", nonBlank(java.util.Arrays.asList(
                project.getTitle(),
                project.getRole(),
                project.getTechStack(),
                project.getSolution(),
                project.getResult()))), 1000);
    }

    private List<String> contextFacts(PreparationContext context) {
        List<String> facts = new ArrayList<>();
        facts.add("日历事件：" + firstText(context.event().getTitle(), "未命名面试")
                + "，时间为 " + eventLocalTime(context.event()) + "。");
        if (context.application() != null) {
            facts.add("关联投递：" + firstText(context.application().getCompanyName(), "未知公司")
                    + " / " + firstText(context.application().getJobTitle(), "未知岗位") + "。");
        }
        if (context.resumeVersion() != null) {
            facts.add("绑定简历版本：" + firstText(context.resumeVersion().getVersionName(),
                    "V" + context.resumeVersion().getVersionNo()) + "。");
        }
        return deduplicate(facts, 8, 500);
    }

    private List<String> limits(
            PreparationContext context,
            GenerateInterviewPreparationAiVO aiResponse,
            boolean aiSafetyAdjusted) {
        List<String> limits = new ArrayList<>();
        limits.add("练习问题仅为建议方向，不代表真实面试题预测。");
        if (aiSafetyAdjusted) {
            limits.add("AI 返回的部分内容未通过中文、隐私或可信边界校验，已省略并由规则补齐。");
        }
        limits.addAll(context.sourceWarnings());
        if (aiResponse != null) {
            limits.addAll(values(aiResponse.getLimits(), 6, 500));
        }
        return deduplicate(limits, 16, 500);
    }

    private List<String> ruleFocusAreas(PreparationContext context) {
        List<String> values = new ArrayList<>();
        values.addAll(context.readinessGaps());
        values.addAll(context.recentWeaknesses());
        values.addAll(context.requirements());
        values.addAll(List.of(
                "围绕已核验的岗位信息练习一版简洁自我介绍。",
                "准备一个有证据支撑的项目故事，说明决策、取舍和可核验结果。",
                "检查面试时间、地点或会议链接，并准备结束时要提的问题。",
                "复习一个与目标岗位相关的核心技术基础，不预测真实面试题。",
                "练习说明假设、边界条件和验证步骤。",
                "准备一个关于协作、冲突处理或责任担当的真实案例。",
                "先练习简洁回答，再根据追问补充细节。",
                "准备一段基于事实的收尾总结和向面试官提问的清单。"));
        return deduplicate(values, 12, 500);
    }

    private List<String> ruleProjectStories(PreparationContext context) {
        if (!context.projectSummaries().isEmpty()) {
            return context.projectSummaries().stream()
                    .map(value -> "复练这条已核验项目证据：" + value)
                    .toList();
        }
        return List.of(
                "选择一个真实项目，按背景、职责、决策、取舍和结果组织表达。",
                "不要编造指标；对仍待核验的结果明确标注。",
                "再准备一个已核验案例，用于展示不同的技术或协作取舍。");
    }

    private List<String> rulePracticeQuestions(PreparationContext context) {
        List<String> values = new ArrayList<>();
        for (String focus : ruleFocusAreas(context)) {
            values.add("建议练习方向：围绕“" + truncate(focus, 240)
                    + "”说明一个具体案例和一个边界条件。");
        }
        values.addAll(List.of(
                "建议练习方向：说明一次困难项目决策，以及你放弃其他方案的原因。",
                "建议练习方向：描述一次线上问题的定位和验证过程。",
                "建议练习方向：说明如果今天重做该项目，你会优先改进什么。"));
        return deduplicate(values, 12, 500);
    }

    private List<String> ruleChecklist(PreparationContext context) {
        return List.of(
                "确认面试时间和时区。",
                "确认地点或会议链接，并检查设备、网络、麦克风和摄像头。",
                "打开当前投递实际绑定的简历版本。",
                "准备一版与目标岗位一致的简洁自我介绍。",
                "准备向面试官提问的清单，不假设公司内部信息。",
                "复习一个已核验项目故事及其可量化证据。",
                "准备饮水、记录工具和安静环境。",
                "准备面试后事实与明确反馈的记录模板。",
                "不要把建议练习方向当作真实面试题预测。");
    }

    private List<String> ruleSchedule(int minutes) {
        return switch (minutes) {
            case 30 -> List.of(
                    "0-8 分钟：核对岗位信息并练习自我介绍。",
                    "8-23 分钟：复练一个项目故事和三个练习方向。",
                    "23-30 分钟：检查面试安排和最终清单。");
            case 120 -> List.of(
                    "0-25 分钟：复习技术要求和岗位准备度缺口。",
                    "25-60 分钟：复练项目故事和技术取舍表达。",
                    "60-95 分钟：练习行为问题和追问方向。",
                    "95-120 分钟：检查安排、提问清单并做简短热身。");
            default -> List.of(
                    "0-15 分钟：核对岗位信息、要求并练习自我介绍。",
                    "15-40 分钟：复练项目故事和建议练习方向。",
                    "40-52 分钟：针对近期弱项做追问练习。",
                    "52-60 分钟：检查面试安排和最终清单。");
        };
    }

    private List<String> ruleNextActions(PreparationContext context) {
        List<String> actions = new ArrayList<>();
        actions.add("在日历事件开始前完成所选时间预算的准备安排。");
        if (context.application() == null) {
            actions.add("将日历事件关联到具体投递，以获得基于 JD 和简历的准备建议。");
        } else {
            actions.add("复练前核对当前关联的简历版本和项目证据。");
        }
        actions.add("面试结束后只记录亲自观察到的事实和明确反馈。");
        return actions;
    }

    private String ruleSummary(PreparationContext context, String fallbackReason) {
        if (context.application() == null) {
            return "当前仅依据日历事件生成低置信度的通用面试准备包。";
        }
        if (StringUtils.hasText(fallbackReason)) {
            return "AI 生成暂不可用，系统已基于可核验的投递证据生成规则准备包。";
        }
        return "当前准备包综合了日历事件、关联岗位、简历版本、项目证据和岗位准备度信息。";
    }

    private String confidence(
            PreparationContext context,
            boolean fallback,
            boolean aiSafetyAdjusted) {
        if (fallback || context.application() == null || context.targetJob() == null) {
            return "LOW";
        }
        boolean readinessCannotSupportHigh = context.readiness() != null
                && (Objects.equals(context.readiness().getFallback(), CommonConstants.YES)
                || "LOW".equals(normalizeCode(context.readiness().getConfidenceLevel())));
        boolean confidenceCapped = !context.jdParseComplete()
                || !context.sourceWarnings().isEmpty()
                || readinessCannotSupportHigh
                || aiSafetyAdjusted;
        if (context.resumeVersion() != null
                && !context.projectSummaries().isEmpty()
                && !context.requirements().isEmpty()
                && context.readiness() != null
                && !confidenceCapped) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private List<String> evidenceSources(
            CareerCalendarEvent event,
            JobApplication application,
            TargetJob targetJob,
            JobDescriptionAnalysis analysis,
            ResumeVersion resumeVersion,
            List<ProjectEvidence> projects,
            JobReadinessSnapshot readiness,
            List<String> recentWeaknesses) {
        List<String> values = new ArrayList<>();
        values.add("CAREER_CALENDAR_EVENT:" + event.getId());
        if (application != null) {
            values.add("JOB_APPLICATION:" + application.getId());
        }
        if (targetJob != null) {
            values.add("TARGET_JOB:" + targetJob.getId());
        }
        if (analysis != null) {
            values.add("JOB_DESCRIPTION_ANALYSIS:" + analysis.getId());
        }
        if (resumeVersion != null) {
            values.add("RESUME_VERSION:" + resumeVersion.getId());
        }
        projects.stream().map(ProjectEvidence::getId)
                .filter(Objects::nonNull)
                .forEach(id -> values.add("PROJECT_EVIDENCE:" + id));
        if (readiness != null) {
            values.add("JOB_READINESS_SNAPSHOT:" + readiness.getId());
        }
        if (!recentWeaknesses.isEmpty()) {
            values.add("INTERVIEW_WEAKNESS_SUMMARY");
        }
        return values;
    }

    private PreparationClaim claimPreparation(
            Long userId,
            CareerCalendarEvent event,
            String sourceHash) {
        if (isFreshGenerating(event)) {
            if (Objects.equals(sourceHash, event.getPreparationSourceHash())) {
                return PreparationClaim.winner(awaitPreparedWinner(userId, event.getId(), sourceHash));
            }
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "面试准备包正在生成，请稍后重试");
        }

        LocalDateTime claimedAt = LocalDateTime.now().withNano(0);
        int affectedRows = eventMapper.compareAndSetPreparation(
                event.getId(),
                userId,
                event.getUpdatedAt(),
                event.getPreparationStatus(),
                event.getPreparationSourceHash(),
                event.getPreparationGeneratedAt(),
                event.getPreparationJson(),
                STATUS_GENERATING,
                null,
                claimedAt,
                sourceHash);
        if (affectedRows == 1) {
            return PreparationClaim.claimed(event.getUpdatedAt(), claimedAt);
        }

        CareerCalendarEvent winner = ownedInterviewEvent(userId, event.getId());
        CareerInterviewPreparationVO winnerPreparation = readPreparation(winner);
        if (reusable(winner, winnerPreparation, sourceHash)) {
            return PreparationClaim.winner(winnerPreparation);
        }
        if (isFreshGenerating(winner)
                && Objects.equals(sourceHash, winner.getPreparationSourceHash())) {
            return PreparationClaim.winner(awaitPreparedWinner(userId, event.getId(), sourceHash));
        }
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "面试准备包正在并发更新，请稍后重试");
    }

    private CareerInterviewPreparationVO awaitPreparedWinner(
            Long userId,
            Long eventId,
            String sourceHash) {
        for (int attempt = 0; attempt < MAX_CLAIM_WAIT_ATTEMPTS; attempt++) {
            CareerCalendarEvent winner = ownedInterviewEvent(userId, eventId);
            CareerInterviewPreparationVO preparation = readPreparation(winner);
            if (reusable(winner, preparation, sourceHash)) {
                return preparation;
            }
            if (!Objects.equals(sourceHash, winner.getPreparationSourceHash())
                    || !STATUS_GENERATING.equals(normalizeCode(winner.getPreparationStatus()))) {
                break;
            }
            try {
                Thread.sleep(CLAIM_WAIT_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "面试准备包仍在生成，请稍后重试");
    }

    private boolean isFreshGenerating(CareerCalendarEvent event) {
        if (event == null
                || !STATUS_GENERATING.equals(normalizeCode(event.getPreparationStatus()))
                || event.getPreparationGeneratedAt() == null) {
            return false;
        }
        Duration age = Duration.between(event.getPreparationGeneratedAt(), LocalDateTime.now());
        return age.isNegative() || age.compareTo(GENERATING_TTL) < 0;
    }

    private boolean reusable(
            CareerCalendarEvent event,
            CareerInterviewPreparationVO preparation,
            String sourceHash) {
        String status = normalizeCode(event == null ? null : event.getPreparationStatus());
        return preparation != null
                && Set.of(STATUS_READY, STATUS_FALLBACK).contains(status)
                && Objects.equals(sourceHash, event.getPreparationSourceHash())
                && !Boolean.TRUE.equals(preparation.getStale());
    }

    private CareerInterviewPreparationVO readPreparation(CareerCalendarEvent event) {
        if (!StringUtils.hasText(event.getPreparationJson())) {
            if (!StringUtils.hasText(event.getPreparationStatus())
                    && !StringUtils.hasText(event.getPreparationSourceHash())
                    && event.getPreparationGeneratedAt() == null) {
                return null;
            }
            return applyPreparationState(new CareerInterviewPreparationVO(), event);
        }
        try {
            CareerInterviewPreparationVO vo =
                    objectMapper.readValue(event.getPreparationJson(), CareerInterviewPreparationVO.class);
            return applyPreparationState(vo, event);
        } catch (Exception ex) {
            log.warn("Ignore malformed interview preparation JSON, calendarEventId={}", event.getId(), ex);
            CareerInterviewPreparationVO unavailable = new CareerInterviewPreparationVO();
            unavailable.setStatus(firstText(event.getPreparationStatus(), STATUS_STALE));
            unavailable.setStale(true);
            unavailable.setStaleReason("已保存的面试准备包内容不可用，不能作为当前结果。");
            unavailable.setLimits(List.of(unavailable.getStaleReason()));
            unavailable = applyPreparationState(unavailable, event);
            unavailable.setStatus(STATUS_STALE);
            markStale(unavailable, "已保存的面试准备包内容不可用，不能作为当前结果。");
            return unavailable;
        }
    }

    private CareerInterviewPreparationVO applyPreparationState(
            CareerInterviewPreparationVO vo,
            CareerCalendarEvent event) {
        vo.setCalendarEventId(event.getId());
        vo.setApplicationId(event.getApplicationId());
        String status = firstText(event.getPreparationStatus(), vo.getStatus());
        vo.setStatus(status);
        vo.setSourceHash(firstText(event.getPreparationSourceHash(), vo.getSourceHash()));
        vo.setGeneratedAt(event.getPreparationGeneratedAt() == null
                ? vo.getGeneratedAt() : event.getPreparationGeneratedAt());
        if (STATUS_GENERATING.equals(normalizeCode(status))) {
            vo.setAiCallLogId(event.getPreparationAiCallLogId());
        } else if (event.getPreparationAiCallLogId() != null) {
            vo.setAiCallLogId(event.getPreparationAiCallLogId());
        }

        String normalizedStatus = normalizeCode(status);
        if (STATUS_STALE.equals(normalizedStatus)) {
            markStale(vo, "日历事件已更新，当前准备包已过期，不能作为本次面试的当前结果。");
        } else if (STATUS_GENERATING.equals(normalizedStatus)) {
            markStale(vo, "面试准备包正在重新生成，已保存的旧内容不能作为当前结果。");
        } else {
            vo.setStale(false);
            vo.setStaleReason(null);
        }
        return vo;
    }

    private void markStale(CareerInterviewPreparationVO vo, String reason) {
        vo.setStale(true);
        vo.setStaleReason(reason);
        List<String> limits = new ArrayList<>();
        limits.add(reason);
        limits.addAll(vo.getLimits() == null ? List.of() : vo.getLimits());
        vo.setLimits(deduplicate(limits, 16, 500));
    }

    private boolean usable(GenerateInterviewPreparationAiVO response) {
        return response != null && (StringUtils.hasText(response.getSummary())
                || hasText(response.getFocusAreas())
                || hasText(response.getPracticeQuestions())
                || hasText(response.getChecklist()));
    }

    private AiSanitizationResult sanitizeAiResponse(GenerateInterviewPreparationAiVO response) {
        if (response == null) {
            return new AiSanitizationResult(null, 0);
        }
        GenerateInterviewPreparationAiVO sanitized = new GenerateInterviewPreparationAiVO();
        int rejected = 0;

        AiTextSanitization summary = sanitizeAiText(response.getSummary(), 1000);
        sanitized.setSummary(summary.value());
        rejected += summary.rejected();

        AiListSanitization facts = sanitizeAiValues(response.getFacts(), 8, 500);
        sanitized.setFacts(facts.values());
        rejected += facts.rejected();
        AiListSanitization limits = sanitizeAiValues(response.getLimits(), 8, 500);
        sanitized.setLimits(limits.values());
        rejected += limits.rejected();
        AiListSanitization focusAreas = sanitizeAiValues(response.getFocusAreas(), 8, 500);
        sanitized.setFocusAreas(focusAreas.values());
        rejected += focusAreas.rejected();
        AiListSanitization projectStories = sanitizeAiValues(response.getProjectStories(), 3, 1000);
        sanitized.setProjectStories(projectStories.values());
        rejected += projectStories.rejected();
        AiListSanitization practiceQuestions = sanitizeAiValues(response.getPracticeQuestions(), 8, 500);
        sanitized.setPracticeQuestions(practiceQuestions.values());
        rejected += practiceQuestions.rejected();
        AiListSanitization checklist = sanitizeAiValues(response.getChecklist(), 10, 500);
        sanitized.setChecklist(checklist.values());
        rejected += checklist.rejected();
        AiListSanitization schedule = sanitizeAiValues(response.getSchedule(), 6, 500);
        sanitized.setSchedule(schedule.values());
        rejected += schedule.rejected();
        AiListSanitization nextActions = sanitizeAiValues(response.getNextActions(), 4, 500);
        sanitized.setNextActions(nextActions.values());
        rejected += nextActions.rejected();

        sanitized.setFallback(response.getFallback());
        sanitized.setAiCallLogId(response.getAiCallLogId());
        sanitized.setSourceHash(response.getSourceHash());
        return new AiSanitizationResult(sanitized, rejected);
    }

    private AiTextSanitization sanitizeAiText(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return new AiTextSanitization(null, 0);
        }
        String cleaned = cleanText(value, limit);
        if (!StringUtils.hasText(cleaned)
                || containsProhibitedAiSemantic(cleaned)
                || !containsMeaningfulChinese(cleaned)) {
            return new AiTextSanitization(null, 1);
        }
        return new AiTextSanitization(cleaned, 0);
    }

    private AiListSanitization sanitizeAiValues(
            Collection<String> values,
            int limit,
            int itemLimit) {
        if (values == null || values.isEmpty()) {
            return new AiListSanitization(new ArrayList<>(), 0);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        int rejected = 0;
        for (String value : values) {
            AiTextSanitization item = sanitizeAiText(value, itemLimit);
            rejected += item.rejected();
            if (StringUtils.hasText(item.value())) {
                result.add(item.value());
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return new AiListSanitization(new ArrayList<>(result), rejected);
    }

    private boolean containsMeaningfulChinese(String value) {
        String meaningful = value
                .replace("[邮箱已脱敏]", "")
                .replace("[电话已脱敏]", "")
                .replace("[证件号已脱敏]", "")
                .replace("[敏感信息已脱敏]", "");
        return HAN_PATTERN.matcher(meaningful).find();
    }

    private boolean containsProhibitedAiSemantic(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return PROHIBITED_AI_SEMANTICS.stream().anyMatch(normalized::contains);
    }

    private List<String> fillToCount(List<String> preferred, List<String> fallback, int count) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (preferred != null) {
            result.addAll(preferred);
        }
        if (fallback != null) {
            result.addAll(fallback);
        }
        return result.stream().filter(StringUtils::hasText).limit(count).toList();
    }

    private int normalizeTimeBudget(Integer requested) {
        if (requested == null) {
            return 60;
        }
        if (requested <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "准备时长必须大于 0 分钟");
        }
        if (requested <= 30) {
            return 30;
        }
        if (requested <= 60) {
            return 60;
        }
        return 120;
    }

    private int focusCount(int minutes) {
        return minutes == 30 ? 3 : minutes == 60 ? 5 : 8;
    }

    private int projectCount(int minutes) {
        return minutes == 30 ? 1 : minutes == 60 ? 2 : 3;
    }

    private int questionCount(int minutes) {
        return minutes == 30 ? 3 : minutes == 60 ? 5 : 8;
    }

    private int checklistCount(int minutes) {
        return minutes == 30 ? 5 : minutes == 60 ? 7 : 9;
    }

    private String eventLocalTime(CareerCalendarEvent event) {
        if (event.getStartsAtUtc() == null) {
            return null;
        }
        ZoneId zone;
        try {
            zone = StringUtils.hasText(event.getTimezone())
                    ? ZoneId.of(event.getTimezone().trim())
                    : ZoneOffset.UTC;
        } catch (RuntimeException ignored) {
            zone = ZoneOffset.UTC;
        }
        return LOCAL_TIME_FORMAT.format(LocalDateTime.ofInstant(
                event.getStartsAtUtc().toInstant(ZoneOffset.UTC), zone));
    }

    private List<String> readStringArray(
            String json,
            String sourceName,
            List<String> warnings,
            int limit) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                warnings.add(sourceName + "字段格式不符合白名单，已省略该来源。");
                return List.of();
            }
            List<String> values = new ArrayList<>();
            boolean invalidItem = false;
            for (JsonNode item : root) {
                if (!item.isTextual()) {
                    invalidItem = true;
                    continue;
                }
                String text = cleanText(item.asText(), 500);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
                if (values.size() >= limit) {
                    break;
                }
            }
            if (invalidItem) {
                warnings.add(sourceName + "包含非白名单字段，相关内容已省略。");
            }
            return deduplicate(values, limit, 500);
        } catch (Exception ex) {
            warnings.add(sourceName + "解析失败，已省略该来源。");
            return List.of();
        }
    }

    private List<String> readinessSummaryGaps(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                warnings.add("岗位准备度摘要格式不符合白名单，已省略摘要明细。");
                return List.of();
            }
            List<String> gaps = new ArrayList<>();
            Integer mustMissingCount = whitelistedInteger(root, "mustMissingCount");
            Integer missingCount = whitelistedInteger(root, "missingCount");
            Integer weakCount = whitelistedInteger(root, "weakCount");
            String confidence = normalizeCode(whitelistedText(root, "confidenceLevel", 40));
            boolean fallback = whitelistedBoolean(root, "fallback");
            boolean sampleInsufficient = whitelistedBoolean(root, "sampleInsufficient");
            if (mustMissingCount != null && mustMissingCount > 0) {
                gaps.add("岗位准备度摘要中有 " + mustMissingCount + " 个必须项缺少证据。");
            }
            if (missingCount != null && missingCount > 0) {
                gaps.add("岗位准备度摘要中有 " + missingCount + " 项要求缺少证据。");
            }
            if (weakCount != null && weakCount > 0) {
                gaps.add("岗位准备度摘要中有 " + weakCount + " 项要求证据偏弱。");
            }
            if (fallback || sampleInsufficient || "LOW".equals(confidence)) {
                gaps.add("岗位准备度摘要当前样本或置信度不足。");
            }
            return gaps;
        } catch (Exception ex) {
            warnings.add("岗位准备度摘要解析失败，已省略摘要明细。");
            return List.of();
        }
    }

    private List<String> readinessMatrixGaps(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode requirements = root.isObject() ? root.get("requirements") : null;
            if (requirements == null || !requirements.isArray()) {
                warnings.add("岗位准备度矩阵格式不符合白名单，已省略矩阵明细。");
                return List.of();
            }
            List<String> gaps = new ArrayList<>();
            boolean invalidItem = false;
            for (JsonNode item : requirements) {
                if (!item.isObject()) {
                    invalidItem = true;
                    continue;
                }
                String name = whitelistedText(item, "requirementName", 240);
                String coverage = normalizeCode(whitelistedText(item, "coverageLevel", 40));
                String confidence = normalizeCode(whitelistedText(item, "requirementConfidence", 40));
                boolean fallback = whitelistedBoolean(item, "requirementFallback");
                String priority = normalizeCode(whitelistedText(item, "priority", 40));
                if (!StringUtils.hasText(name)) {
                    invalidItem = true;
                    continue;
                }
                String prefix = "MUST".equals(priority) ? "必须项“" : "岗位要求“";
                if ("MISSING".equals(coverage)) {
                    gaps.add(prefix + name + "”当前缺少可用证据。");
                } else if ("WEAK".equals(coverage)) {
                    gaps.add(prefix + name + "”当前证据偏弱。");
                } else if (fallback || "LOW".equals(confidence)) {
                    gaps.add(prefix + name + "”当前仅有低置信度或降级证据。");
                }
                if (gaps.size() >= MAX_WEAKNESSES) {
                    break;
                }
            }
            if (invalidItem) {
                warnings.add("岗位准备度矩阵包含非白名单结构，相关内容已省略。");
            }
            return gaps;
        } catch (Exception ex) {
            warnings.add("岗位准备度矩阵解析失败，已省略矩阵明细。");
            return List.of();
        }
    }

    private List<String> readinessDimensionGaps(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                warnings.add("岗位准备度维度数据格式不符合白名单，已省略维度明细。");
                return List.of();
            }
            List<String> gaps = new ArrayList<>();
            boolean invalidItem = false;
            for (JsonNode item : root) {
                if (!item.isObject()) {
                    invalidItem = true;
                    continue;
                }
                String dimension = whitelistedText(item, "dimension", 80);
                String confidence = normalizeCode(whitelistedText(item, "confidenceLevel", 40));
                Integer score = whitelistedInteger(item, "score");
                boolean fallback = whitelistedBoolean(item, "fallback");
                boolean sampleInsufficient = whitelistedBoolean(item, "sampleInsufficient");
                if (!StringUtils.hasText(dimension)) {
                    invalidItem = true;
                    continue;
                }
                if (fallback || sampleInsufficient || "LOW".equals(confidence)
                        || (score != null && score < 60)) {
                    gaps.add("准备度维度“" + dimension + "”当前样本或置信度不足。");
                }
                if (gaps.size() >= MAX_WEAKNESSES) {
                    break;
                }
            }
            if (invalidItem) {
                warnings.add("岗位准备度维度数据包含非白名单结构，相关内容已省略。");
            }
            return gaps;
        } catch (Exception ex) {
            warnings.add("岗位准备度维度数据解析失败，已省略维度明细。");
            return List.of();
        }
    }

    private void addWhitelistedText(
            List<String> target,
            JsonNode node,
            String sourceName,
            List<String> warnings) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isTextual()) {
            warnings.add(sourceName + "字段格式不符合白名单，已省略。");
            return;
        }
        String value = cleanText(node.asText(), 1000);
        if (StringUtils.hasText(value)) {
            target.add(value);
        }
    }

    private void addWhitelistedTextOrArray(
            List<String> target,
            JsonNode node,
            String sourceName,
            List<String> warnings) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addWhitelistedText(target, node, sourceName, warnings);
            return;
        }
        if (!node.isArray()) {
            warnings.add(sourceName + "字段格式不符合白名单，已省略。");
            return;
        }
        List<String> values = new ArrayList<>();
        boolean invalidItem = false;
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                invalidItem = true;
                continue;
            }
            String value = cleanText(item.asText(), 200);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        if (invalidItem) {
            warnings.add(sourceName + "包含非白名单字段，相关内容已省略。");
        }
        if (!values.isEmpty()) {
            target.add(String.join(", ", values));
        }
    }

    private String whitelistedText(JsonNode object, String field, int limit) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isTextual() ? cleanText(value.asText(), limit) : null;
    }

    private boolean whitelistedBoolean(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private Integer whitelistedInteger(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isIntegralNumber() ? value.intValue() : null;
    }

    private List<String> values(Collection<String> values, int limit, int itemLimit) {
        return deduplicate(values, limit, itemLimit);
    }

    private List<String> deduplicate(Collection<String> values, int limit, int itemLimit) {
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

    private List<String> nonBlank(Collection<String> values) {
        return values == null ? List.of() : values.stream().filter(StringUtils::hasText).toList();
    }

    private boolean hasText(Collection<String> values) {
        return values != null && values.stream().anyMatch(StringUtils::hasText);
    }

    private String cleanText(String value, int limit) {
        return StringUtils.hasText(value) ? truncate(maskPii(value.trim()), limit) : null;
    }

    private String maskPii(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String masked = EMAIL_PATTERN.matcher(value).replaceAll("[邮箱已脱敏]");
        masked = CHINA_MOBILE_PATTERN.matcher(masked).replaceAll("[电话已脱敏]");
        masked = CHINA_ID_PATTERN.matcher(masked).replaceAll("[证件号已脱敏]");
        return SECRET_PATTERN.matcher(masked).replaceAll("$1=[敏感信息已脱敏]");
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "面试准备包序列化失败");
        }
    }

    private record PreparationContext(
            Long userId,
            CareerCalendarEvent event,
            JobApplication application,
            TargetJob targetJob,
            JobDescriptionAnalysis analysis,
            ResumeVersion resumeVersion,
            List<ProjectEvidence> projects,
            JobReadinessSnapshot readiness,
            boolean jdParseComplete,
            int timeBudget,
            List<String> requirements,
            String resumeVersionSummary,
            List<String> projectSummaries,
            List<String> readinessGaps,
            List<String> recentWeaknesses,
            List<String> confirmedMemories,
            List<String> sourceWarnings,
            List<String> evidenceSources) {
    }

    private record PreparationClaim(
            boolean claimed,
            LocalDateTime eventUpdatedAt,
            LocalDateTime claimedAt,
            CareerInterviewPreparationVO winner) {

        private static PreparationClaim claimed(
                LocalDateTime eventUpdatedAt,
                LocalDateTime claimedAt) {
            return new PreparationClaim(true, eventUpdatedAt, claimedAt, null);
        }

        private static PreparationClaim winner(CareerInterviewPreparationVO winner) {
            return new PreparationClaim(false, null, null, winner);
        }
    }

    private record AiSanitizationResult(
            GenerateInterviewPreparationAiVO response,
            int rejectedCount) {
    }

    private record AiTextSanitization(String value, int rejected) {
    }

    private record AiListSanitization(List<String> values, int rejected) {
    }
}
