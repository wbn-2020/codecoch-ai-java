package com.codecoachai.resume.support;

import com.codecoachai.resume.support.ResumeTextHeuristics.NarrativeEntry;
import com.codecoachai.resume.support.ResumeTextHeuristics.SkillGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synthesizes a resume document v2 from the legacy flat resume columns.
 *
 * <p>Ids are deterministic ({@code sec-<key>}, {@code <prefix>-e<n>-b<m>}) so synthesizing the same
 * row twice yields the same document, which keeps snapshot hashing stable.
 */
public final class ResumeDocumentMigrator {

    public static final int SCHEMA_VERSION = 2;

    public static final List<String> BUILTIN_SECTION_KEYS =
            List.of("summary", "skills", "experience", "projects", "education");

    private static final Map<String, String> DEFAULT_TITLES = Map.of(
            "summary", "个人总结",
            "skills", "专业技能",
            "experience", "工作经历",
            "projects", "项目经历",
            "education", "教育经历");

    private static final String WORK_FALLBACK_TITLE = "工作经历";
    private static final String EDUCATION_FALLBACK_TITLE = "教育经历";

    private static final Pattern LINE_RUN = Pattern.compile("\\n+");
    private static final Pattern BULLET_MARKER = Pattern.compile("^\\s*[-*•·]\\s+");

    private ResumeDocumentMigrator() {
    }

    public static ObjectNode emptyDocument(ObjectMapper objectMapper) {
        return toDocument(objectMapper, null, List.of(), null);
    }

    /**
     * @param legacy       object holding the flat resume columns (realName/email/phone/targetPosition/
     *                     summary/skillStack/skills/workSummary/workExperience/education/educationExperience)
     * @param projects     project rows keyed by the resume_project field aliases
     * @param presentation normalized presentation config; only sectionOrder/hiddenSections and the
     *                     layout knobs are mirrored into the document
     */
    public static ObjectNode toDocument(ObjectMapper mapper, JsonNode legacy,
                                        List<JsonNode> projects, JsonNode presentation) {
        JsonNode source = legacy != null && legacy.isObject() ? legacy : mapper.createObjectNode();
        JsonNode config = presentation != null && presentation.isObject() ? presentation : mapper.createObjectNode();

        ObjectNode document = mapper.createObjectNode();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.set("basics", buildBasics(mapper, source));
        document.set("layout", buildLayout(mapper, config));
        document.set("sections", buildSections(mapper, source, projects, config));
        return document;
    }

    public static ArrayNode defaultBuiltinSections(ObjectMapper mapper) {
        ArrayNode sections = mapper.createArrayNode();
        for (String key : BUILTIN_SECTION_KEYS) {
            sections.add(newSection(mapper, key));
        }
        return sections;
    }

    private static ObjectNode buildBasics(ObjectMapper mapper, JsonNode legacy) {
        ObjectNode basics = mapper.createObjectNode();
        basics.put("name", ResumeTextHeuristics.normalizeText(text(legacy, "realName")));
        basics.put("headline", ResumeTextHeuristics.normalizeText(text(legacy, "targetPosition")));
        ArrayNode contacts = basics.putArray("contacts");
        addContact(contacts, "phone", "电话", "phone",
                ResumeTextHeuristics.normalizeText(text(legacy, "phone")));
        addContact(contacts, "email", "邮箱", "mail",
                ResumeTextHeuristics.normalizeText(text(legacy, "email")));
        return basics;
    }

    private static void addContact(ArrayNode contacts, String kind, String label, String iconKey, String value) {
        if (value.isEmpty()) {
            return;
        }
        ObjectNode contact = contacts.addObject();
        contact.put("id", "contact-" + kind);
        contact.put("kind", kind);
        contact.put("label", label);
        contact.put("value", value);
        contact.put("iconKey", iconKey);
        contact.put("visible", true);
        contact.put("showLabel", false);
    }

