package com.codecoachai.resume.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Bounds and repairs an untrusted resume document v2 payload at the API boundary: ids are issued,
 * numbers clamped, control characters stripped, arrays capped, and unknown keys dropped.
 *
 * <p>Numeric and length limits mirror {@code resume-workbench/document-normalizer.ts} so the
 * browser and the server never disagree about a payload they just exchanged.
 */
public final class ResumeDocumentNormalizer {

    public static final int SCHEMA_VERSION = ResumeDocumentMigrator.SCHEMA_VERSION;
    public static final int MAX_CUSTOM_SECTIONS = 12;
    public static final int MAX_BLOCKS_PER_FIELD = 60;
    public static final int MAX_BLOCK_TEXT_LENGTH = 2000;
    public static final int MAX_SECTION_TITLE_LENGTH = 40;
    public static final int MAX_ENTRIES_PER_SECTION = 50;
    public static final int MAX_CONTACTS = 8;

    private static final List<String> BUILTIN_KEYS = ResumeDocumentMigrator.BUILTIN_SECTION_KEYS;
    private static final Set<String> CONTACT_KINDS = Set.of("phone", "email", "url", "location", "text");
    private static final Set<String> ICON_KEYS = Set.of(
            "phone", "mail", "user", "briefcase", "graduation-cap", "circle");
    private static final Set<String> ACCENTS = Set.of(
            "default", "blue", "green", "purple", "orange", "red", "slate", "black");
    private static final Set<String> FONTS = Set.of(
            "Arial", "Microsoft YaHei", "Noto Sans SC", "Source Han Sans SC");
    private static final Set<String> AVATAR_SHAPES = Set.of("SQUARE", "ROUNDED", "CIRCLE");
    private static final Set<String> ALIGNMENTS = Set.of("LEFT", "CENTER", "RIGHT");
    private static final Set<String> ICON_MODES = Set.of("ICON", "TEXT", "HIDDEN");
    private static final Set<String> OVERRIDES = Set.of(
            "sectionOrder", "hiddenSections", "fieldVisibility", "fieldOrder",
            "basicLayout", "basicFieldOrder", "basicFieldVisibility", "basicFieldIcons",
            "iconMode", "autoOnePage",
            "fontFamily", "fontScale", "lineHeight", "sectionSpacing", "pageMarginPt", "avatar");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u001f\\u007f]");
    private static final Pattern BLANKS = Pattern.compile("\\s+");
    private static final AtomicLong GENERATED_IDS = new AtomicLong();

    private ResumeDocumentNormalizer() {
    }

    /** Returns null when the payload is not a schema v2 document. */
    public static ObjectNode normalize(ObjectMapper mapper, JsonNode source) {
        if (source == null || !source.isObject() || source.path("schemaVersion").asInt(0) != SCHEMA_VERSION) {
            return null;
        }
        ObjectNode document = mapper.createObjectNode();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.set("basics", normalizeBasics(mapper, source.path("basics")));
        document.set("layout", normalizeLayout(mapper, source.path("layout")));
        document.set("sections", normalizeSections(mapper, source.path("sections")));
        return document;
    }

    public static String normalizeJson(ObjectMapper mapper, JsonNode source) {
        try {
            ObjectNode normalized = normalize(mapper, source);
            return mapper.writeValueAsString(normalized == null
                    ? ResumeDocumentMigrator.emptyDocument(mapper)
                    : normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Resume document cannot be serialized", ex);
        }
    }

    public static JsonNode parseStored(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return ResumeDocumentMigrator.emptyDocument(mapper);
        }
        try {
            ObjectNode normalized = normalize(mapper, mapper.readTree(json));
            return normalized == null ? ResumeDocumentMigrator.emptyDocument(mapper) : normalized;
        } catch (Exception ex) {
            return ResumeDocumentMigrator.emptyDocument(mapper);
        }
    }

    private static ObjectNode normalizeBasics(ObjectMapper mapper, JsonNode source) {
        ObjectNode basics = mapper.createObjectNode();
        basics.put("name", truncate(replaceControls(text(source, "name")), 60));
        basics.put("headline", truncate(replaceControls(text(source, "headline")), 120));

        ArrayNode contacts = basics.putArray("contacts");
        for (JsonNode contact : limit(source.path("contacts"), MAX_CONTACTS)) {
            ObjectNode item = contacts.addObject();
            item.put("id", identifier(contact, "ct"));
            item.put("kind", enumValue(contact, "kind", CONTACT_KINDS, "text"));
            item.put("label", truncate(text(contact, "label"), 20));
            item.put("value", truncate(replaceControls(text(contact, "value")), 120));
            item.put("iconKey", enumValue(contact, "iconKey", ICON_KEYS, "circle"));
            item.put("visible", contact.path("visible").asBoolean(true));
            item.put("showLabel", contact.path("showLabel").asBoolean(false));
        }

        JsonNode avatar = source.path("avatar");
        if (avatar.isObject()) {
            ObjectNode normalized = basics.putObject("avatar");
            normalized.put("url", truncate(text(avatar, "url"), 500));
            normalized.put("visible", avatar.path("visible").asBoolean(false));
            normalized.put("shape", enumValue(avatar, "shape", AVATAR_SHAPES, "ROUNDED"));
            normalized.put("position", enumValue(avatar, "position", ALIGNMENTS, "LEFT"));
        }
        return basics;
    }

    private static ObjectNode normalizeLayout(ObjectMapper mapper, JsonNode source) {
        ObjectNode layout = mapper.createObjectNode();
        String templateCode = text(source, "templateCode").trim();
        layout.put("templateCode", templateCode.isEmpty() ? "ATS_SINGLE_COLUMN" : templateCode);
        layout.put("templateVersion", clampInt(source.path("templateVersion").asInt(1), 1, 99));
        layout.put("accentColor", enumValue(source, "accentColor", ACCENTS, "default"));
        layout.put("fontFamily", enumValue(source, "fontFamily", FONTS, "Arial"));
        layout.put("fontScale", clamp(source.path("fontScale").asDouble(1d), 0.86d, 1.18d, 1d));
        layout.put("lineHeight", clamp(source.path("lineHeight").asDouble(1.2d), 1d, 1.6d, 1.2d));
        layout.put("sectionSpacing", clamp(source.path("sectionSpacing").asDouble(1d), 0.7d, 1.6d, 1d));
        layout.put("pageMarginPt", clamp(source.path("pageMarginPt").asDouble(42d), 24d, 72d, 42d));
        layout.put("autoOnePage", source.path("autoOnePage").asBoolean(false));
        layout.put("basicLayout", enumValue(source, "basicLayout", ALIGNMENTS, "LEFT"));
        layout.put("iconMode", enumValue(source, "iconMode", ICON_MODES, "ICON"));
        layout.put("pageSize", "A4");

        JsonNode overrides = source.path("overrides");
        if (overrides.isObject()) {
            ObjectNode result = layout.putObject("overrides");
            Iterator<String> names = overrides.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (OVERRIDES.contains(name) && overrides.get(name).asBoolean(false)) {
                    result.put(name, true);
                }
            }
        }
        return layout;
    }

    private static ArrayNode normalizeSections(ObjectMapper mapper, JsonNode sections) {
        ArrayNode result = mapper.createArrayNode();
        Set<String> seenBuiltin = new LinkedHashSet<>();
        int customCount = 0;
        if (!sections.isArray()) {
            return result;
        }
        for (JsonNode section : sections) {
            ObjectNode normalized = normalizeSection(mapper, section);
            if (normalized == null) {
                continue;
            }
            String builtinKey = normalized.path("builtinKey").asText("");
            if (!builtinKey.isEmpty()) {
                if (!seenBuiltin.add(builtinKey)) {
                    continue;
                }
            } else if (customCount >= MAX_CUSTOM_SECTIONS) {
                continue;
            } else {
                customCount++;
            }
            result.add(normalized);
        }
        return result;
    }

    private static ObjectNode normalizeSection(ObjectMapper mapper, JsonNode section) {
        if (section == null || !section.isObject()) {
            return null;
        }
        String kind = section.path("kind").asText("");
        if (!List.of("text", "skills", "entry", "project", "custom").contains(kind)) {
            return null;
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("id", identifier(section, "sec"));
        result.put("kind", kind);
        result.put("title", sanitizeTitle(text(section, "title")));
        result.put("visible", section.path("visible").asBoolean(true));
        String builtinKey = section.path("builtinKey").asText("");
        if (BUILTIN_KEYS.contains(builtinKey)) {
            result.put("builtinKey", builtinKey);
        }

        JsonNode content = section.path("content");
        switch (kind) {
            case "text" -> result.putObject("content").set(
                    "blocks", normalizeBlocks(mapper, content.path("blocks")));
            case "skills" -> {
                ArrayNode groups = result.putObject("content").putArray("groups");
                for (JsonNode group : limit(content.path("groups"), MAX_CUSTOM_SECTIONS)) {
                    ObjectNode item = groups.addObject();
                    item.put("id", identifier(group, "grp"));
                    item.put("label", sanitizeTitle(text(group, "label")));
                    ArrayNode values = item.putArray("items");
                    for (JsonNode value : limit(group.path("items"), 100)) {
                        String skill = truncate(replaceControls(value.asText("")).trim(), 80);
                        if (!skill.isEmpty()) {
                            values.add(skill);
                        }
                    }
                }
            }
            case "entry" -> result.putObject("content").set(
                    "items", normalizeEntryItems(mapper, content.path("items")));
            case "project" -> {
                ArrayNode items = result.putObject("content").putArray("items");
                for (JsonNode project : limit(content.path("items"), MAX_ENTRIES_PER_SECTION)) {
                    items.add(normalizeProjectItem(mapper, project));
                }
            }
            case "custom" -> {
                result.remove("builtinKey");
                if ("entry".equals(section.path("variant").asText(""))) {
                    result.put("variant", "entry");
                    result.putObject("content").set(
                            "items", normalizeEntryItems(mapper, content.path("items")));
                } else {
                    result.put("variant", "text");
                    result.putObject("content").set(
                            "blocks", normalizeBlocks(mapper, content.path("blocks")));
                }
            }
            default -> {
                return result;
            }
        }
        return result;
    }

    private static ArrayNode normalizeEntryItems(ObjectMapper mapper, JsonNode items) {
        ArrayNode result = mapper.createArrayNode();
        for (JsonNode item : limit(items, MAX_ENTRIES_PER_SECTION)) {
            ObjectNode normalized = result.addObject();
            normalized.put("id", identifier(item, "item"));
            normalized.put("heading", truncate(replaceControls(text(item, "heading")), 120));
            normalized.put("subheading", truncate(replaceControls(text(item, "subheading")), 120));
            normalized.put("period", truncate(replaceControls(text(item, "period")), 60));
            normalized.put("meta", truncate(replaceControls(text(item, "meta")), 120));
            normalized.set("blocks", normalizeBlocks(mapper, item.path("blocks")));
        }
        return result;
    }

    private static ObjectNode normalizeProjectItem(ObjectMapper mapper, JsonNode project) {
        ObjectNode item = mapper.createObjectNode();
        item.put("id", identifier(project, "prj"));
        long serverId = project.path("serverId").asLong(0);
        if (project.path("serverId").isNumber() && serverId > 0) {
            item.put("serverId", serverId);
        }
        item.put("name", truncate(replaceControls(text(project, "name")), 120));
        item.put("period", truncate(replaceControls(text(project, "period")), 60));
        item.put("role", truncate(replaceControls(text(project, "role")), 60));
        item.put("techStack", truncate(replaceControls(text(project, "techStack")), 200));
        ObjectNode fields = item.putObject("fields");
        for (String field : List.of("background", "coreFeatures", "technicalChallenges",
                "outcome", "supplement")) {
            fields.set(field, normalizeBlocks(mapper, project.path("fields").path(field)));
        }
        if (project.path("sort").isNumber()) {
            item.put("sort", project.path("sort").asLong());
        }
        return item;
    }

    private static ArrayNode normalizeBlocks(ObjectMapper mapper, JsonNode blocks) {
        ArrayNode result = mapper.createArrayNode();
        for (JsonNode block : limit(blocks, MAX_BLOCKS_PER_FIELD)) {
            ObjectNode normalized = result.addObject();
            normalized.put("id", identifier(block, "blk"));
            String kind = block.path("kind").asText("");
            normalized.put("kind", "bullet".equals(kind) || "ordered".equals(kind) ? kind : "line");
            normalized.put("text", truncate(
                    replaceControls(block.path("text").asText("")), MAX_BLOCK_TEXT_LENGTH));
        }
        return result;
    }

    private static List<JsonNode> limit(JsonNode node, int max) {
        List<JsonNode> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (values.size() >= max) {
                    break;
                }
                values.add(item);
            }
        }
        return values;
    }

    private static String identifier(JsonNode node, String prefix) {
        String existing = text(node, "id").trim();
        if (!existing.isEmpty()) {
            return truncate(existing, 64);
        }
        return nextId(prefix);
    }

    static String nextId(String prefix) {
        return prefix + "-s" + Long.toString(System.currentTimeMillis(), 36)
                + "-" + Long.toString(GENERATED_IDS.incrementAndGet() % 1_000_000, 36);
    }

    private static String sanitizeTitle(String value) {
        return truncate(BLANKS.matcher(CONTROL_CHARS.matcher(value).replaceAll(""))
                .replaceAll(" ").trim(), MAX_SECTION_TITLE_LENGTH);
    }

    private static String replaceControls(String value) {
        return CONTROL_CHARS.matcher(value).replaceAll(" ");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.isContainerNode() ? "" : value.asText("");
    }

    private static String enumValue(JsonNode node, String field, Set<String> allowed, String fallback) {
        String value = text(node, field).trim();
        return allowed.contains(value) ? value : fallback;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
