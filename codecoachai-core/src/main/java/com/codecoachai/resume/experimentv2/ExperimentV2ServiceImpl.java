package com.codecoachai.resume.experimentv2;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.experimentv2.ExperimentAttributionCalculator.CalculationInput;
import com.codecoachai.resume.experimentv2.ExperimentAttributionCalculator.DataPoint;
import com.codecoachai.resume.experimentv2.ExperimentAttributionCalculator.VariantSpec;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AssignmentCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AssignmentView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AttributionView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.CohortCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.CohortView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisUpdate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.VariantCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.VariantView;
import com.codecoachai.resume.experimentv2.entity.ExperimentAssignment;
import com.codecoachai.resume.experimentv2.entity.ExperimentAttribution;
import com.codecoachai.resume.experimentv2.entity.ExperimentCohort;
import com.codecoachai.resume.experimentv2.entity.ExperimentHypothesis;
import com.codecoachai.resume.experimentv2.entity.ExperimentVariant;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAssignmentMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAttributionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentCohortMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentHypothesisMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentVariantMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ExperimentV2ServiceImpl implements ExperimentV2Service {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> OUTCOMES = Set.of("POSITIVE_RESPONSE", "INTERVIEW", "OFFER");
    private static final Set<String> HYPOTHESIS_STATUSES =
            Set.of("DRAFT", "RUNNING", "PAUSED", "COMPLETED", "ARCHIVED");
    private static final int DEFAULT_HYPOTHESIS_LIMIT = 50;
    private static final int DEFAULT_ATTRIBUTION_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;
    private static final int MAX_RELATION_LIST_LIMIT = 1000;
    private static final int MAX_ATTRIBUTION_ASSIGNMENTS = 5000;
    private static final long MAX_COHORT_WINDOW_DAYS = 366L;
    private static final String ATTRIBUTION_ALGORITHM_VERSION = "V9_EXPERIMENT_ATTRIBUTION_V1";
    private final ExperimentHypothesisMapper hypothesisMapper;
    private final JobSearchExperimentMapper legacyExperimentMapper;
    private final ExperimentVariantMapper variantMapper;
    private final ExperimentAssignmentMapper assignmentMapper;
    private final ExperimentCohortMapper cohortMapper;
    private final ExperimentAttributionMapper attributionMapper;
    private final JobApplicationMapper applicationMapper;
    private final JobApplicationEventMapper applicationEventMapper;
    private final CareerEvidenceUsageMapper evidenceUsageMapper;
    private final ExperimentAttributionCalculator attributionCalculator;
    private final ObjectMapper objectMapper;

    public ExperimentV2ServiceImpl(
            ExperimentHypothesisMapper hypothesisMapper,
            JobSearchExperimentMapper legacyExperimentMapper,
            ExperimentVariantMapper variantMapper,
            ExperimentAssignmentMapper assignmentMapper,
            ExperimentCohortMapper cohortMapper,
            ExperimentAttributionMapper attributionMapper,
            JobApplicationMapper applicationMapper,
            JobApplicationEventMapper applicationEventMapper,
            ExperimentAttributionCalculator attributionCalculator,
            ObjectMapper objectMapper) {
        this.hypothesisMapper = hypothesisMapper;
        this.legacyExperimentMapper = legacyExperimentMapper;
        this.variantMapper = variantMapper;
        this.assignmentMapper = assignmentMapper;
        this.cohortMapper = cohortMapper;
        this.attributionMapper = attributionMapper;
        this.applicationMapper = applicationMapper;
        this.applicationEventMapper = applicationEventMapper;
        this.evidenceUsageMapper = null;
        this.attributionCalculator = attributionCalculator;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HypothesisView createHypothesis(HypothesisCreate request) {
        Long userId = SecurityAssert.requireLoginUserId();
        validateLegacyExperimentAssociation(userId, request.getLegacyExperimentId());
        ExperimentHypothesis hypothesis = new ExperimentHypothesis();
        hypothesis.setUserId(userId);
        hypothesis.setLegacyExperimentId(request.getLegacyExperimentId());
        hypothesis.setName(requireText(request.getName(), "Hypothesis name is required", 128));
        hypothesis.setStatement(requireText(request.getStatement(), "Hypothesis statement is required", 1000));
        hypothesis.setPrimaryMetric(normalizeOutcome(request.getPrimaryMetric()));
        hypothesis.setStatus("DRAFT");
        hypothesis.setAttributionWindowDays(defaultInt(request.getAttributionWindowDays(), 14));
        hypothesis.setMinSamplePerVariant(defaultInt(request.getMinSamplePerVariant(), 10));
        hypothesis.setAllocationSalt(UUID.randomUUID().toString().replace("-", ""));
        try {
            hypothesisMapper.insert(hypothesis);
        } catch (DuplicateKeyException ex) {
            if (request.getLegacyExperimentId() != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "Legacy experiment is already linked to a v2 hypothesis");
            }
            throw ex;
        }
        if (request.getVariants() != null) {
            for (VariantCreate variant : request.getVariants()) {
                insertVariant(hypothesis, variant);
            }
        }
        return getHypothesis(hypothesis.getId());
    }

    @Override
    public HypothesisView getHypothesis(Long hypothesisId) {
        ExperimentHypothesis hypothesis = ownedHypothesis(hypothesisId);
        HypothesisView view = toHypothesisView(hypothesis);
        view.setVariants(listVariantEntities(hypothesis).stream().map(this::toVariantView).toList());
        view.setCohorts(listCohortEntities(hypothesis).stream().map(this::toCohortView).toList());
        return view;
    }

    @Override
    public List<HypothesisView> listHypotheses(String requestedStatus, String keyword,
                                               Long legacyExperimentId, Integer requestedLimit) {
        Long userId = SecurityAssert.requireLoginUserId();
        String status = normalizeOptionalStatus(requestedStatus);
        String normalizedKeyword = normalizeKeyword(keyword);
        int limit = normalizeLimit(requestedLimit, DEFAULT_HYPOTHESIS_LIMIT);
        return hypothesisMapper.selectList(new LambdaQueryWrapper<ExperimentHypothesis>()
                        .eq(ExperimentHypothesis::getUserId, userId)
                        .eq(ExperimentHypothesis::getDeleted, CommonConstants.NO)
                        .eq(status != null, ExperimentHypothesis::getStatus, status)
                        .eq(legacyExperimentId != null, ExperimentHypothesis::getLegacyExperimentId,
                                legacyExperimentId)
                        .and(normalizedKeyword != null, wrapper -> wrapper
                                .like(ExperimentHypothesis::getName, normalizedKeyword)
                                .or()
                                .like(ExperimentHypothesis::getStatement, normalizedKeyword))
                        .orderByDesc(ExperimentHypothesis::getUpdatedAt)
                        .orderByDesc(ExperimentHypothesis::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toHypothesisView)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HypothesisView updateHypothesis(Long hypothesisId, HypothesisUpdate request) {
        if (request == null || !hasHypothesisUpdate(request)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "At least one hypothesis field is required");
        }
        ExperimentHypothesis hypothesis = ownedHypothesis(hypothesisId);
        if ("ARCHIVED".equals(hypothesis.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Archived hypothesis cannot be changed");
        }

        boolean designChanged = request.getPrimaryMetric() != null
                || request.getAttributionWindowDays() != null
                || request.getMinSamplePerVariant() != null;
        if (designChanged) {
            if (!"DRAFT".equals(hypothesis.getStatus()) || hasAssignments(hypothesis)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "Experiment design can only be changed while DRAFT and before assignments exist");
            }
            if (request.getPrimaryMetric() != null) {
                hypothesis.setPrimaryMetric(normalizeOutcome(request.getPrimaryMetric()));
            }
            if (request.getAttributionWindowDays() != null) {
                hypothesis.setAttributionWindowDays(requireRange(request.getAttributionWindowDays(), 1, 90,
                        "attributionWindowDays must be between 1 and 90"));
            }
            if (request.getMinSamplePerVariant() != null) {
                hypothesis.setMinSamplePerVariant(requireRange(request.getMinSamplePerVariant(), 2, 100,
                        "minSamplePerVariant must be between 2 and 100"));
            }
        }
        if (request.getName() != null) {
            hypothesis.setName(requireText(request.getName(), "Hypothesis name is required", 128));
        }
        if (request.getStatement() != null) {
            hypothesis.setStatement(requireText(request.getStatement(), "Hypothesis statement is required", 1000));
        }
        if (request.getStatus() != null) {
            String targetStatus = normalizeStatus(request.getStatus());
            assertStatusTransition(hypothesis.getStatus(), targetStatus);
            hypothesis.setStatus(targetStatus);
        }
        hypothesisMapper.updateById(hypothesis);
        return getHypothesis(hypothesisId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VariantView addVariant(Long hypothesisId, VariantCreate request) {
        ExperimentHypothesis hypothesis = ownedHypothesis(hypothesisId);
        assertDesignMutable(hypothesis);
        return toVariantView(insertVariant(hypothesis, request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentView assign(Long hypothesisId, AssignmentCreate request) {
        ExperimentHypothesis hypothesis = ownedHypothesis(hypothesisId);
        Long userId = hypothesis.getUserId();
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, request.getApplicationId())
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt)
                .last("limit 1"));
        if (application == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Application not found");
        }
        ExperimentAssignment existing = assignmentMapper.selectOne(new LambdaQueryWrapper<ExperimentAssignment>()
                .eq(ExperimentAssignment::getUserId, userId)
                .eq(ExperimentAssignment::getHypothesisId, hypothesisId)
                .eq(ExperimentAssignment::getApplicationId, application.getId())
                .eq(ExperimentAssignment::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (existing != null) {
            return toAssignmentView(existing,
                    findVariant(hypothesis, existing.getVariantId()));
        }
        assertAssignmentAllowed(hypothesis);

        List<ExperimentVariant> variants = listVariantEntities(hypothesis);
        if (variants.size() < 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "At least two variants are required before assignment");
        }
        String assignmentKey = firstText(request.getAssignmentKey(), String.valueOf(application.getId()));
        ExperimentVariant selected = request.getVariantId() == null
                ? selectStableVariant(hypothesis, variants, assignmentKey)
                : ownedVariant(hypothesis, request.getVariantId());
        LocalDateTime assignedAt = request.getAssignedAt() == null
                ? LocalDateTime.now(ZoneOffset.UTC)
                : request.getAssignedAt();

        ExperimentAssignment assignment = new ExperimentAssignment();
        assignment.setUserId(userId);
        assignment.setHypothesisId(hypothesisId);
        assignment.setVariantId(selected.getId());
        assignment.setApplicationId(application.getId());
        assignment.setAssignmentKey(truncate(assignmentKey, 160));
        assignment.setAssignmentMethod(request.getVariantId() == null ? "STABLE_HASH" : "EXPLICIT");
        assignment.setAssignedAt(assignedAt);
        assignment.setJobFamily(firstText(normalizeDimension(request.getJobFamily()),
                inferJobFamily(application.getJobTitle())));
        assignment.setChannel(firstText(normalizeDimension(request.getChannel()),
                normalizeDimension(application.getSource()), "UNKNOWN"));
        assignment.setTimeBucket(assignedAt.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        try {
            assignmentMapper.insert(assignment);
        } catch (DuplicateKeyException ex) {
            if (!isMysqlDuplicateKey(ex)) {
                throw ex;
            }
            ExperimentAssignment winner = assignmentMapper.selectActiveWinnerForUpdate(
                    userId, hypothesisId, application.getId());
            if (winner == null) {
                throw ex;
            }
            return toAssignmentView(winner, findVariant(hypothesis, winner.getVariantId()));
        }
        if (!"RUNNING".equals(hypothesis.getStatus())) {
            hypothesis.setStatus("RUNNING");
            hypothesisMapper.updateById(hypothesis);
        }
        return toAssignmentView(assignment, selected);
    }

    private boolean isMysqlDuplicateKey(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23000".equals(sqlException.getSQLState())
                    && sqlException.getErrorCode() == 1062) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<AssignmentView> listAssignments(Long hypothesisId) {
        ExperimentHypothesis hypothesis = ownedHypothesis(hypothesisId);
        Map<Long, ExperimentVariant> variants = listVariantEntities(hypothesis).stream()
                .collect(Collectors.toMap(ExperimentVariant::getId, Function.identity()));
        return assignmentMapper.selectList(new LambdaQueryWrapper<ExperimentAssignment>()
                        .eq(ExperimentAssignment::getUserId, hypothesis.getUserId())
                        .eq(ExperimentAssignment::getHypothesisId, hypothesisId)
                        .eq(ExperimentAssignment::getDeleted, CommonConstants.NO)
                        .orderByDesc(ExperimentAssignment::getAssignedAt)
                        .last("limit " + MAX_RELATION_LIST_LIMIT))
                .stream()
                .map(assignment -> toAssignmentView(assignment, variants.get(assignment.getVariantId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CohortView createCohort(Long hypothesisId, CohortCreate request) {
        ExperimentHypothesis hypothesis = ownedHypothesis(hypothesisId);
        if (!request.getWindowEnd().isAfter(request.getWindowStart())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cohort windowEnd must be after windowStart");
        }
        if (java.time.Duration.between(request.getWindowStart(), request.getWindowEnd()).toDays()
                > MAX_COHORT_WINDOW_DAYS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cohort window cannot exceed 366 days");
        }
        ExperimentCohort cohort = new ExperimentCohort();
        cohort.setUserId(hypothesis.getUserId());
        cohort.setHypothesisId(hypothesisId);
        cohort.setName(requireText(request.getName(), "Cohort name is required", 128));
        cohort.setJobFamily(normalizeNullableDimension(request.getJobFamily()));
        cohort.setChannel(normalizeNullableDimension(request.getChannel()));
        cohort.setWindowStart(request.getWindowStart());
        cohort.setWindowEnd(request.getWindowEnd());
        cohort.setOutcomeType(normalizeOutcome(firstText(request.getOutcomeType(), hypothesis.getPrimaryMetric())));
        cohort.setMinSamplePerVariant(defaultInt(request.getMinSamplePerVariant(),
                hypothesis.getMinSamplePerVariant()));
        cohortMapper.insert(cohort);
        return toCohortView(cohort);
    }

    @Override
    public List<CohortView> listCohorts(Long hypothesisId) {
        return listCohortEntities(ownedHypothesis(hypothesisId)).stream().map(this::toCohortView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttributionView attribute(Long cohortId, LocalDateTime requestedAsOf) {
        Long userId = SecurityAssert.requireLoginUserId();
        ExperimentCohort cohort = ownedCohort(cohortId, userId);
        LocalDateTime asOf = requestedAsOf == null ? LocalDateTime.now(ZoneOffset.UTC) : requestedAsOf;
        ExperimentHypothesis hypothesis = ownedHypothesis(cohort.getHypothesisId());
        List<ExperimentVariant> variants = listVariantEntities(hypothesis);
        List<ExperimentAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<ExperimentAssignment>()
                        .eq(ExperimentAssignment::getUserId, userId)
                        .eq(ExperimentAssignment::getHypothesisId, hypothesis.getId())
                        .eq(ExperimentAssignment::getDeleted, CommonConstants.NO)
                        .ge(ExperimentAssignment::getAssignedAt, cohort.getWindowStart())
                        .lt(ExperimentAssignment::getAssignedAt, cohort.getWindowEnd())
                        .le(ExperimentAssignment::getAssignedAt, asOf)
                        .eq(StringUtils.hasText(cohort.getJobFamily()), ExperimentAssignment::getJobFamily,
                                cohort.getJobFamily())
                        .eq(StringUtils.hasText(cohort.getChannel()), ExperimentAssignment::getChannel,
                                cohort.getChannel())
                        .last("limit " + (MAX_ATTRIBUTION_ASSIGNMENTS + 1)));
        if (assignments.size() > MAX_ATTRIBUTION_ASSIGNMENTS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Attribution sample exceeds the maximum supported size");
        }
        Map<Long, JobApplication> applications = loadApplications(assignments, userId);
        Map<Long, List<JobApplicationEvent>> events = loadEvents(assignments, userId, asOf);
        List<CareerEvidenceUsage> evidenceUsages =
                loadEvidenceUsages(assignments, applications, hypothesis.getId(), userId, asOf);
        List<DataPoint> points = assignments.stream()
                .map(assignment -> toDataPoint(assignment, applications.get(assignment.getApplicationId()),
                        events.getOrDefault(assignment.getApplicationId(), List.of()), cohort, hypothesis, asOf))
                .toList();
        String sourceWatermark = sourceWatermark(assignments, applications, events, evidenceUsages);
        Map<String, Integer> evidenceVersionUsageCounts = evidenceVersionUsageCounts(evidenceUsages);
        String inputHash = attributionInputHash(
                hypothesis, cohort, variants, points, sourceWatermark, asOf);
        ExperimentAttribution existing = attributionMapper.selectByIdentity(
                userId, cohortId, inputHash, ATTRIBUTION_ALGORITHM_VERSION);
        if (existing != null) {
            return toAttributionView(existing);
        }
        int completedInterviewCount = completedInterviewCount(events);
        CalculationInput input = new CalculationInput(
                hypothesis.getId(),
                cohort.getId(),
                asOf,
                cohort.getMinSamplePerVariant(),
                variants.stream()
                        .map(variant -> new VariantSpec(variant.getId(), variant.getVariantCode(),
                                Integer.valueOf(CommonConstants.YES).equals(variant.getControlFlag())))
                        .toList(),
                points,
                completedInterviewCount,
                evidenceVersionUsageCounts);
        AttributionView result = attributionCalculator.calculate(input);
        result.setDataCutoffAt(asOf);
        result.setInputHash(inputHash);
        result.setAlgorithmVersion(ATTRIBUTION_ALGORITHM_VERSION);
        result.setSourceWatermark(sourceWatermark);
        result.setResultSource("RULE");
        result.setFallback(false);

        ExperimentAttribution snapshot = new ExperimentAttribution();
        snapshot.setUserId(userId);
        snapshot.setHypothesisId(hypothesis.getId());
        snapshot.setCohortId(cohort.getId());
        snapshot.setAsOf(asOf);
        snapshot.setDataCutoffAt(asOf);
        snapshot.setInputHash(inputHash);
        snapshot.setAlgorithmVersion(ATTRIBUTION_ALGORITHM_VERSION);
        snapshot.setSourceWatermark(sourceWatermark);
        snapshot.setResultSource("RULE");
        snapshot.setFallback(CommonConstants.NO);
        snapshot.setMethod(result.getMethod());
        snapshot.setComparableFlag(Boolean.TRUE.equals(result.getComparable()) ? CommonConstants.YES : CommonConstants.NO);
        snapshot.setSampleCount(result.getEligibleSampleCount());
        snapshot.setCommonStrataCount(result.getCommonStrataCount());
        snapshot.setIncomparableReasonsJson(writeJson(result.getIncomparableReasons()));
        snapshot.setLimitationsJson(writeJson(result.getLimitations()));
        snapshot.setResultJson(writeJson(result));
        try {
            attributionMapper.insert(snapshot);
        } catch (DuplicateKeyException ex) {
            ExperimentAttribution winner = attributionMapper.selectByIdentity(
                    userId, cohortId, inputHash, ATTRIBUTION_ALGORITHM_VERSION);
            if (winner != null) {
                return toAttributionView(winner);
            }
            throw ex;
        }
        result.setSnapshotId(snapshot.getId());
        return result;
    }

    @Override
    public AttributionView getLatestAttribution(Long cohortId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedCohort(cohortId, userId);
        ExperimentAttribution snapshot = attributionMapper.selectOne(
                attributionQuery(userId, cohortId)
                        .orderByDesc(ExperimentAttribution::getAsOf)
                        .orderByDesc(ExperimentAttribution::getId)
                        .last("limit 1"));
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Attribution snapshot not found");
        }
        return toAttributionView(snapshot);
    }

    @Override
    public List<AttributionView> listAttributions(Long cohortId, Integer requestedLimit) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedCohort(cohortId, userId);
        int limit = normalizeLimit(requestedLimit, DEFAULT_ATTRIBUTION_LIMIT);
        return attributionMapper.selectList(attributionQuery(userId, cohortId)
                        .orderByDesc(ExperimentAttribution::getAsOf)
                        .orderByDesc(ExperimentAttribution::getId)
                        .last("limit " + limit))
                .stream()
                .map(this::toAttributionView)
                .toList();
    }

    private ExperimentVariant insertVariant(ExperimentHypothesis hypothesis, VariantCreate request) {
        if (Boolean.TRUE.equals(request.getControl()) && listVariantEntities(hypothesis).stream()
                .anyMatch(variant -> Integer.valueOf(CommonConstants.YES).equals(variant.getControlFlag()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "A hypothesis can have only one control variant");
        }
        ExperimentVariant variant = new ExperimentVariant();
        variant.setUserId(hypothesis.getUserId());
        variant.setHypothesisId(hypothesis.getId());
        variant.setVariantCode(requireText(request.getVariantCode(), "Variant code is required", 40)
                .toUpperCase(Locale.ROOT));
        variant.setName(requireText(request.getName(), "Variant name is required", 128));
        variant.setDescription(truncate(request.getDescription(), 1000));
        variant.setTreatmentJson(writeJson(request.getTreatment() == null ? Map.of() : request.getTreatment()));
        variant.setAllocationWeight(defaultInt(request.getAllocationWeight(), 1));
        variant.setControlFlag(Boolean.TRUE.equals(request.getControl()) ? CommonConstants.YES : CommonConstants.NO);
        variantMapper.insert(variant);
        return variant;
    }

    private DataPoint toDataPoint(ExperimentAssignment assignment, JobApplication application,
                                  List<JobApplicationEvent> events, ExperimentCohort cohort,
                                  ExperimentHypothesis hypothesis, LocalDateTime asOf) {
        LocalDateTime outcomeEnd = assignment.getAssignedAt().plusDays(hypothesis.getAttributionWindowDays());
        boolean mature = !outcomeEnd.isAfter(asOf);
        boolean outcome = hasOutcome(cohort.getOutcomeType(), assignment.getAssignedAt(),
                outcomeEnd.isBefore(asOf) ? outcomeEnd : asOf, application, events);
        return new DataPoint(assignment.getVariantId(), mature, outcome,
                assignment.getJobFamily(), assignment.getChannel(), assignment.getTimeBucket());
    }

    private boolean hasOutcome(String outcomeType, LocalDateTime from, LocalDateTime to,
                               JobApplication application, List<JobApplicationEvent> events) {
        boolean eventOutcome = events.stream()
                .filter(event -> event.getEventTime() != null
                        && !event.getEventTime().isBefore(from)
                        && !event.getEventTime().isAfter(to))
                .map(JobApplicationEvent::getEventType)
                .filter(StringUtils::hasText)
                .map(type -> type.trim().toUpperCase(Locale.ROOT))
                .anyMatch(type -> eventMatches(outcomeType, type));
        if (eventOutcome || application == null || !StringUtils.hasText(application.getStatus())) {
            return eventOutcome;
        }
        if (application.getUpdatedAt() != null && application.getUpdatedAt().isAfter(to)) {
            return false;
        }
        String status = application.getStatus().trim().toUpperCase(Locale.ROOT);
        return switch (outcomeType) {
            case "OFFER" -> "OFFER".equals(status);
            case "INTERVIEW" -> Set.of("INTERVIEWING", "OFFER").contains(status);
            default -> !Set.of("SAVED", "PREPARING", "APPLIED").contains(status);
        };
    }

    private boolean eventMatches(String outcomeType, String eventType) {
        return switch (outcomeType) {
            case "OFFER" -> eventType.contains("OFFER");
            case "INTERVIEW" -> eventType.contains("INTERVIEW") || eventType.contains("SCREEN");
            default -> eventType.contains("RESPONSE") || eventType.contains("SCREEN")
                    || eventType.contains("INTERVIEW") || eventType.contains("OFFER")
                    || eventType.contains("REJECT");
        };
    }

    private Map<Long, JobApplication> loadApplications(List<ExperimentAssignment> assignments, Long userId) {
        List<Long> ids = assignments.stream().map(ExperimentAssignment::getApplicationId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .eq(JobApplication::getDeleted, CommonConstants.NO)
                        .in(JobApplication::getId, ids))
                .stream().collect(Collectors.toMap(JobApplication::getId, Function.identity()));
    }

    private Map<Long, List<JobApplicationEvent>> loadEvents(
            List<ExperimentAssignment> assignments, Long userId, LocalDateTime dataCutoffAt) {
        List<Long> ids = assignments.stream().map(ExperimentAssignment::getApplicationId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return applicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                        .eq(JobApplicationEvent::getUserId, userId)
                        .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                        .isNotNull(JobApplicationEvent::getEventTime)
                        .le(JobApplicationEvent::getEventTime, dataCutoffAt)
                        .in(JobApplicationEvent::getApplicationId, ids))
                .stream().collect(Collectors.groupingBy(JobApplicationEvent::getApplicationId));
    }

    private ExperimentVariant selectStableVariant(ExperimentHypothesis hypothesis,
                                                   List<ExperimentVariant> variants, String assignmentKey) {
        List<ExperimentVariant> ordered = variants.stream()
                .sorted(Comparator.comparing(ExperimentVariant::getId))
                .toList();
        int totalWeight = ordered.stream().mapToInt(variant -> Math.max(1, variant.getAllocationWeight())).sum();
        long slot = positiveHash(hypothesis.getAllocationSalt() + ":" + assignmentKey) % totalWeight;
        int cursor = 0;
        for (ExperimentVariant variant : ordered) {
            cursor += Math.max(1, variant.getAllocationWeight());
            if (slot < cursor) {
                return variant;
            }
        }
        return ordered.get(ordered.size() - 1);
    }

    private long positiveHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ExperimentHypothesis ownedHypothesis(Long hypothesisId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ExperimentHypothesis hypothesis = hypothesisMapper.selectOne(
                new LambdaQueryWrapper<ExperimentHypothesis>()
                        .eq(ExperimentHypothesis::getId, hypothesisId)
                        .eq(ExperimentHypothesis::getUserId, userId)
                        .eq(ExperimentHypothesis::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (hypothesis == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Hypothesis not found");
        }
        return hypothesis;
    }

    private ExperimentCohort ownedCohort(Long cohortId, Long userId) {
        ExperimentCohort cohort = cohortMapper.selectOne(new LambdaQueryWrapper<ExperimentCohort>()
                .eq(ExperimentCohort::getId, cohortId)
                .eq(ExperimentCohort::getUserId, userId)
                .eq(ExperimentCohort::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (cohort == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cohort not found");
        }
        return cohort;
    }

    private ExperimentVariant ownedVariant(ExperimentHypothesis hypothesis, Long variantId) {
        ExperimentVariant variant = findVariant(hypothesis, variantId);
        if (variant == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Variant not found");
        }
        return variant;
    }

    private ExperimentVariant findVariant(ExperimentHypothesis hypothesis, Long variantId) {
        return variantMapper.selectOne(new LambdaQueryWrapper<ExperimentVariant>()
                .eq(ExperimentVariant::getId, variantId)
                .eq(ExperimentVariant::getUserId, hypothesis.getUserId())
                .eq(ExperimentVariant::getHypothesisId, hypothesis.getId())
                .eq(ExperimentVariant::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private List<ExperimentVariant> listVariantEntities(ExperimentHypothesis hypothesis) {
        return variantMapper.selectList(new LambdaQueryWrapper<ExperimentVariant>()
                .eq(ExperimentVariant::getUserId, hypothesis.getUserId())
                .eq(ExperimentVariant::getHypothesisId, hypothesis.getId())
                .eq(ExperimentVariant::getDeleted, CommonConstants.NO)
                .orderByAsc(ExperimentVariant::getId)
                .last("limit 100"));
    }

    private List<ExperimentCohort> listCohortEntities(ExperimentHypothesis hypothesis) {
        return cohortMapper.selectList(new LambdaQueryWrapper<ExperimentCohort>()
                .eq(ExperimentCohort::getUserId, hypothesis.getUserId())
                .eq(ExperimentCohort::getHypothesisId, hypothesis.getId())
                .eq(ExperimentCohort::getDeleted, CommonConstants.NO)
                .orderByDesc(ExperimentCohort::getCreatedAt)
                .last("limit " + MAX_RELATION_LIST_LIMIT));
    }

    private void assertDesignMutable(ExperimentHypothesis hypothesis) {
        if (!"DRAFT".equalsIgnoreCase(hypothesis.getStatus()) || hasAssignments(hypothesis)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Experiment variants can only be changed while DRAFT and before assignments exist");
        }
    }

    private void validateLegacyExperimentAssociation(Long userId, Long legacyExperimentId) {
        if (legacyExperimentId == null) {
            return;
        }
        JobSearchExperiment legacyExperiment = legacyExperimentMapper.selectOne(
                new LambdaQueryWrapper<JobSearchExperiment>()
                        .eq(JobSearchExperiment::getId, legacyExperimentId)
                        .eq(JobSearchExperiment::getUserId, userId)
                        .eq(JobSearchExperiment::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (legacyExperiment == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Legacy experiment not found");
        }
        ExperimentHypothesis linked = hypothesisMapper.selectOne(
                new LambdaQueryWrapper<ExperimentHypothesis>()
                        .eq(ExperimentHypothesis::getUserId, userId)
                        .eq(ExperimentHypothesis::getLegacyExperimentId, legacyExperimentId)
                        .eq(ExperimentHypothesis::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (linked != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Legacy experiment is already linked to a v2 hypothesis");
        }
    }

    private void assertAssignmentAllowed(ExperimentHypothesis hypothesis) {
        if (!Set.of("DRAFT", "RUNNING").contains(hypothesis.getStatus().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Assignments are allowed only while the hypothesis is DRAFT or RUNNING");
        }
    }

    private boolean hasAssignments(ExperimentHypothesis hypothesis) {
        Long count = assignmentMapper.selectCount(new LambdaQueryWrapper<ExperimentAssignment>()
                .eq(ExperimentAssignment::getUserId, hypothesis.getUserId())
                .eq(ExperimentAssignment::getHypothesisId, hypothesis.getId())
                .eq(ExperimentAssignment::getDeleted, CommonConstants.NO));
        return count != null && count > 0;
    }

    private boolean hasHypothesisUpdate(HypothesisUpdate request) {
        return request.getName() != null
                || request.getStatement() != null
                || request.getPrimaryMetric() != null
                || request.getAttributionWindowDays() != null
                || request.getMinSamplePerVariant() != null
                || request.getStatus() != null;
    }

    private void assertStatusTransition(String currentStatus, String targetStatus) {
        String current = normalizeStatus(currentStatus);
        Set<String> allowed = switch (current) {
            case "DRAFT" -> Set.of("DRAFT", "RUNNING", "ARCHIVED");
            case "RUNNING" -> Set.of("RUNNING", "PAUSED", "COMPLETED", "ARCHIVED");
            case "PAUSED" -> Set.of("PAUSED", "RUNNING", "COMPLETED", "ARCHIVED");
            case "COMPLETED" -> Set.of("COMPLETED", "ARCHIVED");
            case "ARCHIVED" -> Set.of("ARCHIVED");
            default -> Set.of();
        };
        if (!allowed.contains(targetStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Invalid hypothesis status transition: " + current + " -> " + targetStatus);
        }
    }

    private String normalizeOptionalStatus(String value) {
        return StringUtils.hasText(value) ? normalizeStatus(value) : null;
    }

    private String normalizeStatus(String value) {
        String status = requireText(value, "Hypothesis status is required", 24).toUpperCase(Locale.ROOT);
        if (!HYPOTHESIS_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "status must be DRAFT, RUNNING, PAUSED, COMPLETED, or ARCHIVED");
        }
        return status;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "keyword must not exceed 100 characters");
        }
        return normalized;
    }

    private int normalizeLimit(Integer requestedLimit, int defaultLimit) {
        int limit = requestedLimit == null ? defaultLimit : requestedLimit;
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "limit must be between 1 and 100");
        }
        return limit;
    }

    private int requireRange(Integer value, int min, int max, String message) {
        if (value == null || value < min || value > max) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return value;
    }

    private LambdaQueryWrapper<ExperimentAttribution> attributionQuery(Long userId, Long cohortId) {
        return new LambdaQueryWrapper<ExperimentAttribution>()
                .eq(ExperimentAttribution::getUserId, userId)
                .eq(ExperimentAttribution::getCohortId, cohortId)
                .eq(ExperimentAttribution::getDeleted, CommonConstants.NO);
    }

    private int completedInterviewCount(Map<Long, List<JobApplicationEvent>> events) {
        return (int) events.values().stream()
                .flatMap(List::stream)
                .map(JobApplicationEvent::getEventType)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> value.contains("INTERVIEW") && value.contains("COMPLETED"))
                .count();
    }

    private String sourceWatermark(List<ExperimentAssignment> assignments,
                                   Map<Long, JobApplication> applications,
                                   Map<Long, List<JobApplicationEvent>> events,
                                   List<CareerEvidenceUsage> evidenceUsages) {
        Map<String, Object> watermark = new LinkedHashMap<>();
        watermark.put("assignments", assignments.stream()
                .sorted(Comparator.comparing(ExperimentAssignment::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .map(assignment -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", assignment.getId());
                    row.put("variantId", assignment.getVariantId());
                    row.put("applicationId", assignment.getApplicationId());
                    row.put("assignedAt", assignment.getAssignedAt());
                    row.put("jobFamily", assignment.getJobFamily());
                    row.put("channel", assignment.getChannel());
                    row.put("timeBucket", assignment.getTimeBucket());
                    row.put("updatedAt", assignment.getUpdatedAt());
                    return row;
                }).toList());
        watermark.put("applications", applications.values().stream()
                .sorted(Comparator.comparing(JobApplication::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .map(application -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", application.getId());
                    row.put("status", application.getStatus());
                    row.put("resumeVersionId", application.getResumeVersionId());
                    row.put("updatedAt", application.getUpdatedAt());
                    return row;
                }).toList());
        watermark.put("events", events.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(JobApplicationEvent::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .map(event -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", event.getId());
                    row.put("applicationId", event.getApplicationId());
                    row.put("eventType", event.getEventType());
                    row.put("eventTime", event.getEventTime());
                    row.put("updatedAt", event.getUpdatedAt());
                    return row;
                }).toList());
        watermark.put("evidenceUsages", evidenceUsages.stream()
                .sorted(Comparator.comparing(CareerEvidenceUsage::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .map(usage -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", usage.getId());
                    row.put("applicationId", usage.getApplicationId());
                    row.put("assignmentId", usage.getAssignmentId());
                    row.put("assetType", usage.getAssetType());
                    row.put("assetId", usage.getAssetId());
                    row.put("assetVersion", usage.getAssetVersion());
                    row.put("sourceHash", usage.getSourceHash());
                    row.put("contentHash", usage.getContentHash());
                    row.put("usedAt", usage.getUsedAt());
                    row.put("status", usage.getStatus());
                    row.put("stale", usage.getStale());
                    row.put("updatedAt", usage.getUpdatedAt());
                    return row;
                }).toList());
        return sha256(writeJson(watermark));
    }

    private List<CareerEvidenceUsage> loadEvidenceUsages(
            List<ExperimentAssignment> assignments,
            Map<Long, JobApplication> applications,
            Long hypothesisId,
            Long userId,
            LocalDateTime asOf) {
        if (evidenceUsageMapper == null || assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        List<Long> assignmentIds = assignments.stream()
                .map(ExperimentAssignment::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Long> applicationIds = applications.keySet().stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, CareerEvidenceUsage> byId = new LinkedHashMap<>();
        if (!assignmentIds.isEmpty()) {
            evidenceUsageMapper.selectList(new LambdaQueryWrapper<CareerEvidenceUsage>()
                            .eq(CareerEvidenceUsage::getUserId, userId)
                            .eq(CareerEvidenceUsage::getDeleted, CommonConstants.NO)
                            .eq(CareerEvidenceUsage::getStale, CommonConstants.NO)
                            .eq(CareerEvidenceUsage::getStatus, "CAPTURED")
                            .in(CareerEvidenceUsage::getAssignmentId, assignmentIds)
                            .le(CareerEvidenceUsage::getUsedAt, asOf)
                            .last("LIMIT 10000"))
                    .forEach(row -> byId.put(row.getId(), row));
        }
        if (hypothesisId != null && !applicationIds.isEmpty()) {
            evidenceUsageMapper.selectList(new LambdaQueryWrapper<CareerEvidenceUsage>()
                            .eq(CareerEvidenceUsage::getUserId, userId)
                            .eq(CareerEvidenceUsage::getDeleted, CommonConstants.NO)
                            .eq(CareerEvidenceUsage::getStale, CommonConstants.NO)
                            .eq(CareerEvidenceUsage::getStatus, "CAPTURED")
                            .eq(CareerEvidenceUsage::getHypothesisId, hypothesisId)
                            .in(CareerEvidenceUsage::getApplicationId, applicationIds)
                            .le(CareerEvidenceUsage::getUsedAt, asOf)
                            .last("LIMIT 10000"))
                    .forEach(row -> byId.put(row.getId(), row));
        }
        return byId.values().stream().toList();
    }

    private Map<String, Integer> evidenceVersionUsageCounts(List<CareerEvidenceUsage> usages) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (usages == null) {
            return counts;
        }
        for (CareerEvidenceUsage usage : usages) {
            String key = String.join(":",
                    String.valueOf(usage.getAssetType()),
                    String.valueOf(usage.getAssetId()),
                    String.valueOf(usage.getAssetVersion()));
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private String attributionInputHash(ExperimentHypothesis hypothesis,
                                        ExperimentCohort cohort,
                                        List<ExperimentVariant> variants,
                                        List<DataPoint> points,
                                        String sourceWatermark,
                                        LocalDateTime dataCutoffAt) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("algorithmVersion", ATTRIBUTION_ALGORITHM_VERSION);
        input.put("hypothesisId", hypothesis.getId());
        input.put("primaryMetric", hypothesis.getPrimaryMetric());
        input.put("attributionWindowDays", hypothesis.getAttributionWindowDays());
        input.put("cohortId", cohort.getId());
        input.put("dataCutoffAt", dataCutoffAt);
        input.put("outcomeType", cohort.getOutcomeType());
        input.put("minSamplePerVariant", cohort.getMinSamplePerVariant());
        input.put("variants", variants.stream()
                .sorted(Comparator.comparing(ExperimentVariant::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .map(variant -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", variant.getId());
                    row.put("code", variant.getVariantCode());
                    row.put("control", Integer.valueOf(CommonConstants.YES)
                            .equals(variant.getControlFlag()));
                    return row;
                }).toList());
        input.put("points", points.stream()
                .sorted(Comparator
                        .comparing(DataPoint::variantId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(DataPoint::jobFamily,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(DataPoint::channel,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(DataPoint::timeBucket,
                                Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(DataPoint::mature)
                        .thenComparing(DataPoint::outcome))
                .toList());
        input.put("sourceWatermark", sourceWatermark);
        return sha256(writeJson(input));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private AttributionView toAttributionView(ExperimentAttribution snapshot) {
        try {
            AttributionView view = objectMapper.readValue(snapshot.getResultJson(), AttributionView.class);
            view.setSnapshotId(snapshot.getId());
            view.setDataCutoffAt(snapshot.getDataCutoffAt() == null
                    ? snapshot.getAsOf() : snapshot.getDataCutoffAt());
            if (StringUtils.hasText(snapshot.getInputHash())) {
                view.setInputHash(snapshot.getInputHash());
            }
            if (StringUtils.hasText(snapshot.getAlgorithmVersion())) {
                view.setAlgorithmVersion(snapshot.getAlgorithmVersion());
            }
            if (StringUtils.hasText(snapshot.getSourceWatermark())) {
                view.setSourceWatermark(snapshot.getSourceWatermark());
            }
            if (StringUtils.hasText(snapshot.getResultSource())) {
                view.setResultSource(snapshot.getResultSource());
            } else if (!StringUtils.hasText(view.getResultSource())) {
                view.setResultSource("RULE");
            }
            if (snapshot.getFallback() != null) {
                view.setFallback(CommonConstants.YES.equals(snapshot.getFallback()));
            } else if (view.getFallback() == null) {
                view.setFallback(false);
            }
            return view;
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Attribution snapshot is invalid");
        }
    }

    private HypothesisView toHypothesisView(ExperimentHypothesis entity) {
        HypothesisView view = new HypothesisView();
        view.setId(entity.getId());
        view.setLegacyExperimentId(entity.getLegacyExperimentId());
        view.setName(entity.getName());
        view.setStatement(entity.getStatement());
        view.setPrimaryMetric(entity.getPrimaryMetric());
        view.setStatus(entity.getStatus());
        view.setAttributionWindowDays(entity.getAttributionWindowDays());
        view.setMinSamplePerVariant(entity.getMinSamplePerVariant());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }

    private VariantView toVariantView(ExperimentVariant entity) {
        VariantView view = new VariantView();
        view.setId(entity.getId());
        view.setVariantCode(entity.getVariantCode());
        view.setName(entity.getName());
        view.setDescription(entity.getDescription());
        view.setTreatment(readMap(entity.getTreatmentJson()));
        view.setAllocationWeight(entity.getAllocationWeight());
        view.setControl(Integer.valueOf(CommonConstants.YES).equals(entity.getControlFlag()));
        return view;
    }

    private AssignmentView toAssignmentView(ExperimentAssignment entity, ExperimentVariant variant) {
        AssignmentView view = new AssignmentView();
        view.setId(entity.getId());
        view.setHypothesisId(entity.getHypothesisId());
        view.setVariantId(entity.getVariantId());
        view.setVariantCode(variant == null ? null : variant.getVariantCode());
        view.setApplicationId(entity.getApplicationId());
        view.setAssignmentKey(entity.getAssignmentKey());
        view.setAssignmentMethod(entity.getAssignmentMethod());
        view.setAssignedAt(entity.getAssignedAt());
        view.setJobFamily(entity.getJobFamily());
        view.setChannel(entity.getChannel());
        view.setTimeBucket(entity.getTimeBucket());
        return view;
    }

    private CohortView toCohortView(ExperimentCohort entity) {
        CohortView view = new CohortView();
        view.setId(entity.getId());
        view.setHypothesisId(entity.getHypothesisId());
        view.setName(entity.getName());
        view.setJobFamily(entity.getJobFamily());
        view.setChannel(entity.getChannel());
        view.setWindowStart(entity.getWindowStart());
        view.setWindowEnd(entity.getWindowEnd());
        view.setOutcomeType(entity.getOutcomeType());
        view.setMinSamplePerVariant(entity.getMinSamplePerVariant());
        return view;
    }

    private String normalizeOutcome(String value) {
        String outcome = firstText(value, "INTERVIEW").toUpperCase(Locale.ROOT);
        if (!OUTCOMES.contains(outcome)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "primaryMetric/outcomeType must be POSITIVE_RESPONSE, INTERVIEW, or OFFER");
        }
        return outcome;
    }

    private String inferJobFamily(String jobTitle) {
        if (!StringUtils.hasText(jobTitle)) {
            return "UNKNOWN";
        }
        String normalized = jobTitle.toUpperCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\b(SENIOR|JUNIOR|LEAD|STAFF|PRINCIPAL|SR|JR)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(normalized) ? truncate(normalized, 100) : "UNKNOWN";
    }

    private String normalizeDimension(String value) {
        return StringUtils.hasText(value)
                ? truncate(value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT), 100)
                : null;
    }

    private String normalizeNullableDimension(String value) {
        return normalizeDimension(value);
    }

    private String requireText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return truncate(value.trim(), maxLength);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Structured experiment data is invalid");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }
}
