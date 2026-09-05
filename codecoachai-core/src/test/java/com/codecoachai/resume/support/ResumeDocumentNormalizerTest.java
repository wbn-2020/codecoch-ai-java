package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ResumeDocumentNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsPayloadsThatAreNotSchemaV2() {
        assertNull(ResumeDocumentNormalizer.normalize(mapper, null));
        assertNull(ResumeDocumentNormalizer.normalize(mapper,
                mapper.createObjectNode().put("schemaVersion", 1)));
        assertNull(ResumeDocumentNormalizer.normalize(mapper, mapper.createArrayNode()));
    }

    @Test
    void clampsNumbersWhitelistsEnumsAndIssuesMissingIds() {
        ObjectNode source = mapper.createObjectNode();
        source.put("schemaVersion", 2);
        source.putObject("basics")
                .put("name", "x".repeat(100))
                .putArray("contacts").addObject().put("kind", "weird").put("value", "v");
        ObjectNode layout = source.putObject("layout");
        layout.put("fontScale", 99);
        layout.put("lineHeight", 0);
        layout.put("sectionSpacing", -4);
        layout.put("pageMarginPt", 500);
        layout.put("accentColor", "BLUE");
        layout.put("fontFamily", "url(evil)");
        layout.putObject("overrides").put("sectionOrder", true).put("unknownSwitch", true);

        ObjectNode section = source.putArray("sections").addObject();
        section.put("id", "sec-summary")
                .put("builtinKey", "summary")
                .put("kind", "text")
                .put("title", "总" + (char) 7 + "结")
                .putObject("content").putArray("blocks").addObject().put("text", "a");

        ObjectNode doc = ResumeDocumentNormalizer.normalize(mapper, source);

        assertTrue(doc.path("basics").path("name").asText().length() <= 60);
        assertEquals("text", doc.path("basics").path("contacts").get(0).path("kind").asText());
        assertFalse(doc.path("basics").path("contacts").get(0).path("id").asText().isEmpty());
        assertEquals(1.18, doc.path("layout").path("fontScale").asDouble(), 0d);
        assertEquals(1d, doc.path("layout").path("lineHeight").asDouble(), 0d);
        assertEquals(0.7, doc.path("layout").path("sectionSpacing").asDouble(), 0d);
        assertEquals(72d, doc.path("layout").path("pageMarginPt").asDouble(), 0d);
        assertEquals("default", doc.path("layout").path("accentColor").asText());
        assertEquals("Arial", doc.path("layout").path("fontFamily").asText());
        assertTrue(doc.path("layout").path("overrides").has("sectionOrder"));
        assertFalse(doc.path("layout").path("overrides").has("unknownSwitch"));
        assertEquals("总结", doc.path("sections").get(0).path("title").asText());
        assertFalse(doc.path("sections").get(0).path("content").path("blocks").get(0)
                .path("id").asText().isEmpty());
    }

    @Test
    void normalizingAMigratedDocumentIsANoOp() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);
        assertEquals(document, ResumeDocumentNormalizer.normalize(mapper, document));
    }

    @Test
    void dedupesBuiltinSectionsCapsCustomOnesAndDropsUnknownKinds() {
        ObjectNode source = mapper.createObjectNode();
        source.put("schemaVersion", 2);
        source.putObject("basics");
        source.putObject("layout");
        ArrayNode sections = source.putArray("sections");
        addTextSection(sections, "a", "summary");
        addTextSection(sections, "b", "summary");
        for (int index = 0; index < 20; index++) {
            ObjectNode custom = sections.addObject();
            custom.put("id", "c" + index)
                    .put("kind", "custom")
                    .put("variant", "text")
                    .put("title", "T" + index)
                    .put("builtinKey", "projects");
            custom.putObject("content").putArray("blocks");
        }
        sections.addObject().put("id", "weird").put("kind", "mystery");

        JsonNode result = ResumeDocumentNormalizer.normalize(mapper, source).path("sections");

        assertEquals(13, result.size());
        assertEquals(1d, count(result, node -> "summary".equals(node.path("builtinKey").asText(""))));
        assertEquals(12d, count(result, node -> node.path("builtinKey").isMissingNode()));
        assertEquals("custom", result.get(1).path("kind").asText());
    }

    @Test
    void parseStoredFallsBackToAnEmptyDocument() {
        assertEquals(5, ResumeDocumentNormalizer.parseStored(mapper, null).path("sections").size());
        assertEquals(5, ResumeDocumentNormalizer.parseStored(mapper, "{ not json").path("sections").size());
        assertEquals(5, ResumeDocumentNormalizer.parseStored(mapper,
                ResumeDocumentNormalizer.normalizeJson(mapper, null)).path("sections").size());
    }

    private void addTextSection(ArrayNode sections, String id, String builtinKey) {
        sections.addObject().put("id", id)
                .put("kind", "text")
                .put("builtinKey", builtinKey)
                .putObject("content").putArray("blocks");
    }

    private long count(JsonNode sections, Predicate<JsonNode> predicate) {
        return StreamSupport.stream(sections.spliterator(), false).filter(predicate).count();
    }
}
