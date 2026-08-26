package com.codecoachai.resume.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codecoachai.resume.support.ResumePresentationConfigNormalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AtsResumeDocumentFactory {

    private static final List<String> PROJECT_FIELDS = List.of(
            "projectName", "projectPeriod", "role", "techStack", "projectBackground",
            "responsibility", "coreFeatures", "technicalDifficulties", "optimizationResults",
            "description", "highlights");

    private final ObjectMapper objectMapper;

    public AtsResumeDocumentFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AtsResumeDocument fromSnapshot(String snapshotJson) {
        return fromSnapshot(snapshotJson, null);
    }

    public AtsResumeDocument fromSnapshot(String snapshotJson, String templateDefinitionJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            JsonNode template = StringUtils.hasText(templateDefinitionJson)
                    ? objectMapper.readTree(templateDefinitionJson)
                    : null;
            JsonNode rawPresentation = root == null ? null : root.get("presentationConfig");
            JsonNode presentation = rawPresentation != null && rawPresentation.isObject()
                    ? ResumePresentationConfigNormalizer.normalize(objectMapper, rawPresentation)
                    : null;
            AtsResumeDocument document = new AtsResumeDocument();
            applyStyle(document, template, presentation, rawPresentation);
            document.setName(isVisible(presentation, rawPresentation, "realName")
                    ? firstText(root, "realName", "name", "title")
                    : "");
            document.setHeadline(isVisible(presentation, rawPresentation, "targetPosition")
                    ? text(root, "targetPosition")
                    : "");
            document.setContact(contactText(root, presentation, rawPresentation));
            addConfiguredSections(document, root, template, presentation, rawPresentation);
            return document;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid resume version snapshot", ex);
        }
    }

    private void applyStyle(
            AtsResumeDocument document, JsonNode template, JsonNode presentation, JsonNode rawPresentation) {
        AtsResumeDocument.Style style = document.getStyle();
        style.setMarginPt(floatValue(template, "marginPt", style.getMarginPt(), 24f, 72f));
        style.setNameFontPt(floatValue(template, "nameFontPt", style.getNameFontPt(), 14f, 24f));
        style.setHeadlineFontPt(floatValue(template, "headlineFontPt", style.getHeadlineFontPt(), 9f, 16f));
        style.setContactFontPt(floatValue(template, "contactFontPt", style.getContactFontPt(), 8f, 13f));
        style.setHeadingFontPt(floatValue(template, "headingFontPt", style.getHeadingFontPt(), 9f, 16f));
        style.setBodyFontPt(floatValue(template, "bodyFontPt", style.getBodyFontPt(), 8f, 14f));
        style.setLineSpacing(floatValue(template, "lineSpacing", style.getLineSpacing(), 1f, 1.6f));
        if (StringUtils.hasText(text(template, "fontFamily"))) {
            style.setFontFamily(text(template, "fontFamily").trim());
        }
        if (presentation != null && presentation.isObject()) {
            if (hasPresentationOverride(rawPresentation, "pageMarginPt")) {
                style.setMarginPt(floatValue(presentation, "pageMarginPt", style.getMarginPt(), 24f, 72f));
            }
            float scale = floatValue(presentation, "fontScale", 1f, 0.86f, 1.18f);
            if (hasPresentationOverride(rawPresentation, "fontScale")) {
                style.setNameFontPt(style.getNameFontPt() * scale);
                style.setHeadlineFontPt(style.getHeadlineFontPt() * scale);
                style.setContactFontPt(style.getContactFontPt() * scale);
                style.setHeadingFontPt(style.getHeadingFontPt() * scale);
                style.setBodyFontPt(style.getBodyFontPt() * scale);
            }
            if (hasPresentationOverride(rawPresentation, "lineHeight")) {
                style.setLineSpacing(floatValue(presentation, "lineHeight", style.getLineSpacing(), 1f, 1.6f));
            }
            if (hasPresentationOverride(rawPresentation, "sectionSpacing")) {
                style.setSectionSpacing(floatValue(
                        presentation, "sectionSpacing", style.getSectionSpacing(), 0.7f, 1.6f));
            }
            if (hasPresentationOverride(rawPresentation, "autoOnePage")
                    && presentation.path("autoOnePage").asBoolean(false)) {
                style.setAutoOnePage(true);
                style.setLineSpacing(Math.max(1f, style.getLineSpacing() * 0.9f));
                style.setSectionSpacing(Math.max(0.7f, style.getSectionSpacing() * 0.82f));
                style.setNameFontPt(style.getNameFontPt() * 0.92f);
                style.setHeadlineFontPt(style.getHeadlineFontPt() * 0.92f);
                style.setContactFontPt(style.getContactFontPt() * 0.92f);
                style.setHeadingFontPt(style.getHeadingFontPt() * 0.92f);
                style.setBodyFontPt(style.getBodyFontPt() * 0.92f);
            }
            if (hasPresentationOverride(rawPresentation, "basicLayout")) {
                style.setIdentityAlignment(enumValue(
                        presentation, "basicLayout", Set.of("LEFT", "CENTER", "RIGHT"), "CENTER"));
            }
            if (hasPresentationOverride(rawPresentation, "fontFamily")
                    && StringUtils.hasText(text(presentation, "fontFamily"))) {
                style.setFontFamily(text(presentation, "fontFamily").trim());
            }
        }
    }

    private void addConfiguredSections(
            AtsResumeDocument document,
            JsonNode root,
            JsonNode template,
            JsonNode presentation,
            JsonNode rawPresentation) {
        List<String> order = hasPresentationOverride(rawPresentation, "sectionOrder")
                ? stringArray(presentation == null ? null : presentation.get("sectionOrder"))
                : List.of();
        if (order.isEmpty()) {
            order = stringArray(template == null ? null : template.get("sectionOrder"));
        }
        if (order.isEmpty()) {
            order = List.of("SUMMARY", "SKILLS", "EXPERIENCE", "PROJECTS", "EDUCATION");
        }
        Set<String> hidden = new LinkedHashSet<>();
        if (hasPresentationOverride(rawPresentation, "hiddenSections")) {
            hidden.addAll(stringArray(presentation == null ? null : presentation.get("hiddenSections")));
        } else {
            hidden.addAll(stringArray(template == null ? null : template.get("hiddenSections")));
        }
        for (String section : order) {
            String normalized = section.toUpperCase(java.util.Locale.ROOT);
            if (hidden.contains(normalized) || !sectionVisible(presentation, rawPresentation, normalized)) {
                continue;
            }
            switch (normalized) {
                case "SUMMARY" -> addSection(document, "Professional Summary", values(root.get("summary")));
                case "SKILLS" -> addSection(document, "Skills", values(root.get("skillStack")));
                case "EXPERIENCE" -> addSection(document, "Experience", values(root.get("workExperience")));
                case "PROJECTS" -> addProjects(document, root.get("projects"));
                case "EDUCATION" -> addSection(document, "Education", values(root.get("educationExperience")));
                default -> {
                    // Ignore unknown template sections instead of emitting invented resume content.
                }
            }
        }
    }

    private boolean sectionVisible(JsonNode presentation, JsonNode rawPresentation, String section) {
        return switch (section) {
            case "SUMMARY" -> isVisible(presentation, rawPresentation, "summary");
            case "SKILLS" -> isVisible(presentation, rawPresentation, "skills");
            case "EXPERIENCE" -> isVisible(presentation, rawPresentation, "workExperience");
            case "PROJECTS" -> isVisible(presentation, rawPresentation, "projects");
            case "EDUCATION" -> isVisible(presentation, rawPresentation, "educationExperience");
            default -> false;
        };
    }

    private boolean isVisible(JsonNode presentation, JsonNode rawPresentation, String field) {
        if (presentation != null
                && Set.of("realName", "targetPosition", "email", "phone").contains(field)
                && hasPresentationOverride(rawPresentation, "basicFieldVisibility")
                && presentation.path("basicFieldVisibility").has(field)) {
            return presentation.path("basicFieldVisibility").path(field).asBoolean(true);
        }
        return presentation == null
                || !hasPresentationOverride(rawPresentation, "fieldVisibility")
                || !presentation.has("fieldVisibility")
                || presentation.path("fieldVisibility").path(field).asBoolean(true);
    }

    private String contactText(JsonNode root, JsonNode presentation, JsonNode rawPresentation) {
        if (presentation == null) {
            return join(" | ", text(root, "phone"), text(root, "email"));
        }
        List<String> fields = new ArrayList<>();
        JsonNode order = presentation.get("basicFieldOrder");
        if (order != null && order.isArray()) {
            order.forEach(item -> {
                if (!item.isTextual()) return;
                String field = item.asText();
                if (("phone".equals(field) || "email".equals(field))
                        && isVisible(presentation, rawPresentation, field)
                        && !fields.contains(field)) {
                    fields.add(field);
                }
            });
        }
        if (fields.isEmpty()) {
            fields.add("phone");
            fields.add("email");
        }
        String mode = enumValue(presentation, "iconMode", Set.of("ICON", "TEXT", "HIDDEN"), "ICON");
        return fields.stream()
                .map(field -> contactValue(root, field, mode))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    private String contactValue(JsonNode root, String field, String mode) {
        String value = text(root, field);
        if (!StringUtils.hasText(value)) return null;
        if ("TEXT".equals(mode)) {
            return ("phone".equals(field) ? "电话: " : "邮箱: ") + value;
        }
        return value;
    }

    private boolean hasPresentationOverride(JsonNode rawPresentation, String field) {
        if (rawPresentation == null || !rawPresentation.isObject()) {
            return false;
        }
        JsonNode overrides = rawPresentation.get("overrides");
        if (overrides != null && overrides.isObject()) {
            return overrides.path(field).asBoolean(false);
        }
        // Pre-schema snapshots may contain only the fields the user explicitly changed.
        return !rawPresentation.has("schemaVersion") && rawPresentation.has(field);
    }

    private List<String> stringArray(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        value.forEach(item -> {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim().toUpperCase(java.util.Locale.ROOT));
            }
        });
        return values;
    }

    private float floatValue(JsonNode source, String field, float fallback, float min, float max) {
        JsonNode value = source == null ? null : source.get(field);
        if (value == null || !value.isNumber()) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value.floatValue()));
    }

    private void addProjects(AtsResumeDocument document, JsonNode projects) {
        JsonNode normalized = normalize(projects);
        if (normalized == null || !normalized.isArray()) {
            addSection(document, "Projects", values(normalized));
            return;
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode project : normalized) {
            List<String> values = new ArrayList<>();
            for (String field : PROJECT_FIELDS) {
                String value = text(project, field);
                if (StringUtils.hasText(value)) {
                    values.add(value.trim());
                }
            }
            if (!values.isEmpty()) {
                lines.add(String.join(" - ", values));
            }
        }
        addSection(document, "Projects", lines);
    }

    private void addSection(AtsResumeDocument document, String heading, List<String> lines) {
        List<String> clean = lines.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (!clean.isEmpty()) {
            document.getSections().add(new AtsResumeDocument.Section(heading, clean));
        }
    }

    private List<String> values(JsonNode value) {
        JsonNode normalized = normalize(value);
        if (normalized == null || normalized.isNull() || normalized.isMissingNode()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        flatten(normalized, values);
        return List.copyOf(values);
    }

    private void flatten(JsonNode node, Set<String> output) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            String value = node.asText();
            if (StringUtils.hasText(value)) {
                for (String line : value.split("\\r?\\n|(?<=[。.!?；;])\\s*")) {
                    if (StringUtils.hasText(line)) {
                        output.add(line.trim());
                    }
                }
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> flatten(item, output));
            return;
        }
        Iterator<JsonNode> values = node.elements();
        while (values.hasNext()) {
            flatten(values.next(), output);
        }
    }

    private JsonNode normalize(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return value;
        }
        String text = value.asText().trim();
        if (!(text.startsWith("{") || text.startsWith("["))) {
            return value;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            String value = text(root, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "Resume";
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String enumValue(JsonNode source, String field, Set<String> allowed, String fallback) {
        JsonNode value = source == null ? null : source.get(field);
        String candidate = value == null || !value.isTextual() ? fallback : value.asText().trim();
        return allowed.contains(candidate) ? candidate : fallback;
    }

    private String join(String delimiter, String... values) {
        return String.join(delimiter, java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList());
    }
}
