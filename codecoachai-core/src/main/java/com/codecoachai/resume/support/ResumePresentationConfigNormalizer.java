package com.codecoachai.resume.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps resume presentation configuration data-only and bounded at the API,
 * snapshot, and export boundaries.
 */
public final class ResumePresentationConfigNormalizer {

    public static final int SCHEMA_VERSION = 1;

    private static final List<String> SECTIONS = List.of(
            "summary", "experience", "projects", "skills", "education");
    private static final List<String> FIELDS = List.of(
            "realName", "targetPosition", "email", "phone", "summary",
            "skills", "workExperience", "projects", "educationExperience");
    private static final List<String> BASIC_FIELDS = List.of(
            "realName", "targetPosition", "email", "phone");
    private static final Set<String> BASIC_FIELD_ICONS = Set.of(
            "user", "phone", "mail", "briefcase", "graduation-cap", "circle");
    private static final List<String> MODULES = List.of(
            "resume-basic", "resume-target", "resume-skills", "resume-projects", "resume-experience");
    private static final Set<String> TEMPLATES = Set.of(
            "ATS_SINGLE_COLUMN", "ATS_COMPACT", "ATS_PROJECT_FOCUS",
            "ATS_CLASSIC_SIDEBAR", "ATS_STREAK_SIGNATURE",
            "MAGIC_TIMELINE", "MAGIC_MINIMALIST", "MAGIC_ELEGANT",
            "MAGIC_CREATIVE", "MAGIC_EDITORIAL", "MAGIC_SWISS");
    private static final Set<String> FONTS = Set.of(
            "Arial", "Microsoft YaHei", "Noto Sans SC", "Source Han Sans SC");
    private static final Set<String> ACCENTS = Set.of(
            "default", "blue", "green", "purple", "orange", "red", "slate", "black");
    private static final Set<String> OVERRIDES = Set.of(
            "sectionOrder", "hiddenSections", "fieldVisibility", "fieldOrder",
            "basicLayout", "basicFieldOrder", "basicFieldVisibility", "basicFieldIcons",
            "iconMode", "autoOnePage",
            "fontFamily", "fontScale", "lineHeight", "sectionSpacing", "pageMarginPt", "avatar");

    private ResumePresentationConfigNormalizer() {
    }

    public static ObjectNode normalize(ObjectMapper objectMapper, JsonNode source) {
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode value = source != null && source.isObject()
                ? (ObjectNode) source
                : objectMapper.createObjectNode();

        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("templateCode", enumValue(value, "templateCode", TEMPLATES, "ATS_SINGLE_COLUMN"));
        result.put("templateVersion", integerValue(value, "templateVersion", 1, 1, 100));

        ArrayNode moduleOrder = result.putArray("moduleOrder");
        appendUniqueModules(moduleOrder, value.get("moduleOrder"), true);
        appendMissing(moduleOrder, MODULES);

        ArrayNode hiddenModules = result.putArray("hiddenModules");
        appendUniqueModules(hiddenModules, value.get("hiddenModules"), false);

        ArrayNode sectionOrder = result.putArray("sectionOrder");
        appendUniqueSections(sectionOrder, value.get("sectionOrder"), SECTIONS);
        appendMissing(sectionOrder, SECTIONS);

        ArrayNode hiddenSections = result.putArray("hiddenSections");
        appendUniqueSections(hiddenSections, value.get("hiddenSections"), SECTIONS);

        ObjectNode fieldVisibility = result.putObject("fieldVisibility");
        JsonNode rawFieldVisibility = value.get("fieldVisibility");
        for (String field : FIELDS) {
            fieldVisibility.put(field, rawFieldVisibility != null
                    && rawFieldVisibility.isObject()
                    && rawFieldVisibility.has(field)
                    ? rawFieldVisibility.get(field).asBoolean(true)
                    : true);
        }

        ObjectNode fieldOrder = result.putObject("fieldOrder");
        JsonNode rawFieldOrder = value.get("fieldOrder");
        if (rawFieldOrder != null && rawFieldOrder.isObject()) {
            rawFieldOrder.fields().forEachRemaining(entry -> {
                if (!FIELDS.contains(entry.getKey()) || !entry.getValue().isArray()) {
                    return;
                }
                ArrayNode values = fieldOrder.putArray(entry.getKey());
                Set<String> seen = new LinkedHashSet<>();
                int count = 0;
                for (JsonNode item : entry.getValue()) {
                    String field = item.isTextual() ? item.asText().trim() : "";
                    if (count >= 16
                            || !item.isTextual()
                            || field.isBlank()
                            || !FIELDS.contains(field)
                            || !seen.add(field)) {
                        continue;
                    }
                    values.add(field);
                    count++;
                }
            });
        }

        result.put("basicLayout", enumValue(value, "basicLayout",
                Set.of("LEFT", "CENTER", "RIGHT"), "LEFT"));
        ArrayNode basicFieldOrder = result.putArray("basicFieldOrder");
        appendUniqueFields(basicFieldOrder, value.get("basicFieldOrder"));
        appendMissing(basicFieldOrder, BASIC_FIELDS);

        ObjectNode basicFieldVisibility = result.putObject("basicFieldVisibility");
        JsonNode rawBasicFieldVisibility = value.get("basicFieldVisibility");
        for (String field : BASIC_FIELDS) {
            basicFieldVisibility.put(field, booleanValue(
                    rawBasicFieldVisibility, field, booleanValue(rawFieldVisibility, field, true)));
        }

        ObjectNode basicFieldIcons = result.putObject("basicFieldIcons");
        JsonNode rawBasicFieldIcons = value.get("basicFieldIcons");
        for (String field : BASIC_FIELDS) {
            basicFieldIcons.put(field, enumValue(
                    rawBasicFieldIcons, field, BASIC_FIELD_ICONS, defaultBasicFieldIcon(field)));
        }
        result.put("iconMode", enumValue(value, "iconMode",
                Set.of("ICON", "TEXT", "HIDDEN"), "ICON"));
        result.put("autoOnePage", booleanValue(value, "autoOnePage", false));

        String rawAccent = value.has("accentColor") ? value.get("accentColor").asText("") : "";
        String accent = switch (rawAccent) {
            case "ocean" -> "blue";
            case "teal" -> "green";
            case "graphite" -> "slate";
            case "berry" -> "red";
            default -> rawAccent;
        };
        result.put("accentColor", ACCENTS.contains(accent) ? accent : "default");
        result.put("fontFamily", enumValue(value, "fontFamily", FONTS, "Arial"));
        result.put("fontScale", decimalValue(value, "fontScale", 1d, 0.86d, 1.18d));
        result.put("lineHeight", decimalValue(value, "lineHeight", 1.2d, 1d, 1.6d));
        result.put("sectionSpacing", decimalValue(value, "sectionSpacing", 1d, 0.7d, 1.6d));
        result.put("pageMarginPt", decimalValue(value, "pageMarginPt", 42d, 24d, 72d));

        ObjectNode overrides = result.putObject("overrides");
        JsonNode rawOverrides = value.get("overrides");
        if (rawOverrides != null && rawOverrides.isObject()) {
            rawOverrides.fields().forEachRemaining(entry -> {
                if (OVERRIDES.contains(entry.getKey()) && entry.getValue().asBoolean(false)) {
                    overrides.put(entry.getKey(), true);
                }
            });
        }

        ObjectNode avatar = result.putObject("avatar");
        JsonNode rawAvatar = value.get("avatar");
        avatar.put("visible", rawAvatar != null && rawAvatar.isObject()
                && rawAvatar.path("visible").asBoolean(false));
        avatar.put("position", enumValue(rawAvatar, "position",
                Set.of("LEFT", "CENTER", "RIGHT"), "RIGHT"));
        avatar.put("shape", enumValue(rawAvatar, "shape",
                Set.of("SQUARE", "ROUNDED", "CIRCLE"), "SQUARE"));
        return result;
    }

