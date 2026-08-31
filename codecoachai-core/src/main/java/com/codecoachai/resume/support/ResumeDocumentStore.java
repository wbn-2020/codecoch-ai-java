package com.codecoachai.resume.support;

import com.codecoachai.resume.domain.entity.Resume;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dual read/write boundary between a resume row and its document v2.
 *
 * <p>Reads prefer the stored document and fall back to synthesizing one from the flat columns
 * without persisting it, so a resume lazily migrates the first time a document-aware client saves
 * it. Writes keep both shapes in step: a document write re-projects the flat columns, and a
 * flat-only write from an older client replaces builtin section content while preserving custom
 * sections, section order and visibility.
 */
public final class ResumeDocumentStore {

    private ResumeDocumentStore() {
    }

    /** Project rows or VOs; their property names already match the aliases the migrator reads. */
    public static List<JsonNode> projectNodes(ObjectMapper mapper, List<?> projects) {
        List<JsonNode> nodes = new ArrayList<>();
        if (projects != null) {
            projects.forEach(project -> {
                if (project != null) {
                    nodes.add(mapper.valueToTree(project));
                }
            });
        }
        return nodes;
    }

    /** Flat-column input for a resume row, keyed the way the migrator reads it. */
    public static ObjectNode legacyNode(ObjectMapper mapper, Resume resume) {
        ObjectNode legacy = mapper.createObjectNode();
        if (resume == null) {
            return legacy;
        }
        legacy.put("resumeName", resume.getTitle());
        legacy.put("realName", resume.getRealName());
        legacy.put("email", resume.getEmail());
        legacy.put("phone", resume.getPhone());
        legacy.put("targetPosition", resume.getTargetPosition());
        legacy.put("skillStack", resume.getSkillStack());
        legacy.put("workExperience", resume.getWorkExperience());
        legacy.put("educationExperience", resume.getEducationExperience());
        legacy.put("summary", resume.getSummary());
        return legacy;
    }

    /** The stored document when it is valid, otherwise one synthesized from the flat columns. */
    public static JsonNode read(ObjectMapper mapper, Resume resume, List<?> projects, JsonNode presentation) {
        JsonNode stored = readStored(mapper, resume == null ? null : resume.getDocumentJson());
        return stored != null
                ? stored
                : ResumeDocumentMigrator.toDocument(
                        mapper, legacyNode(mapper, resume), projectNodes(mapper, projects), presentation);
    }

    public static JsonNode readStored(ObjectMapper mapper, String storedJson) {
        if (storedJson == null || storedJson.isBlank()) {
            return null;
        }
        try {
            return ResumeDocumentNormalizer.normalize(mapper, mapper.readTree(storedJson));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Applies a document-aware write: the document becomes canonical, and the flat columns are
     * rewritten from its projection.
     *
     * @return null when the payload is not a usable v2 document, meaning the caller should treat the
     *         request as a flat-only write
     */
    public static ObjectNode writeDocument(ObjectMapper mapper, Resume resume, JsonNode document) {
        ObjectNode normalized = ResumeDocumentNormalizer.normalize(mapper, document);
        if (normalized == null) {
            return null;
        }
        applyProjection(mapper, resume, normalized);
        resume.setDocumentJson(write(mapper, normalized));
        return normalized;
    }

    /**
     * Applies a flat-only write from a client that does not know about documents. The stored
     * document, if any, keeps the parts the payload cannot express.
     */
    public static ObjectNode writeLegacyEdit(ObjectMapper mapper, Resume resume, List<?> projects,
                                             JsonNode presentation) {
        JsonNode existing = readStored(mapper, resume.getDocumentJson());
        if (existing == null) {
            return null;
        }
        ObjectNode merged = mergeFlatEdit(mapper, existing, legacyNode(mapper, resume), projects, presentation);
        resume.setDocumentJson(write(mapper, merged));
        return merged;
    }

    /** Rewrites the flat columns from a document; used after a version rollback restores one. */
    public static void applyProjection(ObjectMapper mapper, Resume resume, JsonNode document) {
        ObjectNode projected = ResumeDocumentProjector.project(mapper, document);
        resume.setRealName(projected.path("realName").asText(""));
        resume.setTargetPosition(projected.path("targetPosition").asText(""));
        resume.setEmail(projected.path("email").asText(""));
        resume.setPhone(projected.path("phone").asText(""));
        resume.setSummary(projected.path("summary").asText(""));
        resume.setSkillStack(projected.path("skillStack").asText(""));
        resume.setWorkExperience(projected.path("workExperience").asText(""));
        resume.setEducationExperience(projected.path("educationExperience").asText(""));
    }

    /**
     * Replaces builtin section content from a fresh migration while keeping the stored document's
     * custom sections, their positions, and each builtin's id, title and visibility.
     */
    static ObjectNode mergeFlatEdit(ObjectMapper mapper, JsonNode existing, ObjectNode legacy,
                                    List<?> projects, JsonNode presentation) {
        ObjectNode migrated = ResumeDocumentMigrator.toDocument(
                mapper, legacy, projectNodes(mapper, projects), presentation);
        Map<String, ObjectNode> fresh = new LinkedHashMap<>();
        for (JsonNode section : migrated.path("sections")) {
            String builtinKey = section.path("builtinKey").asText("");
            if (!builtinKey.isEmpty()) {
                fresh.put(builtinKey, (ObjectNode) section);
            }
        }

        ArrayNode sections = mapper.createArrayNode();
        for (JsonNode section : existing.path("sections")) {
            if (!section.isObject()) {
                continue;
            }
            String builtinKey = section.path("builtinKey").asText("");
            if (builtinKey.isEmpty()) {
                sections.add(section.deepCopy());
                continue;
            }
            ObjectNode replacement = fresh.remove(builtinKey);
            if (replacement == null) {
                continue;
            }
            ObjectNode carried = replacement.deepCopy();
            copyIfPresent(section, carried, "id");
            copyIfPresent(section, carried, "title");
            carried.put("visible", section.path("visible").asBoolean(true));
            sections.add(carried);
        }
        fresh.values().forEach(sections::add);

        ObjectNode result = migrated.deepCopy();
        result.set("sections", sections);
        return ResumeDocumentNormalizer.normalize(mapper, result);
    }

    private static void copyIfPresent(JsonNode source, ObjectNode target, String field) {
        String value = source.path(field).asText("").trim();
        if (!value.isEmpty()) {
            target.put(field, value);
        }
    }

    private static String write(ObjectMapper mapper, JsonNode document) {
        try {
            return mapper.writeValueAsString(document);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Resume document cannot be serialized", ex);
        }
    }
}
