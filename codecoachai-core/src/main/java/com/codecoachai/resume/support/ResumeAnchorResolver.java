package com.codecoachai.resume.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Identity-style document anchors, safe across reordering because they address sections and items
 * by id instead of array position.
 *
 * <p>Path grammar (segments separated by {@code /}):
 * <pre>
 *   section:&lt;id&gt;                              whole section as plain text
 *   section:&lt;id&gt;/block:&lt;id&gt;                   one block of a text/custom section
 *   section:&lt;id&gt;/group:&lt;idx&gt;                  one skill group
 *   section:&lt;id&gt;/item:&lt;id&gt;                    one entry or project item
 *   section:&lt;id&gt;/item:&lt;id&gt;/field:&lt;name&gt;       scalar field of an item
 *   section:&lt;id&gt;/item:&lt;id&gt;/blocks:&lt;field&gt;     block group of an item
 *   section:&lt;id&gt;/item:&lt;id&gt;/blocks:&lt;field&gt;/block:&lt;id&gt;
 * </pre>
 */
public final class ResumeAnchorResolver {

    private static final List<String> ENTRY_FIELDS = List.of("heading", "subheading", "period", "meta");
    private static final List<String> PROJECT_FIELDS = List.of("name", "period", "role", "techStack");
    private static final Pattern BULLET_LINE = Pattern.compile("^\\s*(?:[-*•·])\\s");
    private static final Pattern ORDERED_LINE = Pattern.compile("^\\s*\\d+[.)、]\\s");
    private static final Pattern LINE_MARKER = Pattern.compile("^\\s*(?:[-*•·]|\\d+[.)、])\\s*");
    private static final Pattern GROUP_DELIMITERS = Pattern.compile("[、,，]");

    private ResumeAnchorResolver() {
    }

    record Anchor(String sectionId, List<String[]> segments) {
    }

    public static String resolveText(JsonNode document, String path) {
        Anchor anchor = parse(path);
        if (anchor == null) {
            return null;
        }
        JsonNode section = findSection(document, anchor.sectionId());
        if (section == null) {
            return null;
        }
        List<String[]> segments = anchor.segments();
        if (segments.isEmpty()) {
            return sectionPlainText(section);
        }

        String firstKey = segments.get(0)[0];
        String firstValue = segments.get(0)[1];
        if ("block".equals(firstKey) && segments.size() == 1) {
            JsonNode block = findById(sectionBlocks(section), firstValue);
            return block == null ? null : block.path("text").asText("");
        }
        if ("group".equals(firstKey) && "skills".equals(section.path("kind").asText(""))) {
            JsonNode groups = section.path("content").path("groups");
            int index = toIndex(firstValue);
            if (index < 0 || index >= groups.size()) {
                return null;
            }
            return groupText(groups.get(index));
        }
        if (!"item".equals(firstKey)) {
            return null;
        }

        boolean isProject = "project".equals(section.path("kind").asText(""));
        JsonNode item = findById(itemsOf(section), firstValue);
        if (item == null) {
            return null;
        }
        if (segments.size() == 1) {
            return isProject ? projectPlainText(item) : entryPlainText(item);
        }

        String secondKey = segments.get(1)[0];
        String secondValue = segments.get(1)[1];
        if ("field".equals(secondKey)) {
            List<String> allowed = isProject ? PROJECT_FIELDS : ENTRY_FIELDS;
            return allowed.contains(secondValue) ? item.path(secondValue).asText("") : null;
        }
        if ("blocks".equals(secondKey)) {
            JsonNode blocks;
            if (!isProject) {
                blocks = item.path("blocks");
            } else if (secondValue.isEmpty()) {
                blocks = allProjectBlocks(item);
            } else {
                blocks = item.path("fields").path(secondValue);
                if (!blocks.isArray()) {
                    return null;
                }
            }
            if (segments.size() == 3 && "block".equals(segments.get(2)[0])) {
                JsonNode block = findById(blocks, segments.get(2)[1]);
                return block == null ? null : block.path("text").asText("");
            }
            return joinTexts(blocks, true);
        }
        if ("block".equals(secondKey)) {
            JsonNode blocks = isProject ? allProjectBlocks(item) : item.path("blocks");
            JsonNode block = findById(blocks, secondValue);
            return block == null ? null : block.path("text").asText("");
        }
        return null;
    }

    /** Applies an AI suggestion: replaces the anchored range and returns the updated document copy. */
    public static ObjectNode applyReplacement(ObjectMapper mapper, JsonNode document,
                                              String path, String replacement) {
        ObjectNode result = document == null ? mapper.createObjectNode() : document.deepCopy();
        Anchor anchor = parse(path);
        if (anchor == null || !result.isObject()) {
            return result;
        }
        for (JsonNode section : result.path("sections")) {
            if (section.isObject() && anchor.sectionId().equals(section.path("id").asText(""))) {
                replaceInSection((ObjectNode) section, anchor.segments(), replacement);
            }
        }
        return result;
    }

