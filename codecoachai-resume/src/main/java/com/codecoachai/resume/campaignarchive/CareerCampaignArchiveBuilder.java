package com.codecoachai.resume.campaignarchive;

import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

@Component
public class CareerCampaignArchiveBuilder {

    static final String SCHEMA_VERSION = "v8.campaign-archive.v2";
    static final String EVIDENCE_SECTION_SCHEMA_VERSION = "V9_EVIDENCE_ARCHIVE_SECTION_V1";
    private static final List<String> CONTENT_NAMES = List.of(
            "README.md", "campaign.json", "applications.csv", "timeline.csv", "calendar.ics",
            "interviews.json", "offers.json", "contacts.csv", "activities.csv",
            "research-snapshots.json", "agent-pulses.json", "campaign-review.md",
            "evidence_usage.json", "evidence_usage_results.json");
    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);

    private final ObjectMapper objectMapper;

    public CareerCampaignArchiveBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public CareerCampaignArchiveModels.ArchiveResult build(
            CareerCampaignArchiveModels.ArchiveBundle bundle,
            LocalDateTime dataCutoffAt,
            String sourceHash,
            CareerCampaignArchiveProperties properties) throws IOException {
        if (bundle == null || bundle.getCampaign() == null) {
            throw new IOException("求职周期档案来源缺失。");
        }
        Map<String, byte[]> contents = new LinkedHashMap<>();
        contents.put("README.md", readme(bundle, dataCutoffAt, sourceHash).getBytes(StandardCharsets.UTF_8));
        contents.put("campaign.json", json(bundle.getCampaign()));
        contents.put("applications.csv", applicationsCsv(bundle.getApplications()));
        contents.put("timeline.csv", timelineCsv(bundle.getTimeline()));
        contents.put("calendar.ics", calendarIcs(bundle.getCalendar()).getBytes(StandardCharsets.UTF_8));
        contents.put("interviews.json", json(bundle.getInterviews()));
        contents.put("offers.json", json(bundle.getOffers()));
        contents.put("contacts.csv", contactsCsv(bundle.getContacts()));
        contents.put("activities.csv", activitiesCsv(bundle.getActivities()));
        contents.put("research-snapshots.json", researchJson(bundle.getResearchSnapshots()));
        contents.put("agent-pulses.json", json(bundle.getAgentPulses() == null
                ? objectMapper.createArrayNode() : bundle.getAgentPulses()));
        contents.put("campaign-review.md",
                safeMarkdown(bundle.getCampaignReviewMarkdown()).getBytes(StandardCharsets.UTF_8));
        contents.put("evidence_usage.json", evidenceUsageJson(
                bundle.getEvidenceUsages(), dataCutoffAt,
                bundle.getEvidenceUsageSection()));
        contents.put("evidence_usage_results.json", evidenceUsageJson(
                bundle.getEvidenceUsageResults(), dataCutoffAt,
                bundle.getEvidenceUsageResultsSection()));

        if (!contents.keySet().equals(new java.util.LinkedHashSet<>(CONTENT_NAMES))) {
            throw new IOException("求职周期档案内容契约无效。");
        }
        long total = 0L;
        List<CareerCampaignArchiveModels.ManifestFile> files = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
            validateEntryName(entry.getKey());
            long size = entry.getValue().length;
            if (size > properties.effectiveMaxEntryBytes()) {
                throw new IOException("求职周期档案单文件超过大小限制：" + entry.getKey());
            }
            total = Math.addExact(total, size);
            files.add(new CareerCampaignArchiveModels.ManifestFile(
                    entry.getKey(), size, ResumeArtifactHashes.sha256(entry.getValue())));
        }
        if (contents.size() + 1 > properties.effectiveMaxEntries()) {
            throw new IOException("求职周期档案文件数量超过限制。");
        }
        if (total > properties.effectiveMaxTotalUncompressedBytes()) {
            throw new IOException("求职周期档案解压后总大小超过限制。");
        }

        CareerCampaignArchiveModels.Manifest manifest = new CareerCampaignArchiveModels.Manifest();
        manifest.setSchemaVersion(SCHEMA_VERSION);
        manifest.setCampaignId(bundle.getCampaign().getId());
        manifest.setDataCutoffAt(dataCutoffAt);
        manifest.setSourceHash(sourceHash);
        manifest.setFiles(files);
        manifest.setMissingSections(cleanList(bundle.getMissingSections()));
        manifest.setWarnings(cleanList(bundle.getWarnings()));
        byte[] manifestBytes = json(manifest);
        String manifestHash = ResumeArtifactHashes.sha256(manifestBytes);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(
                new SizeLimitedOutputStream(output, properties.effectiveMaxArchiveBytes()),
                StandardCharsets.UTF_8)) {
            put(zip, "manifest.json", manifestBytes);
            for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
                put(zip, entry.getKey(), entry.getValue());
            }
            zip.finish();
        }

        CareerCampaignArchiveModels.ArchiveResult result = new CareerCampaignArchiveModels.ArchiveResult();
        result.setZipBytes(output.toByteArray());
        result.setManifestHash(manifestHash);
        result.setFileSize(result.getZipBytes().length);
        return result;
    }

    static void validateEntryName(String name) throws IOException {
        if (name == null || name.isBlank() || name.indexOf('\\') >= 0
                || name.indexOf('/') >= 0 || name.startsWith(".")
                || name.contains("..") || name.indexOf('\0') >= 0) {
            throw new IOException("ZIP 文件项名称不安全。");
        }
    }

    private void put(ZipOutputStream zip, String name, byte[] value) throws IOException {
        validateEntryName(name);
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(value);
        zip.closeEntry();
    }

    private String readme(CareerCampaignArchiveModels.ArchiveBundle bundle,
                          LocalDateTime cutoff, String sourceHash) {
        StringBuilder text = new StringBuilder();
        text.append("# 求职周期档案\n\n")
                .append("这是 CodeCoachAI V8 周期档案导出，内容为截至 ")
                .append(cutoff)
                .append(" 的服务端快照。\n\n")
                .append("- 周期：").append(nullText(bundle.getCampaign().getName())).append('\n')
                .append("- 状态：").append(nullText(bundle.getCampaign().getStatus())).append('\n')
                .append("- 截点：").append(cutoff).append('\n')
                .append("- 来源哈希：").append(sourceHash).append('\n')
                .append("- 文件编码：UTF-8\n");
        if (!bundle.getMissingSections().isEmpty()) {
            text.append("\n## 缺失区块\n\n");
            bundle.getMissingSections().forEach(item -> text.append("- ").append(item).append('\n'));
        }
        if (!bundle.getWarnings().isEmpty()) {
            text.append("\n## 导出说明\n\n");
            bundle.getWarnings().forEach(item -> text.append("- ").append(item).append('\n'));
        }
        return text.toString();
    }

    private byte[] applicationsCsv(List<CareerCampaignArchiveModels.ApplicationRow> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("id,companyName,jobTitle,status,source,priorityLevel,opportunityOutcome,")
                .append("appliedAt,nextFollowUpAt,stageChangedAt,createdAt,updatedAt\n");
        for (CareerCampaignArchiveModels.ApplicationRow row : safe(rows)) {
            csv.append(csvRow(row.getId(), row.getCompanyName(), row.getJobTitle(), row.getStatus(),
                    row.getSource(), row.getPriorityLevel(), row.getOpportunityOutcome(),
                    row.getAppliedAt(), row.getNextFollowUpAt(), row.getStageChangedAt(),
                    row.getCreatedAt(), row.getUpdatedAt())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] timelineCsv(List<CareerCampaignArchiveModels.TimelineRow> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("sourceType,sourceId,applicationId,eventType,eventAt,summary\n");
        for (CareerCampaignArchiveModels.TimelineRow row : safe(rows)) {
            csv.append(csvRow(row.getSourceType(), row.getSourceId(), row.getApplicationId(),
                    row.getEventType(), row.getEventAt(), row.getSummary())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] contactsCsv(List<CareerCampaignArchiveModels.ContactRow> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("contactId,applicationId,displayName,roleType,channelType,maskedContactHint,")
                .append("relationshipType,relationshipSummary\n");
        for (CareerCampaignArchiveModels.ContactRow row : safe(rows)) {
            csv.append(csvRow(row.getContactId(), row.getApplicationId(), row.getDisplayName(),
                    row.getRoleType(), row.getChannelType(), row.getMaskedContactHint(),
                    row.getRelationshipType(), row.getRelationshipSummary())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] activitiesCsv(List<CareerCampaignArchiveModels.ActivityRow> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("id,applicationId,contactId,activityType,channelType,subject,summary,")
                .append("occurredAt,nextFollowUpAt,status\n");
        for (CareerCampaignArchiveModels.ActivityRow row : safe(rows)) {
            csv.append(csvRow(row.getId(), row.getApplicationId(), row.getContactId(),
                    row.getActivityType(), row.getChannelType(), row.getSubject(), row.getSummary(),
                    row.getOccurredAt(), row.getNextFollowUpAt(), row.getStatus())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String calendarIcs(List<CareerCampaignArchiveModels.CalendarRow> rows) {
        StringBuilder ics = new StringBuilder("\uFEFF");
        ics.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//CodeCoachAI//Campaign Archive//EN\r\n");
        for (CareerCampaignArchiveModels.CalendarRow row : safe(rows)) {
            ics.append("BEGIN:VEVENT\r\n")
                    .append("UID:campaign-").append(row.getId()).append("@codecoachai\r\n")
                    .append("DTSTAMP:").append(utc(row.getStartsAtUtc())).append("\r\n")
                    .append("DTSTART:").append(utc(row.getStartsAtUtc())).append("\r\n");
            if (row.getEndsAtUtc() != null) {
                ics.append("DTEND:").append(utc(row.getEndsAtUtc())).append("\r\n");
            }
            ics.append("SUMMARY:").append(icsText(row.getTitle())).append("\r\n")
                    .append("DESCRIPTION:").append(icsText(row.getEventType())).append("\r\n")
                    .append("STATUS:").append(icsText(row.getStatus())).append("\r\n")
                    .append("END:VEVENT\r\n");
        }
        return ics.append("END:VCALENDAR\r\n").toString();
    }

    private byte[] researchJson(List<CareerCampaignArchiveModels.ResearchSnapshotRow> rows)
            throws JsonProcessingException {
        List<Map<String, Object>> safeRows = new ArrayList<>();
        for (CareerCampaignArchiveModels.ResearchSnapshotRow row : safe(rows)) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", row.getId());
            value.put("reportId", row.getReportId());
            value.put("applicationId", row.getApplicationId());
            value.put("sourceSetHash", row.getSourceSetHash());
            value.put("confidenceLevel", row.getConfidenceLevel());
            value.put("fallback", row.getFallback());
            value.put("createdAt", row.getCreatedAt());
            JsonNode snapshot = parseJson(row.getSnapshotJson());
            value.put("snapshot", snapshot == null ? objectMapper.createObjectNode() : snapshot);
            safeRows.add(value);
        }
        return json(safeRows);
    }

    private byte[] evidenceUsageJson(
            List<?> rows,
            LocalDateTime dataCutoffAt,
            CareerCampaignArchiveModels.SectionMetadata sectionMetadata)
            throws JsonProcessingException {
        List<?> safeRows = rows == null ? List.of() : rows;
        CareerCampaignArchiveModels.SectionMetadata metadata = sectionMetadata == null
                ? new CareerCampaignArchiveModels.SectionMetadata() : sectionMetadata;
        boolean available = metadata.isAvailable();
        List<String> missingSections = cleanList(metadata.getMissingSections());
        List<String> warnings = cleanList(metadata.getWarnings());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", EVIDENCE_SECTION_SCHEMA_VERSION);
        envelope.put("dataCutoffAt", dataCutoffAt);
        envelope.put("sourceSetHash", ResumeArtifactHashes.sha256(json(safeRows)));
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("itemCount", safeRows.size());
        coverage.put("available", available);
        envelope.put("coverage", coverage);
        envelope.put("warnings", warnings);
        envelope.put("unknowns", List.of());
        envelope.put("limits", sectionLimits(available, safeRows.size()));
        envelope.put("confidenceLevel", available && safeRows.size() >= 5 ? "MEDIUM" : "LOW");
        envelope.put("fallback", false);
        envelope.put("missingSections", missingSections);
        envelope.put("items", safeRows);
        envelope.put("contentHash", ResumeArtifactHashes.sha256(json(envelope)));
        return json(envelope);
    }

    private List<String> sectionLimits(boolean available, int itemCount) {
        if (!available) {
            return List.of("章节来源不可用，不能据此判断是否存在使用记录或结果。");
        }
        if (itemCount < 5) {
            return List.of("样本少于 5 条，仅支持事实展示。");
        }
        return List.of("当前章节仅支持有限观察，不输出强策略、排名或因果结论。");
    }

    private String safeMarkdown(String value) {
        return value == null || value.isBlank() ? "## 周期复盘\n\n当前没有可导出的周期复盘摘要。\n" : value;
    }

    private byte[] json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(value);
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> cleanList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
    }

    private String csvRow(Object... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvCell(values[i]));
        }
        return row.toString();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf('"') >= 0 || text.indexOf(',') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private String icsText(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace(";", "\\;").replace(",", "\\,")
                .replace("\r", "").replace("\n", "\\n");
    }

    private String utc(LocalDateTime value) {
        return value == null ? "" : value.toInstant(ZoneOffset.UTC).atOffset(ZoneOffset.UTC)
                .format(UTC_FORMAT);
    }

    private String nullText(String value) {
        return value == null || value.isBlank() ? "未命名周期" : value;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class SizeLimitedOutputStream extends java.io.OutputStream {
        private final ByteArrayOutputStream delegate;
        private final long limit;

        private SizeLimitedOutputStream(ByteArrayOutputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            ensure(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ensure(length);
            delegate.write(bytes, offset, length);
        }

        private void ensure(long size) throws IOException {
            if ((long) delegate.size() + size > limit) {
                throw new IOException("求职周期档案压缩后大小超过限制。");
            }
        }
    }
}