    private static ObjectNode buildLayout(ObjectMapper mapper, JsonNode config) {
        ObjectNode layout = mapper.createObjectNode();
        layout.put("templateCode", textOr(config, "templateCode", "ATS_SINGLE_COLUMN"));
        layout.put("templateVersion", config.path("templateVersion").asInt(1));
        layout.put("accentColor", textOr(config, "accentColor", "default"));
        layout.put("fontFamily", textOr(config, "fontFamily", "Arial"));
        layout.put("fontScale", config.path("fontScale").asDouble(1d));
        layout.put("lineHeight", config.path("lineHeight").asDouble(1.2d));
        layout.put("sectionSpacing", config.path("sectionSpacing").asDouble(1d));
        layout.put("pageMarginPt", config.path("pageMarginPt").asDouble(42d));
        layout.put("autoOnePage", config.path("autoOnePage").asBoolean(false));
        layout.put("basicLayout", textOr(config, "basicLayout", "LEFT"));
        layout.put("iconMode", textOr(config, "iconMode", "ICON"));
        layout.put("pageSize", "A4");
        if (config.path("overrides").isObject()) {
            layout.set("overrides", config.get("overrides").deepCopy());
        }
        return layout;
    }

    private static ArrayNode buildSections(ObjectMapper mapper, JsonNode legacy,
                                          List<JsonNode> projects, JsonNode config) {
        Map<String, ObjectNode> byKey = new LinkedHashMap<>();
        for (String key : BUILTIN_SECTION_KEYS) {
            byKey.put(key, newSection(mapper, key));
        }

        writeSummaryBlocks(byKey.get("summary").putObject("content").putArray("blocks"),
                text(legacy, "summary"), "sec-summary-b");

        ArrayNode groups = byKey.get("skills").putObject("content").putArray("groups");
        writeSkillGroups(groups, firstText(legacy, "skillStack", "skills"), "sec-skills");

        String workText = firstText(legacy, "workSummary", "workExperience");
        writeEntryItems(byKey.get("experience").putObject("content").putArray("items"),
                ResumeTextHeuristics.buildNarrativeEntries(workText, WORK_FALLBACK_TITLE, "sec-work"),
                "sec-work");

        String educationText = firstText(legacy, "education", "educationExperience");
        writeEntryItems(byKey.get("education").putObject("content").putArray("items"),
                ResumeTextHeuristics.buildNarrativeEntries(educationText, EDUCATION_FALLBACK_TITLE, "sec-edu"),
                "sec-edu");

        ArrayNode projectItems = byKey.get("projects").putObject("content").putArray("items");
        if (projects != null) {
            for (int index = 0; index < projects.size(); index++) {
                projectItems.add(projectItem(mapper, projects.get(index), index));
            }
        }

        ArrayNode sections = mapper.createArrayNode();
        List<ObjectNode> ordered = reorder(byKey, config.path("sectionOrder"));
        List<String> hidden = readTextList(config.path("hiddenSections"));
        ordered.forEach(section -> {
            if (hidden.contains(section.path("builtinKey").asText())) {
                section.put("visible", false);
            }
            sections.add(section);
        });
        return sections;
    }

    private static ObjectNode newSection(ObjectMapper mapper, String key) {
        ObjectNode section = mapper.createObjectNode();
        section.put("id", "sec-" + key);
        section.put("title", DEFAULT_TITLES.get(key));
        section.put("visible", true);
        section.put("builtinKey", key);
        section.put("kind", switch (key) {
            case "summary" -> "text";
            case "skills" -> "skills";
            case "projects" -> "project";
            default -> "entry";
        });
        return section;
    }

    private static List<ObjectNode> reorder(Map<String, ObjectNode> byKey, JsonNode order) {
        List<ObjectNode> ordered = new ArrayList<>();
        if (order.isArray()) {
            for (JsonNode item : order) {
                ObjectNode section = byKey.remove(item.asText(""));
                if (section != null) {
                    ordered.add(section);
                }
            }
        }
        ordered.addAll(byKey.values());
        return ordered;
    }

    private static void writeSkillGroups(ArrayNode groups, String raw, String prefix) {
        List<String> skills = ResumeTextHeuristics.splitSkills(raw);
        if (skills.isEmpty()) {
            return;
        }
        List<SkillGroup> bucketed = ResumeTextHeuristics.groupSkills(skills);
        if (bucketed.size() <= 1) {
            writeGroup(groups.addObject(), prefix + "-g0", "", skills);
            return;
        }
        for (int index = 0; index < bucketed.size(); index++) {
            SkillGroup group = bucketed.get(index);
            writeGroup(groups.addObject(), prefix + "-g" + index, group.label(), group.items());
        }
    }