    public static String sectionPlainText(JsonNode section) {
        switch (section.path("kind").asText("")) {
            case "text" -> {
                return joinTexts(sectionBlocks(section), true);
            }
            case "skills" -> {
                List<String> lines = new ArrayList<>();
                for (JsonNode group : section.path("content").path("groups")) {
                    lines.add(groupText(group));
                }
                return String.join("\n", lines);
            }
            case "entry" -> {
                return joinNonEmpty(section.path("content").path("items"), true);
            }
            case "project" -> {
                return joinNonEmpty(section.path("content").path("items"), false);
            }
            case "custom" -> {
                if ("entry".equals(section.path("variant").asText(""))) {
                    return joinNonEmpty(section.path("content").path("items"), true);
                }
                return joinTexts(sectionBlocks(section), true);
            }
            default -> {
                return "";
            }
        }
    }

    static Anchor parse(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("/");
        if (!parts[0].startsWith("section:")) {
            return null;
        }
        String sectionId = parts[0].substring("section:".length());
        if (sectionId.isEmpty()) {
            return null;
        }
        List<String[]> segments = new ArrayList<>();
        for (int index = 1; index < parts.length; index++) {
            int separator = parts[index].indexOf(':');
            if (separator <= 0) {
                return null;
            }
            segments.add(new String[] {parts[index].substring(0, separator),
                    parts[index].substring(separator + 1)});
        }
        return new Anchor(sectionId, segments);
    }

    private static void replaceInSection(ObjectNode section, List<String[]> segments, String replacement) {
        if (segments.isEmpty()) {
            return;
        }
        String firstKey = segments.get(0)[0];
        String firstValue = segments.get(0)[1];

        if ("block".equals(firstKey) && segments.size() == 1) {
            String kind = section.path("kind").asText("");
            boolean textSection = "text".equals(kind)
                    || ("custom".equals(kind) && "text".equals(section.path("variant").asText("")));
            if (!textSection) {
                return;
            }
            replaceBlockText(sectionBlocks(section), firstValue, replacement);
            return;
        }

        if ("group".equals(firstKey) && "skills".equals(section.path("kind").asText(""))) {
            int index = toIndex(firstValue);
            JsonNode groups = section.path("content").path("groups");
            if (index < 0 || index >= groups.size()) {
                return;
            }
            String[] parts = GROUP_DELIMITERS.split(replacement);
            List<String> values = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            ObjectNode group = (ObjectNode) groups.get(index);
            if (!values.isEmpty()) {
                group.put("label", values.get(0));
                if (values.size() > 1) {
                    group.set("items", toArray(values.subList(1, values.size())));
                }
            }
            return;
        }

        if (!"item".equals(firstKey) || segments.size() < 2) {
            return;
        }
        String secondKey = segments.get(1)[0];
        String secondValue = segments.get(1)[1];
        boolean isProject = "project".equals(section.path("kind").asText(""));
        for (JsonNode item : itemsOf(section)) {
            if (!item.isObject() || !firstValue.equals(item.path("id").asText(""))) {
                continue;
            }
            ObjectNode target = (ObjectNode) item;
            if ("field".equals(secondKey)) {
                if ((isProject ? PROJECT_FIELDS : ENTRY_FIELDS).contains(secondValue)) {
                    target.put(secondValue, replacement);
                }
                return;
            }
            if ("blocks".equals(secondKey)) {
                if (isProject) {
                    JsonNode fields = target.path("fields");
                    if (!fields.has(secondValue)) {
                        return;
                    }
                    ObjectNode mutable = (ObjectNode) fields;
                    mutable.set(secondValue, toBlocks(mutable.path(secondValue), replacement,
                            target.path("id").asText("") + "-" + secondValue));
                } else {
                    target.set("blocks", toBlocks(target.path("blocks"), replacement,
                            target.path("id").asText("") + "-blk"));
                }
                return;
            }
            if ("block".equals(secondKey)) {
                replaceBlockText(isProject ? allProjectBlocks(target) : target.path("blocks"),
                        secondValue, replacement);
            }
            return;
        }
    }

