package com.codecoachai.resume.domain.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Accepts the browser's space-separated timestamp and internal ISO local timestamps.
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        String value = parser.getValueAsString();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported wire format.
            }
        }
        throw InvalidFormatException.from(
                parser,
                "Expected yyyy-MM-dd HH:mm:ss or ISO local date-time",
                value,
                LocalDateTime.class);
    }
}
