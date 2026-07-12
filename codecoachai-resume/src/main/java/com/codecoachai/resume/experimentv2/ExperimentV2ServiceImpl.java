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
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
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
    private static final long ATTRIBUTION_REUSE_SECONDS = 30L;
    private final ExperimentHypothesisMapper hypothesisMapper;
    private final JobSearchExperimentMapper legacyExperimentMapper;
    private final ExperimentVariantMapper variantMapper;
    private final ExperimentAssignmentMapper assignmentMapper;
    private final ExperimentCohortMapper cohortMapper;
    private final ExperimentAttributionMapper attributionMapper;
    private final JobApplicationMapper applicationMapper;
    private final JobApplicationEventMapper applicationEventMapper;
    private final ExperimentAttributionCalculator attributionCalculator;
    private final ObjectMapper objectMapper;

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
        AttributionView reusable = findReusableAttribution(cohortId, userId, requestedAsOf, asOf);
        if (reusable != null) {
            return reusable;
        }
        ExperimentHypothesis hypothesis = ownedHypothesis(cohort.getHypothesisId());
        List<ExperimentVariant> variants = listVariantEntities(hypothesis);
        List<ExperimentAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<ExperimentAssignment>()
                        .eq(ExperimentAssignment::getUserId, userId)
                        .eq(ExperimentAssignment::getHypothesisId, hypothesis.getId())
                        .eq(ExperimentAssignment::getDeleted, CommonConstants.NO)
                        .ge(ExperimentAssignment::getAssignedAt, cohort.getWindowStart())
                        .lt(ExperimentAssignment::getAssignedAt, cohort.getWindowEnd())
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
        Map<Long, List<JobApplicationEvent>> events = loadEvents(assignments, userId);
        List<DataPoint> points = assignments.stream()
                .map(assignment -> toDataPoint(assignment, applications.get(assignment.getApplicationId()),
                        events.getOrDefault(assignment.getApplicationId(), List.of()), cohort, hypothesis, asOf))
                .toList();
        CalculationInput input = new CalculationInput(
                hypothesis.getId(),
                cohort.getId(),
                asOf,
                cohort.getMinSamplePerVariant(),
                variants.stream()
                        .map(variant -> new VariantSpec(variant.getId(), variant.getVariantCode(),
                                Integer.valueOf(CommonConstants.YES).equals(variant.getControlFlag())))
                        .toList(),
                points);
        AttributionView result = attributionCalculator.calculate(input);

        ExperimentAttribution snapshot = new ExperimentAttribution();
        snapshot.setUserId(userId);
        snapshot.setHypothesisId(hypothesis.getId());
        snapshot.setCohortId(cohort.getId());
        snapshot.setAsOf(asOf);
        snapshot.setMethod(result.getMethod());
        snapshot.setComparableFlag(Boolean.TRUE.equals(result.getComparable()) ? CommonConstants.YES : CommonConstants.NO);
        snapshot.setSampleCount(result.getEligibleSampleCount());
        snapshot.setCommonStrataCount(result.getCommonStrataCount());
        snapshot.setIncomparableReasonsJson(writeJson(result.getIncomparableReasons()));
        snapshot.setLimitationsJson(writeJson(result.getLimitations()));
        snapshot.setResultJson(writeJson(result));
        attributionMapper.insert(snapshot);
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

    private Map<Long, List<JobApplicationEvent>> loadEvents(List<ExperimentAssignment> assignments, Long userId) {
        List<Long> ids = assignments.stream().map(ExperimentAssignment::getApplicationId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return applicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                        .eq(JobApplicationEvent::getUserId, userId)
                        .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
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

    private AttributionView findReusableAttribution(Long cohortId, Long userId,
                                                    LocalDateTime requestedAsOf, LocalDateTime resolvedAsOf) {
        LambdaQueryWrapper<ExperimentAttribution> query = attributionQuery(userId, cohortId);
        if (requestedAsOf != null) {
            query.eq(ExperimentAttribution::getAsOf, requestedAsOf);
        } else {
            query.ge(ExperimentAttribution::getCreatedAt,
                    resolvedAsOf.minusSeconds(ATTRIBUTION_REUSE_SECONDS));
        }
        ExperimentAttribution snapshot = attributionMapper.selectOne(query
                .orderByDesc(ExperimentAttribution::getCreatedAt)
                .orderByDesc(ExperimentAttribution::getId)
                .last("limit 1"));
        return snapshot == null ? null : toAttributionView(snapshot);
    }

    private AttributionView toAttributionView(ExperimentAttribution snapshot) {
        try {
            AttributionView view = objectMapper.readValue(snapshot.getResultJson(), AttributionView.class);
            view.setSnapshotId(snapshot.getId());
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