    public static String normalizeJson(ObjectMapper objectMapper, JsonNode source) {
        try {
            return objectMapper.writeValueAsString(normalize(objectMapper, source));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Resume presentation configuration cannot be serialized", ex);
        }
    }

    public static JsonNode parseStored(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return normalize(objectMapper, null);
        }
        try {
            return normalize(objectMapper, objectMapper.readTree(json));
        } catch (Exception ex) {
            return normalize(objectMapper, null);
        }
    }

    private static void appendUniqueSections(ArrayNode target, JsonNode source, List<String> allowed) {
        if (source == null || !source.isArray()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : source) {
            if (!item.isTextual()) {
                continue;
            }
            String normalized = item.asText().trim().toLowerCase(Locale.ROOT);
            if (allowed.contains(normalized) && seen.add(normalized)) {
                target.add(normalized);
            }
        }
    }

    private static void appendMissing(ArrayNode target, List<String> allowed) {
        Set<String> existing = new LinkedHashSet<>();
        target.forEach(item -> existing.add(item.asText()));
        allowed.stream()
                .filter(section -> !existing.contains(section))
                .forEach(target::add);
    }

    private static void appendUniqueFields(ArrayNode target, JsonNode source) {
        if (source == null || !source.isArray()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : source) {
            if (!item.isTextual()) {
                continue;
            }
            String field = item.asText().trim();
            if (BASIC_FIELDS.contains(field) && seen.add(field)) {
                target.add(field);
            }
        }
    }

    private static String defaultBasicFieldIcon(String field) {
        return switch (field) {
            case "realName" -> "user";
            case "targetPosition" -> "briefcase";
            case "email" -> "mail";
            case "phone" -> "phone";
            default -> "circle";
        };
    }

    private static void appendUniqueModules(ArrayNode target, JsonNode source, boolean allowProtected) {
        if (source == null || !source.isArray()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : source) {
            if (!item.isTextual()) {
                continue;
            }
            String module = item.asText().trim();
            if (MODULES.contains(module)
                    && (allowProtected
                    || (!"resume-basic".equals(module) && !"resume-target".equals(module)))
                    && seen.add(module)) {
                target.add(module);
            }
        }
    }

    private static String enumValue(JsonNode source, String field, Set<String> allowed, String fallback) {
        JsonNode value = source == null ? null : source.get(field);
        String candidate = value == null || !value.isTextual()
                ? fallback
                : value.asText().trim();
        return allowed.contains(candidate) ? candidate : fallback;
    }

    private static int integerValue(JsonNode source, String field, int fallback, int min, int max) {
        JsonNode value = source == null ? null : source.get(field);
        int candidate = value != null && value.isNumber() ? value.asInt(fallback) : fallback;
        return Math.max(min, Math.min(max, candidate));
    }

    private static boolean booleanValue(JsonNode source, String field, boolean fallback) {
        JsonNode value = source == null ? null : source.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }

    private static double decimalValue(
            JsonNode source, String field, double fallback, double min, double max) {
        JsonNode value = source == null ? null : source.get(field);
        double candidate = value != null && value.isNumber() ? value.asDouble(fallback) : fallback;
        if (!Double.isFinite(candidate)) {
            candidate = fallback;
        }
        return Math.max(min, Math.min(max, candidate));
    }
}
