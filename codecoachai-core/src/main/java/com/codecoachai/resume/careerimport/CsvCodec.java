package com.codecoachai.resume.careerimport;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CsvCodec {

    public CsvTable parse(byte[] content) {
        return parse(content, Integer.MAX_VALUE);
    }

    public CsvTable parse(byte[] content, int maxRows) {
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<List<String>> records = parseRecords(text, maxRows);
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "CSV header is required");
        }
        List<String> headers = records.get(0).stream().map(this::normalizeHeader).toList();
        Set<String> unique = new LinkedHashSet<>(headers);
        if (unique.size() != headers.size() || unique.contains("")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "CSV headers must be non-empty and unique");
        }
        List<CsvRow> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            if (record.stream().allMatch(value -> !StringUtils.hasText(value))) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                values.put(headers.get(column), column < record.size() ? record.get(column).trim() : "");
            }
            rows.add(new CsvRow(index + 1, values));
        }
        return new CsvTable(headers, rows);
    }

    public byte[] encode(List<String> headers, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendRecord(csv, headers);
        for (List<String> row : rows) {
            appendRecord(csv, row);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<List<String>> parseRecords(String text, int maxRows) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        int dataRows = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"' && field.length() == 0 && !quoteClosed) {
                quoted = true;
            } else if (ch == ',') {
                record.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                records.add(record);
                if (records.size() > 1 && record.stream().anyMatch(StringUtils::hasText) && ++dataRows > maxRows) {
                    throw new BusinessException(
                            ErrorCode.PARAM_ERROR, "CSV cannot exceed " + maxRows + " data rows");
                }
                record = new ArrayList<>();
                field.setLength(0);
                quoteClosed = false;
            } else if (quoteClosed && !Character.isWhitespace(ch)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Unexpected character after closing CSV quote");
            } else {
                field.append(ch);
            }
        }
        if (quoted) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unclosed quoted CSV field");
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
            if (records.size() > 1 && record.stream().anyMatch(StringUtils::hasText) && ++dataRows > maxRows) {
                throw new BusinessException(
                        ErrorCode.PARAM_ERROR, "CSV cannot exceed " + maxRows + " data rows");
            }
        }
        return records;
    }

    private void appendRecord(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            String value = values.get(i) == null ? "" : values.get(i);
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                csv.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                csv.append(value);
            }
        }
        csv.append("\r\n");
    }

    public String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    public record CsvTable(List<String> headers, List<CsvRow> rows) {
    }

    public record CsvRow(int rowNumber, Map<String, String> values) {
    }
}
