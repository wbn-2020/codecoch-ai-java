package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateFromGapDTO;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateFromMatchReportDTO;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
import com.codecoachai.interview.domain.dto.StudyTaskStatusUpdateDTO;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.StudyPlan;
import com.codecoachai.interview.domain.entity.StudyPlanSkillRelation;
import com.codecoachai.interview.domain.entity.StudyTask;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.enums.StudyPlanSourceType;
import com.codecoachai.interview.domain.vo.StudyPlanDailyViewVO;
import com.codecoachai.interview.domain.vo.InnerStudyPlanSkillRelationVO;
import com.codecoachai.interview.domain.vo.InnerStudyPlanVO;
import com.codecoachai.interview.domain.vo.InnerStudyTaskVO;
import com.codecoachai.interview.domain.vo.StudyPlanAgentEvidenceVO;
import com.codecoachai.interview.domain.vo.StudyPlanDetailVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.domain.vo.StudyPlanSkillRelationVO;
import com.codecoachai.interview.domain.vo.StudyPlanSourceTypeVO;
import com.codecoachai.interview.domain.vo.StudyTaskVO;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.GenerateLearningPlanDTO;
import com.codecoachai.interview.feign.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.interview.feign.vo.GenerateLearningPlanVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.interview.feign.vo.InnerSkillGapItemVO;
import com.codecoachai.interview.feign.vo.InnerSkillProfileVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.StudyPlanMapper;
import com.codecoachai.interview.mapper.StudyPlanSkillRelationMapper;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import com.codecoachai.interview.mq.StudyPlanMqDispatcher;
import com.codecoachai.interview.service.StudyPlanService;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudyPlanServiceImpl implements StudyPlanService {

    private static final String SOURCE_TYPE_REPORT = "REPORT";
    private static final String PLAN_GENERATING = "GENERATING";
    private static final String PLAN_ACTIVE = "ACTIVE";
    private static final String PLAN_FAILED = "FAILED";
    private static final String STUDY_PLAN_ASYNC_BIZ_TYPE = StudyPlanMqDispatcher.BIZ_TYPE_GENERATE;
    private static final String TASK_TODO = "TODO";
    private static final String TASK_COMPLETED = "COMPLETED";
    private static final String TASK_SKIPPED = "SKIPPED";
    private static final int DEFAULT_DURATION_DAYS = 14;
    private static final int MAX_SUMMARY_LENGTH = 4000;

    private final StudyPlanMapper studyPlanMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final StudyPlanSkillRelationMapper relationMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final ResumeFeignClient resumeFeignClient;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Optional<StudyPlanMqDispatcher> studyPlanMqDispatcher;

    @Override
    public StudyPlanGenerateVO generate(StudyPlanGenerateDTO dto) {
        Long userId = requireCurrentUserId();
        InterviewReport report = getOwnedGeneratedReport(dto.getReportId(), userId);
        StudyPlan existing = latestPlan(userId, report.getId());
        if (existing != null && (PLAN_ACTIVE.equals(existing.getPlanStatus())
                || PLAN_GENERATING.equals(existing.getPlanStatus()))) {
            return toGenerateVO(existing);
        }
        StudyPlan plan = transactionTemplate.execute(status -> prepareReportPlan(userId, report, dto, null));
        return submitForGeneration(plan, null);
    }

    @Override
    public StudyPlanGenerateVO generateFromGap(StudyPlanGenerateFromGapDTO dto) {
        if (dto == null || dto.getProfileId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像链接不完整，请从能力画像页面重新进入");
        }
        InnerSkillProfileVO profile = FeignResultUtils.unwrap(resumeFeignClient.getSkillProfile(dto.getProfileId()));
        return generateFromSkillProfile(dto, profile, StudyPlanSourceType.JD_GAP, dto.getProfileId());
    }

    @Override
    public StudyPlanGenerateVO generateFromMatchReport(StudyPlanGenerateFromMatchReportDTO dto) {
        if (dto == null || dto.getMatchReportId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匹配报告链接不完整，请从匹配报告页面重新进入");
        }
        InnerSkillProfileVO profile = FeignResultUtils.unwrap(
                resumeFeignClient.getSuccessSkillProfileByMatchReport(dto.getMatchReportId()));
        if (profile == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "当前匹配报告还没有可用的能力画像，请先生成能力画像");
        }
        if (!dto.getMatchReportId().equals(profile.getMatchReportId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像与匹配报告不一致");
        }
        StudyPlanGenerateFromGapDTO gapDTO = new StudyPlanGenerateFromGapDTO();
        gapDTO.setProfileId(profile.getProfileId());
        gapDTO.setDays(dto.getDays());
        gapDTO.setDailyMinutes(dto.getDailyMinutes());
        gapDTO.setStartDate(dto.getStartDate());
        gapDTO.setPlanTitle(dto.getPlanTitle());
        return generateFromSkillProfile(gapDTO, profile, StudyPlanSourceType.RESUME_JOB_MATCH, dto.getMatchReportId());
    }

    @Override
    public List<StudyPlanSourceTypeVO> sourceTypes() {
        return java.util.Arrays.stream(StudyPlanSourceType.values())
                .map(type -> {
                    StudyPlanSourceTypeVO vo = new StudyPlanSourceTypeVO();
                    vo.setCode(type.getCode());
                    vo.setDescription(type.getDescription());
                    return vo;
                })
                .toList();
    }

    @Override
    public PageResult<StudyPlanListVO> list(StudyPlanQueryDTO dto) {
        Long userId = requireCurrentUserId();
        long pageNo = dto == null || dto.getPageNo() == null || dto.getPageNo() < 1 ? 1L : dto.getPageNo();
        long pageSize = dto == null || dto.getPageSize() == null || dto.getPageSize() < 1 ? 10L : Math.min(dto.getPageSize(), 100L);
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
        vo.setTasks(taskEntities(plan.getId(), plan.getUserId()).stream().map(this::toTaskVO).toList());
        return vo;
    }

    @Override
    public InnerStudyPlanVO getInnerPlan(Long id) {
        Long userId = requireCurrentUserId();
        StudyPlan plan = getOwnedPlan(id, userId);
        InnerStudyPlanVO vo = toInnerPlanVO(plan);
        vo.setTasks(taskEntities(plan.getId(), userId).stream().map(this::toInnerTaskVO).toList());
        vo.setSkillRelations(relationMapper.selectList(new LambdaQueryWrapper<StudyPlanSkillRelation>()
                        .eq(StudyPlanSkillRelation::getStudyPlanId, plan.getId())
                        .eq(StudyPlanSkillRelation::getUserId, userId)
                        .eq(StudyPlanSkillRelation::getDeleted, CommonConstants.NO)
                        .orderByAsc(StudyPlanSkillRelation::getPriority)
                        .orderByAsc(StudyPlanSkillRelation::getId))
                .stream()
                .map(this::toInnerRelationVO)
                .toList());
        return vo;
    }

    @Override
    public StudyPlanAgentEvidenceVO getPlanEvidence(Long userId, Long planId) {
        StudyPlan plan = studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getId, planId)
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getPlanStatus, PLAN_ACTIVE)
                .eq(StudyPlan::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (plan == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Study plan does not exist or is not active");
        }
        StudyPlanAgentEvidenceVO vo = new StudyPlanAgentEvidenceVO();
        vo.setId(plan.getId());
        vo.setUserId(plan.getUserId());
        vo.setSourceType(plan.getSourceType());
        vo.setSourceId(plan.getSourceId());
        vo.setTargetJobId(plan.getTargetJobId());
        vo.setSkillProfileId(plan.getSkillProfileId());
        vo.setMatchReportId(plan.getMatchReportId());
        vo.setReportId(plan.getReportId());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setStartDate(plan.getStartDate());
        vo.setGeneratedAt(plan.getUpdatedAt());
        vo.setCreatedAt(plan.getCreatedAt());
        return vo;
    }

    @Override
    public List<StudyTaskVO> tasks(Long planId) {
        StudyPlan plan = getOwnedPlan(planId, requireCurrentUserId());
        return taskEntities(plan.getId(), plan.getUserId()).stream().map(this::toTaskVO).toList();
    }

    @Override
    public List<StudyPlanSkillRelationVO> skillRelations(Long planId) {
        Long userId = requireCurrentUserId();
        StudyPlan plan = getOwnedPlan(planId, userId);
        List<StudyPlanSkillRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<StudyPlanSkillRelation>()
                        .eq(StudyPlanSkillRelation::getStudyPlanId, plan.getId())
                        .eq(StudyPlanSkillRelation::getUserId, userId)
                        .eq(StudyPlanSkillRelation::getDeleted, CommonConstants.NO)
                        .orderByAsc(StudyPlanSkillRelation::getPriority)
                        .orderByAsc(StudyPlanSkillRelation::getId));
        Map<Long, InnerSkillGapItemVO> gapMap = loadGapMap(plan.getSkillProfileId());
        return relations.stream().map(relation -> toRelationVO(relation, gapMap)).toList();
    }

    @Override
    public StudyPlanDailyViewVO dailyView(Long planId, String date) {
        Long userId = requireCurrentUserId();
        StudyPlan plan = getOwnedPlan(planId, userId);
        LocalDate targetDate = parseDailyViewDate(date);
        int dayIndex = inferDayIndex(plan, targetDate);
        List<StudyTask> tasks = taskEntities(plan.getId(), userId).stream()
                .filter(task -> matchesDailyViewDate(task, targetDate, dayIndex))
                .toList();

        int total = tasks.size();
        int completed = (int) tasks.stream().filter(task -> isTaskDone(task.getTaskStatus())).count();
        int skipped = (int) tasks.stream().filter(task -> TASK_SKIPPED.equals(task.getTaskStatus())).count();
        int pending = Math.max(0, total - completed - skipped);
        log.info("Daily study view loaded userId={} planId={} date={} dayIndex={} taskCount={}",
                userId, planId, targetDate, dayIndex, total);

        StudyPlanDailyViewVO vo = new StudyPlanDailyViewVO();
        vo.setPlanId(plan.getId());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setDate(targetDate);
        vo.setDayIndex(dayIndex);
        vo.setTotalTaskCount(total);
        vo.setCompletedTaskCount(completed);
        vo.setSkippedTaskCount(skipped);
        vo.setPendingTaskCount(pending);
        vo.setCompletionRate(total == 0 ? 0 : completed * 100 / total);
        vo.setTasks(tasks.stream().map(this::toTaskVO).toList());
        return vo;
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习任务不存在或已不可用");
        }
        StudyPlan plan = getOwnedPlan(task.getPlanId(), userId);
        if (PLAN_FAILED.equals(plan.getPlanStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划生成失败，暂时不能更新任务");
        }
        task.setTaskStatus(normalizeTaskStatus(dto.getTaskStatus()));
        studyTaskMapper.updateById(task);
        return toTaskVO(task);
    }

    @Override
    public StudyTaskVO completeTask(Long taskId) {
        StudyTaskStatusUpdateDTO dto = new StudyTaskStatusUpdateDTO();
        dto.setTaskStatus(TASK_COMPLETED);
        return updateTaskStatus(taskId, dto);
    }

    @Override
    public StudyTaskVO skipTask(Long taskId) {
        StudyTaskStatusUpdateDTO dto = new StudyTaskStatusUpdateDTO();
        dto.setTaskStatus(TASK_SKIPPED);
        return updateTaskStatus(taskId, dto);
    }

    @Override
    public StudyPlanGenerateVO regenerate(Long id) {
        Long userId = requireCurrentUserId();
        StudyPlan plan = getOwnedPlan(id, userId);
        if (!PLAN_FAILED.equals(plan.getPlanStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只有生成失败的学习计划可以重新生成");
        }
        InterviewReport report = getOwnedGeneratedReport(plan.getReportId(), userId);
        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(report.getId());
        dto.setResumeId(plan.getResumeId());
        dto.setOptimizeRecordId(plan.getOptimizeRecordId());
        dto.setTargetPosition(plan.getTargetPosition());
        dto.setIndustryDirection(plan.getIndustryDirection());
        dto.setExpectedDurationDays(plan.getDurationDays());
        StudyPlan prepared = transactionTemplate.execute(status -> prepareReportPlan(userId, report, dto, plan));
        return submitForGeneration(prepared, null);
    }

    @Override
    public StudyPlanGenerateVO executeGeneration(Long id) {
        StudyPlan plan = getAvailablePlan(id);
        return executeGeneration(plan.getId(), plan.getUserId());
    }

    @Override
    public StudyPlanGenerateVO executeGeneration(Long id, Long userId) {
        StudyPlan plan = getOwnedPlan(id, requireExecutionUserId(userId));
        if (PLAN_ACTIVE.equals(plan.getPlanStatus()) || PLAN_FAILED.equals(plan.getPlanStatus())) {
            return toGenerateVO(plan);
        }
        if (!PLAN_GENERATING.equals(plan.getPlanStatus())) {
            return toGenerateVO(plan);
        }
        if (SOURCE_TYPE_REPORT.equals(plan.getSourceType()) || plan.getReportId() != null) {
            return executeReportPlan(plan);
        }
        return executeTargetedPlan(plan);
    }

    private StudyPlanGenerateVO submitForGeneration(StudyPlan plan, Integer skillGapCount) {
        MqDispatchReceipt receipt = dispatchGenerate(plan);
        StudyPlanGenerateVO vo = receipt == null
                ? executeGeneration(plan.getId(), plan.getUserId())
                : withAsyncReceipt(toGenerateVO(plan), receipt);
        if (skillGapCount != null) {
            vo.setSkillGapCount(skillGapCount);
        }
        return vo;
    }

    private MqDispatchReceipt dispatchGenerate(StudyPlan plan) {
        return plan == null ? null : studyPlanMqDispatcher
                .map(dispatcher -> dispatcher.dispatchGenerateWithReceipt(plan.getId(), plan.getUserId()))
                .orElse(null);
    }

    private StudyPlanGenerateVO withAsyncReceipt(StudyPlanGenerateVO vo, MqDispatchReceipt receipt) {
        if (vo == null || receipt == null) {
            return vo;
        }
        vo.setAsyncMessageId(receipt.getMessageId());
        vo.setAsyncTraceId(receipt.getTraceId());
        vo.setAsyncBizType(firstText(receipt.getBizType(), STUDY_PLAN_ASYNC_BIZ_TYPE));
        vo.setAsyncBizId(firstText(receipt.getBizId(), String.valueOf(vo.getPlanId())));
        vo.setAsyncSendStatus(receipt.getSendStatus());
        return vo;
    }

    private StudyPlanGenerateVO executeReportPlan(StudyPlan plan) {
        try {
            GenerateLearningPlanDTO aiRequest = readRequestJson(plan, GenerateLearningPlanDTO.class);
            GenerateLearningPlanVO aiPlan = FeignResultUtils.unwrap(aiFeignClient.generateLearningPlan(aiRequest));
            validateAiPlan(aiPlan);
            StudyPlan success = transactionTemplate.execute(status -> markReportPlanSuccess(plan, aiPlan));
            return toGenerateVO(success);
        } catch (RuntimeException ex) {
            log.warn("Study plan generation failed, planId={}, reportId={}", plan.getId(), plan.getReportId(), ex);
            StudyPlan failed = transactionTemplate.execute(status -> markStudyPlanFailed(plan.getId(), plan.getUserId()));
            return toGenerateVO(failed);
        }
    }

    private StudyPlanGenerateVO executeTargetedPlan(StudyPlan plan) {
        try {
            GenerateTargetedStudyPlanDTO aiRequest = readRequestJson(plan, GenerateTargetedStudyPlanDTO.class);
            List<InnerSkillGapItemVO> selectedGaps = readList(aiRequest.getSkillGapsJson(),
                    new TypeReference<List<InnerSkillGapItemVO>>() {});
            GenerateLearningPlanVO aiPlan = FeignResultUtils.unwrap(
                    aiFeignClient.generateTargetedStudyPlan(aiRequest));
            validateAiPlan(aiPlan);
            StudyPlan success = transactionTemplate.execute(status ->
                    markTargetedPlanSuccess(plan, aiPlan, selectedGaps, aiRequest));
            StudyPlanGenerateVO vo = toGenerateVO(success);
            vo.setSkillGapCount(selectedGaps.size());
            return vo;
        } catch (RuntimeException ex) {
            log.warn("Gap-driven study plan generation failed, planId={}, profileId={}",
                    plan.getId(), plan.getSkillProfileId(), ex);
            StudyPlan failed = transactionTemplate.execute(status -> markStudyPlanFailed(plan.getId(), plan.getUserId()));
            return toGenerateVO(failed);
        }
    }

    private StudyPlan markReportPlanSuccess(StudyPlan executionPlan, GenerateLearningPlanVO aiPlan) {
        StudyPlan plan = getOwnedPlan(executionPlan.getId(), executionPlan.getUserId());
        cleanupTasks(plan.getId(), plan.getUserId());
        cleanupRelations(plan.getId(), plan.getUserId());
        plan.setPlanTitle(firstText(aiPlan.getPlanTitle(), defaultTitle(plan)));
        plan.setPlanSummary(firstText(aiPlan.getPlanSummary(), plan.getPlanSummary()));
        plan.setDurationDays(normalizeDuration(aiPlan.getDurationDays()));
        plan.setAiCallLogId(aiPlan.getAiCallLogId());
        plan.setResultJson(toJson(aiPlan));
        plan.setFailureReason(null);
        insertTasks(plan, aiPlan);
        plan.setPlanStatus(PLAN_ACTIVE);
        updateOwnedPlan(plan);
        return plan;
    }

    private StudyPlan markTargetedPlanSuccess(StudyPlan executionPlan, GenerateLearningPlanVO aiPlan,
                                              List<InnerSkillGapItemVO> selectedGaps,
                                              GenerateTargetedStudyPlanDTO aiRequest) {
        StudyPlan plan = getOwnedPlan(executionPlan.getId(), executionPlan.getUserId());
        cleanupTasks(plan.getId(), plan.getUserId());
        cleanupRelations(plan.getId(), plan.getUserId());
        insertTargetedTasks(plan, aiPlan, selectedGaps == null ? List.of() : selectedGaps);
        plan.setPlanTitle(firstText(aiPlan.getPlanTitle(), plan.getPlanTitle()));
        plan.setPlanSummary(firstText(aiPlan.getPlanSummary(), plan.getPlanSummary()));
        plan.setDurationDays(aiPlan.getDurationDays() == null
                ? normalizeDuration(aiRequest.getAvailableDays())
                : normalizeDuration(aiPlan.getDurationDays()));
        plan.setAiCallLogId(aiPlan.getAiCallLogId());
        plan.setResultJson(toJson(aiPlan));
        plan.setFailureReason(null);
        plan.setPlanStatus(PLAN_ACTIVE);
        updateOwnedPlan(plan);
        return plan;
    }

    private StudyPlan markStudyPlanFailed(Long planId, Long userId) {
        cleanupTasks(planId, userId);
        cleanupRelations(planId, userId);
        StudyPlan plan = getOwnedPlan(planId, userId);
        plan.setPlanStatus(PLAN_FAILED);
        plan.setFailureReason(studyPlanFailureMessage());
        updateOwnedPlan(plan);
        return plan;
    }

    private <T> T readRequestJson(StudyPlan plan, Class<T> type) {
        if (plan == null || !StringUtils.hasText(plan.getRequestJson())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习计划生成依据缺失，请重新提交");
        }
        try {
            return objectMapper.readValue(plan.getRequestJson(), type);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习计划生成依据暂时无法读取，请重新提交");
        }
    }

    private StudyPlan prepareReportPlan(Long userId, InterviewReport report,
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
            cleanupTasks(plan.getId(), userId);
        }

        GenerateLearningPlanDTO aiRequest = buildAiRequest(plan, session, report, dto, resume, optimizeRecord);
        plan.setRequestJson(toJson(aiRequest));
        studyPlanMapper.updateById(plan);
        return plan;
    }

    private StudyPlanGenerateVO generateFromSkillProfile(StudyPlanGenerateFromGapDTO dto,
                                                         InnerSkillProfileVO profile,
                                                         StudyPlanSourceType sourceType,
                                                         Long sourceBizId) {
        Long userId = requireCurrentUserId();
        validateSkillProfile(profile, userId);
        int days = normalizeGapDays(dto.getDays());
        int dailyMinutes = normalizeDailyMinutes(dto.getDailyMinutes());
        LocalDate startDate = dto.getStartDate() == null ? LocalDate.now() : dto.getStartDate();
        List<InnerSkillGapItemVO> selectedGaps = resolveSelectedGaps(profile, dto.getGapItemIds());

        StudyPlan plan = transactionTemplate.execute(status ->
                prepareTargetedPlan(dto, profile, sourceType, sourceBizId, selectedGaps,
                        days, dailyMinutes, startDate));
        return submitForGeneration(plan, selectedGaps.size());
    }

    private StudyPlan prepareTargetedPlan(StudyPlanGenerateFromGapDTO dto,
                                          InnerSkillProfileVO profile,
                                          StudyPlanSourceType sourceType,
                                          Long sourceBizId,
                                          List<InnerSkillGapItemVO> selectedGaps,
                                          int days,
                                          int dailyMinutes,
                                          LocalDate startDate) {
        StudyPlan plan = new StudyPlan();
        plan.setUserId(profile.getUserId());
        plan.setSourceType(sourceType.getCode());
        plan.setSourceId(sourceBizId);
        plan.setTargetJobId(profile.getTargetJobId());
        plan.setSkillProfileId(profile.getProfileId());
        plan.setMatchReportId(profile.getMatchReportId());
        plan.setTargetPosition(profile.getTargetJobTitle());
        plan.setPlanTitle(firstText(dto.getPlanTitle(), defaultGapPlanTitle(profile, days)));
        plan.setPlanSummary(profile.getSummary());
        plan.setPlanStatus(PLAN_GENERATING);
        plan.setDurationDays(days);
        plan.setDailyMinutes(dailyMinutes);
        plan.setStartDate(startDate);
        plan.setRequestJson(toJson(dto));
        studyPlanMapper.insert(plan);

        GenerateTargetedStudyPlanDTO aiRequest = buildTargetedAiRequest(
                plan, profile, selectedGaps, days, dailyMinutes, startDate);
        plan.setRequestJson(toJson(aiRequest));
        studyPlanMapper.updateById(plan);
        return plan;
    }

    private GenerateTargetedStudyPlanDTO buildTargetedAiRequest(StudyPlan plan,
                                                                InnerSkillProfileVO profile,
                                                                List<InnerSkillGapItemVO> selectedGaps,
                                                                int days,
                                                                int dailyMinutes,
                                                                LocalDate startDate) {
        GenerateTargetedStudyPlanDTO request = new GenerateTargetedStudyPlanDTO();
        request.setLearningPlanId(plan.getId());
        request.setUserId(plan.getUserId());
        request.setTargetJobId(plan.getTargetJobId());
        request.setSkillProfileId(plan.getSkillProfileId());
        request.setMatchReportId(plan.getMatchReportId());
        request.setTargetJobJson(toJson(targetJobSnapshot(profile)));
        request.setSkillProfileJson(toJson(skillProfileSnapshot(profile)));
        request.setSkillGapsJson(toJson(selectedGaps.stream().map(this::skillGapSnapshot).toList()));
        request.setAvailableDays(days);
        request.setDailyMinutes(dailyMinutes);
        request.setStartDate(startDate);
        request.setExistingStudyPlansJson(existingStudyPlansJson(plan.getUserId(), plan.getSkillProfileId()));
        request.setPlanTitle(plan.getPlanTitle());
        return request;
    }

    private int insertTargetedTasks(StudyPlan plan, GenerateLearningPlanVO aiPlan,
                                    List<InnerSkillGapItemVO> selectedGaps) {
        Map<Long, InnerSkillGapItemVO> gapById = selectedGaps.stream()
                .filter(gap -> gap.getId() != null)
                .collect(Collectors.toMap(InnerSkillGapItemVO::getId, Function.identity(), (a, b) -> a));
        int order = 1;
        int relationPriority = 1;
        for (GenerateLearningPlanVO.StageVO stage : aiPlan.getStages()) {
            if (stage == null || stage.getItems() == null) {
                continue;
            }
            int stageNo = stage.getStageNo() == null ? 1 : Math.max(1, stage.getStageNo());
            for (GenerateLearningPlanVO.ItemVO item : stage.getItems()) {
                if (item == null || !StringUtils.hasText(item.getTaskTitle())) {
                    continue;
                }
                InnerSkillGapItemVO gap = resolveTaskGap(item, selectedGaps, gapById);
                StudyTask task = new StudyTask();
                task.setPlanId(plan.getId());
                task.setUserId(plan.getUserId());
                task.setTargetJobId(plan.getTargetJobId());
                task.setSkillProfileId(plan.getSkillProfileId());
                task.setSkillGapItemId(gap == null ? null : gap.getId());
                task.setSourceType(plan.getSourceType());
                task.setSourceBizId(plan.getSourceId());
                task.setStageNo(stageNo);
                task.setPlannedDate(targetedPlannedDate(plan, item, stageNo));
                task.setStageTitle(stage.getStageTitle());
                task.setTaskOrder(order++);
                task.setKnowledgePoint(firstText(item.getKnowledgePoint(), item.getSkillName(),
                        gap == null ? null : gap.getSkillName()));
                task.setTaskTitle(item.getTaskTitle());
                task.setTaskDescription(item.getTaskDescription());
                task.setTaskType(normalizeTaskType(item.getTaskType()));
                task.setPriority(normalizePriority(item.getPriority()));
                task.setEstimatedMinutes(firstInteger(item.getEstimatedMinutes(), plan.getDailyMinutes()));
                task.setEstimatedHours(firstInteger(item.getEstimatedHours(),
                        Math.max(1, (task.getEstimatedMinutes() + 59) / 60)));
                task.setAcceptanceCriteria(item.getAcceptance());
                task.setTaskStatus(TASK_TODO);
                task.setRelatedQuestionIdsJson(toJson(item.getRelatedQuestionIds() == null
                        ? List.of()
                        : item.getRelatedQuestionIds()));
                task.setRelatedTagsJson(toJson(item.getRelatedTags() == null ? List.of() : item.getRelatedTags()));
                task.setResourcesJson(toJson(item.getResources() == null ? List.of() : item.getResources()));
                studyTaskMapper.insert(task);
                if (gap != null) {
                    insertRelation(plan, task, gap, relationPriority++);
                }
            }
        }
        return order - 1;
    }

    private void insertRelation(StudyPlan plan, StudyTask task, InnerSkillGapItemVO gap, int priority) {
        StudyPlanSkillRelation relation = new StudyPlanSkillRelation();
        relation.setUserId(plan.getUserId());
        relation.setStudyPlanId(plan.getId());
        relation.setStudyTaskId(task.getId());
        relation.setTargetJobId(plan.getTargetJobId());
        relation.setSkillProfileId(plan.getSkillProfileId());
        relation.setSkillGapItemId(gap.getId());
        relation.setSourceType(plan.getSourceType());
        relation.setSourceBizId(plan.getSourceId());
        relation.setPriority(priority);
        relationMapper.insert(relation);
    }

    private void validateSkillProfile(InnerSkillProfileVO profile, Long userId) {
        if (profile == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像不存在或已不可用");
        }
        if (!userId.equals(profile.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像不存在或已不可用");
        }
        if (!"SUCCESS".equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像暂时不可用");
        }
        validateSkillProfileSource(profile, userId);
        if (profile.getGapItems() == null || profile.getGapItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像还没有可用短板项");
        }
    }

    private void validateSkillProfileSource(InnerSkillProfileVO profile, Long userId) {
        String sourceType = profile == null ? null : profile.getSourceType();
        if ("RESUME_JOB_MATCH".equalsIgnoreCase(sourceType)) {
            if (profile.getMatchReportId() == null
                    || (profile.getSourceBizId() != null
                    && !profile.getMatchReportId().equals(profile.getSourceBizId()))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像来源与匹配报告不一致");
            }
            return;
        }
        if ("INTERVIEW_REPORT".equalsIgnoreCase(sourceType)) {
            InterviewReport sourceReport = reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                    .eq(InterviewReport::getSessionId, profile.getSourceBizId())
                    .eq(InterviewReport::getUserId, userId)
                    .eq(InterviewReport::getDeleted, CommonConstants.NO)
                    .orderByDesc(InterviewReport::getId)
                    .last("limit 1"));
            if (!InterviewReportTrustPolicy.isTrustedForFormalAction(sourceReport)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像来源报告可信度不足，不能生成学习计划");
            }
            return;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像来源不可信，不能生成学习计划");
    }

    private List<InnerSkillGapItemVO> resolveSelectedGaps(InnerSkillProfileVO profile, List<Long> gapItemIds) {
        List<InnerSkillGapItemVO> available = profile.getGapItems().stream()
                .filter(Objects::nonNull)
                .filter(gap -> profile.getProfileId().equals(gap.getProfileId()))
                .filter(gap -> profile.getUserId().equals(gap.getUserId()))
                .sorted(Comparator.comparing(gap -> firstInteger(gap.getPriority(), Integer.MAX_VALUE)))
                .toList();
        if (gapItemIds == null || gapItemIds.isEmpty()) {
            List<InnerSkillGapItemVO> topGaps = available.stream().limit(5).toList();
            if (topGaps.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "能力画像还没有可用于生成计划的短板项");
            }
            return topGaps;
        }
        Map<Long, InnerSkillGapItemVO> gapMap = available.stream()
                .filter(gap -> gap.getId() != null)
                .collect(Collectors.toMap(InnerSkillGapItemVO::getId, Function.identity(), (a, b) -> a));
        List<Long> distinctIds = gapItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择有效的短板项");
        }
        List<InnerSkillGapItemVO> selected = new ArrayList<>();
        for (Long gapItemId : distinctIds) {
            InnerSkillGapItemVO gap = gapMap.get(gapItemId);
            if (gap == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "短板项不存在或不属于当前能力画像");
            }
            selected.add(gap);
        }
        return selected;
    }

    private InnerSkillGapItemVO resolveTaskGap(GenerateLearningPlanVO.ItemVO item,
                                               List<InnerSkillGapItemVO> selectedGaps,
                                               Map<Long, InnerSkillGapItemVO> gapById) {
        Long sourceGapId = parseLong(item.getSourceGapId());
        if (sourceGapId != null && gapById.containsKey(sourceGapId)) {
            return gapById.get(sourceGapId);
        }
        String skillName = firstText(item.getSkillName(), item.getKnowledgePoint(), item.getTaskTitle());
        if (StringUtils.hasText(skillName)) {
            String normalized = skillName.toLowerCase(Locale.ROOT);
            for (InnerSkillGapItemVO gap : selectedGaps) {
                if (StringUtils.hasText(gap.getSkillName())
                        && normalized.contains(gap.getSkillName().toLowerCase(Locale.ROOT))) {
                    return gap;
                }
            }
        }
        return selectedGaps.isEmpty() ? null : selectedGaps.get(0);
    }

    private LocalDate targetedPlannedDate(StudyPlan plan, GenerateLearningPlanVO.ItemVO item, int stageNo) {
        LocalDate start = plan.getStartDate() == null ? LocalDate.now() : plan.getStartDate();
        int dayOffset = firstInteger(item.getDayOffset(), stageNo);
        return start.plusDays(Math.max(0, dayOffset - 1));
    }

    private void cleanupRelations(Long planId, Long userId) {
        if (planId == null || userId == null) {
            return;
        }
        relationMapper.delete(new LambdaQueryWrapper<StudyPlanSkillRelation>()
                .eq(StudyPlanSkillRelation::getStudyPlanId, planId)
                .eq(StudyPlanSkillRelation::getUserId, userId));
    }

    private Map<String, Object> targetJobSnapshot(InnerSkillProfileVO profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("targetJobId", profile.getTargetJobId());
        snapshot.put("jobTitle", profile.getTargetJobTitle());
        snapshot.put("companyName", profile.getTargetCompanyName());
        snapshot.put("jobLevel", profile.getTargetJobLevel());
        snapshot.put("jdSource", profile.getTargetJdSource());
        return snapshot;
    }

    private Map<String, Object> skillProfileSnapshot(InnerSkillProfileVO profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("profileId", profile.getProfileId());
        snapshot.put("targetJobId", profile.getTargetJobId());
        snapshot.put("matchReportId", profile.getMatchReportId());
        snapshot.put("profileName", profile.getProfileName());
        snapshot.put("overallLevel", profile.getOverallLevel());
        snapshot.put("overallScore", profile.getOverallScore());
        snapshot.put("summary", profile.getSummary());
        snapshot.put("sourceType", profile.getSourceType());
        snapshot.put("sourceBizId", profile.getSourceBizId());
        return snapshot;
    }

    private Map<String, Object> skillGapSnapshot(InnerSkillGapItemVO gap) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", gap.getId());
        snapshot.put("skillName", gap.getSkillName());
        snapshot.put("category", gap.getCategory());
        snapshot.put("targetLevel", gap.getTargetLevel());
        snapshot.put("currentLevel", gap.getCurrentLevel());
        snapshot.put("gapLevel", gap.getGapLevel());
        snapshot.put("confidence", gap.getConfidence());
        snapshot.put("severity", gap.getSeverity());
        snapshot.put("evidenceSources", readJsonFallback(gap.getEvidenceSourcesJson()));
        snapshot.put("gapDescription", gap.getGapDescription());
        snapshot.put("recommendedActions", readJsonFallback(gap.getRecommendedActionsJson()));
        snapshot.put("priority", gap.getPriority());
        return snapshot;
    }

    private String existingStudyPlansJson(Long userId, Long skillProfileId) {
        List<StudyPlan> plans = studyPlanMapper.selectList(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getSkillProfileId, skillProfileId)
                .eq(StudyPlan::getDeleted, CommonConstants.NO)
                .orderByDesc(StudyPlan::getUpdatedAt)
                .last("limit 3"));
        return toJson(plans.stream().map(plan -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("planId", plan.getId());
            snapshot.put("planTitle", plan.getPlanTitle());
            snapshot.put("planStatus", plan.getPlanStatus());
            snapshot.put("durationDays", plan.getDurationDays());
            snapshot.put("createdAt", plan.getCreatedAt());
            return snapshot;
        }).toList());
    }

    private Object readJsonFallback(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return json;
        }
    }

    private String defaultGapPlanTitle(InnerSkillProfileVO profile, int days) {
        return firstText(profile.getTargetJobTitle(), "Java backend") + " gap repair " + days + "-day plan";
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
                task.setPlannedDate(plannedDate(plan, stageNo));
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

    private void cleanupTasks(Long planId, Long userId) {
        if (planId == null || userId == null) {
            return;
        }
        studyTaskMapper.delete(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getPlanId, planId)
                .eq(StudyTask::getUserId, userId));
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试报告不存在或已不可用");
        }
        if (!ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试报告尚未生成完成");
        }
        if (!InterviewReportTrustPolicy.isTrustedForFormalAction(report)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "面试报告可信度不足，不能生成正式学习计划，请补充回答后重新生成报告");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试会话不存在或已不可用");
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划不存在或已不可用");
        }
        return plan;
    }

    private StudyPlan getAvailablePlan(Long id) {
        StudyPlan plan = studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getId, id)
                .eq(StudyPlan::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (plan == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划不存在或已不可用");
        }
        return plan;
    }

    private Long requireExecutionUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户ID不能为空");
        }
        return userId;
    }

    private void updateOwnedPlan(StudyPlan plan) {
        int updated = studyPlanMapper.update(plan, new LambdaUpdateWrapper<StudyPlan>()
                .eq(StudyPlan::getId, plan.getId())
                .eq(StudyPlan::getUserId, plan.getUserId())
                .eq(StudyPlan::getDeleted, CommonConstants.NO));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划不存在或已不可用");
        }
    }

    private InnerResumeDetailVO loadResume(Long userId, Long resumeId) {
        if (resumeId == null) {
            return null;
        }
        InnerResumeDetailVO resume = FeignResultUtils.unwrap(resumeFeignClient.getResume(resumeId));
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历不存在或已不可用");
        }
        return resume;
    }

    private InnerResumeOptimizeRecordVO loadOptimizeRecord(Long userId, Long optimizeRecordId) {
        if (optimizeRecordId == null) {
            return null;
        }
        InnerResumeOptimizeRecordVO record = FeignResultUtils.unwrap(resumeFeignClient.getOptimizeRecord(optimizeRecordId));
        if (record == null || !userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历优化记录不存在或已不可用");
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

    private List<StudyTask> taskEntities(Long planId, Long userId) {
        return studyTaskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getPlanId, planId)
                .eq(StudyTask::getUserId, userId)
                .eq(StudyTask::getDeleted, CommonConstants.NO)
                .orderByAsc(StudyTask::getStageNo)
                .orderByAsc(StudyTask::getTaskOrder)
                .orderByAsc(StudyTask::getId));
    }

    private LocalDate parseDailyViewDate(String date) {
        if (!StringUtils.hasText(date)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日期格式不正确，请使用 yyyy-MM-dd");
        }
    }

    private int inferDayIndex(StudyPlan plan, LocalDate targetDate) {
        if (plan.getCreatedAt() == null) {
            return 1;
        }
        LocalDate startDate = plan.getCreatedAt().toLocalDate();
        long days = ChronoUnit.DAYS.between(startDate, targetDate);
        if (days < 0) {
            return 1;
        }
        if (days >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) days + 1;
    }

    private int normalizeTaskDayIndex(Integer stageNo) {
        return stageNo == null || stageNo < 1 ? 1 : stageNo;
    }

    private boolean matchesDailyViewDate(StudyTask task, LocalDate targetDate, int fallbackDayIndex) {
        if (task.getPlannedDate() != null) {
            return task.getPlannedDate().equals(targetDate);
        }
        return fallbackDayIndex == normalizeTaskDayIndex(task.getStageNo());
    }

    private LocalDate plannedDate(StudyPlan plan, Integer stageNo) {
        LocalDate startDate = plan.getCreatedAt() == null ? LocalDate.now() : plan.getCreatedAt().toLocalDate();
        return startDate.plusDays(Math.max(0, normalizeTaskDayIndex(stageNo) - 1));
    }

    private StudyPlanListVO toListVO(StudyPlan plan) {
        StudyPlanListVO vo = new StudyPlanListVO();
        vo.setId(plan.getId());
        vo.setReportId(plan.getReportId());
        vo.setTargetJobId(plan.getTargetJobId());
        vo.setSkillProfileId(plan.getSkillProfileId());
        vo.setMatchReportId(plan.getMatchReportId());
        vo.setSessionId(plan.getSessionId());
        vo.setSourceType(plan.getSourceType());
        vo.setTargetPosition(plan.getTargetPosition());
        vo.setIndustryDirection(plan.getIndustryDirection());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanSummary(plan.getPlanSummary());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setDurationDays(plan.getDurationDays());
        vo.setDailyMinutes(plan.getDailyMinutes());
        vo.setStartDate(plan.getStartDate());
        fillProgress(vo, plan);
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private StudyPlanDetailVO toDetailVO(StudyPlan plan) {
        StudyPlanDetailVO vo = new StudyPlanDetailVO();
        vo.setId(plan.getId());
        vo.setReportId(plan.getReportId());
        vo.setTargetJobId(plan.getTargetJobId());
        vo.setSkillProfileId(plan.getSkillProfileId());
        vo.setMatchReportId(plan.getMatchReportId());
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
        vo.setDailyMinutes(plan.getDailyMinutes());
        vo.setStartDate(plan.getStartDate());
        vo.setAiCallLogId(plan.getAiCallLogId());
        vo.setFailureReason(plan.getFailureReason());
        fillProgress(vo, plan);
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private InnerStudyPlanVO toInnerPlanVO(StudyPlan plan) {
        InnerStudyPlanVO vo = new InnerStudyPlanVO();
        vo.setPlanId(plan.getId());
        vo.setUserId(plan.getUserId());
        vo.setSourceType(plan.getSourceType());
        vo.setSourceId(plan.getSourceId());
        vo.setTargetJobId(plan.getTargetJobId());
        vo.setSkillProfileId(plan.getSkillProfileId());
        vo.setMatchReportId(plan.getMatchReportId());
        vo.setTargetPosition(plan.getTargetPosition());
        vo.setIndustryDirection(plan.getIndustryDirection());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanSummary(plan.getPlanSummary());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setDurationDays(plan.getDurationDays());
        vo.setDailyMinutes(plan.getDailyMinutes());
        vo.setStartDate(plan.getStartDate());
        vo.setAiCallLogId(plan.getAiCallLogId());
        vo.setResultJson(plan.getResultJson());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private InnerStudyTaskVO toInnerTaskVO(StudyTask task) {
        InnerStudyTaskVO vo = new InnerStudyTaskVO();
        vo.setId(task.getId());
        vo.setPlanId(task.getPlanId());
        vo.setUserId(task.getUserId());
        vo.setTargetJobId(task.getTargetJobId());
        vo.setSkillProfileId(task.getSkillProfileId());
        vo.setSkillGapItemId(task.getSkillGapItemId());
        vo.setSourceType(task.getSourceType());
        vo.setSourceBizId(task.getSourceBizId());
        vo.setStageNo(task.getStageNo());
        vo.setPlannedDate(task.getPlannedDate());
        vo.setStageTitle(task.getStageTitle());
        vo.setTaskOrder(task.getTaskOrder());
        vo.setKnowledgePoint(task.getKnowledgePoint());
        vo.setTaskTitle(task.getTaskTitle());
        vo.setTaskDescription(task.getTaskDescription());
        vo.setTaskType(task.getTaskType());
        vo.setPriority(task.getPriority());
        vo.setEstimatedMinutes(task.getEstimatedMinutes());
        vo.setAcceptanceCriteria(task.getAcceptanceCriteria());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setRelatedQuestionIdsJson(task.getRelatedQuestionIdsJson());
        vo.setRelatedTagsJson(task.getRelatedTagsJson());
        vo.setResourcesJson(task.getResourcesJson());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private InnerStudyPlanSkillRelationVO toInnerRelationVO(StudyPlanSkillRelation relation) {
        InnerStudyPlanSkillRelationVO vo = new InnerStudyPlanSkillRelationVO();
        vo.setId(relation.getId());
        vo.setUserId(relation.getUserId());
        vo.setStudyPlanId(relation.getStudyPlanId());
        vo.setStudyTaskId(relation.getStudyTaskId());
        vo.setTargetJobId(relation.getTargetJobId());
        vo.setSkillProfileId(relation.getSkillProfileId());
        vo.setSkillGapItemId(relation.getSkillGapItemId());
        vo.setSourceType(relation.getSourceType());
        vo.setSourceBizId(relation.getSourceBizId());
        vo.setPriority(relation.getPriority());
        vo.setCreatedAt(relation.getCreatedAt());
        vo.setUpdatedAt(relation.getUpdatedAt());
        return vo;
    }

    private void fillProgress(StudyPlanListVO vo, StudyPlan plan) {
        List<StudyTask> tasks = taskEntities(plan.getId(), plan.getUserId());
        int total = tasks.size();
        int done = (int) tasks.stream().filter(task -> isTaskDone(task.getTaskStatus())).count();
        vo.setTotalTaskCount(total);
        vo.setDoneTaskCount(done);
        vo.setProgressPercent(total == 0 ? 0 : done * 100 / total);
    }

    private void fillProgress(StudyPlanDetailVO vo, StudyPlan plan) {
        List<StudyTask> tasks = taskEntities(plan.getId(), plan.getUserId());
        int total = tasks.size();
        int done = (int) tasks.stream().filter(task -> isTaskDone(task.getTaskStatus())).count();
        vo.setTotalTaskCount(total);
        vo.setDoneTaskCount(done);
        vo.setProgressPercent(total == 0 ? 0 : done * 100 / total);
    }

    private StudyTaskVO toTaskVO(StudyTask task) {
        StudyTaskVO vo = new StudyTaskVO();
        vo.setId(task.getId());
        vo.setPlanId(task.getPlanId());
        vo.setTargetJobId(task.getTargetJobId());
        vo.setSkillProfileId(task.getSkillProfileId());
        vo.setSkillGapItemId(task.getSkillGapItemId());
        vo.setSourceType(task.getSourceType());
        vo.setSourceBizId(task.getSourceBizId());
        vo.setStageNo(task.getStageNo());
        vo.setPlannedDate(task.getPlannedDate());
        vo.setStageTitle(task.getStageTitle());
        vo.setTaskOrder(task.getTaskOrder());
        vo.setKnowledgePoint(task.getKnowledgePoint());
        vo.setTaskTitle(task.getTaskTitle());
        vo.setTaskDescription(task.getTaskDescription());
        vo.setTaskType(task.getTaskType());
        vo.setPriority(task.getPriority());
        vo.setEstimatedHours(task.getEstimatedHours());
        vo.setEstimatedMinutes(task.getEstimatedMinutes());
        vo.setAcceptanceCriteria(task.getAcceptanceCriteria());
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
        vo.setTaskCount(taskEntities(plan.getId(), plan.getUserId()).size());
        vo.setSkillGapCount(countRelations(plan.getId(), plan.getUserId()));
        vo.setAiCallLogId(plan.getAiCallLogId());
        vo.setFailureReason(plan.getFailureReason());
        return vo;
    }

    private StudyPlanSkillRelationVO toRelationVO(StudyPlanSkillRelation relation,
                                                  Map<Long, InnerSkillGapItemVO> gapMap) {
        InnerSkillGapItemVO gap = gapMap.get(relation.getSkillGapItemId());
        StudyPlanSkillRelationVO vo = new StudyPlanSkillRelationVO();
        vo.setId(relation.getId());
        vo.setStudyPlanId(relation.getStudyPlanId());
        vo.setStudyTaskId(relation.getStudyTaskId());
        vo.setTargetJobId(relation.getTargetJobId());
        vo.setSkillProfileId(relation.getSkillProfileId());
        vo.setSkillGapItemId(relation.getSkillGapItemId());
        vo.setSkillName(gap == null ? null : gap.getSkillName());
        vo.setCategory(gap == null ? null : gap.getCategory());
        vo.setSeverity(gap == null ? null : gap.getSeverity());
        vo.setSourceType(relation.getSourceType());
        vo.setSourceBizId(relation.getSourceBizId());
        vo.setPriority(relation.getPriority());
        vo.setCreatedAt(relation.getCreatedAt());
        vo.setUpdatedAt(relation.getUpdatedAt());
        return vo;
    }

    private void validateAiPlan(GenerateLearningPlanVO aiPlan) {
        if (aiPlan == null || aiPlan.getStages() == null || aiPlan.getStages().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习计划生成结果缺少阶段任务");
        }
        boolean hasItem = aiPlan.getStages().stream()
                .anyMatch(stage -> stage != null && stage.getItems() != null && !stage.getItems().isEmpty());
        if (!hasItem) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习计划生成结果缺少可执行任务");
        }
    }

    private String studyPlanFailureMessage() {
        return "学习计划生成失败：AI 返回内容暂时不可用，请稍后重试，或返回报告页重新生成。";
    }

    private Map<Long, InnerSkillGapItemVO> loadGapMap(Long skillProfileId) {
        if (skillProfileId == null) {
            return Map.of();
        }
        InnerSkillProfileVO profile = FeignResultUtils.unwrap(resumeFeignClient.getSkillProfile(skillProfileId));
        if (profile == null || profile.getGapItems() == null) {
            return Map.of();
        }
        return profile.getGapItems().stream()
                .filter(Objects::nonNull)
                .filter(gap -> gap.getId() != null)
                .collect(Collectors.toMap(InnerSkillGapItemVO::getId, Function.identity(), (a, b) -> a));
    }

    private int countRelations(Long planId, Long userId) {
        if (planId == null) {
            return 0;
        }
        return Math.toIntExact(relationMapper.selectCount(new LambdaQueryWrapper<StudyPlanSkillRelation>()
                .eq(StudyPlanSkillRelation::getStudyPlanId, planId)
                .eq(StudyPlanSkillRelation::getUserId, userId)
                .eq(StudyPlanSkillRelation::getDeleted, CommonConstants.NO)));
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习计划状态不支持");
        }
        return value;
    }

    private String normalizeTaskStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(value)) {
            return TASK_TODO;
        }
        if (!List.of(TASK_TODO, "DOING", "DONE", TASK_COMPLETED, TASK_SKIPPED).contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习任务状态不支持");
        }
        return value;
    }

    private boolean isTaskDone(String taskStatus) {
        return "DONE".equals(taskStatus) || TASK_COMPLETED.equals(taskStatus);
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

    private int normalizeGapDays(Integer days) {
        if (days == null) {
            return DEFAULT_DURATION_DAYS;
        }
        if (days < 1 || days > 60) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学习天数需要在 1 到 60 天之间");
        }
        return days;
    }

    private int normalizeDailyMinutes(Integer dailyMinutes) {
        if (dailyMinutes == null) {
            return 60;
        }
        if (dailyMinutes < 15 || dailyMinutes > 480) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "每日学习时长需要在 15 到 480 分钟之间");
        }
        return dailyMinutes;
    }

    private String defaultTitle(StudyPlan plan) {
        return firstText(plan.getTargetPosition(), "Java 后端") + " " + plan.getDurationDays() + " 天学习计划";
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

    private Integer firstInteger(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习计划生成结果处理失败，请稍后重试");
        }
    }

    private <T> T readList(String json, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(json)) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习计划生成结果处理失败，请稍后重试");
            }
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (Exception ignored) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习计划生成结果处理失败，请稍后重试");
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
