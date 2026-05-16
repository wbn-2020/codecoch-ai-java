package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
import com.codecoachai.interview.domain.dto.StudyTaskStatusUpdateDTO;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.StudyPlan;
import com.codecoachai.interview.domain.entity.StudyTask;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.vo.StudyPlanDetailVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.domain.vo.StudyTaskVO;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.GenerateLearningPlanDTO;
import com.codecoachai.interview.feign.vo.GenerateLearningPlanVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.StudyPlanMapper;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import com.codecoachai.interview.service.StudyPlanService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StudyPlanServiceImpl implements StudyPlanService {

    private static final String SOURCE_TYPE_REPORT = "REPORT";
    private static final String PLAN_GENERATING = "GENERATING";
    private static final String PLAN_ACTIVE = "ACTIVE";
    private static final String PLAN_FAILED = "FAILED";
    private static final String TASK_TODO = "TODO";
    private static final int DEFAULT_DURATION_DAYS = 14;
    private static final int MAX_SUMMARY_LENGTH = 4000;

    private final StudyPlanMapper studyPlanMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final ResumeFeignClient resumeFeignClient;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyPlanGenerateVO generate(StudyPlanGenerateDTO dto) {
        Long userId = requireCurrentUserId();
        InterviewReport report = getOwnedGeneratedReport(dto.getReportId(), userId);
        StudyPlan existing = latestPlan(userId, report.getId());
        if (existing != null && (PLAN_ACTIVE.equals(existing.getPlanStatus())
                || PLAN_GENERATING.equals(existing.getPlanStatus()))) {
            return toGenerateVO(existing);
        }
        return generateNewPlan(userId, report, dto, null);
    }

    @Override
    public PageResult<StudyPlanListVO> list(StudyPlanQueryDTO dto) {
        Long userId = requireCurrentUserId();
        long pageNo = dto == null || dto.getPageNo() == null ? 1L : dto.getPageNo();
        long pageSize = dto == null || dto.getPageSize() == null ? 10L : dto.getPageSize();
        LambdaQueryWrapper<StudyPlan> query = new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getDeleted, CommonConstants.NO)
                .orderByDesc(StudyPlan::getUpdatedAt);
        if (dto != null && StringUtils.hasText(dto.getPlanStatus())) {
            query.eq(StudyPlan::getPlanStatus, normalizePlanStatus(dto.getPlanStatus()));
        }
        Page<StudyPlan> page = studyPlanMapper.selectPage(Page.of(pageNo, pageSize), query);
        return PageResult.of(page.getRecords().stream().map(this::toListVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public StudyPlanDetailVO detail(Long id) {
        StudyPlan plan = getOwnedPlan(id, requireCurrentUserId());
        StudyPlanDetailVO vo = toDetailVO(plan);
        vo.setTasks(taskEntities(plan.getId()).stream().map(this::toTaskVO).toList());
        return vo;
    }

    @Override
    public List<StudyTaskVO> tasks(Long planId) {
        StudyPlan plan = getOwnedPlan(planId, requireCurrentUserId());
        return taskEntities(plan.getId()).stream().map(this::toTaskVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyTaskVO updateTaskStatus(Long taskId, StudyTaskStatusUpdateDTO dto) {
        Long userId = requireCurrentUserId();
        StudyTask task = studyTaskMapper.selectOne(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getId, taskId)
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Study task not found");
        }
        StudyPlan plan = getOwnedPlan(task.getPlanId(), userId);
        if (PLAN_FAILED.equals(plan.getPlanStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Study plan is failed");
        }
        task.setTaskStatus(normalizeTaskStatus(dto.getTaskStatus()));
        studyTaskMapper.updateById(task);
        return toTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyPlanGenerateVO regenerate(Long id) {
        Long userId = requireCurrentUserId();
        StudyPlan plan = getOwnedPlan(id, userId);
        if (!PLAN_FAILED.equals(plan.getPlanStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only failed study plan can be regenerated");
        }
        InterviewReport report = getOwnedGeneratedReport(plan.getReportId(), userId);
        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(report.getId());
        dto.setResumeId(plan.getResumeId());
        dto.setOptimizeRecordId(plan.getOptimizeRecordId());
        dto.setTargetPosition(plan.getTargetPosition());
        dto.setIndustryDirection(plan.getIndustryDirection());
        dto.setExpectedDurationDays(plan.getDurationDays());
        return generateNewPlan(userId, report, dto, plan);
    }

    private StudyPlanGenerateVO generateNewPlan(Long userId, InterviewReport report,
                                                StudyPlanGenerateDTO dto, StudyPlan reusablePlan) {
        InterviewSession session = getSession(report.getSessionId(), userId);
        InnerResumeDetailVO resume = loadResume(userId, firstLong(dto.getResumeId(), session.getResumeId()));
        InnerResumeOptimizeRecordVO optimizeRecord = loadOptimizeRecord(userId, dto.getOptimizeRecordId());

        StudyPlan plan = reusablePlan == null ? new StudyPlan() : reusablePlan;
        plan.setUserId(userId);
        plan.setSourceType(SOURCE_TYPE_REPORT);
        plan.setSourceId(report.getId());
        plan.setReportId(report.getId());
        plan.setSessionId(session.getId());
        plan.setResumeId(resume == null ? firstLong(dto.getResumeId(), session.getResumeId()) : resume.getId());
        plan.setOptimizeRecordId(optimizeRecord == null ? dto.getOptimizeRecordId()
                : optimizeRecord.getOptimizeRecordId());
        plan.setTargetPosition(firstText(dto.getTargetPosition(), session.getTargetPosition()));
        plan.setIndustryDirection(firstText(dto.getIndustryDirection(), session.getIndustryDirection()));
        plan.setDurationDays(normalizeDuration(dto.getExpectedDurationDays()));
        plan.setPlanStatus(PLAN_GENERATING);
        plan.setFailureReason(null);
        plan.setRequestJson(toJson(dto));
        if (plan.getId() == null) {
            studyPlanMapper.insert(plan);
        } else {
            studyPlanMapper.updateById(plan);
            cleanupTasks(plan.getId());
        }

        GenerateLearningPlanDTO aiRequest = buildAiRequest(plan, session, report, dto, resume, optimizeRecord);
        plan.setRequestJson(toJson(aiRequest));
        studyPlanMapper.updateById(plan);
        try {
            GenerateLearningPlanVO aiPlan = FeignResultUtils.unwrap(aiFeignClient.generateLearningPlan(aiRequest));
            validateAiPlan(aiPlan);
            plan.setPlanTitle(firstText(aiPlan.getPlanTitle(), defaultTitle(plan)));
            plan.setPlanSummary(firstText(aiPlan.getPlanSummary(), report.getSummary()));
            plan.setDurationDays(normalizeDuration(aiPlan.getDurationDays()));
            plan.setAiCallLogId(aiPlan.getAiCallLogId());
            plan.setResultJson(toJson(aiPlan));
            plan.setFailureReason(null);
            insertTasks(plan, aiPlan);
            plan.setPlanStatus(PLAN_ACTIVE);
            studyPlanMapper.updateById(plan);
        } catch (RuntimeException ex) {
            cleanupTasks(plan.getId());
            plan.setPlanStatus(PLAN_FAILED);
            plan.setFailureReason(truncate(firstText(ex.getMessage(), "Learning plan generation failed"), 500));
            studyPlanMapper.updateById(plan);
        }
        return toGenerateVO(plan);
    }

    private GenerateLearningPlanDTO buildAiRequest(StudyPlan plan, InterviewSession session, InterviewReport report,
                                                   StudyPlanGenerateDTO dto, InnerResumeDetailVO resume,
                                                   InnerResumeOptimizeRecordVO optimizeRecord) {
        GenerateLearningPlanDTO request = new GenerateLearningPlanDTO();
        request.setLearningPlanId(plan.getId());
        request.setUserId(plan.getUserId());
        request.setReportId(report.getId());
        request.setSessionId(session.getId());
        request.setResumeId(plan.getResumeId());
        request.setOptimizeRecordId(plan.getOptimizeRecordId());
        request.setTargetPosition(plan.getTargetPosition());
        request.setIndustryDirection(plan.getIndustryDirection());
        request.setExperienceLevel(session.getExperienceLevel());
        request.setInterviewSummary(summary(report));
        request.setWeaknessSummary(weaknessSummary(report));
        request.setQuestionPerformanceSummary(questionPerformanceSummary(session.getId()));
        request.setResumeWeaknessSummary(resumeWeaknessSummary(resume, optimizeRecord));
        request.setExpectedDurationDays(plan.getDurationDays());
        request.setExtraRequirements(dto.getExtraRequirements());
        return request;
    }

    private void insertTasks(StudyPlan plan, GenerateLearningPlanVO aiPlan) {
        int order = 1;
        for (GenerateLearningPlanVO.StageVO stage : aiPlan.getStages()) {
            if (stage == null || stage.getItems() == null) {
                continue;
            }
            int stageNo = stage.getStageNo() == null ? 1 : stage.getStageNo();
            for (GenerateLearningPlanVO.ItemVO item : stage.getItems()) {
                if (item == null || !StringUtils.hasText(item.getTaskTitle())) {
                    continue;
                }
                StudyTask task = new StudyTask();
                task.setPlanId(plan.getId());
                task.setUserId(plan.getUserId());
                task.setStageNo(stageNo);
                task.setStageTitle(stage.getStageTitle());
                task.setTaskOrder(order++);
                task.setKnowledgePoint(item.getKnowledgePoint());
                task.setTaskTitle(item.getTaskTitle());
                task.setTaskDescription(item.getTaskDescription());
                task.setTaskType(normalizeTaskType(item.getTaskType()));
                task.setPriority(normalizePriority(item.getPriority()));
                task.setEstimatedHours(item.getEstimatedHours() == null ? 1 : Math.max(1, item.getEstimatedHours()));
                task.setTaskStatus(TASK_TODO);
                task.setRelatedQuestionIdsJson(toJson(item.getRelatedQuestionIds() == null
                        ? List.of()
                        : item.getRelatedQuestionIds()));
                task.setRelatedTagsJson(toJson(item.getRelatedTags() == null ? List.of() : item.getRelatedTags()));
                task.setResourcesJson(toJson(item.getResources() == null ? List.of() : item.getResources()));
                studyTaskMapper.insert(task);
            }
        }
    }

    private void cleanupTasks(Long planId) {
        if (planId == null) {
            return;
        }
        studyTaskMapper.delete(new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getPlanId, planId));
    }

    private StudyPlan latestPlan(Long userId, Long reportId) {
        return studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getReportId, reportId)
                .eq(StudyPlan::getDeleted, CommonConstants.NO)
                .orderByDesc(StudyPlan::getUpdatedAt)
                .last("limit 1"));
    }

    private InterviewReport getOwnedGeneratedReport(Long reportId, Long userId) {
        InterviewReport report = reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getId, reportId)
                .eq(InterviewReport::getUserId, userId)
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview report not found");
        }
        if (!ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview report is not generated");
        }
        return report;
    }

    private InterviewSession getSession(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.selectOne(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview session not found");
        }
        return session;
    }

    private StudyPlan getOwnedPlan(Long id, Long userId) {
        StudyPlan plan = studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getId, id)
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (plan == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Study plan not found");
        }
        return plan;
    }

    private InnerResumeDetailVO loadResume(Long userId, Long resumeId) {
        if (resumeId == null) {
            return null;
        }
        InnerResumeDetailVO resume = FeignResultUtils.unwrap(resumeFeignClient.getResume(resumeId));
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume not found");
        }
        return resume;
    }

    private InnerResumeOptimizeRecordVO loadOptimizeRecord(Long userId, Long optimizeRecordId) {
        if (optimizeRecordId == null) {
            return null;
        }
        InnerResumeOptimizeRecordVO record = FeignResultUtils.unwrap(resumeFeignClient.getOptimizeRecord(optimizeRecordId));
        if (record == null || !userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume optimize record not found");
        }
        return record;
    }

    private List<StudyTask> taskEntities(Long planId) {
        return studyTaskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getPlanId, planId)
                .eq(StudyTask::getDeleted, CommonConstants.NO)
                .orderByAsc(StudyTask::getStageNo)
                .orderByAsc(StudyTask::getTaskOrder)
                .orderByAsc(StudyTask::getId));
    }

    private StudyPlanListVO toListVO(StudyPlan plan) {
        StudyPlanListVO vo = new StudyPlanListVO();
        vo.setId(plan.getId());
        vo.setReportId(plan.getReportId());
        vo.setSessionId(plan.getSessionId());
        vo.setSourceType(plan.getSourceType());
        vo.setTargetPosition(plan.getTargetPosition());
        vo.setIndustryDirection(plan.getIndustryDirection());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanSummary(plan.getPlanSummary());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setDurationDays(plan.getDurationDays());
        fillProgress(vo, plan.getId());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private StudyPlanDetailVO toDetailVO(StudyPlan plan) {
        StudyPlanDetailVO vo = new StudyPlanDetailVO();
        vo.setId(plan.getId());
        vo.setReportId(plan.getReportId());
        vo.setSessionId(plan.getSessionId());
        vo.setResumeId(plan.getResumeId());
        vo.setOptimizeRecordId(plan.getOptimizeRecordId());
        vo.setSourceType(plan.getSourceType());
        vo.setTargetPosition(plan.getTargetPosition());
        vo.setIndustryDirection(plan.getIndustryDirection());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanSummary(plan.getPlanSummary());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setDurationDays(plan.getDurationDays());
        vo.setAiCallLogId(plan.getAiCallLogId());
        vo.setFailureReason(plan.getFailureReason());
        fillProgress(vo, plan.getId());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private void fillProgress(StudyPlanListVO vo, Long planId) {
        List<StudyTask> tasks = taskEntities(planId);
        int total = tasks.size();
        int done = (int) tasks.stream().filter(task -> "DONE".equals(task.getTaskStatus())).count();
        vo.setTotalTaskCount(total);
        vo.setDoneTaskCount(done);
        vo.setProgressPercent(total == 0 ? 0 : done * 100 / total);
    }

    private void fillProgress(StudyPlanDetailVO vo, Long planId) {
        List<StudyTask> tasks = taskEntities(planId);
        int total = tasks.size();
        int done = (int) tasks.stream().filter(task -> "DONE".equals(task.getTaskStatus())).count();
        vo.setTotalTaskCount(total);
        vo.setDoneTaskCount(done);
        vo.setProgressPercent(total == 0 ? 0 : done * 100 / total);
    }

    private StudyTaskVO toTaskVO(StudyTask task) {
        StudyTaskVO vo = new StudyTaskVO();
        vo.setId(task.getId());
        vo.setPlanId(task.getPlanId());
        vo.setStageNo(task.getStageNo());
        vo.setStageTitle(task.getStageTitle());
        vo.setTaskOrder(task.getTaskOrder());
        vo.setKnowledgePoint(task.getKnowledgePoint());
        vo.setTaskTitle(task.getTaskTitle());
        vo.setTaskDescription(task.getTaskDescription());
        vo.setTaskType(task.getTaskType());
        vo.setPriority(task.getPriority());
        vo.setEstimatedHours(task.getEstimatedHours());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setRelatedQuestionIds(readList(task.getRelatedQuestionIdsJson(), new TypeReference<List<Long>>() {}));
        vo.setRelatedTags(readList(task.getRelatedTagsJson(), new TypeReference<List<String>>() {}));
        vo.setResources(readList(task.getResourcesJson(), new TypeReference<List<String>>() {}));
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private StudyPlanGenerateVO toGenerateVO(StudyPlan plan) {
        StudyPlanGenerateVO vo = new StudyPlanGenerateVO();
        vo.setPlanId(plan.getId());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setFailureReason(plan.getFailureReason());
        return vo;
    }

    private void validateAiPlan(GenerateLearningPlanVO aiPlan) {
        if (aiPlan == null || aiPlan.getStages() == null || aiPlan.getStages().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Learning plan response missing stages");
        }
        boolean hasItem = aiPlan.getStages().stream()
                .anyMatch(stage -> stage != null && stage.getItems() != null && !stage.getItems().isEmpty());
        if (!hasItem) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Learning plan response missing tasks");
        }
    }

    private String questionPerformanceSummary(Long sessionId) {
        List<InterviewMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .eq(InterviewMessage::getDeleted, CommonConstants.NO)
                .orderByAsc(InterviewMessage::getCreatedAt)
                .orderByAsc(InterviewMessage::getId));
        StringBuilder builder = new StringBuilder();
        for (InterviewMessage message : messages) {
            if (builder.length() > MAX_SUMMARY_LENGTH) {
                break;
            }
            if ("ANSWER".equals(message.getMessageType())) {
                appendLine(builder, "answer", message.getUserAnswer());
            }
            if ("EVALUATION".equals(message.getMessageType())) {
                appendLine(builder, "score", message.getScore() == null ? null : String.valueOf(message.getScore()));
                appendLine(builder, "comment", firstText(message.getAiComment(), message.getComment()));
                appendLine(builder, "knowledgePoints", message.getKnowledgePoints());
            }
        }
        return truncate(builder.toString(), MAX_SUMMARY_LENGTH);
    }

    private String resumeWeaknessSummary(InnerResumeDetailVO resume, InnerResumeOptimizeRecordVO optimizeRecord) {
        StringBuilder builder = new StringBuilder();
        if (resume != null) {
            appendLine(builder, "resumeTitle", resume.getTitle());
            appendLine(builder, "resumeSummary", resume.getSummary());
        }
        if (optimizeRecord != null) {
            appendLine(builder, "optimizeStatus", optimizeRecord.getOptimizeStatus());
            appendLine(builder, "optimizeResult", truncate(optimizeRecord.getResultJson(), 2000));
            appendLine(builder, "optimizeError", optimizeRecord.getErrorMessage());
        }
        return truncate(builder.toString(), MAX_SUMMARY_LENGTH);
    }

    private String summary(InterviewReport report) {
        return truncate(firstText(report.getSummary(), report.getReportContent()), MAX_SUMMARY_LENGTH);
    }

    private String weaknessSummary(InterviewReport report) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "weakPoints", report.getWeakPoints());
        appendLine(builder, "weaknesses", report.getWeaknesses());
        appendLine(builder, "mainProblems", report.getMainProblems());
        appendLine(builder, "projectProblems", report.getProjectProblems());
        appendLine(builder, "reviewSuggestions", report.getReviewSuggestions());
        appendLine(builder, "recommendedQuestions", report.getRecommendedQuestions());
        appendLine(builder, "suggestions", report.getSuggestions());
        return truncate(builder.toString(), MAX_SUMMARY_LENGTH);
    }

    private String normalizePlanStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(PLAN_GENERATING, PLAN_ACTIVE, PLAN_FAILED, "ARCHIVED").contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported planStatus");
        }
        return value;
    }

    private String normalizeTaskStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(TASK_TODO, "DOING", "DONE", "SKIPPED").contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported taskStatus");
        }
        return value;
    }

    private String normalizeTaskType(String taskType) {
        String value = StringUtils.hasText(taskType) ? taskType.trim().toUpperCase(Locale.ROOT) : "KNOWLEDGE_REVIEW";
        if (!List.of("KNOWLEDGE_REVIEW", "CODING_PRACTICE", "PROJECT_REVIEW", "INTERVIEW_PRACTICE",
                "RESUME_IMPROVEMENT").contains(value)) {
            return "KNOWLEDGE_REVIEW";
        }
        return value;
    }

    private String normalizePriority(String priority) {
        String value = StringUtils.hasText(priority) ? priority.trim().toUpperCase(Locale.ROOT) : "MEDIUM";
        if (!List.of("HIGH", "MEDIUM", "LOW").contains(value)) {
            return "MEDIUM";
        }
        return value;
    }

    private int normalizeDuration(Integer durationDays) {
        if (durationDays == null) {
            return DEFAULT_DURATION_DAYS;
        }
        if (durationDays <= 7) {
            return 7;
        }
        if (durationDays <= 14) {
            return 14;
        }
        return 30;
    }

    private String defaultTitle(StudyPlan plan) {
        return firstText(plan.getTargetPosition(), "Java backend") + " " + plan.getDurationDays() + "-day study plan";
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

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON serialization failed");
        }
    }

    private <T> T readList(String json, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(json)) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON parse failed");
            }
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (Exception ignored) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON parse failed");
            }
        }
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