    private static ArrayNode toBlocks(JsonNode existing, String replacement, String prefix) {
        ArrayNode blocks = newArray();
        String[] lines = replacement.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            boolean bullet = BULLET_LINE.matcher(raw).find();
            boolean ordered = ORDERED_LINE.matcher(raw).find();
            ObjectNode block = blocks.addObject();
            String existingId = existing.isArray() && index < existing.size()
                    ? existing.get(index).path("id").asText("")
                    : "";
            block.put("id", existingId.isEmpty() ? prefix + "-" + index : existingId);
            block.put("kind", bullet ? "bullet" : ordered ? "ordered" : "line");
            block.put("text", LINE_MARKER.matcher(raw).replaceFirst("").trim());
        }
        return blocks;
    }

    private static void replaceBlockText(JsonNode blocks, String blockId, String replacement) {
        if (blocks == null || !blocks.isArray()) {
            return;
        }
        for (JsonNode block : blocks) {
            if (block.isObject() && blockId.equals(block.path("id").asText(""))) {
                ((ObjectNode) block).put("text", replacement);
            }
        }
    }

    private static JsonNode findSection(JsonNode document, String sectionId) {
        if (document == null) {
            return null;
        }
        for (JsonNode section : document.path("sections")) {
            if (sectionId.equals(section.path("id").asText(""))) {
                return section;
            }
        }
        return null;
    }

    private static JsonNode sectionBlocks(JsonNode section) {
        String kind = section.path("kind").asText("");
        if ("text".equals(kind) || ("custom".equals(kind) && "text".equals(section.path("variant").asText("")))) {
            return section.path("content").path("blocks");
        }
        return MissingNode.getInstance();
    }

    private static JsonNode itemsOf(JsonNode section) {
        String kind = section.path("kind").asText("");
        if ("entry".equals(kind) || "project".equals(kind)
                || ("custom".equals(kind) && "entry".equals(section.path("variant").asText("")))) {
            return section.path("content").path("items");
        }
        return MissingNode.getInstance();
    }

    private static JsonNode allProjectBlocks(JsonNode item) {
        ArrayNode blocks = newArray();
        Iterator<JsonNode> fields = item.path("fields").elements();
        while (fields.hasNext()) {
            fields.next().forEach(blocks::add);
        }
        return blocks;
    }

    private static JsonNode findById(JsonNode nodes, String id) {
        if (nodes == null || !nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (id.equals(node.path("id").asText(""))) {
                return node;
            }
        }
        return null;
    }

    private static String entryPlainText(JsonNode item) {
        List<String> lines = new ArrayList<>();
        addNonEmpty(lines, item.path("heading").asText(""));
        addNonEmpty(lines, item.path("subheading").asText(""));
        addNonEmpty(lines, item.path("period").asText(""));
        addNonEmpty(lines, item.path("meta").asText(""));
        for (JsonNode block : item.path("blocks")) {
            addNonEmpty(lines, blockLine(block));
        }
        return String.join("\n", lines);
    }

    private static String projectPlainText(JsonNode item) {
        List<String> lines = new ArrayList<>();
        addNonEmpty(lines, item.path("name").asText(""));
        addNonEmpty(lines, item.path("period").asText(""));
        addNonEmpty(lines, item.path("role").asText(""));
        addNonEmpty(lines, item.path("techStack").asText(""));
        for (JsonNode block : allProjectBlocks(item)) {
            addNonEmpty(lines, blockLine(block));
        }
        return String.join("\n", lines);
    }

    private static String joinNonEmpty(JsonNode items, boolean entry) {
        List<String> lines = new ArrayList<>();
        for (JsonNode item : items) {
            lines.add(entry ? entryPlainText(item) : projectPlainText(item));
        }
        lines.removeIf(String::isEmpty);
        return String.join("\n", lines);
    }

    private static String joinTexts(JsonNode blocks, boolean textOnly) {
        List<String> lines = new ArrayList<>();
        if (blocks != null && blocks.isArray()) {
            for (JsonNode block : blocks) {
                String value = textOnly ? block.path("text").asText("") : blockLine(block);
                if (!value.isEmpty()) {
                    lines.add(value);
                }
            }
        }
        return String.join("\n", lines);
    }

    private static String groupText(JsonNode group) {
        List<String> parts = new ArrayList<>();
        String label = group.path("label").asText("");
        if (!label.isEmpty()) {
            parts.add(label);
        }
        for (JsonNode item : group.path("items")) {
            String value = item.asText("");
            if (!value.isEmpty()) {
                parts.add(value);
            }
        }
        return String.join("、", parts);
    }

    private static String blockLine(JsonNode block) {
        String kind = block.path("kind").asText("");
        String prefix = "bullet".equals(kind) ? "- " : "ordered".equals(kind) ? "1. " : "";
        return prefix + block.path("text").asText("");
    }

    private static void addNonEmpty(List<String> lines, String value) {
        if (value != null && !value.isEmpty()) {
            lines.add(value);
        }
    }

    private static ArrayNode toArray(List<String> values) {
        ArrayNode array = newArray();
        values.forEach(array::add);
        return array;
    }

    private static ArrayNode newArray() {
        return new ArrayNode(JsonNodeFactory.instance);
    }

    private static int toIndex(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
