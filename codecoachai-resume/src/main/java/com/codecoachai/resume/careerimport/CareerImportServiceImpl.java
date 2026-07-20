package com.codecoachai.resume.careerimport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventView;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.ImportedEvent;
import com.codecoachai.resume.careercalendar.CareerCalendarService;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careerimport.CareerImportModels.DuplicateCandidate;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportPreview;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportResult;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportRowView;
import com.codecoachai.resume.careerimport.CareerImportApplicationWriter.WriteOutcome;
import com.codecoachai.resume.careerimport.CsvCodec.CsvRow;
import com.codecoachai.resume.careerimport.CsvCodec.CsvTable;
import com.codecoachai.resume.careerimport.IcsCodec.IcsEvent;
import com.codecoachai.resume.careerimport.entity.CareerImportBatch;
import com.codecoachai.resume.careerimport.entity.CareerImportRow;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportBatchMapper;
import com.codecoachai.resume.mapper.careerimport.CareerImportRowMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerImportServiceImpl implements CareerImportService {

    private static final int MAX_ROWS = 500;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Set<String> APPLICATION_STATUSES = Set.of(
            "SAVED", "PREPARING", "APPLIED", "INTERVIEWING", "OFFER", "REJECTED", "CLOSED");
    private static final Set<String> UNSUPPORTED_AUTOMATION_HEADERS = Set.of(
            "email_password", "password", "platform_password", "platform_token", "auto_apply", "auto_submit");
    private static final List<String> SUPPORTED_CSV_FIELDS = List.of(
            "company_name", "job_title", "source", "status", "applied_at", "next_follow_up_at", "note",
            "timezone", "event_start", "event_end", "event_title", "event_type", "all_day", "location",
            "event_description", "event_status");
    private static final Map<String, Set<String>> CSV_FIELD_ALIASES = Map.ofEntries(
            Map.entry("company_name", Set.of("company_name", "company", "employer", "公司", "公司名称")),
            Map.entry("job_title", Set.of("job_title", "title", "role", "position", "职位", "岗位", "岗位名称")),
            Map.entry("source", Set.of("source", "channel", "来源", "渠道")),
            Map.entry("status", Set.of("status", "application_status", "状态", "投递状态")),
            Map.entry("applied_at", Set.of("applied_at", "submitted", "application_date", "申请时间", "投递时间")),
            Map.entry("next_follow_up_at", Set.of("next_follow_up_at", "follow_up_at", "下次跟进", "跟进时间")),
            Map.entry("note", Set.of("note", "notes", "备注")),
            Map.entry("timezone", Set.of("timezone", "time_zone", "时区")),
            Map.entry("event_start", Set.of("event_start", "start_time", "事件开始", "日历开始")),
            Map.entry("event_end", Set.of("event_end", "end_time", "事件结束", "日历结束")),
            Map.entry("event_title", Set.of("event_title", "calendar_title", "事件标题")),
            Map.entry("event_type", Set.of("event_type", "calendar_type", "事件类型")),
            Map.entry("all_day", Set.of("all_day", "全天")),
            Map.entry("location", Set.of("location", "地点")),
            Map.entry("event_description", Set.of("event_description", "description", "事件说明")),
            Map.entry("event_status", Set.of("event_status", "calendar_status", "事件状态")));

    private final CareerImportBatchMapper batchMapper;
    private final CareerImportRowMapper rowMapper;
    private final JobApplicationMapper applicationMapper;
    private final CareerImportApplicationWriter applicationWriter;
    private final CareerImportCanonicalSupport canonicalSupport;
    private final CareerCalendarEventMapper calendarEventMapper;
    private final CareerCalendarService calendarService;
    private final CareerImportRowTransaction rowTransaction;
    private final CsvCodec csvCodec;
    private final IcsCodec icsCodec;

    @Override
    public ImportPreview previewCsv(String filename, byte[] content, String timezone, Map<String, String> mapping) {
        Long userId = SecurityAssert.requireLoginUserId();
        ZoneId defaultZone = validateInput(content, timezone);
        CsvTable table = parseCsv(content);
        rejectUnsupportedAutomationHeaders(table.headers());
        Map<String, String> suggestedMapping = suggestMapping(table.headers());
        Map<String, String> resolvedMapping = resolveMapping(table.headers(), suggestedMapping, mapping);
        List<JobApplication> existing = loadApplications(userId);
        List<ImportRowView> rows = new ArrayList<>();
        for (CsvRow row : table.rows()) {
            rows.add(previewCsvRow(
                    applyMapping(row, resolvedMapping), row.values(), defaultZone, existing));
        }
        return toPreview("CSV", defaultZone.getId(), rows, table.headers(), suggestedMapping);
    }

    @Override
    public ImportResult importCsv(String filename, byte[] content, String timezone, String duplicatePolicy,
                                  Map<String, String> mapping) {
        Long userId = SecurityAssert.requireLoginUserId();
        ZoneId defaultZone = validateInput(content, timezone);
        String policy = normalizeDuplicatePolicy(duplicatePolicy);
        CsvTable table = parseCsv(content);
        rejectUnsupportedAutomationHeaders(table.headers());
        Map<String, String> resolvedMapping = resolveMapping(
                table.headers(), suggestMapping(table.headers()), mapping);
        List<JobApplication> existing = loadApplications(userId);
        CareerImportBatch batch = createBatch(
                userId, "CSV", filename, defaultZone.getId(), policy, table.rows().size());
        List<ImportRowView> results = new ArrayList<>();
        for (CsvRow row : table.rows()) {
            CsvRow mappedRow = applyMapping(row, resolvedMapping);
            ImportRowView result;
            try {
                result = rowTransaction.execute(
                        userId,
                        batch.getId(),
                        () -> importCsvRow(
                                userId,
                                batch.getId(),
                                mappedRow,
                                row.values(),
                                defaultZone,
                                policy,
                                existing));
                rememberImportedApplication(result, mappedRow, defaultZone, existing);
            } catch (RuntimeException ex) {
                result = failedRow(
                        row.rowNumber(),
                        row.values(),
                        "ROW_TRANSACTION_FAILED",
                        "Import row transaction rolled back: " + safeMessage(ex));
                persistFailureAudit(userId, batch.getId(), result);
            }
            results.add(result);
        }
        return finishBatch(batch, "CSV", results);
    }

    @Override
    public ImportPreview previewIcs(String filename, byte[] content, String timezone) {
        Long userId = SecurityAssert.requireLoginUserId();
        ZoneId zone = validateInput(content, timezone);
        List<IcsEvent> events = parseIcs(content, zone);
        List<CareerCalendarEvent> existing = loadCalendarEvents(userId);
        List<ImportRowView> rows = events.stream()
                .map(event -> previewIcsEvent(event, existing))
                .toList();
        return toPreview("ICS", zone.getId(), rows);
    }

    @Override
    public ImportResult importIcs(String filename, byte[] content, String timezone) {
        Long userId = SecurityAssert.requireLoginUserId();
        ZoneId zone = validateInput(content, timezone);
        List<IcsEvent> events = parseIcs(content, zone);
        List<CareerCalendarEvent> existing = loadCalendarEvents(userId);
        CareerImportBatch batch = createBatch(userId, "ICS", filename, zone.getId(), "SKIP", events.size());
        List<ImportRowView> results = new ArrayList<>();
        for (IcsEvent event : events) {
            ImportRowView result;
            try {
                result = rowTransaction.execute(
                        userId,
                        batch.getId(),
                        () -> importIcsEvent(userId, batch.getId(), event, existing));
                rememberImportedEvent(result, event, userId, existing);
            } catch (RuntimeException ex) {
                result = failedRow(
                        event.rowNumber(),
                        rawIcs(event),
                        "ROW_TRANSACTION_FAILED",
                        "Import row transaction rolled back: " + safeMessage(ex));
                persistFailureAudit(userId, batch.getId(), result);
            }
            results.add(result);
        }
        return finishBatch(batch, "ICS", results);
    }

    @Override
    public byte[] exportErrorRowsCsv(Long batchId) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !userId.equals(batch.getUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Import batch not found");
        }
        List<CareerImportRow> rows = rowMapper.selectList(new LambdaQueryWrapper<CareerImportRow>()
                .eq(CareerImportRow::getUserId, userId)
                .eq(CareerImportRow::getBatchId, batchId)
                .in(CareerImportRow::getDisposition, "ERROR", "PARTIAL_SUCCESS")
                .orderByAsc(CareerImportRow::getRowNumber)
                .last("LIMIT " + MAX_ROWS));
        List<List<String>> values = rows.stream()
                .map(row -> List.of(
                        text(row.getRowNumber()),
                        text(row.getDisposition()),
                        text(row.getErrorCode()),
                        text(row.getErrorMessage()),
                        text(row.getRawDataJson())))
                .toList();
        return csvCodec.encode(
                List.of("row_number", "disposition", "error_code", "error_message", "raw_data"), values);
    }

    private ImportRowView previewCsvRow(CsvRow row, Map<String, String> raw, ZoneId defaultZone,
                                        List<JobApplication> existing) {
        ImportRowView view = baseRow(row.rowNumber(), raw);
        try {
            CsvApplication parsed = parseApplication(row.values(), defaultZone);
            List<DuplicateCandidate> duplicates =
                    canonicalSupport.findDuplicates(toApplication(null, parsed), existing);
            view.setDuplicateCandidates(duplicates);
            view.setDisposition(duplicates.isEmpty() ? "VALID" : "DUPLICATE_CANDIDATE");
        } catch (BusinessException ex) {
            markError(view, "VALIDATION_ERROR", ex.getMessage());
        }
        return view;
    }

    private ImportRowView importCsvRow(Long userId, Long batchId, CsvRow row, Map<String, String> raw,
                                       ZoneId defaultZone,
                                       String duplicatePolicy, List<JobApplication> existing) {
        ImportRowView view = baseRow(row.rowNumber(), raw);
        CsvApplication parsed;
        try {
            parsed = parseApplication(row.values(), defaultZone);
        } catch (BusinessException ex) {
            markError(view, "VALIDATION_ERROR", ex.getMessage());
            return view;
        }
        JobApplication application = toApplication(userId, parsed);
        List<DuplicateCandidate> previewDuplicates =
                canonicalSupport.findDuplicates(application, existing);
        WriteOutcome outcome;
        try {
            outcome = "SKIP".equals(duplicatePolicy)
                    ? applicationWriter.writeSkip(application)
                    : applicationWriter.writeCreate(application);
        } catch (RuntimeException ex) {
            markError(view, "APPLICATION_INSERT_FAILED", safeMessage(ex));
            return view;
        }
        if ("SKIPPED_DUPLICATE".equals(outcome.disposition())) {
            view.setDuplicateCandidates(outcome.duplicateCandidates());
            view.setDisposition("SKIPPED_DUPLICATE");
            view.setErrorCode(outcome.errorCode());
            view.setErrorMessage(outcome.errorMessage());
            return view;
        }
        if ("ERROR".equals(outcome.disposition())) {
            markError(view, outcome.errorCode(), outcome.errorMessage());
            return view;
        }
        view.setApplicationId(outcome.applicationId());
        if (parsed.calendarEvent() != null) {
            try {
                ImportedEvent event = parsed.calendarEvent();
                EventView created = calendarService.createImported(userId, new ImportedEvent(
                        application.getId(), event.title(), event.eventType(), event.startsAt(), event.endsAt(),
                        event.timezone(), event.allDay(), event.location(), event.description(), event.status(),
                        "CSV", "row:" + row.rowNumber(), null, batchId));
                view.setCalendarEventId(created.getId());
            } catch (RuntimeException ex) {
                view.setDisposition("PARTIAL_SUCCESS");
                view.setErrorCode("CALENDAR_INSERT_FAILED");
                view.setErrorMessage(safeMessage(ex));
                return view;
            }
        }
        view.setDuplicateCandidates("CREATE".equals(duplicatePolicy)
                ? previewDuplicates
                : List.of());
        view.setDisposition("CREATE".equals(duplicatePolicy) && !previewDuplicates.isEmpty()
                ? "CREATED_WITH_DUPLICATE_WARNING"
                : "SUCCESS");
        return view;
    }

    private ImportRowView previewIcsEvent(IcsEvent event, List<CareerCalendarEvent> existing) {
        ImportRowView view = baseRow(event.rowNumber(), rawIcs(event));
        if (isDuplicateIcs(event, existing)) {
            view.setDisposition("DUPLICATE_CANDIDATE");
            view.setErrorCode("DUPLICATE_ICS_EVENT");
            view.setErrorMessage("An event with the same UID or title/start time already exists");
        } else {
            view.setDisposition("VALID");
        }
        return view;
    }

    private ImportRowView importIcsEvent(Long userId, Long batchId, IcsEvent event,
                                         List<CareerCalendarEvent> existing) {
        ImportRowView view = baseRow(event.rowNumber(), rawIcs(event));
        if (isDuplicateIcs(event, existing)) {
            view.setDisposition("SKIPPED_DUPLICATE");
            view.setErrorCode("DUPLICATE_ICS_EVENT");
            view.setErrorMessage("An event with the same UID or title/start time already exists");
            return view;
        }
        try {
            EventView created = calendarService.createImported(userId, new ImportedEvent(
                    event.applicationId(), event.title(), importedIcsEventType(event),
                    event.startsAt(), event.endsAt(),
                    event.timezone(), event.allDay(), event.location(), event.description(), event.status(),
                    "ICS", "vevent:" + event.rowNumber(), event.uid(), batchId));
            view.setCalendarEventId(created.getId());
            view.setDisposition("SUCCESS");
        } catch (DuplicateKeyException ex) {
            CareerCalendarEvent winner = findConcurrentIcsWinner(userId, event.uid(), ex);
            if (winner == null) {
                markError(view, "ICS_EVENT_INSERT_FAILED", safeMessage(ex));
                return view;
            }
            view.setCalendarEventId(winner.getId());
            view.setDisposition("SKIPPED_DUPLICATE");
            view.setErrorCode("CONCURRENT_DUPLICATE");
            view.setErrorMessage("The same ICS event was imported concurrently");
        } catch (RuntimeException ex) {
            markError(view, "ICS_EVENT_INSERT_FAILED", safeMessage(ex));
        }
        return view;
    }

    private CareerCalendarEvent findConcurrentIcsWinner(Long userId, String externalUid,
                                                         DuplicateKeyException exception) {
        if (!StringUtils.hasText(externalUid)
                || !isMysqlDuplicateKey(exception)) {
            return null;
        }
        CareerCalendarEvent winner =
                calendarEventMapper.selectActiveByExternalUidBinaryForUpdate(userId, externalUid);
        if (winner == null
                || !userId.equals(winner.getUserId())
                || !externalUid.equals(winner.getExternalUid())) {
            return null;
        }
        return winner;
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

    private CsvApplication parseApplication(Map<String, String> row, ZoneId defaultZone) {
        String jobTitle = firstText(row.get("job_title"), row.get("title"));
        if (!StringUtils.hasText(jobTitle)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "job_title is required");
        }
        String company = firstText(row.get("company_name"), row.get("company"));
        String source = firstText(row.get("source"), row.get("channel"));
        String status = firstText(row.get("status"), "SAVED").toUpperCase(Locale.ROOT);
        if (!APPLICATION_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported application status: " + status);
        }
        ZoneId rowZone = zone(firstText(row.get("timezone"), defaultZone.getId()));
        LocalDateTime appliedAt = parseLocalDateTime(row.get("applied_at"), rowZone);
        LocalDateTime nextFollowUp = parseLocalDateTime(row.get("next_follow_up_at"), rowZone);
        ImportedEvent calendarEvent = parseCalendarEvent(row, rowZone);
        return new CsvApplication(truncate(company, 120), truncate(jobTitle.trim(), 120),
                truncate(source, 120), status, appliedAt, nextFollowUp, truncate(row.get("note"), 1000),
                calendarEvent);
    }

    private ImportedEvent parseCalendarEvent(Map<String, String> row, ZoneId rowZone) {
        String startRaw = row.get("event_start");
        if (!StringUtils.hasText(startRaw)) {
            return null;
        }
        LocalDateTime start = parseLocalDateTime(startRaw, rowZone);
        LocalDateTime end = StringUtils.hasText(row.get("event_end"))
                ? parseLocalDateTime(row.get("event_end"), rowZone)
                : start.plusHours(1);
        String title = firstText(row.get("event_title"), "Application follow-up");
        return new ImportedEvent(null, truncate(title, 200),
                firstText(row.get("event_type"), "FOLLOW_UP").toUpperCase(Locale.ROOT),
                start, end, rowZone.getId(), parseBoolean(row.get("all_day")),
                truncate(row.get("location"), 300), truncate(row.get("event_description"), 2000),
                firstText(row.get("event_status"), "CONFIRMED").toUpperCase(Locale.ROOT),
                "CSV", null, null, null);
    }

    private JobApplication toApplication(Long userId, CsvApplication parsed) {
        JobApplication application = new JobApplication();
        application.setUserId(userId);
        application.setCompanyName(parsed.companyName());
        application.setJobTitle(parsed.jobTitle());
        application.setSource(parsed.source());
        application.setStatus(parsed.status());
        application.setAppliedAt(parsed.appliedAt());
        application.setNextFollowUpAt(parsed.nextFollowUpAt());
        application.setNote(parsed.note());
        return application;
    }

    private boolean isDuplicateIcs(IcsEvent event, List<CareerCalendarEvent> existing) {
        Instant eventStart = event.startsAt().atZone(ZoneId.of(event.timezone())).toInstant();
        for (CareerCalendarEvent current : existing) {
            if (StringUtils.hasText(event.uid()) && event.uid().equals(current.getExternalUid())) {
                return true;
            }
            if (normalize(event.title()).equals(normalize(current.getTitle()))
                    && current.getStartsAtUtc() != null
                    && eventStart.equals(current.getStartsAtUtc().toInstant(ZoneOffset.UTC))) {
                return true;
            }
        }
        return false;
    }

    private CareerImportBatch createBatch(Long userId, String format, String filename, String timezone,
                                          String duplicatePolicy, int totalCount) {
        CareerImportBatch batch = new CareerImportBatch();
        batch.setUserId(userId);
        batch.setFormat(format);
        batch.setFilename(truncate(filename, 255));
        batch.setTimezone(timezone);
        batch.setDuplicatePolicy(duplicatePolicy);
        batch.setStatus("RUNNING");
        batch.setTotalCount(totalCount);
        batch.setSuccessCount(0);
        batch.setErrorCount(0);
        batch.setDuplicateCount(0);
        batchMapper.insert(batch);
        return batch;
    }

    private ImportResult finishBatch(CareerImportBatch batch, String format, List<ImportRowView> rows) {
        int success = (int) rows.stream().filter(this::isSuccess).count();
        int errors = (int) rows.stream().filter(this::isError).count();
        int duplicates = (int) rows.stream().filter(this::isDuplicate).count();
        batch.setSuccessCount(success);
        batch.setErrorCount(errors);
        batch.setDuplicateCount(duplicates);
        batch.setStatus(errors == 0 ? "COMPLETED" : success > 0 ? "PARTIAL" : "FAILED");
        if (batchMapper.updateById(batch) != 1) {
            throw new IllegalStateException("Failed to finalize import batch " + batch.getId());
        }

        ImportResult result = new ImportResult();
        result.setBatchId(batch.getId());
        result.setFormat(format);
        result.setStatus(batch.getStatus());
        result.setTotalCount(rows.size());
        result.setSuccessCount(success);
        result.setErrorCount(errors);
        result.setDuplicateCount(duplicates);
        result.setRows(rows);
        return result;
    }

    private ImportPreview toPreview(String format, String timezone, List<ImportRowView> rows) {
        return toPreview(format, timezone, rows, List.of(), Map.of());
    }

    private ImportPreview toPreview(String format, String timezone, List<ImportRowView> rows,
                                    List<String> headers, Map<String, String> suggestedMapping) {
        ImportPreview preview = new ImportPreview();
        preview.setFormat(format);
        preview.setTimezone(timezone);
        preview.setHeaders(headers);
        preview.setSuggestedMapping(suggestedMapping);
        preview.setSupportedFields("CSV".equals(format) ? SUPPORTED_CSV_FIELDS : List.of());
        preview.setTotalCount(rows.size());
        preview.setValidCount((int) rows.stream().filter(row -> "VALID".equals(row.getDisposition())).count());
        preview.setErrorCount((int) rows.stream().filter(this::isError).count());
        preview.setDuplicateCount((int) rows.stream().filter(this::isDuplicate).count());
        preview.setRows(rows);
        return preview;
    }

    private List<JobApplication> loadApplications(Long userId) {
        List<JobApplication> applications = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .orderByDesc(JobApplication::getUpdatedAt)
                .last("LIMIT 5001"));
        if (applications.size() > 5000) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "Too many active applications to safely preview career import duplicates");
        }
        return new ArrayList<>(applications);
    }

    private List<CareerCalendarEvent> loadCalendarEvents(Long userId) {
        List<CareerCalendarEvent> events =
                calendarEventMapper.selectList(new LambdaQueryWrapper<CareerCalendarEvent>()
                .eq(CareerCalendarEvent::getUserId, userId)
                .eq(CareerCalendarEvent::getDeleted, CommonConstants.NO)
                .orderByDesc(CareerCalendarEvent::getStartsAtUtc)
                .last("LIMIT 5001"));
        if (events.size() > 5000) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "Too many active calendar events to safely preview ICS duplicates");
        }
        return new ArrayList<>(events);
    }

    private CsvTable parseCsv(byte[] content) {
        return csvCodec.parse(content, MAX_ROWS);
    }

    private List<IcsEvent> parseIcs(byte[] content, ZoneId zone) {
        return icsCodec.parse(content, zone.getId(), MAX_ROWS);
    }

    private ZoneId validateInput(byte[] content, String timezone) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Import file cannot be empty");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Import file cannot exceed 2 MB");
        }
        return zone(timezone);
    }

    private void rejectUnsupportedAutomationHeaders(List<String> headers) {
        if (headers.stream().anyMatch(UNSUPPORTED_AUTOMATION_HEADERS::contains)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Email/platform credentials and automatic application fields are not supported");
        }
    }

    private Map<String, String> suggestMapping(List<String> headers) {
        Map<String, String> suggestions = new LinkedHashMap<>();
        for (String field : SUPPORTED_CSV_FIELDS) {
            Set<String> aliases = CSV_FIELD_ALIASES.getOrDefault(field, Set.of(field));
            headers.stream()
                    .filter(aliases::contains)
                    .findFirst()
                    .ifPresent(header -> suggestions.put(field, header));
        }
        return suggestions;
    }

    private Map<String, String> resolveMapping(List<String> headers, Map<String, String> suggestions,
                                               Map<String, String> requested) {
        Map<String, String> resolved = new LinkedHashMap<>(suggestions);
        if (requested == null) {
            return resolved;
        }
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            String field = csvCodec.normalizeHeader(entry.getKey());
            if (!SUPPORTED_CSV_FIELDS.contains(field)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported CSV mapping field: " + field);
            }
            if (!StringUtils.hasText(entry.getValue())) {
                resolved.remove(field);
                continue;
            }
            String source = csvCodec.normalizeHeader(entry.getValue());
            if (!headers.contains(source)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "CSV mapping header not found: " + source);
            }
            resolved.put(field, source);
        }
        return resolved;
    }

    private CsvRow applyMapping(CsvRow row, Map<String, String> mapping) {
        Map<String, String> values = new LinkedHashMap<>(row.values());
        SUPPORTED_CSV_FIELDS.forEach(values::remove);
        values.remove("company");
        values.remove("title");
        values.remove("channel");
        mapping.forEach((field, source) -> values.put(field, row.values().getOrDefault(source, "")));
        return new CsvRow(row.rowNumber(), values);
    }

    private ZoneId zone(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Timezone is required");
        }
        try {
            return ZoneId.of(value.trim());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unknown timezone: " + value);
        }
    }

    private LocalDateTime parseLocalDateTime(String value, ZoneId zone) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            return OffsetDateTime.parse(normalized).atZoneSameInstant(zone).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized);
            } catch (DateTimeParseException second) {
                try {
                    return LocalDate.parse(normalized).atStartOfDay();
                } catch (DateTimeParseException third) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "Invalid date-time '" + value + "'. Use ISO-8601 and provide timezone.");
                }
            }
        }
    }

    private ImportRowView baseRow(int rowNumber, Map<String, String> raw) {
        ImportRowView view = new ImportRowView();
        view.setRowNumber(rowNumber);
        view.setRaw(new LinkedHashMap<>(raw));
        return view;
    }

    private ImportRowView failedRow(
            int rowNumber,
            Map<String, String> raw,
            String code,
            String message) {
        ImportRowView view = baseRow(rowNumber, raw);
        markError(view, code, message);
        return view;
    }

    private void persistFailureAudit(Long userId, Long batchId, ImportRowView view) {
        try {
            rowTransaction.persistFailure(userId, batchId, view);
        } catch (RuntimeException auditException) {
            throw new IllegalStateException(
                    "Failed to persist failure audit for import batch "
                            + batchId
                            + " row "
                            + view.getRowNumber(),
                    auditException);
        }
    }

    private void rememberImportedApplication(
            ImportRowView result,
            CsvRow row,
            ZoneId defaultZone,
            List<JobApplication> existing) {
        if (result.getApplicationId() == null) {
            return;
        }
        JobApplication application = toApplication(
                null, parseApplication(row.values(), defaultZone));
        application.setId(result.getApplicationId());
        existing.add(application);
    }

    private void rememberImportedEvent(
            ImportRowView result,
            IcsEvent event,
            Long userId,
            List<CareerCalendarEvent> existing) {
        if (result.getCalendarEventId() == null
                || !"SUCCESS".equals(result.getDisposition())) {
            return;
        }
        CareerCalendarEvent shadow = new CareerCalendarEvent();
        shadow.setId(result.getCalendarEventId());
        shadow.setUserId(userId);
        shadow.setTitle(event.title());
        shadow.setExternalUid(event.uid());
        shadow.setStartsAtUtc(LocalDateTime.ofInstant(
                event.startsAt().atZone(ZoneId.of(event.timezone())).toInstant(), ZoneOffset.UTC));
        existing.add(shadow);
    }

    private Map<String, String> rawIcs(IcsEvent event) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("uid", text(event.uid()));
        raw.put("title", text(event.title()));
        raw.put("starts_at", text(event.startsAt()));
        raw.put("ends_at", text(event.endsAt()));
        raw.put("timezone", event.timezone());
        raw.put("application_id", text(event.applicationId()));
        raw.put("event_type", importedIcsEventType(event));
        return raw;
    }

    private String importedIcsEventType(IcsEvent event) {
        String searchable = text(event == null ? null : event.title())
                + " " + text(event == null ? null : event.description());
        String normalized = searchable.toUpperCase(Locale.ROOT);
        if (containsAny(normalized, "FINAL INTERVIEW", "FINAL ROUND", "终面", "最终面试")) {
            return "FINAL_INTERVIEW";
        }
        if (containsAny(normalized, "HR INTERVIEW", "HR面", "人事面试")) {
            return "HR_INTERVIEW";
        }
        if (containsAny(normalized, "TECHNICAL INTERVIEW", "TECH INTERVIEW", "技术面", "技术面试")) {
            return "TECHNICAL_INTERVIEW";
        }
        if (containsAny(normalized, "PHONE SCREEN", "TELEPHONE INTERVIEW", "电话面试", "电话筛选")) {
            return "PHONE_SCREEN";
        }
        if (containsAny(normalized, "INTERVIEW", "面试", "一面", "二面", "三面")) {
            return "INTERVIEW";
        }
        return "IMPORTED";
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void markError(ImportRowView view, String code, String message) {
        view.setDisposition("ERROR");
        view.setErrorCode(code);
        view.setErrorMessage(message);
    }

    private boolean isSuccess(ImportRowView row) {
        return Set.of("SUCCESS", "PARTIAL_SUCCESS", "CREATED_WITH_DUPLICATE_WARNING")
                .contains(row.getDisposition());
    }

    private boolean isError(ImportRowView row) {
        return "ERROR".equals(row.getDisposition()) || "PARTIAL_SUCCESS".equals(row.getDisposition());
    }

    private boolean isDuplicate(ImportRowView row) {
        return "DUPLICATE_CANDIDATE".equals(row.getDisposition())
                || "SKIPPED_DUPLICATE".equals(row.getDisposition())
                || "CREATED_WITH_DUPLICATE_WARNING".equals(row.getDisposition());
    }

    private String normalizeDuplicatePolicy(String value) {
        String policy = firstText(value, "SKIP").toUpperCase(Locale.ROOT);
        if (!Set.of("SKIP", "CREATE").contains(policy)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "duplicatePolicy must be SKIP or CREATE");
        }
        return policy;
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private String normalize(String value) {
        return canonicalSupport.normalize(value);
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

    private String safeMessage(RuntimeException ex) {
        return StringUtils.hasText(ex.getMessage()) ? truncate(ex.getMessage(), 500) : "Import row failed";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record CsvApplication(String companyName, String jobTitle, String source, String status,
                                  LocalDateTime appliedAt, LocalDateTime nextFollowUpAt, String note,
                                  ImportedEvent calendarEvent) {
    }
}
