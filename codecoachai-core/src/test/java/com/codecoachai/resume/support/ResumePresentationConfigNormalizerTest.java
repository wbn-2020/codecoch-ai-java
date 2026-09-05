package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumePresentationConfigNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesPresentationConfigToAllowlistedBoundedData() {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("templateCode", "UNTRUSTED_TEMPLATE");
        source.put("templateVersion", 0);
        source.put("fontFamily", "url(https://example.com/font.woff)");
        source.put("fontScale", 9);
        source.put("lineHeight", -3);
        source.put("pageMarginPt", 500);
        source.put("html", "<script>alert(1)</script>");
        source.putArray("moduleOrder")
                .add("resume-projects")
                .add("unknown-module")
                .add("resume-basic");
        source.putArray("hiddenModules")
                .add("resume-basic")
                .add("resume-skills")
                .add("unknown-module");
        source.putArray("sectionOrder")
                .add("projects")
                .add("projects")
                .add("unknown");
        source.putObject("fieldOrder")
                .putArray("realName")
                .add("email")
                .add("<script>alert(1)</script>")
                .add("unknown")
                .add("email");
        source.putObject("fieldVisibility").put("email", false);
        source.put("basicLayout", "INVALID_LAYOUT");
        source.putArray("basicFieldOrder")
                .add("phone")
                .add("unknown")
                .add("phone")
                .add("email");
        source.putObject("basicFieldVisibility")
                .put("phone", false)
                .put("email", "not-a-boolean")
                .put("unknown", false);
        source.putObject("basicFieldIcons")
                .put("phone", "phone")
                .put("email", "mail")
                .put("realName", "not-an-icon")
                .put("unknown", "briefcase");
        source.put("iconMode", "INVALID_MODE");
        source.put("autoOnePage", true);

        ObjectNode normalized = ResumePresentationConfigNormalizer.normalize(objectMapper, source);

        assertEquals("ATS_SINGLE_COLUMN", normalized.path("templateCode").asText());
        assertEquals(1, normalized.path("templateVersion").asInt());
        assertEquals("Arial", normalized.path("fontFamily").asText());
        assertEquals(1.18d, normalized.path("fontScale").asDouble());
        assertEquals(1d, normalized.path("lineHeight").asDouble());
        assertEquals(72d, normalized.path("pageMarginPt").asDouble());
        assertEquals(
                List.of("resume-projects", "resume-basic", "resume-target", "resume-skills", "resume-experience"),
                objectMapper.convertValue(normalized.path("moduleOrder"), List.class));
        assertEquals(
                List.of("resume-skills"),
                objectMapper.convertValue(normalized.path("hiddenModules"), List.class));
        assertEquals(
                List.of("projects", "summary", "experience", "skills", "education"),
                objectMapper.convertValue(normalized.path("sectionOrder"), List.class));
        assertEquals(
                List.of("email"),
                objectMapper.convertValue(normalized.path("fieldOrder").path("realName"), List.class));
        assertEquals("LEFT", normalized.path("basicLayout").asText());
        assertEquals(
                List.of("phone", "email", "realName", "targetPosition"),
                objectMapper.convertValue(normalized.path("basicFieldOrder"), List.class));
        assertFalse(normalized.path("basicFieldVisibility").path("phone").asBoolean());
        assertFalse(normalized.path("basicFieldVisibility").path("email").asBoolean());
        assertEquals("user", normalized.path("basicFieldIcons").path("realName").asText());
        assertEquals("briefcase", normalized.path("basicFieldIcons").path("targetPosition").asText());
        assertEquals("mail", normalized.path("basicFieldIcons").path("email").asText());
        assertEquals("phone", normalized.path("basicFieldIcons").path("phone").asText());
        assertEquals("ICON", normalized.path("iconMode").asText());
        assertTrue(normalized.path("autoOnePage").asBoolean());
        assertFalse(normalized.has("html"));
        assertFalse(normalized.path("fieldVisibility").path("email").asBoolean());
    }

    @Test
    void preservesValidBasicLayoutAndFiltersNewFieldConfiguration() {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("basicLayout", " CENTER ");
        source.putArray("basicFieldOrder")
                .add("realName")
                .add("targetPosition")
                .add("email")
                .add("phone");
        source.putObject("basicFieldVisibility")
                .put("realName", false)
                .put("targetPosition", true)
                .put("email", false)
                .put("phone", true);
        source.putObject("basicFieldIcons")
                .put("realName", "circle")
                .put("targetPosition", "briefcase")
                .put("email", "mail")
                .put("phone", "phone");
        source.put("iconMode", "TEXT");

        ObjectNode normalized = ResumePresentationConfigNormalizer.normalize(objectMapper, source);

        assertEquals("CENTER", normalized.path("basicLayout").asText());
        assertEquals(
                List.of("realName", "targetPosition", "email", "phone"),
                objectMapper.convertValue(normalized.path("basicFieldOrder"), List.class));
        assertFalse(normalized.path("basicFieldVisibility").path("realName").asBoolean());
        assertTrue(normalized.path("basicFieldVisibility").path("targetPosition").asBoolean());
        assertFalse(normalized.path("basicFieldVisibility").path("email").asBoolean());
        assertTrue(normalized.path("basicFieldVisibility").path("phone").asBoolean());
        assertEquals("circle", normalized.path("basicFieldIcons").path("realName").asText());
        assertEquals("briefcase", normalized.path("basicFieldIcons").path("targetPosition").asText());
        assertEquals("mail", normalized.path("basicFieldIcons").path("email").asText());
        assertEquals("phone", normalized.path("basicFieldIcons").path("phone").asText());
        assertEquals("TEXT", normalized.path("iconMode").asText());
        assertFalse(normalized.path("autoOnePage").asBoolean());
    }

    @Test
    void defaultsNewFieldsWithoutChangingSchemaVersionOrLegacyFields() {
        ObjectNode source = objectMapper.createObjectNode();
        source.putObject("fieldVisibility").put("phone", false);
        source.putObject("fieldOrder").putArray("realName").add("phone");

        ObjectNode normalized = ResumePresentationConfigNormalizer.normalize(objectMapper, source);

        assertEquals(1, normalized.path("schemaVersion").asInt());
        assertEquals(
                List.of("realName", "targetPosition", "email", "phone"),
                objectMapper.convertValue(normalized.path("basicFieldOrder"), List.class));
        assertTrue(normalized.path("basicFieldVisibility").path("realName").asBoolean());
        assertFalse(normalized.path("basicFieldVisibility").path("phone").asBoolean());
        assertEquals("mail", normalized.path("basicFieldIcons").path("email").asText());
        assertEquals("briefcase", normalized.path("basicFieldIcons").path("targetPosition").asText());
        assertEquals("phone", normalized.path("basicFieldIcons").path("phone").asText());
        assertEquals("LEFT", normalized.path("basicLayout").asText());
        assertEquals("ICON", normalized.path("iconMode").asText());
        assertFalse(normalized.path("autoOnePage").asBoolean());
    }

    @Test
    void invalidStoredJsonFallsBackToACompleteDefaultConfig() {
        ObjectNode normalized = (ObjectNode) ResumePresentationConfigNormalizer.parseStored(
                objectMapper, "{not-json");

        assertEquals(ResumePresentationConfigNormalizer.SCHEMA_VERSION,
                normalized.path("schemaVersion").asInt());
        assertEquals(5, normalized.path("sectionOrder").size());
        assertEquals(5, normalized.path("moduleOrder").size());
        assertEquals(0, normalized.path("hiddenSections").size());
    }

    @Test
    void normalizesMagicResumeAccentPaletteAndLegacyAliases() {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("accentColor", "berry");
        ObjectNode legacyNormalized = ResumePresentationConfigNormalizer.normalize(objectMapper, source);
        assertEquals("red", legacyNormalized.path("accentColor").asText());

        for (String accent : List.of(
                "default", "blue", "green", "purple",
                "orange", "red", "slate", "black")) {
            source.put("accentColor", accent);
            ObjectNode normalized = ResumePresentationConfigNormalizer.normalize(objectMapper, source);
            assertEquals(accent, normalized.path("accentColor").asText());
        }

        source.put("accentColor", "unsupported");
        ObjectNode fallback = ResumePresentationConfigNormalizer.normalize(objectMapper, source);
        assertEquals("default", fallback.path("accentColor").asText());
    }
}
