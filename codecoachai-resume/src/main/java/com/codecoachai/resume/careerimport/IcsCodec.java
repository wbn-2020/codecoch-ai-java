package com.codecoachai.resume.careerimport;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IcsCodec {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASIC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    public List<IcsEvent> parse(byte[] content, String defaultTimezone) {
        return parse(content, defaultTimezone, Integer.MAX_VALUE);
    }

    public List<IcsEvent> parse(byte[] content, String defaultTimezone, int maxEvents) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        ZoneId fallbackZone = requireZone(defaultTimezone);
        String text = new String(content, StandardCharsets.UTF_8);
        List<String> lines = unfold(text);
        List<IcsEvent> events = new ArrayList<>();
        Map<String, Property> properties = null;
        int eventNumber = 0;
        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line.trim())) {
                properties = new LinkedHashMap<>();
                eventNumber++;
            } else if ("END:VEVENT".equalsIgnoreCase(line.trim()) && properties != null) {
                events.add(toEvent(eventNumber, properties, fallbackZone));
                if (events.size() > maxEvents) {
                    throw new BusinessException(
                            ErrorCode.PARAM_ERROR, "ICS cannot exceed " + maxEvents + " VEVENT records");
                }
                properties = null;
            } else if (properties != null) {
                Property property = parseProperty(line);
                properties.putIfAbsent(property.name(), property);
            }
        }
        if (events.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ICS must contain at least one VEVENT");
        }
        return events;
    }

    public byte[] encode(List<IcsExportEvent> events, String calendarTimezone) {
        ZoneId zone = requireZone(calendarTimezone);
        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//CodeCoachAI//Career Calendar//EN\r\n")
                .append("CALSCALE:GREGORIAN\r\n")
                .append("METHOD:PUBLISH\r\n")
                .append("X-WR-TIMEZONE:").append(escape(zone.getId())).append("\r\n");
        for (IcsExportEvent event : events) {
            ics.append("BEGIN:VEVENT\r\n")
                    .append("UID:").append(escape(event.uid())).append("\r\n")
                    .append("DTSTAMP:").append(UTC_DATE_TIME.format(Instant.now())).append("\r\n")
                    .append("DTSTART:").append(UTC_DATE_TIME.format(event.startsAt())).append("\r\n")
                    .append("DTEND:").append(UTC_DATE_TIME.format(event.endsAt())).append("\r\n")
                    .append("SUMMARY:").append(escape(event.title())).append("\r\n")
                    .append("STATUS:").append(escape(firstText(event.status(), "CONFIRMED"))).append("\r\n")
                    .append("X-CODECOACHAI-TIMEZONE:").append(escape(event.sourceTimezone())).append("\r\n");
            appendOptional(ics, "LOCATION", event.location());
            appendOptional(ics, "DESCRIPTION", event.description());
            if (event.applicationId() != null) {
                ics.append("X-CODECOACHAI-APPLICATION-ID:").append(event.applicationId()).append("\r\n");
            }
            ics.append("END:VEVENT\r\n");
        }
        ics.append("END:VCALENDAR\r\n");
        return ics.toString().getBytes(StandardCharsets.UTF_8);
    }

    private IcsEvent toEvent(int eventNumber, Map<String, Property> properties, ZoneId fallbackZone) {
        Property startProperty = required(properties, "DTSTART", eventNumber);
        ZoneId eventTimezone = eventTimezone(properties);
        ParsedDate start = parseDate(startProperty, fallbackZone, eventTimezone);
        Property endProperty = properties.get("DTEND");
        ParsedDate end = endProperty == null
                ? new ParsedDate(start.localDateTime().plus(start.allDay() ? java.time.Period.ofDays(1)
                        : java.time.Duration.ofHours(1)), start.zone(), start.allDay())
                : parseDate(endProperty, fallbackZone, eventTimezone);
        Instant startInstant = start.localDateTime().atZone(start.zone()).toInstant();
        Instant endInstant = end.localDateTime().atZone(end.zone()).toInstant();
        if (!endInstant.isAfter(startInstant)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "ICS VEVENT " + eventNumber + " DTEND must be after DTSTART");
        }
        return new IcsEvent(
                eventNumber + 1,
                unescape(value(properties, "UID")),
                firstText(unescape(value(properties, "SUMMARY")), "Imported calendar event"),
                unescape(value(properties, "DESCRIPTION")),
                unescape(value(properties, "LOCATION")),
                firstText(value(properties, "STATUS"), "CONFIRMED").toUpperCase(Locale.ROOT),
                start.localDateTime(),
                end.localDateTime(),
                start.zone().getId(),
                start.allDay(),
                parseLong(value(properties, "X-CODECOACHAI-APPLICATION-ID")));
    }

    private ParsedDate parseDate(Property property, ZoneId fallbackZone, ZoneId eventTimezone) {
        String raw = property.value().trim();
        boolean allDay = "DATE".equalsIgnoreCase(property.params().get("VALUE")) || raw.length() == 8;
        try {
            if (allDay) {
                return new ParsedDate(LocalDate.parse(raw, BASIC_DATE).atStartOfDay(), fallbackZone, true);
            }
            String tzid = property.params().get("TZID");
            if (raw.endsWith("Z")) {
                Instant instant = Instant.from(UTC_DATE_TIME.parse(raw));
                ZoneId zone = eventTimezone == null ? ZoneOffset.UTC : eventTimezone;
                return new ParsedDate(LocalDateTime.ofInstant(instant, zone), zone, false);
            }
            if (raw.matches(".*[+-]\\d{4}$")) {
                OffsetDateTime offset = OffsetDateTime.parse(raw,
                        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssxx"));
                ZoneId zone = eventTimezone == null ? offset.getOffset() : eventTimezone;
                return new ParsedDate(LocalDateTime.ofInstant(offset.toInstant(), zone), zone, false);
            }
            ZoneId zone = StringUtils.hasText(tzid) ? requireZone(tzid) : fallbackZone;
            return new ParsedDate(LocalDateTime.parse(raw, BASIC_DATE_TIME), zone, false);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Invalid ICS date-time: " + raw);
        }
    }

    private ZoneId eventTimezone(Map<String, Property> properties) {
        String value = unescape(value(properties, "X-CODECOACHAI-TIMEZONE"));
        return StringUtils.hasText(value) ? requireZone(value) : null;
    }

    private Property parseProperty(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return new Property(line.toUpperCase(Locale.ROOT), Map.of(), "");
        }
        String[] tokens = line.substring(0, colon).split(";");
        String name = tokens[0].toUpperCase(Locale.ROOT);
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < tokens.length; i++) {
            int equals = tokens[i].indexOf('=');
            if (equals > 0) {
                params.put(tokens[i].substring(0, equals).toUpperCase(Locale.ROOT),
                        tokens[i].substring(equals + 1));
            }
        }
        return new Property(name, params, line.substring(colon + 1));
    }

    private List<String> unfold(String text) {
        String[] physical = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> logical = new ArrayList<>();
        for (String line : physical) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !logical.isEmpty()) {
                int last = logical.size() - 1;
                logical.set(last, logical.get(last) + line.substring(1));
            } else {
                logical.add(line);
            }
        }
        return logical;
    }

    private Property required(Map<String, Property> properties, String name, int eventNumber) {
        Property property = properties.get(name);
        if (property == null || !StringUtils.hasText(property.value())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "ICS VEVENT " + eventNumber + " requires " + name);
        }
        return property;
    }

    private String value(Map<String, Property> properties, String name) {
        Property property = properties.get(name);
        return property == null ? null : property.value();
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

    private void appendOptional(StringBuilder target, String name, String value) {
        if (StringUtils.hasText(value)) {
            target.append(name).append(':').append(escape(value)).append("\r\n");
        }
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    private String unescape(String value) {
        return value == null ? null : value
                .replace("\\n", "\n")
                .replace("\\N", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
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

    private record Property(String name, Map<String, String> params, String value) {
    }

    private record ParsedDate(LocalDateTime localDateTime, ZoneId zone, boolean allDay) {
    }

    public record IcsEvent(int rowNumber, String uid, String title, String description, String location,
                           String status, LocalDateTime startsAt, LocalDateTime endsAt, String timezone,
                           boolean allDay, Long applicationId) {
    }

    public record IcsExportEvent(String uid, String title, String description, String location, String status,
                                 Instant startsAt, Instant endsAt, String sourceTimezone, Long applicationId) {
    }
}
