package com.codecoachai.resume.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Projects a resume document v2 back onto the legacy flat resume columns so older clients,
 * search indexes, and the ATS export path keep reading one canonical source of truth.
 *
 * <p>The projection is lossy by design: skill group labels are dropped and section content is
 * flattened to markdown-lite text, which is what the flat columns could express anyway.
 */
public final class ResumeDocumentProjector {

    private static final Map<String, String> PROJECT_COLUMN_BY_FIELD = Map.of(
            "projectBackground", "background",
            "coreFeatures", "coreFeatures",
            "technicalChallenges", "technicalChallenges",
            "optimizationResult", "outcome",
            "extraInfo", "supplement");

    private ResumeDocumentProjector() {
    }

    public static ObjectNode project(ObjectMapper mapper, JsonNode document) {
        ObjectNode result = mapper.createObjectNode();
        JsonNode source = document != null && document.isObject() ? document : mapper.createObjectNode();
        JsonNode basics = source.path("basics");

        result.put("realName", basics.path("name").asText(""));
        result.put("targetPosition", basics.path("headline").asText(""));
        result.put("phone", contactValue(basics.path("contacts"), "phone"));
        result.put("email", contactValue(basics.path("contacts"), "email"));

        result.put("summary", blocksToText(builtin(source, "summary").path("content").path("blocks")));
        result.put("skillStack", skillsToText(builtin(source, "skills")));
        result.put("workExperience", entriesToText(builtin(source, "experience")));
        result.put("educationExperience", entriesToText(builtin(source, "education")));

        ArrayNode sectionOrder = result.putArray("sectionOrder");
        ArrayNode hiddenSections = result.putArray("hiddenSections");
        for (JsonNode section : source.path("sections")) {
            String builtinKey = section.path("builtinKey").asText("");
            if (builtinKey.isEmpty()) {
                continue;
            }
            sectionOrder.add(builtinKey);
            if (!section.path("visible").asBoolean(true)) {
                hiddenSections.add(builtinKey);
            }
        }

        ArrayNode projects = result.putArray("projects");
        builtin(source, "projects").path("content").path("items").forEach(item -> {
            ObjectNode row = projects.addObject();
            if (item.path("serverId").isNumber()) {
                row.put("projectId", item.path("serverId").asLong());
            }
            row.put("projectName", item.path("name").asText(""));
            row.put("projectTime", item.path("period").asText(""));
            row.put("role", item.path("role").asText(""));
            row.put("techStack", item.path("techStack").asText(""));
            PROJECT_COLUMN_BY_FIELD.forEach((column, field) ->
                    row.put(column, blocksToText(item.path("fields").path(field))));
            row.put("sort", item.path("sort").asInt(projects.size() - 1));
        });
        return result;
    }

    /** Same text contract as the client projector, reused by the ATS export factory. */
    public static String blocksToText(JsonNode blocks) {
        List<String> lines = new ArrayList<>();
        if (blocks != null && blocks.isArray()) {
            for (JsonNode block : blocks) {
                String line = blockLine(block);
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return String.join("\n", lines);
    }

    private static String blockLine(JsonNode block) {
        String text = block.path("text").asText("");
        return "bullet".equals(block.path("kind").asText("")) ? "- " + text : text;
    }

    private static String entriesToText(JsonNode section) {
        if (!"entry".equals(section.path("kind").asText(""))) {
            return "";
        }
        return itemsToText(section.path("content").path("items"));
    }

    /** Flattens entry items to flat-column text: heading+period line, subheading, meta, bullet blocks. */
    public static String itemsToText(JsonNode items) {
        List<String> entries = new ArrayList<>();
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String heading = item.path("heading").asText("");
                String period = item.path("period").asText("");
                List<String> lines = new ArrayList<>();
                addIfPresent(lines, !heading.isEmpty() && !period.isEmpty()
                        ? heading + "    " + period
                        : (heading.isEmpty() ? period : heading));
                addIfPresent(lines, item.path("subheading").asText(""));
                addIfPresent(lines, item.path("meta").asText(""));
                for (JsonNode block : item.path("blocks")) {
                    String line = blockLine(block);
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
                entries.add(String.join("\n", lines));
            }
        }
        return String.join("\n\n", entries);
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private static String skillsToText(JsonNode section) {
        if (!"skills".equals(section.path("kind").asText(""))) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (JsonNode group : section.path("content").path("groups")) {
            for (JsonNode item : group.path("items")) {
                String value = item.asText("");
                if (!value.isEmpty()) {
                    items.add(value);
                }
            }
        }
        return String.join("、", items);
    }

    private static String contactValue(JsonNode contacts, String kind) {
        for (JsonNode contact : contacts) {
            if (kind.equals(contact.path("kind").asText(""))) {
                return contact.path("value").asText("");
            }
        }
        return "";
    }

    private static JsonNode builtin(JsonNode document, String builtinKey) {
        for (JsonNode section : document.path("sections")) {
            if (builtinKey.equals(section.path("builtinKey").asText(""))) {
                return section;
            }
        }
        return MissingNode.getInstance();
    }
}
