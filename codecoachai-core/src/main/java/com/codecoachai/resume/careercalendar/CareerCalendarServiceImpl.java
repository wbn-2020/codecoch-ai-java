package com.codecoachai.resume.careercalendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventSave;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventView;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.ImportedEvent;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careerimport.CsvCodec;
import com.codecoachai.resume.careerimport.IcsCodec;
import com.codecoachai.resume.careerimport.IcsCodec.IcsExportEvent;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerCalendarServiceImpl implements CareerCalendarService {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Duration MAX_WINDOW = Duration.ofDays(366);
    private static final int MAX_EVENTS = 5000;
    private static final String PREPARATION_STATUS_GENERATING = "GENERATING";
    private static final String PREPARATION_STATUS_STALE = "STALE";

    private final CareerCalendarEventMapper calendarEventMapper;
    private final JobApplicationMapper applicationMapper;
    private final CsvCodec csvCodec;
    private final IcsCodec icsCodec;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventView create(EventSave request) {
        Long userId = SecurityAssert.requireLoginUserId();
        validateApplication(userId, request.getApplicationId());
        CareerCalendarEvent entity = new CareerCalendarEvent();
        fill(entity, request.getApplicationId(), request.getTitle(), request.getEventType(),
                request.getStartsAt(), request.getEndsAt(), request.getTimezone(),
                Boolean.TRUE.equals(request.getAllDay()), request.getLocation(), request.getDescription(),
                request.getStatus());
        entity.setUserId(userId);
        entity.setSourceType("MANUAL");
        calendarEventMapper.insert(entity);
        return toView(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventView update(Long eventId, EventSave request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerCalendarEvent entity = ownedEvent(userId, eventId);
        validateApplication(userId, request.getApplicationId());
        boolean hadPreparation = hasPreparation(entity);
        fill(entity, request.getApplicationId(), request.getTitle(), request.getEventType(),
                request.getStartsAt(), request.getEndsAt(), request.getTimezone(),
                Boolean.TRUE.equals(request.getAllDay()), request.getLocation(), request.getDescription(),
                request.getStatus());
        if (hadPreparation) {
            entity.setPreparationStatus(PREPARATION_STATUS_STALE);
        }
        calendarEventMapper.updateById(entity);
        if (calendarEventMapper.markPreparationStale(eventId, userId) == 1) {
            entity.setPreparationStatus(PREPARATION_STATUS_STALE);
        }
        return toView(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long eventId) {
        calendarEventMapper.deleteById(ownedEvent(SecurityAssert.requireLoginUserId(), eventId));
    }

    @Override
    public List<EventView> list(Instant from, Instant to) {
        Long userId = SecurityAssert.requireLoginUserId();
        return listEntities(userId, from, to).stream().map(this::toView).toList();
    }

    @Override
    public byte[] exportCsv(Instant from, Instant to) {
        Long userId = SecurityAssert.requireLoginUserId();
        List<String> headers = List.of("id", "application_id", "title", "event_type", "starts_at",
                "ends_at", "timezone", "all_day", "location", "description", "status", "source_type",
                "external_uid");
        List<List<String>> rows = new ArrayList<>();
        for (CareerCalendarEvent event : listEntities(userId, from, to)) {
            ZoneId zone = requireZone(event.getTimezone());
            rows.add(List.of(
                    text(event.getId()),
                    text(event.getApplicationId()),
                    text(event.getTitle()),
                    text(event.getEventType()),
                    ISO_OFFSET.format(toInstant(event.getStartsAtUtc()).atZone(zone)),
                    ISO_OFFSET.format(toInstant(event.getEndsAtUtc()).atZone(zone)),
                    zone.getId(),
                    Integer.valueOf(CommonConstants.YES).equals(event.getAllDayFlag()) ? "true" : "false",
                    text(event.getLocation()),
                    text(event.getDescription()),
                    text(event.getStatus()),
                    text(event.getSourceType()),
                    text(event.getExternalUid())));
        }
        return csvCodec.encode(headers, rows);
    }

    @Override
    public byte[] exportIcs(Instant from, Instant to, String calendarTimezone) {
        Long userId = SecurityAssert.requireLoginUserId();
        requireZone(calendarTimezone);
        List<IcsExportEvent> events = listEntities(userId, from, to).stream()
                .map(event -> new IcsExportEvent(
                        firstText(event.getExternalUid(), "career-event-" + event.getId() + "@codecoachai"),
                        event.getTitle(),
                        event.getDescription(),
                        event.getLocation(),
                        event.getStatus(),
                        toInstant(event.getStartsAtUtc()),
                        toInstant(event.getEndsAtUtc()),
                        event.getTimezone(),
                        event.getApplicationId()))
                .toList();
        return icsCodec.encode(events, calendarTimezone);
    }

    @Override
    @Transactional(
            rollbackFor = Exception.class,
            noRollbackFor = DuplicateKeyException.class)
    public EventView createImported(Long userId, ImportedEvent event) {
        validateApplication(userId, event.applicationId());
        CareerCalendarEvent entity = new CareerCalendarEvent();
        fill(entity, event.applicationId(), event.title(), event.eventType(), event.startsAt(), event.endsAt(),
                event.timezone(), event.allDay(), event.location(), event.description(), event.status());
        entity.setUserId(userId);
        entity.setSourceType(firstText(event.sourceType(), "IMPORT"));
        entity.setSourceRef(truncate(event.sourceRef(), 160));
        entity.setExternalUid(truncate(event.externalUid(), 255));
        entity.setImportBatchId(event.importBatchId());
        calendarEventMapper.insert(entity);
        return toView(entity);
    }

    private List<CareerCalendarEvent> listEntities(Long userId, Instant from, Instant to) {
        Window window = normalizeWindow(from, to);
        List<CareerCalendarEvent> events = calendarEventMapper.selectList(
                new LambdaQueryWrapper<CareerCalendarEvent>()
                .eq(CareerCalendarEvent::getUserId, userId)
                .eq(CareerCalendarEvent::getDeleted, CommonConstants.NO)
                .lt(CareerCalendarEvent::getStartsAtUtc, LocalDateTime.ofInstant(window.to(), ZoneOffset.UTC))
                .gt(CareerCalendarEvent::getEndsAtUtc, LocalDateTime.ofInstant(window.from(), ZoneOffset.UTC))
                .orderByAsc(CareerCalendarEvent::getStartsAtUtc)
                .orderByAsc(CareerCalendarEvent::getId)
                .last("LIMIT " + (MAX_EVENTS + 1)));
        if (events.size() > MAX_EVENTS) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR, "Calendar query cannot return more than " + MAX_EVENTS + " events");
        }
        return events;
    }

    private Window normalizeWindow(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant normalizedFrom = from;
        Instant normalizedTo = to;
        if (normalizedFrom == null && normalizedTo == null) {
            normalizedFrom = now.minus(Duration.ofDays(183));
            normalizedTo = now.plus(Duration.ofDays(183));
        } else if (normalizedFrom == null) {
            normalizedFrom = normalizedTo.minus(MAX_WINDOW);
        } else if (normalizedTo == null) {
            normalizedTo = normalizedFrom.plus(MAX_WINDOW);
        }
        if (!normalizedTo.isAfter(normalizedFrom)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "to must be after from");
        }
        if (Duration.between(normalizedFrom, normalizedTo).compareTo(MAX_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Calendar window cannot exceed 366 days");
        }
        return new Window(normalizedFrom, normalizedTo);
    }

    private void fill(CareerCalendarEvent entity, Long applicationId, String title, String eventType,
                      LocalDateTime startsAt, LocalDateTime endsAt, String timezone, boolean allDay,
                      String location, String description, String status) {
        ZoneId zone = requireZone(timezone);
        if (startsAt == null || endsAt == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "startsAt and endsAt are required");
        }
        ZonedDateTime zonedStart = startsAt.atZone(zone);
        ZonedDateTime zonedEnd = endsAt.atZone(zone);
        if (!zonedEnd.toInstant().isAfter(zonedStart.toInstant())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "endsAt must be after startsAt");
        }
        entity.setApplicationId(applicationId);
        entity.setTitle(requireText(title, "Event title is required", 200));
        entity.setEventType(firstText(eventType, "FOLLOW_UP").toUpperCase(Locale.ROOT));
        entity.setStartsAtUtc(LocalDateTime.ofInstant(zonedStart.toInstant(), ZoneOffset.UTC));
        entity.setEndsAtUtc(LocalDateTime.ofInstant(zonedEnd.toInstant(), ZoneOffset.UTC));
        entity.setTimezone(zone.getId());
        entity.setAllDayFlag(allDay ? CommonConstants.YES : CommonConstants.NO);
        entity.setLocation(truncate(location, 300));
        entity.setDescription(truncate(description, 2000));
        entity.setStatus(firstText(status, "CONFIRMED").toUpperCase(Locale.ROOT));
    }

    private EventView toView(CareerCalendarEvent entity) {
        ZoneId zone = requireZone(entity.getTimezone());
        Instant start = toInstant(entity.getStartsAtUtc());
        Instant end = toInstant(entity.getEndsAtUtc());
        EventView view = new EventView();
        view.setId(entity.getId());
        view.setApplicationId(entity.getApplicationId());
        view.setTitle(entity.getTitle());
        view.setEventType(entity.getEventType());
        view.setStartsAt(LocalDateTime.ofInstant(start, zone));
        view.setEndsAt(LocalDateTime.ofInstant(end, zone));
        view.setStartsAtUtc(start);
        view.setEndsAtUtc(end);
        view.setTimezone(zone.getId());
        view.setAllDay(Integer.valueOf(CommonConstants.YES).equals(entity.getAllDayFlag()));
        view.setLocation(entity.getLocation());
        view.setDescription(entity.getDescription());
        view.setStatus(entity.getStatus());
        view.setSourceType(entity.getSourceType());
        view.setSourceRef(entity.getSourceRef());
        view.setExternalUid(entity.getExternalUid());
        view.setImportBatchId(entity.getImportBatchId());
        view.setPreparationStatus(entity.getPreparationStatus());
        view.setPreparationAiCallLogId(entity.getPreparationAiCallLogId());
        view.setPreparationGeneratedAt(entity.getPreparationGeneratedAt());
        view.setPreparationSourceHash(entity.getPreparationSourceHash());
        view.setPreparationStale(isPreparationUnavailable(entity.getPreparationStatus()));
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }

    private CareerCalendarEvent ownedEvent(Long userId, Long eventId) {
        CareerCalendarEvent event = calendarEventMapper.selectById(eventId);
        if (event == null || !userId.equals(event.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Calendar event not found");
        }
        return event;
    }

    private void validateApplication(Long userId, Long applicationId) {
        if (applicationId == null) {
            return;
        }
        JobApplication application = applicationMapper.selectById(applicationId);
        if (application == null || !userId.equals(application.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Application not found");
        }
    }

    private ZoneId requireZone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Timezone is required");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unknown timezone: " + timezone);
        }
    }

    private Instant toInstant(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC);
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean hasPreparation(CareerCalendarEvent event) {
        return StringUtils.hasText(event.getPreparationJson())
                || StringUtils.hasText(event.getPreparationStatus())
                || StringUtils.hasText(event.getPreparationSourceHash())
                || event.getPreparationGeneratedAt() != null;
    }

    private boolean isPreparationUnavailable(String status) {
        String normalized = StringUtils.hasText(status)
                ? status.trim().toUpperCase(Locale.ROOT) : "";
        return PREPARATION_STATUS_STALE.equals(normalized)
                || PREPARATION_STATUS_GENERATING.equals(normalized);
    }

    private record Window(Instant from, Instant to) {
    }
}
