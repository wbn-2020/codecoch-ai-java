package com.codecoachai.resume.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            AtsResumeDocument document = new AtsResumeDocument();
            applyStyle(document, template);
            document.setName(firstText(root, "realName", "name", "title"));
            document.setHeadline(text(root, "targetPosition"));
            document.setContact(join(" | ", text(root, "phone"), text(root, "email")));
            addConfiguredSections(document, root, template);
            return document;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid resume version snapshot", ex);
        }
    }

    private void applyStyle(AtsResumeDocument document, JsonNode template) {
        if (template == null || !template.isObject()) {
            return;
        }
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
    }

    private void addConfiguredSections(AtsResumeDocument document, JsonNode root, JsonNode template) {
        List<String> order = stringArray(template == null ? null : template.get("sectionOrder"));
        if (order.isEmpty()) {
            order = List.of("SUMMARY", "SKILLS", "EXPERIENCE", "PROJECTS", "EDUCATION");
        }
        Set<String> hidden = new LinkedHashSet<>(stringArray(template == null ? null : template.get("hiddenSections")));
        for (String section : order) {
            String normalized = section.toUpperCase(java.util.Locale.ROOT);
            if (hidden.contains(normalized)) {
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

    private String join(String delimiter, String... values) {
        return String.join(delimiter, java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList());
    }
}