    private static void writeGroup(ObjectNode group, String id, String label, List<String> items) {
        group.put("id", id);
        group.put("label", label);
        ArrayNode values = group.putArray("items");
        items.forEach(values::add);
    }

    private static void writeEntryItems(ArrayNode items, List<NarrativeEntry> entries, String prefix) {
        for (int index = 0; index < entries.size(); index++) {
            NarrativeEntry entry = entries.get(index);
            String id = prefix + "-e" + index;
            ObjectNode item = items.addObject();
            item.put("id", id);
            item.put("heading", entry.title());
            item.put("subheading", "");
            item.put("period", entry.period());
            item.put("meta", "");
            writeBlocks(item.putArray("blocks"), entry.bullets(), id + "-b", "bullet");
        }
    }

    private static void writeBlocks(ArrayNode blocks, List<String> values, String idPrefix, String kind) {
        for (int index = 0; index < values.size(); index++) {
            ObjectNode block = blocks.addObject();
            block.put("id", idPrefix + index);
            block.put("kind", kind);
            block.put("text", values.get(index));
        }
    }

    /**
     * Mirrors the client's summary builder. A flat summary expresses bullets as a leading "- ", and
     * the projection writes them back, so migration has to read them as bullet blocks; otherwise the
     * first document write would silently eat the markers the user typed.
     */
    private static void writeSummaryBlocks(ArrayNode blocks, String value, String idPrefix) {
        for (String raw : LINE_RUN.split(ResumeTextHeuristics.normalizeText(value))) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher marker = BULLET_MARKER.matcher(line);
            boolean bullet = marker.find();
            String body = bullet ? line.substring(marker.end()).trim() : line;
            for (String sentence : ResumeTextHeuristics.splitSentences(body)) {
                int index = blocks.size();
                ObjectNode block = blocks.addObject();
                block.put("id", idPrefix + index);
                block.put("kind", bullet ? "bullet" : "line");
                block.put("text", sentence);
            }
        }
    }

    private static ObjectNode projectItem(ObjectMapper mapper, JsonNode project, int index) {
        JsonNode source = project != null && project.isObject() ? project : mapper.createObjectNode();
        Long serverId = firstLong(source, "projectId", "id");
        String id = "prj-" + (serverId != null ? serverId : index);

        ObjectNode item = mapper.createObjectNode();
        item.put("id", id);
        if (serverId != null) {
            item.put("serverId", serverId);
        }
        item.put("name", ResumeTextHeuristics.normalizeText(text(source, "projectName")));
        item.put("period", ResumeTextHeuristics.normalizeText(firstText(source, "projectTime", "projectPeriod")));
        item.put("role", ResumeTextHeuristics.normalizeText(firstText(source, "role", "responsibility")));
        item.put("techStack", ResumeTextHeuristics.normalizeText(text(source, "techStack")));

        ObjectNode fields = item.putObject("fields");
        projectField(fields, mapper, source, "background", id + "-bg-", "projectBackground", "description");
        projectField(fields, mapper, source, "coreFeatures", id + "-core-", "coreFeatures", "highlights");
        projectField(fields, mapper, source, "technicalChallenges", id + "-tech-",
                "technicalChallenges", "technicalDifficulties");
        projectField(fields, mapper, source, "outcome", id + "-out-", "optimizationResult", "optimizationResults");
        projectField(fields, mapper, source, "supplement", id + "-sup-", "extraInfo");

        Long sort = firstLong(source, "sort", "sortOrder");
        item.put("sort", sort != null ? sort : index);
        return item;
    }

    private static void projectField(ObjectNode fields, ObjectMapper mapper, JsonNode source,
                                     String key, String idPrefix, String... aliases) {
        ArrayNode blocks = mapper.createArrayNode();
        writeBlocks(blocks, ResumeTextHeuristics.splitSentences(
                ResumeTextHeuristics.normalizeText(firstText(source, aliases))), idPrefix, "bullet");
        fields.set(key, blocks);
    }

    static List<String> readTextList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
        }
        return values;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String value = text(node, field).trim();
        return value.isEmpty() ? fallback : value;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node == null ? null : node.get(field);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
            if (value != null && value.isTextual() && value.asText().matches("\\d+")) {
                return Long.parseLong(value.asText());
            }
        }
        return null;
    }
}
