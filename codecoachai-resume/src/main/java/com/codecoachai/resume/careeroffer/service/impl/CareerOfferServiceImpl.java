package com.codecoachai.resume.careeroffer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercampaign.CareerCampaign;
import com.codecoachai.resume.careercampaign.CareerCampaignMapper;
import com.codecoachai.resume.careeroffer.dto.CareerOfferCreateDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionConfirmDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionPreviewDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferTransitionDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferVersionCreateDTO;
import com.codecoachai.resume.careeroffer.entity.CareerOffer;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecision;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecisionItem;
import com.codecoachai.resume.careeroffer.entity.CareerOfferDecisionSnapshot;
import com.codecoachai.resume.careeroffer.entity.CareerOfferEvent;
import com.codecoachai.resume.careeroffer.entity.CareerOfferVersion;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionItemMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionSnapshotMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferEventMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferVersionMapper;
import com.codecoachai.resume.careeroffer.service.CareerOfferApplicationLifecyclePort;
import com.codecoachai.resume.careeroffer.service.CareerOfferCampaignClosurePort;
import com.codecoachai.resume.careeroffer.service.CareerOfferReminderProvider;
import com.codecoachai.resume.careeroffer.service.CareerOfferService;
import com.codecoachai.resume.careeroffer.service.impl.CareerOfferComparator.Comparison;
import com.codecoachai.resume.careeroffer.service.impl.CareerOfferComparator.Item;
import com.codecoachai.resume.careeroffer.vo.CareerOfferDecisionVO;
import com.codecoachai.resume.careeroffer.vo.CareerOfferReminderCandidateVO;
import com.codecoachai.resume.careeroffer.vo.CareerOfferVO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerOfferServiceImpl implements CareerOfferService, CareerOfferReminderProvider {

    private static final Set<String> FINAL_STATUSES = Set.of("ACCEPTED", "DECLINED", "EXPIRED", "WITHDRAWN");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("RECEIVED", "WITHDRAWN"),
            "RECEIVED", Set.of("NEGOTIATING", "ACCEPTED", "DECLINED", "EXPIRED", "WITHDRAWN"),
            "NEGOTIATING", Set.of("ACCEPTED", "DECLINED", "EXPIRED", "WITHDRAWN"));
    private static final int MAX_REMINDERS = 1000;

    private final CareerOfferMapper offerMapper;
    private final CareerOfferVersionMapper versionMapper;
    private final CareerOfferEventMapper eventMapper;
    private final CareerOfferDecisionMapper decisionMapper;
    private final CareerOfferDecisionSnapshotMapper snapshotMapper;
    private final CareerOfferDecisionItemMapper itemMapper;
    private final JobApplicationMapper applicationMapper;
    private final CareerCampaignMapper campaignMapper;
    private final CareerOfferApplicationLifecyclePort lifecyclePort;
    private final CareerOfferCampaignClosurePort campaignClosurePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<CareerOfferVO> listByApplication(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        CareerOffer offer = offerMapper.selectByApplication(applicationId, userId);
        return offer == null ? List.of() : List.of(toView(offer));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerOfferVO create(Long applicationId, CareerOfferCreateDTO request, String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        requireKey(idempotencyKey);
        JobApplication application = ownedApplication(userId, applicationId);
        if (request != null && request.getApplicationId() != null
                && !applicationId.equals(request.getApplicationId())) {
            throw parameter("Application path and payload do not match");
        }
        String payloadHash = payloadHash(Map.of("applicationId", applicationId));
        String keyHash = keyHash(userId, "CREATE_OFFER", applicationId, idempotencyKey);
        CareerOffer replay = offerMapper.selectByIdempotency(userId, keyHash);
        if (replay != null) {
            requireSamePayload(replay.getPayloadHash(), payloadHash);
            return toView(replay);
        }
        CareerOffer existing = offerMapper.selectByApplication(applicationId, userId);
        if (existing != null) {
            throw parameter("Application already has an Offer root");
        }
        CareerOffer offer = new CareerOffer();
        offer.setUserId(userId);
        offer.setApplicationId(applicationId);
        offer.setStatus("DRAFT");
        offer.setLockVersion(1);
        offer.setNextVersionNo(1);
        offer.setIdempotencyKeyHash(keyHash);
        offer.setPayloadHash(payloadHash);
        offerMapper.insert(offer);
        appendEvent(offer, null, "CREATED", null, "DRAFT", "Offer created",
                keyHash(userId, "OFFER_CREATED", offer.getId(), idempotencyKey), payloadHash);
        return toView(offer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerOfferVO createVersion(Long offerId, CareerOfferVersionCreateDTO request, String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        requireKey(idempotencyKey);
        CareerOffer offer = ownedOffer(userId, offerId);
        if (FINAL_STATUSES.contains(offer.getStatus())) {
            throw parameter("Final Offer cannot receive a new version");
        }
        validateVersion(request);
        String payloadHash = payloadHash(request);
        String keyHash = keyHash(userId, "CREATE_OFFER_VERSION", offerId, idempotencyKey);
        CareerOfferVersion replay = versionMapper.selectByIdempotency(offerId, userId, keyHash);
        if (replay != null) {
            requireSamePayload(replay.getPayloadHash(), payloadHash);
            return toView(ownedOffer(userId, offerId));
        }
        int expectedLock = defaultVersion(offer.getLockVersion());
        int versionNo = offer.getNextVersionNo() == null ? 1 : offer.getNextVersionNo();
        if (offerMapper.claimNextVersion(offerId, userId, expectedLock) != 1) {
            throw optimisticConflict();
        }
        JobApplication application = ownedApplication(userId, offer.getApplicationId());
        CareerOfferVersion version = version(request, offer, application.getCampaignId(), versionNo, keyHash, payloadHash);
        versionMapper.insert(version);
        if (offerMapper.attachVersion(offerId, userId, version.getId(), version.getDecisionDeadline(),
                expectedLock + 1) != 1) {
            throw optimisticConflict();
        }
        CareerOffer updated = ownedOffer(userId, offerId);
        appendEvent(updated, version.getId(), "VERSION_CREATED", updated.getStatus(), updated.getStatus(),
                "Offer version " + versionNo + " created",
                keyHash(userId, "OFFER_VERSION_EVENT", offerId, idempotencyKey), payloadHash);
        return toView(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerOfferVO transition(Long offerId, CareerOfferTransitionDTO request, String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        requireKey(idempotencyKey);
        if (request == null || !StringUtils.hasText(request.getTargetStatus())) {
            throw parameter("Target status is required");
        }
        return transitionForUser(userId, offerId, request, idempotencyKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerOfferDecisionVO previewDecision(Long campaignId, CareerOfferDecisionPreviewDTO request,
                                                 String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        requireKey(idempotencyKey);
        ownedCampaign(userId, campaignId);
        CareerOfferDecisionPreviewDTO actual = request == null ? new CareerOfferDecisionPreviewDTO() : request;
        String payloadHash = payloadHash(actual);
        String keyHash = keyHash(userId, "PREVIEW_OFFER_DECISION", campaignId, idempotencyKey);
        CareerOfferDecision replay = decisionMapper.selectByIdempotency(campaignId, userId, keyHash);
        if (replay != null) {
            requireSamePayload(replay.getPayloadHash(), payloadHash);
            return decisionView(replay);
        }
        List<CareerOffer> offers = offerMapper.selectByCampaign(campaignId, userId);
        if (offers.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign has no Offers");
        }
        List<CareerOfferVersion> versions = new ArrayList<>();
        for (CareerOffer offer : offers) {
            if (offer.getCurrentVersionId() != null) {
                CareerOfferVersion version = versionMapper.selectById(offer.getCurrentVersionId());
                if (version != null && userId.equals(version.getUserId()) && offer.getId().equals(version.getOfferId())) {
                    versions.add(version);
                }
            }
        }
        if (versions.isEmpty()) {
            throw parameter("Campaign Offers do not have terms versions");
        }
        Comparison comparison = CareerOfferComparator.compare(versions, actual);
        CareerOfferDecision decision = new CareerOfferDecision();
        decision.setUserId(userId);
        decision.setCampaignId(campaignId);
        decision.setStatus("PREVIEWED");
        decision.setLockVersion(1);
        decision.setIdempotencyKeyHash(keyHash);
        decision.setPayloadHash(payloadHash);
        decisionMapper.insert(decision);

        CareerOfferDecisionSnapshot snapshot = snapshot(userId, campaignId, decision.getId(), comparison, actual);
        snapshotMapper.insert(snapshot);
        List<CareerOfferDecisionItem> items = decisionItems(userId, snapshot.getId(), comparison);
        items.forEach(itemMapper::insert);
        decision.setCurrentSnapshotId(snapshot.getId());
        decisionMapper.updateById(decision);
        return decisionView(decision);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerOfferDecisionVO confirmDecision(Long campaignId, Long decisionId,
                                                 CareerOfferDecisionConfirmDTO request,
                                                 String idempotencyKey) {
        Long userId = SecurityAssert.requireLoginUserId();
        requireKey(idempotencyKey);
        if (request == null || !Boolean.TRUE.equals(request.getUserConfirmed())) {
            throw parameter("Explicit user confirmation is required");
        }
        CareerOfferDecision decision = ownedDecision(userId, decisionId);
        if (!campaignId.equals(decision.getCampaignId())) {
            throw parameter("Decision does not belong to campaign");
        }
        String payloadHash = payloadHash(request);
        String keyHash = keyHash(userId, "CONFIRM_OFFER_DECISION", decisionId, idempotencyKey);
        CareerOffer selected = ownedOffer(userId, request.getSelectedOfferId());
        JobApplication application = ownedApplication(userId, selected.getApplicationId());
        if (!decision.getCampaignId().equals(application.getCampaignId())) {
            throw parameter("Selected Offer does not belong to the decision campaign");
        }
        CareerOfferEvent replay = eventMapper.selectByIdempotency(selected.getId(), userId, keyHash);
        if (replay != null) {
            requireSamePayload(replay.getPayloadHash(), payloadHash);
            return decisionView(ownedDecision(userId, decisionId));
        }
        if (!"PREVIEWED".equals(decision.getStatus())) {
            throw parameter("Decision is already final");
        }
        CareerOfferTransitionDTO transition = new CareerOfferTransitionDTO();
        transition.setTargetStatus("ACCEPTED");
        transition.setExpectedLockVersion(selected.getLockVersion());
        transition.setApplicationLockVersion(request.getApplicationLockVersion());
        transition.setUserConfirmed(true);
        transitionForUser(userId, selected.getId(), transition, "decision:" + idempotencyKey);
        int expectedLock = request.getExpectedLockVersion() == null
                ? defaultVersion(decision.getLockVersion()) : request.getExpectedLockVersion();
        if (decisionMapper.confirm(decisionId, userId, selected.getId(), "ACCEPTED", expectedLock) != 1) {
            throw optimisticConflict();
        }
        appendEvent(ownedOffer(userId, selected.getId()), selected.getCurrentVersionId(), "DECISION_CONFIRMED",
                "ACCEPTED", "ACCEPTED", "Offer decision confirmed", keyHash, payloadHash);
        if (Boolean.TRUE.equals(request.getCloseCampaign())) {
            campaignClosurePort.close(userId, decision.getCampaignId(),
                    Boolean.TRUE.equals(request.getRetainOpenApplications()));
        }
        return decisionView(ownedDecision(userId, decisionId));
    }

    @Override
    public List<CareerOfferReminderCandidateVO> deadlineReminderCandidates(LocalDate day, int limit) {
        return deadlineReminderCandidatesForUser(SecurityAssert.requireLoginUserId(), day, limit);
    }

    @Override
    public List<CareerOfferReminderCandidateVO> deadlineReminderCandidatesForUser(
            Long userId, LocalDate day, int limit) {
        if (userId == null) {
            throw parameter("userId is required");
        }
        LocalDate actualDay = day == null ? LocalDate.now(ZoneOffset.UTC) : day;
        int actualLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, MAX_REMINDERS));
        LocalDateTime from = actualDay.atStartOfDay();
        LocalDateTime to = actualDay.atTime(LocalTime.MAX).plusNanos(1);
        return offerMapper.selectReminderCandidates(userId, from, to, actualLimit).stream()
                .map(offer -> reminder(offer, actualDay)).toList();
    }

    @Override
    public CareerOfferDecisionVO decision(Long decisionId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return decisionView(ownedDecision(userId, decisionId));
    }

    private CareerOfferVO transitionForUser(Long userId, Long offerId, CareerOfferTransitionDTO request,
                                            String idempotencyKey) {
        CareerOffer offer = ownedOffer(userId, offerId);
        String next = normalizeStatus(request.getTargetStatus());
        String payloadHash = payloadHash(request);
        String keyHash = keyHash(userId, "TRANSITION_OFFER", offerId, idempotencyKey);
        CareerOfferEvent replay = eventMapper.selectByIdempotency(offerId, userId, keyHash);
        if (replay != null) {
            requireSamePayload(replay.getPayloadHash(), payloadHash);
            return toView(offer);
        }
        String current = normalizeStatus(offer.getStatus());
        if (current.equals(next)) {
            return toView(offer);
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw parameter("Offer status cannot transition from " + current + " to " + next);
        }
        if (FINAL_STATUSES.contains(next) && !Boolean.TRUE.equals(request.getUserConfirmed())) {
            throw parameter("Final Offer transition requires explicit user confirmation");
        }
        int expectedLock = request.getExpectedLockVersion() == null
                ? defaultVersion(offer.getLockVersion()) : request.getExpectedLockVersion();
        if (offerMapper.transition(offerId, userId, current, next, expectedLock) != 1) {
            throw optimisticConflict();
        }
        if (FINAL_STATUSES.contains(next)) {
            lifecyclePort.synchronizeFinalOutcome(userId, offer.getApplicationId(), next,
                    request.getApplicationLockVersion(), "offer:" + keyHash);
        }
        CareerOffer updated = ownedOffer(userId, offerId);
        appendEvent(updated, updated.getCurrentVersionId(), "STATUS_CHANGED", current, next,
                current + " -> " + next, keyHash, payloadHash);
        return toView(updated);
    }

    private CareerOfferDecisionSnapshot snapshot(Long userId, Long campaignId, Long decisionId,
                                                 Comparison comparison, CareerOfferDecisionPreviewDTO request) {
        CareerOfferDecisionSnapshot snapshot = new CareerOfferDecisionSnapshot();
        snapshot.setUserId(userId);
        snapshot.setDecisionId(decisionId);
        snapshot.setCampaignId(campaignId);
        snapshot.setSnapshotNo(1);
        snapshot.setComparisonCurrency(comparison.comparisonCurrency());
        snapshot.setComparable(comparison.comparable() ? 1 : 0);
        snapshot.setWeightsJson(json(comparison.weights()));
        snapshot.setRuleResultJson(json(Map.of(
                "algorithm", "weighted-max-normalization-v1",
                "itemCount", comparison.items().size())));
        snapshot.setMissingItemsJson(json(comparison.items().stream()
                .collect(LinkedHashMap::new,
                        (map, item) -> map.put(String.valueOf(item.version().getOfferId()), item.missingItems()),
                        Map::putAll)));
        snapshot.setLimitationsJson(json(comparison.limitations()));
        snapshot.setExchangeRatesJson(json(request.getExchangeRates() == null ? Map.of() : request.getExchangeRates()));
        snapshot.setExchangeRateSource(trim(request.getExchangeRateSource(), 255));
        snapshot.setExchangeRateDate(request.getExchangeRateDate() == null
                ? null : request.getExchangeRateDate().atStartOfDay());
        snapshot.setFallback(1);
        snapshot.setFallbackReason("RULE_ONLY");
        return snapshot;
    }

    private List<CareerOfferDecisionItem> decisionItems(Long userId, Long snapshotId, Comparison comparison) {
        List<Item> sorted = comparison.items();
        List<CareerOfferDecisionItem> result = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            Item source = sorted.get(index);
            CareerOfferDecisionItem item = new CareerOfferDecisionItem();
            item.setUserId(userId);
            item.setSnapshotId(snapshotId);
            item.setOfferId(source.version().getOfferId());
            item.setOfferVersionId(source.version().getId());
            item.setComparableAnnualValue(source.annualValue());
            item.setWeightedScore(source.score());
            item.setRankNo(source.score() == null ? null : index + 1);
            item.setRuleResultJson(json(Map.of(
                    "currency", comparison.comparisonCurrency(),
                    "algorithm", "weighted-max-normalization-v1")));
            item.setMissingItemsJson(json(source.missingItems()));
            result.add(item);
        }
        return result;
    }

    private CareerOfferVersion version(CareerOfferVersionCreateDTO request, CareerOffer offer, Long campaignId,
                                       int versionNo, String keyHash, String payloadHash) {
        CareerOfferVersion version = new CareerOfferVersion();
        version.setUserId(offer.getUserId());
        version.setOfferId(offer.getId());
        version.setVersionNo(versionNo);
        version.setCampaignIdAtCreation(campaignId);
        version.setCurrency(CareerOfferComparator.normalizeCurrency(request.getCurrency()));
        version.setAnnualBaseSalary(request.getAnnualBaseSalary());
        version.setAnnualBonus(request.getAnnualBonus());
        version.setSignOnBonus(request.getSignOnBonus());
        version.setAnnualEquityValue(request.getAnnualEquityValue());
        version.setOtherAnnualCompensation(request.getOtherAnnualCompensation());
        version.setPaidLeaveDays(request.getPaidLeaveDays());
        version.setLocation(trim(request.getLocation(), 255));
        version.setWorkMode(normalizeNullable(request.getWorkMode()));
        version.setStartDate(request.getStartDate());
        version.setDecisionDeadline(request.getDecisionDeadline());
        version.setTermsJson(trim(request.getTermsJson(), 20000));
        version.setNote(trim(request.getNote(), 2000));
        version.setIdempotencyKeyHash(keyHash);
        version.setPayloadHash(payloadHash);
        return version;
    }

    private void appendEvent(CareerOffer offer, Long versionId, String type, String previousStatus,
                             String currentStatus, String summary, String keyHash, String payloadHash) {
        CareerOfferEvent event = new CareerOfferEvent();
        event.setUserId(offer.getUserId());
        event.setOfferId(offer.getId());
        event.setVersionId(versionId);
        event.setEventType(type);
        event.setPreviousStatus(previousStatus);
        event.setCurrentStatus(currentStatus);
        event.setOccurredAt(LocalDateTime.now());
        event.setSummary(summary);
        event.setIdempotencyKeyHash(keyHash);
        event.setPayloadHash(payloadHash);
        eventMapper.insert(event);
    }

    private CareerOfferVO toView(CareerOffer offer) {
        CareerOfferVO view = new CareerOfferVO();
        view.setId(offer.getId());
        view.setApplicationId(offer.getApplicationId());
        view.setCurrentVersionId(offer.getCurrentVersionId());
        view.setStatus(offer.getStatus());
        view.setLockVersion(offer.getLockVersion());
        view.setDecisionDeadline(offer.getDecisionDeadline());
        view.setFinalizedAt(offer.getFinalizedAt());
        if (offer.getCurrentVersionId() != null) {
            CareerOfferVersion version = versionMapper.selectById(offer.getCurrentVersionId());
            if (version != null && offer.getUserId().equals(version.getUserId())
                    && offer.getId().equals(version.getOfferId())) {
                view.setCurrentVersion(version);
            }
        }
        return view;
    }

    private CareerOfferDecisionVO decisionView(CareerOfferDecision decision) {
        CareerOfferDecisionVO view = new CareerOfferDecisionVO();
        view.setId(decision.getId());
        view.setCampaignId(decision.getCampaignId());
        view.setStatus(decision.getStatus());
        view.setSelectedOfferId(decision.getSelectedOfferId());
        view.setOutcome(decision.getOutcome());
        view.setLockVersion(decision.getLockVersion());
        if (decision.getCurrentSnapshotId() != null) {
            view.setSnapshot(snapshotMapper.selectOwned(decision.getCurrentSnapshotId(), decision.getUserId()));
            view.setItems(itemMapper.selectBySnapshot(decision.getCurrentSnapshotId(), decision.getUserId()));
        }
        return view;
    }

    private CareerOfferReminderCandidateVO reminder(CareerOffer offer, LocalDate day) {
        CareerOfferReminderCandidateVO candidate = new CareerOfferReminderCandidateVO();
        candidate.setOfferId(offer.getId());
        candidate.setUserId(offer.getUserId());
        candidate.setApplicationId(offer.getApplicationId());
        candidate.setDecisionDeadline(offer.getDecisionDeadline());
        candidate.setReminderDate(day);
        candidate.setBizType("CAREER_OFFER");
        candidate.setBizId(String.valueOf(offer.getId()));
        candidate.setIdempotencyKey("CAREER_OFFER:" + offer.getId() + ":" + day);
        return candidate;
    }

    private CareerOffer ownedOffer(Long userId, Long offerId) {
        CareerOffer offer = offerMapper.selectOwned(offerId, userId);
        if (offer == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Offer not found");
        }
        return offer;
    }

    private CareerOfferDecision ownedDecision(Long userId, Long decisionId) {
        CareerOfferDecision decision = decisionMapper.selectOwned(decisionId, userId);
        if (decision == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Offer decision not found");
        }
        return decision;
    }

    private JobApplication ownedApplication(Long userId, Long applicationId) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found");
        }
        return application;
    }

    private CareerCampaign ownedCampaign(Long userId, Long campaignId) {
        CareerCampaign campaign = campaignMapper.selectOne(new LambdaQueryWrapper<CareerCampaign>()
                .eq(CareerCampaign::getId, campaignId)
                .eq(CareerCampaign::getUserId, userId)
                .eq(CareerCampaign::getDeleted, CommonConstants.NO));
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign not found");
        }
        return campaign;
    }

    private void validateVersion(CareerOfferVersionCreateDTO request) {
        if (request == null) {
            throw parameter("Offer version payload is required");
        }
        validateMoney(request.getAnnualBaseSalary(), "annualBaseSalary");
        validateMoney(request.getAnnualBonus(), "annualBonus");
        validateMoney(request.getSignOnBonus(), "signOnBonus");
        validateMoney(request.getAnnualEquityValue(), "annualEquityValue");
        validateMoney(request.getOtherAnnualCompensation(), "otherAnnualCompensation");
        boolean hasMoney = request.getAnnualBaseSalary() != null || request.getAnnualBonus() != null
                || request.getSignOnBonus() != null || request.getAnnualEquityValue() != null
                || request.getOtherAnnualCompensation() != null;
        if (hasMoney && CareerOfferComparator.normalizeCurrency(request.getCurrency()) == null) {
            throw parameter("Currency is required when monetary terms are provided");
        }
        if (request.getPaidLeaveDays() != null && request.getPaidLeaveDays() < 0) {
            throw parameter("paidLeaveDays cannot be negative");
        }
    }

    private static void validateMoney(java.math.BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw parameter(field + " cannot be negative");
        }
    }

    private String payloadHash(Object value) {
        return sha256(canonicalJson(value));
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Offer idempotency payload", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Offer snapshot", exception);
        }
    }

    private static String keyHash(Long userId, String operation, Long aggregateId, String key) {
        return sha256(userId + "|" + operation + "|" + aggregateId + "|" + key.trim());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSamePayload(String stored, String incoming) {
        if (!incoming.equals(stored)) {
            throw parameter("Idempotency key was reused with a different payload");
        }
    }

    private static void requireKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw parameter("Idempotency-Key is required");
        }
    }

    private static int defaultVersion(Integer value) {
        return value == null ? 1 : value;
    }

    private static String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static BusinessException parameter(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }

    private static BusinessException optimisticConflict() {
        return parameter("Offer was changed by another request");
    }
}
