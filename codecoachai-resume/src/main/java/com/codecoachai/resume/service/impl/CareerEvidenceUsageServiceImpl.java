package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.V9FeatureGate;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageCreateDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultCommandDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultWriteDTO;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResult;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResultSnapshot;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidenceVersion;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageResultVO;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageVO;
import com.codecoachai.resume.domain.vo.EvidenceAssetEnvelopeVO;
import com.codecoachai.resume.domain.vo.EvidenceAssetOverviewEnvelopeVO;
import com.codecoachai.resume.domain.vo.EvidenceAssetOverviewVO;
import com.codecoachai.resume.domain.vo.InnerCareerEvidenceUsageFactsVO;
import com.codecoachai.resume.experimentv2.entity.ExperimentAttribution;
import com.codecoachai.resume.experimentv2.entity.ExperimentAssignment;
import com.codecoachai.resume.experimentv2.entity.ExperimentHypothesis;
import com.codecoachai.resume.experimentv2.entity.ExperimentVariant;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.mapper.CareerEvidenceUsageMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultSnapshotMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceVersionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAttributionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAssignmentMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentHypothesisMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentVariantMapper;
import com.codecoachai.resume.service.CareerEvidenceUsageService;
import com.codecoachai.resume.service.support.CareerEvidenceSourceResolver;
import com.codecoachai.resume.service.support.CareerEvidenceSourceResolver.AssetResolution;
import com.codecoachai.resume.service.support.CareerEvidenceSourceResolver.EventResolution;
import com.codecoachai.resume.service.support.EvidenceProfileFeedbackService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerEvidenceUsageServiceImpl implements CareerEvidenceUsageService {

    private static final Set<String> USAGE_SCENES = Set.of(
            "APPLICATION_PACKAGE", "APPLICATION_SUBMISSION", "INTERVIEW_PREPARATION",
            "INTERVIEW", "FOLLOW_UP", "OTHER");
    private static final Set<String> OUTCOME_CODES = Set.of(
            "NO_RESPONSE", "REPLIED", "INTERVIEW_ADVANCED", "INTERVIEW_NOT_ADVANCED",
            "OFFER_RECEIVED", "OFFER_ACCEPTED", "OFFER_DECLINED", "UNKNOWN");
    private static final Set<String> USAGE_STATUSES = Set.of("CAPTURED", "SUPERSEDED");
    private static final Set<String> RESULT_STATUSES =
            Set.of("RECORDED", "CONFIRMED", "CORRECTED", "VOID");

    private final CareerEvidenceUsageMapper usageMapper;
    private final CareerEvidenceUsageResultMapper resultMapper;
    private final CareerEvidenceUsageResultSnapshotMapper resultSnapshotMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final ProjectEvidenceVersionMapper projectEvidenceVersionMapper;
    private final ExperimentAttributionMapper attributionMapper;
    private final ExperimentAssignmentMapper assignmentMapper;
    private final ExperimentHypothesisMapper hypothesisMapper;
    private final ExperimentVariantMapper variantMapper;
    private final CareerEvidenceSourceResolver sourceResolver;
    private final V9FeatureGate featureGate;
    private final ObjectMapper objectMapper;
    private final EvidenceProfileFeedbackService profileFeedbackService;

    /** Lazily derived key-ordered mapper for JVM-stable persistence hashes (see {@link #writeCanonicalJson}). */
    private volatile ObjectMapper canonicalHashMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerEvidenceUsageVO createUsage(
            Long applicationId, CareerEvidenceUsageCreateDTO request) {
        featureGate.requireEvidenceUsage();
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplication application = sourceResolver.ownedApplication(userId, applicationId);
        validateUsageRequest(request);
        AssetResolution asset = sourceResolver.resolveAsset(userId, application, request);
        Long packageSnapshotId = request.getPackageSnapshotId();
        if (packageSnapshotId != null) {
            sourceResolver.ownedPackageSnapshotForApplication(userId, applicationId, packageSnapshotId);
        }
        AttributionBinding attribution = resolveAttributionBinding(
                userId, applicationId, request);
        String scene = normalize(request.getUsageScene());
        LocalDateTime usedAt = request.getUsedAt() == null
                ? LocalDateTime.now(ZoneOffset.UTC) : request.getUsedAt();
        Map<String, Object> payload = usagePayload(
                userId, application, asset, packageSnapshotId, scene, usedAt, attribution);
        String idempotencyKeyHash = hash(Map.of(
                "userId", userId,
                "command", "CREATE_EVIDENCE_USAGE",
                "key", request.getIdempotencyKey().trim()));
        String payloadHash = hash(payload);
        CareerEvidenceUsage replay = usageMapper.selectByIdempotencyKey(userId, idempotencyKeyHash);
        if (replay != null) {
            assertSamePayload(replay.getIdempotencyPayloadHash(), payloadHash, "证据使用请求");
            return toUsageVO(replay, asset);
        }
        String usageKeyHash = hash(Map.of(
                "userId", userId,
                "applicationId", applicationId,
                "assetType", asset.assetType(),
                "assetId", asset.assetId(),
                "assetVersion", asset.assetVersion(),
                "packageSnapshotId", packageSnapshotId == null ? "" : packageSnapshotId,
                "usageScene", scene));
        CareerEvidenceUsage existing = usageMapper.selectByUsageKey(userId, usageKeyHash);
        if (existing != null) {
            assertSamePayload(existing.getIdempotencyPayloadHash(), payloadHash, "证据使用请求");
            assertSameAttribution(existing, attribution);
            return toUsageVO(existing, asset);
        }
        CareerEvidenceUsage row = new CareerEvidenceUsage();
        row.setUserId(userId);
        row.setCampaignId(application.getCampaignId());
        row.setApplicationId(applicationId);
        row.setTargetJobId(application.getTargetJobId());
        row.setAssetType(asset.assetType());
        row.setAssetId(asset.assetId());
        row.setAssetVersion(asset.assetVersion());
        row.setPackageSnapshotId(packageSnapshotId);
        row.setSourceHash(asset.sourceHash());
        row.setContentHash(asset.contentHash());
        row.setUsageScene(scene);
        row.setUsedAt(usedAt);
        row.setHypothesisId(attribution.hypothesisId());
        row.setVariantId(attribution.variantId());
        row.setAssignmentId(attribution.assignmentId());
        row.setUsageKeyHash(usageKeyHash);
        row.setIdempotencyKeyHash(idempotencyKeyHash);
        row.setIdempotencyPayloadHash(payloadHash);
        row.setStatus("CAPTURED");
        row.setStale(CommonConstants.NO);
        row.setDeleted(CommonConstants.NO);
        try {
            usageMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            CareerEvidenceUsage winner =
                    usageMapper.selectByIdempotencyKey(userId, idempotencyKeyHash);
            if (winner == null) {
                winner = usageMapper.selectByUsageKey(userId, usageKeyHash);
            }
            if (winner != null) {
                assertSamePayload(winner.getIdempotencyPayloadHash(), payloadHash, "证据使用请求");
                assertSameAttribution(winner, attribution);
                return toUsageVO(winner, asset);
            }
            throw ex;
        }
        return toUsageVO(row, asset);
    }

    @Override
    public EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO> listApplicationUsages(
            Long applicationId, CareerEvidenceUsageQueryDTO query) {
        CareerEvidenceUsageQueryDTO actual = query == null
                ? new CareerEvidenceUsageQueryDTO() : query;
        actual.setApplicationId(applicationId);
        return listUsages(actual);
    }

    @Override
    public EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO> listUsages(
            CareerEvidenceUsageQueryDTO query) {
        featureGate.requireEvidenceUsage();
        Long userId = SecurityAssert.requireLoginUserId();
        return listUsagesForUser(userId, query);
    }

    @Override
    public CareerEvidenceUsageVO usage(Long usageId) {
        featureGate.requireEvidenceUsage();
        Long userId = SecurityAssert.requireLoginUserId();
        return toUsageVO(ownedUsage(userId, usageId), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerEvidenceUsageResultVO createResult(
            Long usageId, CareerEvidenceUsageResultWriteDTO request) {
        featureGate.requireEvidenceFeedback();
        Long userId = SecurityAssert.requireLoginUserId();
        CareerEvidenceUsage usage = ownedUsage(userId, usageId);
        JobApplication application =
                sourceResolver.ownedApplication(userId, usage.getApplicationId());
        validateResultWrite(request);
        EventResolution event = sourceResolver.resolveEvent(userId, application, request);
        String eventKeyHash = hash(Map.of(
                "userId", userId,
                "usageId", usageId,
                "eventType", event.eventType(),
                "eventId", event.eventId()));
        CareerEvidenceUsageResult root =
                resultMapper.selectByEventKey(userId, usageId, eventKeyHash);
        if (root == null) {
            root = new CareerEvidenceUsageResult();
            root.setUserId(userId);
            root.setUsageId(usageId);
            root.setApplicationId(usage.getApplicationId());
            root.setEventType(event.eventType());
            root.setEventId(event.eventId());
            root.setEventKeyHash(eventKeyHash);
            root.setSnapshotVersion(0);
            root.setStatus("RECORDED");
            root.setLockVersion(0);
            root.setDeleted(CommonConstants.NO);
            try {
                resultMapper.insert(root);
            } catch (DuplicateKeyException ex) {
                root = resultMapper.selectByEventKey(userId, usageId, eventKeyHash);
                if (root == null) {
                    throw ex;
                }
            }
        }
        String idempotencyKeyHash = resultIdempotencyKeyHash(
                userId, root.getId(), "CREATE", request.getIdempotencyKey());
        ResultSnapshotInput input = new ResultSnapshotInput(
                normalizeOutcome(request.getOutcomeCode()),
                normalizeList(request.getKnownFacts()),
                trim(request.getExternalFeedbackText()),
                trim(request.getUserInterpretationText()),
                normalizeList(request.getUnknowns()),
                normalizeList(request.getLimits()),
                event.eventType(), event.eventId(), event.sourceVersion(), event.sourceHash(),
                request.getOccurredAt() == null ? event.occurredAt() : request.getOccurredAt(),
                null);
        String payloadHash = hash(resultSnapshotPayload(input, "CREATE"));
        CareerEvidenceUsageResultSnapshot replay =
                resultSnapshotMapper.selectByIdempotencyKey(
                        root.getId(), userId, idempotencyKeyHash);
        if (replay != null) {
            assertSamePayload(replay.getIdempotencyPayloadHash(), payloadHash, "结果记录请求");
            return toResultVO(root, replay);
        }
        CareerEvidenceUsageResultSnapshot current = currentSnapshot(root, userId);
        String currentStatus = current != null && StringUtils.hasText(current.getStatus())
                ? current.getStatus() : root.getStatus();
        if (current != null && Objects.equals(
                current.getContentHash(), resultContentHash(currentStatus, input))) {
            return toResultVO(root, current);
        }
        // createResult records the initial RECORDED snapshot. Once the result root has advanced past
        // RECORDED (CONFIRMED/CORRECTED/VOID), a fresh-key re-record with changed content must not silently
        // append a RECORDED snapshot — that would demote the confirmed status and drop the event from
        // confirmed facts. The root status (not the snapshot status) is the authoritative lifecycle state.
        // Route such changes through the confirm/correct/void commands instead.
        if (!"RECORDED".equals(root.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "该结果已确认或作废，请通过确认/更正/作废操作修改，不能重新记录。");
        }
        return appendSnapshot(root, current, input, "RECORDED",
                idempotencyKeyHash, payloadHash, root.getLockVersion());
    }

    @Override
    public EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO> listUsageResults(Long usageId) {
        featureGate.requireEvidenceFeedback();
        Long userId = SecurityAssert.requireLoginUserId();
        ownedUsage(userId, usageId);
        CareerEvidenceUsageResultQueryDTO query = new CareerEvidenceUsageResultQueryDTO();
        query.setUsageId(usageId);
        query.setPageSize(100L);
        return listResultsForUser(userId, query);
    }

    @Override
    public EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO> listResults(
            CareerEvidenceUsageResultQueryDTO query) {
        featureGate.requireEvidenceFeedback();
        Long userId = SecurityAssert.requireLoginUserId();
        return listResultsForUser(userId, query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerEvidenceUsageResultVO confirmResult(
            Long resultId, CareerEvidenceUsageResultCommandDTO request) {
        return mutateResult(resultId, request, "CONFIRMED", "CONFIRM");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerEvidenceUsageResultVO correctResult(
            Long resultId, CareerEvidenceUsageResultCommandDTO request) {
        return mutateResult(resultId, request, "CORRECTED", "CORRECT");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerEvidenceUsageResultVO voidResult(
            Long resultId, CareerEvidenceUsageResultCommandDTO request) {
        return mutateResult(resultId, request, "VOID", "VOID");
    }

    @Override
    public EvidenceAssetOverviewEnvelopeVO overview(Long campaignId, Long applicationId) {
        featureGate.requireEvidenceAssetsView();
        Long userId = SecurityAssert.requireLoginUserId();
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC);
        long usageCount = countUsage(userId, null, campaignId, applicationId);
        long resultCount = resultMapper.selectCountByUsageScope(
                userId, campaignId, applicationId, null);
        long projectUsageCount =
                countUsage(userId, "PROJECT_EVIDENCE", campaignId, applicationId);
        long projectResultCount = resultMapper.selectCountByUsageScope(
                userId, campaignId, applicationId, "PROJECT_EVIDENCE");
        long packageUsageCount =
                countUsage(userId, "APPLICATION_PACKAGE_SNAPSHOT", campaignId, applicationId);
        long packageResultCount = resultMapper.selectCountByUsageScope(
                userId, campaignId, applicationId, "APPLICATION_PACKAGE_SNAPSHOT");
        long projectCount = projectEvidenceMapper.selectCount(
                new LambdaQueryWrapper<ProjectEvidence>()
                        .eq(ProjectEvidence::getUserId, userId)
                        .eq(ProjectEvidence::getDeleted, CommonConstants.NO));
        long projectVersionCount = projectEvidenceVersionMapper.selectCount(
                new LambdaQueryWrapper<ProjectEvidenceVersion>()
                        .eq(ProjectEvidenceVersion::getUserId, userId)
                        .eq(ProjectEvidenceVersion::getDeleted, CommonConstants.NO));
        EvidenceAssetOverviewVO overview = new EvidenceAssetOverviewVO();
        overview.setAssetCount(projectCount);
        overview.setVersionedAssetCount(projectVersionCount);
        overview.setUsageCount(usageCount);
        overview.setOutcomeSampleCount(resultCount);
        overview.getReadiness().add(readiness(
                "PROJECT_EVIDENCE", "项目证据", projectCount, projectVersionCount,
                projectUsageCount, projectResultCount,
                "/project-evidence"));
        overview.getReadiness().add(readiness(
                "APPLICATION_PACKAGE_SNAPSHOT", "投递包快照", 0L, 0L,
                packageUsageCount, packageResultCount,
                "/application-packages"));
        EvidenceAssetOverviewEnvelopeVO envelope = new EvidenceAssetOverviewEnvelopeVO();
        envelope.setOverview(overview);
        envelope.setItems(overview.getReadiness());
        envelope.setTotal((long) overview.getReadiness().size());
        envelope.setDataCutoffAt(cutoff);
        envelope.setSourceSetHash(hash(Map.of(
                "userId", userId,
                "usageCount", usageCount,
                "resultCount", resultCount,
                "campaignId", campaignId == null ? "" : campaignId,
                "applicationId", applicationId == null ? "" : applicationId,
                "projectCount", projectCount,
                "projectVersionCount", projectVersionCount)));
        envelope.getCoverage().put("resumeEvidence", "AVAILABLE");
        envelope.getCoverage().put("learningCandidates", "AI_SERVICE");
        envelope.getLimits().add("证据效果只显示使用事实和人工确认结果，不进行单因素因果归因。");
        envelope.setConfidenceLevel(usageCount < 5 ? "LOW" : "MEDIUM");
        return envelope;
    }

    @Override
    public InnerCareerEvidenceUsageFactsVO innerFacts(
            Long userId, Long campaignId, Long applicationId, Long usageId,
            LocalDateTime dataCutoffAt) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户 ID 不能为空。");
        }
        LocalDateTime cutoff = dataCutoffAt == null
                ? LocalDateTime.now(ZoneOffset.UTC) : dataCutoffAt;
        CareerEvidenceUsageQueryDTO query = new CareerEvidenceUsageQueryDTO();
        query.setUsageId(usageId);
        query.setCampaignId(campaignId);
        query.setApplicationId(applicationId);
        query.setDataCutoffAt(cutoff);
        query.setPageSize(500L);
        EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO> usages =
                listUsagesForUser(userId, query);
        List<CareerEvidenceUsageVO> selected = usages.getItems();
        InnerCareerEvidenceUsageFactsVO facts = new InnerCareerEvidenceUsageFactsVO();
        facts.setUserId(userId);
        facts.setDataCutoffAt(cutoff);
        facts.setSourceSetHash(usages.getSourceSetHash());
        if (usages.getTotal() != null && usages.getTotal() > selected.size()) {
            facts.getWarnings().add("证据使用记录超过 500 条，当前事实信封已截断。");
        }
        for (CareerEvidenceUsageVO item : selected) {
            InnerCareerEvidenceUsageFactsVO.UsageFact fact =
                    new InnerCareerEvidenceUsageFactsVO.UsageFact();
            fact.setUsageId(item.getId());
            fact.setApplicationId(item.getApplicationId());
            fact.setCampaignId(item.getCampaignId());
            fact.setTargetJobId(item.getTargetJobId());
            fact.setAssetType(item.getAssetType());
            fact.setAssetId(item.getAssetId());
            fact.setAssetVersion(item.getAssetVersion());
            fact.setPackageSnapshotId(item.getPackageSnapshotId());
            fact.setSourceHash(item.getSourceHash());
            fact.setContentHash(item.getContentHash());
            fact.setUsageScene(item.getUsageScene());
            fact.setUsedAt(item.getUsedAt());
            fact.setStatus(item.getStatus());
            fact.setStale(item.getStale());
            fact.setSourceRefs(item.getSources().stream()
                    .map(source -> source.getSourceType() + ":" + source.getSourceId()
                            + ":" + source.getSourceVersion())
                    .toList());
            facts.getUsageSnapshots().add(fact);
            EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO> resultEnvelope =
                    listResultsForUser(userId, resultQuery(item.getId(), cutoff));
            if (resultEnvelope.getTotal() != null
                    && resultEnvelope.getTotal() > resultEnvelope.getItems().size()) {
                facts.getWarnings().add(
                        "使用记录 " + item.getId() + " 的结果超过 100 条，当前事实信封已截断。");
            }
            for (CareerEvidenceUsageResultVO result : resultEnvelope.getItems()) {
                if (!Set.of("CONFIRMED", "CORRECTED").contains(result.getStatus())) {
                    continue;
                }
                InnerCareerEvidenceUsageFactsVO.ResultFact resultFact =
                        new InnerCareerEvidenceUsageFactsVO.ResultFact();
                resultFact.setResultId(result.getId());
                resultFact.setUsageId(result.getUsageId());
                resultFact.setApplicationId(result.getApplicationId());
                resultFact.setEventType(result.getEventType());
                resultFact.setEventId(result.getEventId());
                resultFact.setStatus(result.getStatus());
                resultFact.setSnapshotVersion(result.getSnapshotVersion());
                resultFact.setOutcomeCode(result.getOutcomeCode());
                resultFact.setKnownFacts(result.getKnownFacts());
                resultFact.setUnknowns(result.getUnknowns());
                resultFact.setLimits(result.getLimits());
                resultFact.setSourceHash(result.getSourceHash());
                resultFact.setOccurredAt(result.getOccurredAt());
                resultFact.setConfirmedAt(result.getConfirmedAt());
                facts.getConfirmedResults().add(resultFact);
            }
        }
        appendAttributionFacts(userId, selected, facts, cutoff);
        facts.getCoverage().put("usageCount", facts.getUsageSnapshots().size());
        facts.getCoverage().put("confirmedResultCount", facts.getConfirmedResults().size());
        facts.getCoverage().put("attributionCount", facts.getExperimentAttributions().size());
        if (facts.getUsageSnapshots().size() < 5) {
            facts.getLimits().add("可比较使用样本少于 5 条，只能陈述事实和未知项。");
        }
        Map<String, Object> sourceEnvelope = new LinkedHashMap<>();
        sourceEnvelope.put("userId", userId);
        sourceEnvelope.put("dataCutoffAt", facts.getDataCutoffAt());
        sourceEnvelope.put("usageSnapshots", facts.getUsageSnapshots());
        sourceEnvelope.put("confirmedResults", facts.getConfirmedResults());
        sourceEnvelope.put("experimentAttributions", facts.getExperimentAttributions());
        sourceEnvelope.put("limits", facts.getLimits());
        sourceEnvelope.put("warnings", facts.getWarnings());
        facts.setSourceSetHash(hash(sourceEnvelope));
        return facts;
    }

    public InnerCareerEvidenceUsageFactsVO innerFacts(
            Long userId, Long campaignId, Long applicationId, Long usageId) {
        return innerFacts(userId, campaignId, applicationId, usageId, null);
    }

    private void appendAttributionFacts(
            Long userId,
            List<CareerEvidenceUsageVO> usages,
            InnerCareerEvidenceUsageFactsVO facts,
            LocalDateTime dataCutoffAt) {
        Set<Long> hypothesisIds = usages.stream()
                .map(CareerEvidenceUsageVO::getHypothesisId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (hypothesisIds.isEmpty()) {
            return;
        }
        List<ExperimentAttribution> snapshots = attributionMapper.selectList(
                new LambdaQueryWrapper<ExperimentAttribution>()
                        .eq(ExperimentAttribution::getUserId, userId)
                        .eq(ExperimentAttribution::getDeleted, CommonConstants.NO)
                        .in(ExperimentAttribution::getHypothesisId, hypothesisIds)
                        .and(wrapper -> wrapper
                                .le(ExperimentAttribution::getDataCutoffAt, dataCutoffAt)
                                .or(nested -> nested
                                        .isNull(ExperimentAttribution::getDataCutoffAt)
                                        .le(ExperimentAttribution::getAsOf, dataCutoffAt)))
                        .orderByDesc(ExperimentAttribution::getDataCutoffAt)
                        .orderByDesc(ExperimentAttribution::getAsOf)
                        .orderByDesc(ExperimentAttribution::getId));
        Map<Long, ExperimentAttribution> latestByHypothesis = new LinkedHashMap<>();
        for (ExperimentAttribution snapshot : snapshots) {
            if (snapshot != null && snapshot.getHypothesisId() != null) {
                latestByHypothesis.putIfAbsent(snapshot.getHypothesisId(), snapshot);
            }
        }
        Set<String> seen = new java.util.HashSet<>();
        for (CareerEvidenceUsageVO usage : usages) {
            if (usage == null || usage.getHypothesisId() == null) {
                continue;
            }
            ExperimentAttribution snapshot = latestByHypothesis.get(usage.getHypothesisId());
            if (snapshot == null) {
                continue;
            }
            String key = snapshot.getId() + ":" + usage.getId();
            if (!seen.add(key)) {
                continue;
            }
            InnerCareerEvidenceUsageFactsVO.ExperimentAttributionFact fact =
                    new InnerCareerEvidenceUsageFactsVO.ExperimentAttributionFact();
            fact.setAttributionId(snapshot.getId());
            fact.setHypothesisId(snapshot.getHypothesisId());
            fact.setVariantId(usage.getVariantId());
            fact.setAssignmentId(usage.getAssignmentId());
            fact.setUsageId(usage.getId());
            fact.setStatus(CommonConstants.YES.equals(snapshot.getComparableFlag())
                    ? "COMPARABLE" : "INCOMPARABLE");
            fact.setConfidenceLevel(attributionConfidence(snapshot));
            fact.setFallback(CommonConstants.YES.equals(snapshot.getFallback()));
            facts.getExperimentAttributions().add(fact);
        }
    }

    private String attributionConfidence(ExperimentAttribution snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.getResultJson())) {
            return "LOW";
        }
        try {
            String value = objectMapper.readTree(snapshot.getResultJson())
                    .path("confidenceLevel").asText(null);
            return StringUtils.hasText(value) ? value : "LOW";
        } catch (Exception ex) {
            return "LOW";
        }
    }

    private CareerEvidenceUsageResultVO mutateResult(
            Long resultId, CareerEvidenceUsageResultCommandDTO request,
            String targetStatus, String command) {
        featureGate.requireEvidenceFeedback();
        Long userId = SecurityAssert.requireLoginUserId();
        validateResultCommand(request);
        CareerEvidenceUsageResult root = resultMapper.selectForUpdate(resultId, userId);
        if (root == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "结果记录不存在或无权访问。");
        }
        CareerEvidenceUsageResultSnapshot current = currentSnapshot(root, userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "结果快照不存在。");
        }
        String idempotencyKeyHash = resultIdempotencyKeyHash(
                userId, resultId, command, request.getIdempotencyKey());
        ResultSnapshotInput input = commandInput(current, request, command);
        String payloadHash = hash(resultSnapshotPayload(input, command));
        CareerEvidenceUsageResultSnapshot replay =
                resultSnapshotMapper.selectByIdempotencyKey(
                        resultId, userId, idempotencyKeyHash);
        if (replay != null) {
            assertSamePayload(replay.getIdempotencyPayloadHash(), payloadHash, "结果操作请求");
            return toResultVO(root, replay);
        }
        if (!"CORRECT".equals(command) && Objects.equals(root.getStatus(), targetStatus)) {
            return toResultVO(root, current);
        }
        if ("VOID".equals(root.getStatus())) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "已作废结果不能再次修改。");
        }
        if (!Objects.equals(root.getLockVersion(), request.getExpectedLockVersion())) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "结果记录已更新，请刷新后重试。");
        }
        if (Objects.equals(current.getContentHash(), resultContentHash(targetStatus, input))) {
            return toResultVO(root, current);
        }
        CareerEvidenceUsageResultVO mutated = appendSnapshot(root, current, input, targetStatus,
                idempotencyKeyHash, payloadHash, request.getExpectedLockVersion());
        // Real transition committed to the working set: fold the trusted outcome back into the
        // skill profile. Replay and no-op paths above never reach this hook, and the hook itself
        // never throws.
        profileFeedbackService.afterResultTransition(
                root, input.outcomeCode(), input.userInterpretationText());
        return mutated;
    }

    private CareerEvidenceUsageResultVO appendSnapshot(
            CareerEvidenceUsageResult root,
            CareerEvidenceUsageResultSnapshot current,
            ResultSnapshotInput input,
            String status,
            String idempotencyKeyHash,
            String payloadHash,
            Integer expectedLockVersion) {
        int nextVersion = current == null || current.getSnapshotVersion() == null
                ? 1 : current.getSnapshotVersion() + 1;
        CareerEvidenceUsageResultSnapshot snapshot = new CareerEvidenceUsageResultSnapshot();
        snapshot.setResultId(root.getId());
        snapshot.setUserId(root.getUserId());
        snapshot.setSnapshotVersion(nextVersion);
        snapshot.setStatus(status);
        snapshot.setOutcomeCode(input.outcomeCode());
        snapshot.setKnownFactsJson(writeJson(input.knownFacts()));
        snapshot.setExternalFeedbackText(input.externalFeedbackText());
        snapshot.setUserInterpretationText(input.userInterpretationText());
        snapshot.setUnknownsJson(writeJson(input.unknowns()));
        snapshot.setLimitsJson(writeJson(input.limits()));
        snapshot.setSourceType(input.sourceType());
        snapshot.setSourceId(input.sourceId());
        snapshot.setSourceVersion(input.sourceVersion());
        snapshot.setSourceHash(input.sourceHash());
        snapshot.setOccurredAt(input.occurredAt());
        snapshot.setConfirmedAt(input.confirmedAt());
        snapshot.setContentHash(resultContentHash(status, input));
        snapshot.setIdempotencyKeyHash(idempotencyKeyHash);
        snapshot.setIdempotencyPayloadHash(payloadHash);
        snapshot.setSupersedesSnapshotId(current == null ? null : current.getId());
        try {
            resultSnapshotMapper.insert(snapshot);
        } catch (DuplicateKeyException ex) {
            CareerEvidenceUsageResultSnapshot replay =
                    resultSnapshotMapper.selectByIdempotencyKey(
                            root.getId(), root.getUserId(), idempotencyKeyHash);
            if (replay != null) {
                assertSamePayload(replay.getIdempotencyPayloadHash(), payloadHash, "结果操作请求");
                return toResultVO(root, replay);
            }
            // Not an idempotency-key replay, so this is a concurrent append colliding on the
            // (result_id, snapshot_version) unique key. Surface it as a friendly stale-version
            // signal so the caller refreshes and retries, rather than leaking DuplicateKeyException.
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "结果记录已更新，请刷新后重试。");
        }
        int updated = resultMapper.updateCurrentSnapshot(
                root.getId(), root.getUserId(), snapshot.getId(), nextVersion,
                status, expectedLockVersion == null ? 0 : expectedLockVersion);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "结果记录已更新，请刷新后重试。");
        }
        root.setCurrentSnapshotId(snapshot.getId());
        root.setSnapshotVersion(nextVersion);
        root.setStatus(status);
        root.setLockVersion((expectedLockVersion == null ? 0 : expectedLockVersion) + 1);
        return toResultVO(root, snapshot);
    }

    private EvidenceAssetEnvelopeVO<CareerEvidenceUsageVO> listUsagesForUser(
            Long userId, CareerEvidenceUsageQueryDTO query) {
        CareerEvidenceUsageQueryDTO actual = query == null
                ? new CareerEvidenceUsageQueryDTO() : query;
        long pageNo = sanitizePage(actual.getPageNo());
        long pageSize = sanitizePageSize(actual.getPageSize());
        List<Long> experimentHypothesisIds =
                experimentHypothesisIds(userId, actual.getExperimentId());
        if (actual.getExperimentId() != null && experimentHypothesisIds.isEmpty()) {
            return envelope(List.of(), 0L, pageNo, pageSize,
                    actual.getDataCutoffAt(), "证据使用记录");
        }
        LambdaQueryWrapper<CareerEvidenceUsage> wrapper = usageBase(userId)
                .eq(actual.getUsageId() != null,
                        CareerEvidenceUsage::getId, actual.getUsageId())
                .eq(actual.getApplicationId() != null,
                        CareerEvidenceUsage::getApplicationId, actual.getApplicationId())
                .eq(actual.getCampaignId() != null,
                        CareerEvidenceUsage::getCampaignId, actual.getCampaignId())
                .eq(actual.getTargetJobId() != null,
                        CareerEvidenceUsage::getTargetJobId, actual.getTargetJobId())
                .eq(StringUtils.hasText(actual.getAssetType()),
                        CareerEvidenceUsage::getAssetType, normalize(actual.getAssetType()))
                .eq(actual.getAssetId() != null,
                        CareerEvidenceUsage::getAssetId, actual.getAssetId())
                .eq(actual.getPackageSnapshotId() != null,
                        CareerEvidenceUsage::getPackageSnapshotId, actual.getPackageSnapshotId())
                .eq(actual.getHypothesisId() != null,
                        CareerEvidenceUsage::getHypothesisId, actual.getHypothesisId())
                .in(actual.getExperimentId() != null,
                        CareerEvidenceUsage::getHypothesisId, experimentHypothesisIds)
                .eq(StringUtils.hasText(actual.getStatus()),
                        CareerEvidenceUsage::getStatus, normalizeUsageStatus(actual.getStatus()))
                .eq(actual.getStale() != null,
                        CareerEvidenceUsage::getStale,
                        Boolean.TRUE.equals(actual.getStale()) ? CommonConstants.YES : CommonConstants.NO)
                .le(actual.getDataCutoffAt() != null,
                        CareerEvidenceUsage::getUsedAt, actual.getDataCutoffAt())
                .le(actual.getDataCutoffAt() != null,
                        CareerEvidenceUsage::getCreatedAt, actual.getDataCutoffAt())
                .orderByDesc(CareerEvidenceUsage::getUsedAt)
                .orderByDesc(CareerEvidenceUsage::getId);
        Page<CareerEvidenceUsage> page =
                usageMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<CareerEvidenceUsageVO> items = page.getRecords().stream()
                .map(row -> toUsageVO(row, null))
                .toList();
        return envelope(items, page.getTotal(), pageNo, pageSize,
                actual.getDataCutoffAt(), "证据使用记录");
    }

    private EvidenceAssetEnvelopeVO<CareerEvidenceUsageResultVO> listResultsForUser(
            Long userId, CareerEvidenceUsageResultQueryDTO query) {
        CareerEvidenceUsageResultQueryDTO actual = query == null
                ? new CareerEvidenceUsageResultQueryDTO() : query;
        long pageNo = sanitizePage(actual.getPageNo());
        long pageSize = sanitizePageSize(actual.getPageSize());
        LocalDateTime cutoff = actual.getDataCutoffAt();
        String status = normalizeResultStatus(actual.getStatus());
        LambdaQueryWrapper<CareerEvidenceUsageResult> wrapper =
                new LambdaQueryWrapper<CareerEvidenceUsageResult>()
                        .eq(CareerEvidenceUsageResult::getUserId, userId)
                        .eq(CareerEvidenceUsageResult::getDeleted, CommonConstants.NO)
                        .eq(actual.getResultId() != null,
                                CareerEvidenceUsageResult::getId, actual.getResultId())
                        .eq(actual.getApplicationId() != null,
                                CareerEvidenceUsageResult::getApplicationId, actual.getApplicationId())
                        .eq(actual.getUsageId() != null,
                                CareerEvidenceUsageResult::getUsageId, actual.getUsageId())
                        .le(cutoff != null, CareerEvidenceUsageResult::getCreatedAt, cutoff)
                        .eq(cutoff == null && StringUtils.hasText(status),
                                CareerEvidenceUsageResult::getStatus, status)
                        .orderByDesc(CareerEvidenceUsageResult::getUpdatedAt)
                        .orderByDesc(CareerEvidenceUsageResult::getId);
        if (hasUsageScope(actual)) {
            List<Long> usageIds = scopedUsageIds(userId, actual);
            if (usageIds.isEmpty()) {
                return envelope(List.of(), 0L, pageNo, pageSize, cutoff, "证据使用结果");
            }
            wrapper.in(CareerEvidenceUsageResult::getUsageId, usageIds);
        }
        applyResultSnapshotFilters(
                wrapper,
                userId,
                StringUtils.hasText(actual.getOutcomeCode())
                        ? normalizeOutcome(actual.getOutcomeCode()) : null,
                cutoff == null ? null : status,
                cutoff);
        Page<CareerEvidenceUsageResult> page =
                resultMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<CareerEvidenceUsageResultVO> items = new ArrayList<>();
        for (CareerEvidenceUsageResult root : page.getRecords()) {
            CareerEvidenceUsageResultSnapshot snapshot = cutoff == null
                    ? currentSnapshot(root, userId)
                    : resultSnapshotMapper.selectLatestAtCutoff(root.getId(), userId, cutoff);
            items.add(toResultVO(root, snapshot));
        }
        return envelope(items, page.getTotal(), pageNo, pageSize,
                actual.getDataCutoffAt(), "证据使用结果");
    }

    private <T> EvidenceAssetEnvelopeVO<T> envelope(
            List<T> items, long total, long pageNo, long pageSize,
            LocalDateTime requestedCutoff, String sourceLabel) {
        EvidenceAssetEnvelopeVO<T> envelope = new EvidenceAssetEnvelopeVO<>();
        envelope.setItems(items);
        envelope.setTotal(total);
        envelope.setPageNo(pageNo);
        envelope.setPageSize(pageSize);
        envelope.setDataCutoffAt(requestedCutoff == null
                ? LocalDateTime.now(ZoneOffset.UTC) : requestedCutoff);
        envelope.setSourceSetHash(hash(Map.of(
                "source", sourceLabel,
                "ids", items.stream().map(this::stableItemIdentity).toList(),
                "total", total)));
        envelope.getCoverage().put("source", sourceLabel);
        envelope.getCoverage().put("loaded", items.size());
        envelope.setConfidenceLevel(items.size() < 5 ? "LOW" : "MEDIUM");
        envelope.getLimits().add("结果只用于弱观察，不代表单一证据导致了后续结果。");
        return envelope;
    }

    private Object stableItemIdentity(Object value) {
        if (value instanceof CareerEvidenceUsageVO usage) {
            return usage.getId() + ":" + usage.getSourceHash();
        }
        if (value instanceof CareerEvidenceUsageResultVO result) {
            return result.getId() + ":" + result.getContentHash();
        }
        return String.valueOf(value);
    }

    private CareerEvidenceUsageVO toUsageVO(CareerEvidenceUsage row, AssetResolution asset) {
        CareerEvidenceUsageVO vo = new CareerEvidenceUsageVO();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setCampaignId(row.getCampaignId());
        vo.setApplicationId(row.getApplicationId());
        vo.setTargetJobId(row.getTargetJobId());
        vo.setAssetType(row.getAssetType());
        vo.setAssetId(row.getAssetId());
        vo.setAssetVersion(row.getAssetVersion());
        vo.setPackageSnapshotId(row.getPackageSnapshotId());
        vo.setSourceHash(row.getSourceHash());
        vo.setContentHash(row.getContentHash());
        vo.setUsageScene(row.getUsageScene());
        vo.setUsedAt(row.getUsedAt());
        vo.setHypothesisId(row.getHypothesisId());
        vo.setVariantId(row.getVariantId());
        vo.setAssignmentId(row.getAssignmentId());
        vo.setUsageKeyHash(row.getUsageKeyHash());
        vo.setIdempotencyKeyHash(row.getIdempotencyKeyHash());
        vo.setStatus(row.getStatus());
        vo.setStale(CommonConstants.YES.equals(row.getStale()));
        vo.setStaleReason(row.getStaleReason());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        vo.setDataCutoffAt(LocalDateTime.now(ZoneOffset.UTC));
        vo.setSourceSetHash(row.getSourceHash());
        vo.setConfidenceLevel("LOW");
        vo.setFallback(false);
        vo.getCoverage().put("asset", "AVAILABLE");
        vo.getLimits().add("单条使用记录不能证明证据有效性。");
        CareerEvidenceUsageVO.SourceRef source =
                asset == null ? sourceFromRow(row) : sourceResolver.sourceRef(asset);
        vo.getSources().add(source);
        return vo;
    }

    private CareerEvidenceUsageVO.SourceRef sourceFromRow(CareerEvidenceUsage row) {
        CareerEvidenceUsageVO.SourceRef source = new CareerEvidenceUsageVO.SourceRef();
        source.setSourceType(row.getAssetType());
        source.setSourceId(row.getAssetId());
        source.setSourceVersion(row.getAssetVersion());
        source.setSourceHash(row.getSourceHash());
        source.setObservedAt(row.getUsedAt());
        source.setSummary("已记录的证据资产版本");
        return source;
    }

    private CareerEvidenceUsageResultVO toResultVO(
            CareerEvidenceUsageResult root, CareerEvidenceUsageResultSnapshot snapshot) {
        CareerEvidenceUsageResultVO vo = new CareerEvidenceUsageResultVO();
        vo.setId(root.getId());
        vo.setUserId(root.getUserId());
        vo.setUsageId(root.getUsageId());
        vo.setApplicationId(root.getApplicationId());
        vo.setEventType(root.getEventType());
        vo.setEventId(root.getEventId());
        vo.setEventKeyHash(root.getEventKeyHash());
        vo.setCurrentSnapshotId(root.getCurrentSnapshotId());
        vo.setSnapshotVersion(root.getSnapshotVersion());
        vo.setStatus(snapshot != null && StringUtils.hasText(snapshot.getStatus())
                ? snapshot.getStatus() : root.getStatus());
        vo.setLockVersion(root.getLockVersion());
        vo.setCreatedAt(root.getCreatedAt());
        vo.setUpdatedAt(root.getUpdatedAt());
        if (snapshot != null) {
            vo.setOutcomeCode(snapshot.getOutcomeCode());
            vo.setKnownFacts(readList(snapshot.getKnownFactsJson()));
            vo.setExternalFeedbackText(snapshot.getExternalFeedbackText());
            vo.setUserInterpretationText(snapshot.getUserInterpretationText());
            vo.setUnknowns(readList(snapshot.getUnknownsJson()));
            vo.setLimits(readList(snapshot.getLimitsJson()));
            vo.setSourceType(snapshot.getSourceType());
            vo.setSourceId(snapshot.getSourceId());
            vo.setSourceVersion(snapshot.getSourceVersion());
            vo.setSourceHash(snapshot.getSourceHash());
            vo.setOccurredAt(snapshot.getOccurredAt());
            vo.setConfirmedAt(snapshot.getConfirmedAt());
            vo.setContentHash(snapshot.getContentHash());
            vo.setSupersedesSnapshotId(snapshot.getSupersedesSnapshotId());
            CareerEvidenceUsageVO.SourceRef source = new CareerEvidenceUsageVO.SourceRef();
            source.setSourceType(snapshot.getSourceType());
            source.setSourceId(snapshot.getSourceId());
            source.setSourceVersion(snapshot.getSourceVersion());
            source.setSourceHash(snapshot.getSourceHash());
            source.setObservedAt(snapshot.getOccurredAt());
            source.setSummary("已校验的结果来源");
            vo.getSources().add(source);
        }
        vo.setDataCutoffAt(LocalDateTime.now(ZoneOffset.UTC));
        vo.setSourceSetHash(snapshot == null ? root.getEventKeyHash() : snapshot.getContentHash());
        vo.getCoverage().put("eventSource", snapshot == null ? "MISSING" : "AVAILABLE");
        vo.setConfidenceLevel("LOW");
        vo.setFallback(false);
        vo.getLimitsFromCoverage().add("结果与证据使用相关联，但不能据此作单因素因果判断。");
        return vo;
    }

    private CareerEvidenceUsageResultSnapshot currentSnapshot(
            CareerEvidenceUsageResult root, Long userId) {
        if (root == null || root.getCurrentSnapshotId() == null) {
            return null;
        }
        return resultSnapshotMapper.selectOwned(
                root.getCurrentSnapshotId(), root.getId(), userId);
    }

    private CareerEvidenceUsage ownedUsage(Long userId, Long usageId) {
        CareerEvidenceUsage usage = usageMapper.selectOwned(usageId, userId);
        if (usage == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "证据使用记录不存在或无权访问。");
        }
        return usage;
    }

    private LambdaQueryWrapper<CareerEvidenceUsage> usageBase(Long userId) {
        return new LambdaQueryWrapper<CareerEvidenceUsage>()
                .eq(CareerEvidenceUsage::getUserId, userId)
                .eq(CareerEvidenceUsage::getDeleted, CommonConstants.NO);
    }

    private void validateUsageRequest(CareerEvidenceUsageCreateDTO request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "证据使用请求不能为空。");
        }
        String scene = normalize(request.getUsageScene());
        if (!USAGE_SCENES.contains(scene)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的证据使用场景。");
        }
    }

    private void validateResultWrite(CareerEvidenceUsageResultWriteDTO request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结果记录请求不能为空。");
        }
        normalizeOutcome(request.getOutcomeCode());
    }

    private void validateResultCommand(CareerEvidenceUsageResultCommandDTO request) {
        if (request == null || request.getExpectedLockVersion() == null
                || !StringUtils.hasText(request.getIdempotencyKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结果操作缺少锁版本或幂等键。");
        }
    }

    private String normalizeOutcome(String value) {
        String normalized = normalize(value);
        if (!OUTCOME_CODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的结果代码。");
        }
        return normalized;
    }

    private String normalizeUsageStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = normalize(value);
        if (!USAGE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的证据使用状态。");
        }
        return normalized;
    }

    private String normalizeResultStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = normalize(value);
        if (!RESULT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的结果状态。");
        }
        return normalized;
    }

    private Map<String, Object> usagePayload(
            Long userId, JobApplication application, AssetResolution asset,
            Long packageSnapshotId, String scene, LocalDateTime usedAt,
            AttributionBinding attribution) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("applicationId", application.getId());
        payload.put("campaignId", application.getCampaignId());
        payload.put("targetJobId", application.getTargetJobId());
        payload.put("assetType", asset.assetType());
        payload.put("assetId", asset.assetId());
        payload.put("assetVersion", asset.assetVersion());
        payload.put("packageSnapshotId", packageSnapshotId);
        payload.put("sourceHash", asset.sourceHash());
        payload.put("contentHash", asset.contentHash());
        payload.put("usageScene", scene);
        payload.put("usedAt", usedAt);
        payload.put("hypothesisId", attribution.hypothesisId());
        payload.put("variantId", attribution.variantId());
        payload.put("assignmentId", attribution.assignmentId());
        return payload;
    }

    private AttributionBinding resolveAttributionBinding(
            Long userId, Long applicationId, CareerEvidenceUsageCreateDTO request) {
        boolean hasAttribution = request.getHypothesisId() != null
                || request.getVariantId() != null
                || request.getAssignmentId() != null;
        if (!hasAttribution) {
            return AttributionBinding.empty();
        }
        if (request.getAssignmentId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "实验归因必须绑定有效的分配记录。");
        }
        ExperimentAssignment assignment = assignmentMapper.selectOne(
                new LambdaQueryWrapper<ExperimentAssignment>()
                        .eq(ExperimentAssignment::getId, request.getAssignmentId())
                        .eq(ExperimentAssignment::getUserId, userId)
                        .eq(ExperimentAssignment::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        if (assignment == null || !Objects.equals(assignment.getApplicationId(), applicationId)) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "实验分配与当前投递记录不一致。");
        }
        ExperimentHypothesis hypothesis = hypothesisMapper.selectOne(
                new LambdaQueryWrapper<ExperimentHypothesis>()
                        .eq(ExperimentHypothesis::getId, assignment.getHypothesisId())
                        .eq(ExperimentHypothesis::getUserId, userId)
                        .eq(ExperimentHypothesis::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        ExperimentVariant variant = variantMapper.selectOne(
                new LambdaQueryWrapper<ExperimentVariant>()
                        .eq(ExperimentVariant::getId, assignment.getVariantId())
                        .eq(ExperimentVariant::getUserId, userId)
                        .eq(ExperimentVariant::getHypothesisId, assignment.getHypothesisId())
                        .eq(ExperimentVariant::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        if (hypothesis == null || variant == null
                || (request.getHypothesisId() != null
                && !Objects.equals(request.getHypothesisId(), assignment.getHypothesisId()))
                || (request.getVariantId() != null
                && !Objects.equals(request.getVariantId(), assignment.getVariantId()))) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "实验假设、变体与分配记录不一致。");
        }
        return new AttributionBinding(
                assignment.getHypothesisId(), assignment.getVariantId(), assignment.getId());
    }

    private void assertSameAttribution(
            CareerEvidenceUsage existing, AttributionBinding requested) {
        if (!Objects.equals(existing.getHypothesisId(), requested.hypothesisId())
                || !Objects.equals(existing.getVariantId(), requested.variantId())
                || !Objects.equals(existing.getAssignmentId(), requested.assignmentId())) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "同一证据使用事实不能绑定不同的实验分配。");
        }
    }

    private ResultSnapshotInput commandInput(
            CareerEvidenceUsageResultSnapshot current,
            CareerEvidenceUsageResultCommandDTO request,
            String command) {
        List<String> limits = request.getLimits() != null
                ? normalizeList(request.getLimits()) : readList(current.getLimitsJson());
        if ("VOID".equals(command) && StringUtils.hasText(request.getReason())) {
            limits = new ArrayList<>(limits);
            limits.add("结果已作废：" + request.getReason().trim());
        }
        return new ResultSnapshotInput(
                StringUtils.hasText(request.getOutcomeCode())
                        ? normalizeOutcome(request.getOutcomeCode()) : current.getOutcomeCode(),
                request.getKnownFacts() != null
                        ? normalizeList(request.getKnownFacts()) : readList(current.getKnownFactsJson()),
                request.getExternalFeedbackText() == null
                        ? current.getExternalFeedbackText() : trim(request.getExternalFeedbackText()),
                request.getUserInterpretationText() == null
                        ? current.getUserInterpretationText() : trim(request.getUserInterpretationText()),
                request.getUnknowns() != null
                        ? normalizeList(request.getUnknowns()) : readList(current.getUnknownsJson()),
                limits,
                current.getSourceType(),
                current.getSourceId(),
                current.getSourceVersion(),
                current.getSourceHash(),
                request.getOccurredAt() == null ? current.getOccurredAt() : request.getOccurredAt(),
                "CONFIRM".equals(command) ? LocalDateTime.now(ZoneOffset.UTC)
                        : current.getConfirmedAt());
    }

    private Map<String, Object> resultSnapshotPayload(
            ResultSnapshotInput input, String command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", command);
        payload.put("outcomeCode", input.outcomeCode());
        payload.put("knownFacts", input.knownFacts());
        payload.put("externalFeedbackText", input.externalFeedbackText());
        payload.put("userInterpretationText", input.userInterpretationText());
        payload.put("unknowns", input.unknowns());
        payload.put("limits", input.limits());
        payload.put("sourceType", input.sourceType());
        payload.put("sourceId", input.sourceId());
        payload.put("sourceVersion", input.sourceVersion());
        payload.put("sourceHash", input.sourceHash());
        payload.put("occurredAt", input.occurredAt());
        payload.put("confirmedAt", input.confirmedAt());
        return payload;
    }

    private String resultContentHash(String status, ResultSnapshotInput input) {
        return hash(Map.of(
                "status", status,
                "input", input));
    }

    private String resultIdempotencyKeyHash(
            Long userId, Long resultId, String command, String key) {
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "幂等键不能为空。");
        }
        return hash(Map.of(
                "userId", userId,
                "resultId", resultId,
                "command", command,
                "key", key.trim()));
    }

    private EvidenceAssetOverviewVO.ReadinessItem readiness(
            String type, String label, long total, long versioned,
            long used, long results, String path) {
        EvidenceAssetOverviewVO.ReadinessItem item =
                new EvidenceAssetOverviewVO.ReadinessItem();
        item.setAssetType(type);
        item.setLabel(label);
        item.setTotalCount(total);
        item.setVersionedCount(versioned);
        item.setUsedCount(used);
        item.setResultCount(results);
        item.setReadinessStatus(total == 0 ? "MISSING"
                : versioned == 0 ? "PARTIAL" : "READY");
        item.setReadinessReason(total == 0
                ? "尚未读取到可追踪资产。"
                : versioned == 0 ? "已有资产，但尚无可回读版本。"
                : "已有可回读版本，可继续记录实际使用。");
        item.setActionPath(path);
        return item;
    }

    private long countUsage(
            Long userId, String assetType, Long campaignId, Long applicationId) {
        return usageMapper.selectCount(usageBase(userId)
                .eq(campaignId != null, CareerEvidenceUsage::getCampaignId, campaignId)
                .eq(applicationId != null, CareerEvidenceUsage::getApplicationId, applicationId)
                .eq(StringUtils.hasText(assetType),
                        CareerEvidenceUsage::getAssetType, assetType));
    }

    private void applyResultSnapshotFilters(
            LambdaQueryWrapper<CareerEvidenceUsageResult> wrapper,
            Long userId,
            String outcomeCode,
            String status,
            LocalDateTime cutoff) {
        boolean hasOutcome = StringUtils.hasText(outcomeCode);
        boolean hasStatus = StringUtils.hasText(status);
        if (!hasOutcome && !hasStatus) {
            return;
        }
        if (cutoff == null) {
            if (!hasOutcome) {
                return;
            }
            wrapper.apply("""
                    EXISTS (
                        SELECT 1
                          FROM career_evidence_usage_result_snapshot s
                         WHERE s.id = career_evidence_usage_result.current_snapshot_id
                           AND s.result_id = career_evidence_usage_result.id
                           AND s.user_id = {0}
                           AND s.outcome_code = {1}
                    )
                    """, userId, outcomeCode);
            return;
        }
        if (hasOutcome && hasStatus) {
            wrapper.apply("""
                    EXISTS (
                        SELECT 1
                          FROM career_evidence_usage_result_snapshot s
                         WHERE s.id = (
                               SELECT s2.id
                                 FROM career_evidence_usage_result_snapshot s2
                                WHERE s2.result_id = career_evidence_usage_result.id
                                  AND s2.user_id = {0}
                                  AND s2.created_at <= {1}
                                ORDER BY s2.snapshot_version DESC, s2.id DESC
                                LIMIT 1
                         )
                           AND s.result_id = career_evidence_usage_result.id
                           AND s.user_id = {0}
                           AND s.outcome_code = {2}
                           AND s.status = {3}
                    )
                    """, userId, cutoff, outcomeCode, status);
        } else if (hasOutcome) {
            wrapper.apply("""
                    EXISTS (
                        SELECT 1
                          FROM career_evidence_usage_result_snapshot s
                         WHERE s.id = (
                               SELECT s2.id
                                 FROM career_evidence_usage_result_snapshot s2
                                WHERE s2.result_id = career_evidence_usage_result.id
                                  AND s2.user_id = {0}
                                  AND s2.created_at <= {1}
                                ORDER BY s2.snapshot_version DESC, s2.id DESC
                                LIMIT 1
                         )
                           AND s.result_id = career_evidence_usage_result.id
                           AND s.user_id = {0}
                           AND s.outcome_code = {2}
                    )
                    """, userId, cutoff, outcomeCode);
        } else {
            wrapper.apply("""
                    EXISTS (
                        SELECT 1
                          FROM career_evidence_usage_result_snapshot s
                         WHERE s.id = (
                               SELECT s2.id
                                 FROM career_evidence_usage_result_snapshot s2
                                WHERE s2.result_id = career_evidence_usage_result.id
                                  AND s2.user_id = {0}
                                  AND s2.created_at <= {1}
                                ORDER BY s2.snapshot_version DESC, s2.id DESC
                                LIMIT 1
                         )
                           AND s.result_id = career_evidence_usage_result.id
                           AND s.user_id = {0}
                           AND s.status = {2}
                    )
                    """, userId, cutoff, status);
        }
    }

    private boolean hasUsageScope(CareerEvidenceUsageResultQueryDTO query) {
        return query.getCampaignId() != null
                || query.getTargetJobId() != null
                || query.getExperimentId() != null
                || query.getHypothesisId() != null
                || StringUtils.hasText(query.getAssetType())
                || query.getAssetId() != null
                || query.getPackageSnapshotId() != null;
    }

    private List<Long> scopedUsageIds(
            Long userId, CareerEvidenceUsageResultQueryDTO query) {
        List<Long> experimentHypothesisIds =
                experimentHypothesisIds(userId, query.getExperimentId());
        if (query.getExperimentId() != null && experimentHypothesisIds.isEmpty()) {
            return List.of();
        }
        return usageMapper.selectList(usageBase(userId)
                        .eq(query.getCampaignId() != null,
                                CareerEvidenceUsage::getCampaignId, query.getCampaignId())
                        .eq(query.getApplicationId() != null,
                                CareerEvidenceUsage::getApplicationId, query.getApplicationId())
                        .eq(query.getTargetJobId() != null,
                                CareerEvidenceUsage::getTargetJobId, query.getTargetJobId())
                        .eq(StringUtils.hasText(query.getAssetType()),
                                CareerEvidenceUsage::getAssetType, normalize(query.getAssetType()))
                        .eq(query.getAssetId() != null,
                                CareerEvidenceUsage::getAssetId, query.getAssetId())
                        .eq(query.getPackageSnapshotId() != null,
                                CareerEvidenceUsage::getPackageSnapshotId,
                                query.getPackageSnapshotId())
                        .eq(query.getHypothesisId() != null,
                                CareerEvidenceUsage::getHypothesisId, query.getHypothesisId())
                        .in(query.getExperimentId() != null,
                                CareerEvidenceUsage::getHypothesisId, experimentHypothesisIds)
                        .select(CareerEvidenceUsage::getId))
                .stream()
                .map(CareerEvidenceUsage::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> experimentHypothesisIds(Long userId, Long experimentId) {
        if (experimentId == null) {
            return List.of();
        }
        return hypothesisMapper.selectList(new LambdaQueryWrapper<ExperimentHypothesis>()
                        .eq(ExperimentHypothesis::getUserId, userId)
                        .eq(ExperimentHypothesis::getLegacyExperimentId, experimentId)
                        .eq(ExperimentHypothesis::getDeleted, CommonConstants.NO)
                        .select(ExperimentHypothesis::getId))
                .stream()
                .map(ExperimentHypothesis::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private CareerEvidenceUsageResultQueryDTO resultQuery(
            Long usageId, LocalDateTime dataCutoffAt) {
        CareerEvidenceUsageResultQueryDTO query = new CareerEvidenceUsageResultQueryDTO();
        query.setUsageId(usageId);
        query.setPageSize(100L);
        query.setDataCutoffAt(dataCutoffAt);
        return query;
    }

    private long sanitizePage(Long value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long sanitizePageSize(Long value) {
        return value == null || value < 1 ? 20 : Math.min(value, 500);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean hasValues(List<String> values) {
        return values != null && values.stream().anyMatch(StringUtils::hasText);
    }

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "证据使用快照序列化失败。");
        }
    }

    private String hash(Object value) {
        return ResumeArtifactHashes.sha256(writeCanonicalJson(value));
    }

    /**
     * Serializes with map entries ordered by key so persisted hashes stay stable across JVMs.
     * {@code Map.of(...)} (ImmutableCollections.MapN) iteration order is randomized per JVM by a
     * nanoTime-seeded salt; without this, the same logical payload hashes differently across
     * instances/restarts, breaking the unique-key and idempotency guarantees this service relies on.
     * Kept separate from {@link #writeJson(Object)} because that method also serializes JSON columns
     * whose stored field order must not change.
     */
    private String writeCanonicalJson(Object value) {
        try {
            return canonicalHashMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "证据使用哈希序列化失败。");
        }
    }

    private ObjectMapper canonicalHashMapper() {
        ObjectMapper mapper = canonicalHashMapper;
        if (mapper == null) {
            mapper = objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            canonicalHashMapper = mapper;
        }
        return mapper;
    }

    private void assertSamePayload(String stored, String requested, String label) {
        if (!Objects.equals(stored, requested)) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    label + "的幂等键已用于不同内容。");
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ResultSnapshotInput(
            String outcomeCode,
            List<String> knownFacts,
            String externalFeedbackText,
            String userInterpretationText,
            List<String> unknowns,
            List<String> limits,
            String sourceType,
            Long sourceId,
            String sourceVersion,
            String sourceHash,
            LocalDateTime occurredAt,
            LocalDateTime confirmedAt) {
    }

    private record AttributionBinding(
            Long hypothesisId, Long variantId, Long assignmentId) {

        private static AttributionBinding empty() {
            return new AttributionBinding(null, null, null);
        }
    }
}
