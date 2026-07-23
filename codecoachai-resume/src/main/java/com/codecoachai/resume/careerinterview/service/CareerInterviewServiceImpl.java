package com.codecoachai.resume.careerinterview.service;

import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewCalendarLinkDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewProcessCreateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRescheduleDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRoundCreateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRoundUpdateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewTransitionDTO;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewProcess;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRound;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRoundEvent;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewProcessMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundEventMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundMapper;
import com.codecoachai.resume.careerinterview.vo.CareerInterviewProcessVO;
import com.codecoachai.resume.careerinterview.vo.CareerInterviewRoundEventVO;
import com.codecoachai.resume.careerinterview.vo.CareerInterviewRoundVO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.service.support.ResumeGenerationHashUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerInterviewServiceImpl implements CareerInterviewService {

    private static final Set<String> INTERVIEW_EVENT_TYPES = Set.of(
            "INTERVIEW", "INTERVIEW_SCHEDULED", "PHONE_SCREEN", "TECHNICAL_INTERVIEW",
            "HR_INTERVIEW", "FINAL_INTERVIEW");
    private static final Set<String> ROUND_TYPES = Set.of(
            "PHONE_SCREEN", "TECHNICAL", "BEHAVIORAL", "ONSITE", "FINAL", "OTHER");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PLANNED", Set.of("SCHEDULED"),
            "SCHEDULED", Set.of("PREPARING", "CANCELLED", "RESCHEDULED"),
            "PREPARING", Set.of("COMPLETED", "CANCELLED"),
            "RESCHEDULED", Set.of("SCHEDULED"));

    private final CareerInterviewProcessMapper processMapper;
    private final CareerInterviewRoundMapper roundMapper;
    private final CareerInterviewRoundEventMapper eventMapper;
    private final CareerCalendarEventMapper calendarEventMapper;
    private final JobApplicationMapper applicationMapper;
    private final CareerInterviewPreparationReader preparationReader;
    private final ObjectMapper objectMapper;

    @Override
    public CareerInterviewProcessVO getProcess(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        CareerInterviewProcess process = processMapper.selectActiveByApplication(applicationId, userId);
        return process == null ? null : toProcessVO(process, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerInterviewProcessVO createProcess(Long applicationId, CareerInterviewProcessCreateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        CareerInterviewProcess existing = processMapper.selectActiveByApplication(applicationId, userId);
        if (existing != null) {
            return toProcessVO(existing, userId);
        }
        CareerInterviewProcess process = new CareerInterviewProcess();
        process.setUserId(userId);
        process.setApplicationId(applicationId);
        process.setStatus("ACTIVE");
        process.setCurrentRoundNo(0);
        process.setLockVersion(1);
        try {
            processMapper.insert(process);
        } catch (DuplicateKeyException ex) {
            CareerInterviewProcess winner = processMapper.selectActiveByApplication(applicationId, userId);
            if (winner != null) {
                return toProcessVO(winner, userId);
            }
            throw ex;
        }
        return toProcessVO(process, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerInterviewRoundVO createRound(Long processId, CareerInterviewRoundCreateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerInterviewProcess process = ownedProcess(userId, processId);
        Schedule schedule = schedule(request.getScheduledStartsAt(), request.getScheduledEndsAt(), request.getTimezone());
        Map<String, Object> requestPayload = payload(
                "roundType", roundType(request.getRoundType()),
                "title", trim(request.getTitle(), 200),
                "timezone", schedule.timezone(),
                "scheduledStartsAtUtc", schedule.startsAtUtc(),
                "scheduledEndsAtUtc", schedule.endsAtUtc());
        String requestHash = requestHash(requestPayload);
        String keyHash = scopedKeyHash("CREATE_ROUND", processId, request.getIdempotencyKey());
        CareerInterviewRoundEvent replay = eventMapper.selectCreatedByProcessIdempotency(processId, userId, keyHash);
        if (replay != null) {
            assertSameRequest(replay, requestHash);
            return toRoundVO(ownedRound(userId, replay.getRoundId()), userId);
        }
        int expectedVersion = version(process.getLockVersion());
        if (processMapper.claimNextRound(processId, userId, expectedVersion) != 1) {
            replay = eventMapper.selectCreatedByProcessIdempotency(processId, userId, keyHash);
            if (replay != null) {
                assertSameRequest(replay, requestHash);
                return toRoundVO(ownedRound(userId, replay.getRoundId()), userId);
            }
            throw concurrent();
        }
        CareerInterviewProcess claimed = ownedProcess(userId, processId);
        CareerInterviewRound round = new CareerInterviewRound();
        round.setProcessId(processId);
        round.setRoundNo(claimed.getCurrentRoundNo());
        round.setRoundType(roundType(request.getRoundType()));
        round.setTitle(required(request.getTitle(), "面试轮次标题不能为空", 200));
        round.setTimezone(schedule.timezone());
        round.setScheduledStartsAtUtc(schedule.startsAtUtc());
        round.setScheduledEndsAtUtc(schedule.endsAtUtc());
        round.setStatus("PLANNED");
        round.setLockVersion(1);
        roundMapper.insert(round);
        appendEvent(userId, round, "ROUND_CREATED", keyHash, requestHash, null, "PLANNED",
                payload("roundNo", round.getRoundNo(), "roundType", round.getRoundType(),
                        "title", round.getTitle(), "timezone", round.getTimezone(),
                        "scheduledStartsAtUtc", round.getScheduledStartsAtUtc(),
                        "scheduledEndsAtUtc", round.getScheduledEndsAtUtc()));
        return toRoundVO(round, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerInterviewRoundVO updateRound(Long roundId, CareerInterviewRoundUpdateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerInterviewRound round = ownedRound(userId, roundId);
        String requestHash = requestHash(payload(
                "title", trim(request.getTitle(), 200),
                "resultSummary", trim(request.getResultSummary(), 2000),
                "nextStep", trim(request.getNextStep(), 1000)));
        String keyHash = scopedKeyHash("UPDATE_ROUND", roundId, request.getIdempotencyKey());
        if (replayed(roundId, userId, keyHash, requestHash)) {
            return toRoundVO(round, userId);
        }
        if (roundMapper.updateDetails(roundId, required(request.getTitle(), "面试轮次标题不能为空", 200),
                trim(request.getResultSummary(), 2000), trim(request.getNextStep(), 1000),
                request.getExpectedLockVersion()) != 1) {
            throw concurrent();
        }
        CareerInterviewRound updated = ownedRound(userId, roundId);
        appendEvent(userId, updated, "ROUND_UPDATED", keyHash, requestHash, null, null,
                payload("title", updated.getTitle(), "resultSummary", updated.getResultSummary(),
                        "nextStep", updated.getNextStep()));
        return toRoundVO(updated, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerInterviewRoundVO transition(Long roundId, CareerInterviewTransitionDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerInterviewRound round = ownedRound(userId, roundId);
        String target = code(request.getTargetStatus());
        String requestHash = requestHash(payload("targetStatus", target));
        String keyHash = scopedKeyHash("TRANSITION_ROUND", roundId, request.getIdempotencyKey());
        if (replayed(roundId, userId, keyHash, requestHash)) {
            return toRoundVO(round, userId);
        }
        String current = code(round.getStatus());
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "面试轮次不能从 " + current + " 变更为 " + target);
        }
        if (roundMapper.transition(roundId, current, target, request.getExpectedLockVersion()) != 1) {
            throw concurrent();
        }
        CareerInterviewRound updated = ownedRound(userId, roundId);
        appendEvent(userId, updated, "STATUS_TRANSITIONED", keyHash, requestHash, current, target,
                payload("previousStatus", current, "currentStatus", target));
        return toRoundVO(updated, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerInterviewRoundVO reschedule(Long roundId, CareerInterviewRescheduleDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerInterviewRound round = ownedRound(userId, roundId);
        Schedule next = schedule(request.getScheduledStartsAt(), request.getScheduledEndsAt(), request.getTimezone());
        String reason = trim(request.getReason(), 1000);
        String requestHash = requestHash(payload(
                "timezone", next.timezone(),
                "scheduledStartsAtUtc", next.startsAtUtc(),
                "scheduledEndsAtUtc", next.endsAtUtc(),
                "calendarEventId", request.getCalendarEventId(),
                "reason", reason));
        String keyHash = scopedKeyHash("RESCHEDULE_ROUND", roundId, request.getIdempotencyKey());
        if (replayed(roundId, userId, keyHash, requestHash)) {
            return toRoundVO(round, userId);
        }
        if (!"SCHEDULED".equals(code(round.getStatus()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只有已排期轮次可以改期");
        }
        CareerCalendarEvent calendar = request.getCalendarEventId() == null
                ? null : validCalendarForRound(userId, round, request.getCalendarEventId());
        String preparationHash = calendar == null ? null : usablePreparation(calendar).sourceHash();
        ensureCalendarUnique(userId, roundId, request.getCalendarEventId());
        if (roundMapper.reschedule(roundId, next.timezone(), next.startsAtUtc(), next.endsAtUtc(),
                request.getCalendarEventId(), preparationHash, request.getExpectedLockVersion()) != 1) {
            throw concurrent();
        }
        CareerInterviewRound updated = ownedRound(userId, roundId);
        appendEvent(userId, updated, "RESCHEDULED", keyHash, requestHash, "SCHEDULED", "RESCHEDULED", payload(
                "previousStartsAtUtc", round.getScheduledStartsAtUtc(),
                "previousEndsAtUtc", round.getScheduledEndsAtUtc(),
                "previousCalendarEventId", round.getCalendarEventId(),
                "newStartsAtUtc", next.startsAtUtc(),
                "newEndsAtUtc", next.endsAtUtc(),
                "newCalendarEventId", request.getCalendarEventId(),
                "reason", reason));
        return toRoundVO(updated, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerInterviewRoundVO linkCalendarEvent(Long roundId, CareerInterviewCalendarLinkDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerInterviewRound round = ownedRound(userId, roundId);
        String requestHash = requestHash(payload("calendarEventId", request.getCalendarEventId()));
        String keyHash = scopedKeyHash("LINK_CALENDAR", roundId, request.getIdempotencyKey());
        if (replayed(roundId, userId, keyHash, requestHash)) {
            return toRoundVO(round, userId);
        }
        CareerCalendarEvent calendar = validCalendarForRound(userId, round, request.getCalendarEventId());
        ensureCalendarUnique(userId, roundId, calendar.getId());
        CareerInterviewPreparationReader.PreparationSnapshot preparation = usablePreparation(calendar);
        if (roundMapper.linkCalendar(roundId, calendar.getId(), preparation.sourceHash(),
                request.getExpectedLockVersion()) != 1) {
            throw concurrent();
        }
        CareerInterviewRound updated = ownedRound(userId, roundId);
        appendEvent(userId, updated, "CALENDAR_LINKED", keyHash, requestHash, null, null,
                payload("calendarEventId", calendar.getId(),
                        "preparationSourceHash", preparation.sourceHash()));
        return toRoundVO(updated, userId);
    }

    private CareerCalendarEvent validCalendarForRound(Long userId, CareerInterviewRound round, Long eventId) {
        CareerCalendarEvent event = calendarEventMapper.selectById(eventId);
        if (event == null || !userId.equals(event.getUserId())
                || Integer.valueOf(CommonConstants.YES).equals(event.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "日历事件不存在");
        }
        if (!INTERVIEW_EVENT_TYPES.contains(code(event.getEventType()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能关联真实面试类型日历事件");
        }
        CareerInterviewProcess process = ownedProcess(userId, round.getProcessId());
        if (event.getApplicationId() == null || !event.getApplicationId().equals(process.getApplicationId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日历事件必须属于当前机会");
        }
        return event;
    }

    private void ensureCalendarUnique(Long userId, Long roundId, Long calendarEventId) {
        if (calendarEventId == null) {
            return;
        }
        CareerInterviewRound conflict = roundMapper.selectActiveByCalendarEvent(calendarEventId, userId);
        if (conflict != null && !roundId.equals(conflict.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该日历事件已关联其他有效面试轮次");
        }
    }

    private CareerInterviewPreparationReader.PreparationSnapshot usablePreparation(CareerCalendarEvent event) {
        CareerInterviewPreparationReader.PreparationSnapshot preparation = preparationReader.read(event);
        if (!preparation.usable()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试准备包不存在或已 stale");
        }
        return preparation;
    }

    private JobApplication ownedApplication(Long userId, Long applicationId) {
        JobApplication application = applicationMapper.selectById(applicationId);
        if (application == null || !userId.equals(application.getUserId())
                || Integer.valueOf(CommonConstants.YES).equals(application.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "机会不存在");
        }
        return application;
    }

    private CareerInterviewProcess ownedProcess(Long userId, Long processId) {
        CareerInterviewProcess process = processMapper.selectOwned(processId, userId);
        if (process == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试流程不存在");
        }
        return process;
    }

    private CareerInterviewRound ownedRound(Long userId, Long roundId) {
        CareerInterviewRound round = roundMapper.selectOwned(roundId, userId);
        if (round == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "面试轮次不存在");
        }
        return round;
    }

    private boolean replayed(Long roundId, Long userId, String keyHash, String requestHash) {
        CareerInterviewRoundEvent event = eventMapper.selectByIdempotency(roundId, userId, keyHash);
        if (event == null) {
            return false;
        }
        assertSameRequest(event, requestHash);
        return true;
    }

    private void assertSameRequest(CareerInterviewRoundEvent event, String requestHash) {
        try {
            Map<?, ?> stored = objectMapper.readValue(event.getPayloadJson(), Map.class);
            if (requestHash.equals(stored.get("requestHash"))) {
                return;
            }
        } catch (Exception ignored) {
            // Treat malformed legacy payloads as a conflicting replay.
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR,
                "This idempotency key was already used for a different interview request");
    }

    private void appendEvent(Long userId, CareerInterviewRound round, String type,
                             String keyHash, String requestHash,
                             String previousStatus, String currentStatus,
                             Map<String, Object> payload) {
        CareerInterviewRoundEvent event = new CareerInterviewRoundEvent();
        event.setUserId(userId);
        event.setProcessId(round.getProcessId());
        event.setRoundId(round.getId());
        event.setEventType(type);
        event.setPreviousStatus(previousStatus);
        event.setCurrentStatus(currentStatus);
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("requestHash", requestHash);
        eventPayload.putAll(payload);
        event.setPayloadJson(json(eventPayload));
        event.setIdempotencyKeyHash(keyHash);
        event.setOccurredAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private CareerInterviewProcessVO toProcessVO(CareerInterviewProcess process, Long userId) {
        CareerInterviewProcessVO view = new CareerInterviewProcessVO();
        view.setId(process.getId());
        view.setApplicationId(process.getApplicationId());
        view.setStatus(process.getStatus());
        view.setCurrentRoundNo(process.getCurrentRoundNo());
        view.setOutcome(process.getOutcome());
        view.setLockVersion(process.getLockVersion());
        view.setCreatedAt(process.getCreatedAt());
        view.setUpdatedAt(process.getUpdatedAt());
        view.setRounds(roundMapper.selectByProcess(process.getId()).stream()
                .map(round -> toRoundVO(round, userId)).toList());
        return view;
    }

    private CareerInterviewRoundVO toRoundVO(CareerInterviewRound round, Long userId) {
        CareerInterviewRoundVO view = new CareerInterviewRoundVO();
        view.setId(round.getId());
        view.setProcessId(round.getProcessId());
        view.setRoundNo(round.getRoundNo());
        view.setRoundType(round.getRoundType());
        view.setTitle(round.getTitle());
        view.setTimezone(round.getTimezone());
        view.setScheduledStartsAtUtc(round.getScheduledStartsAtUtc());
        view.setScheduledEndsAtUtc(round.getScheduledEndsAtUtc());
        view.setCalendarEventId(round.getCalendarEventId());
        view.setPreparationSourceHash(round.getPreparationSourceHash());
        view.setPreparationStale(preparationStale(userId, round));
        view.setStatus(round.getStatus());
        view.setResultSummary(round.getResultSummary());
        view.setNextStep(round.getNextStep());
        view.setLockVersion(round.getLockVersion());
        view.setCreatedAt(round.getCreatedAt());
        view.setUpdatedAt(round.getUpdatedAt());
        view.setEvents(eventMapper.selectByRound(round.getId(), userId).stream().map(this::toEventVO).toList());
        return view;
    }

    private Boolean preparationStale(Long userId, CareerInterviewRound round) {
        if (round.getCalendarEventId() == null) {
            return false;
        }
        CareerCalendarEvent event = calendarEventMapper.selectById(round.getCalendarEventId());
        if (event == null || !userId.equals(event.getUserId())) {
            return true;
        }
        CareerInterviewPreparationReader.PreparationSnapshot current = preparationReader.read(event);
        return !current.usable() || !current.sourceHash().equals(round.getPreparationSourceHash());
    }

    private CareerInterviewRoundEventVO toEventVO(CareerInterviewRoundEvent event) {
        CareerInterviewRoundEventVO view = new CareerInterviewRoundEventVO();
        view.setId(event.getId());
        view.setRoundId(event.getRoundId());
        view.setEventType(event.getEventType());
        view.setPayloadJson(event.getPayloadJson());
        view.setOccurredAt(event.getOccurredAt());
        return view;
    }

    private Schedule schedule(LocalDateTime starts, LocalDateTime ends, String timezone) {
        if (starts == null && ends == null) {
            return new Schedule(null, null, normalizeTimezone(timezone));
        }
        if (starts == null || ends == null || !StringUtils.hasText(timezone)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "排期时间和时区必须同时提供");
        }
        try {
            ZoneId zone = ZoneId.of(timezone.trim());
            if (!ends.atZone(zone).toInstant().isAfter(starts.atZone(zone).toInstant())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
            }
            return new Schedule(
                    LocalDateTime.ofInstant(starts.atZone(zone).toInstant(), ZoneOffset.UTC),
                    LocalDateTime.ofInstant(ends.atZone(zone).toInstant(), ZoneOffset.UTC),
                    zone.getId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "排期时间或时区格式无效");
        }
    }

    private String normalizeTimezone(String timezone) {
        try {
            return ZoneId.of(StringUtils.hasText(timezone) ? timezone.trim() : "UTC").getId();
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Invalid interview timezone");
        }
    }

    private String roundType(String value) {
        String type = code(value);
        if (!ROUND_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的面试轮次类型");
        }
        return type;
    }

    private String scopedKeyHash(String operation, Long aggregateId, String key) {
        return ResumeGenerationHashUtils.sha256(objectMapper,
                operation + "|" + aggregateId + "|" + required(key, "Idempotency key is required", 200));
    }

    private String requestHash(Map<String, Object> requestPayload) {
        return ResumeGenerationHashUtils.sha256(objectMapper, requestPayload);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "面试事件序列化失败");
        }
    }

    private Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String required(String value, String message, int max) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return trim(value, max);
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private String code(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private int version(Integer value) {
        return value == null ? 1 : value;
    }

    private BusinessException concurrent() {
        return new BusinessException(ErrorCode.PARAM_ERROR, "面试数据已被其他请求修改，请刷新后重试");
    }

    private record Schedule(LocalDateTime startsAtUtc, LocalDateTime endsAtUtc, String timezone) {
    }
}
