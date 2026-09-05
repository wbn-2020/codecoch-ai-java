package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ResumeDocumentMigratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsFiveBuiltinSectionsWhenNothingIsFilledIn() {
        ObjectNode document = ResumeDocumentMigrator.emptyDocument(mapper);

        assertEquals(2, document.path("schemaVersion").asInt());
        assertEquals(List.of("summary", "skills", "experience", "projects", "education"),
                document.path("sections").findValues("builtinKey").stream().map(JsonNode::asText).toList());
        assertTrue(document.path("basics").path("contacts").isEmpty());
        assertEquals("ATS_SINGLE_COLUMN", document.path("layout").path("templateCode").asText());
        assertEquals("A4", document.path("layout").path("pageSize").asText());
    }

    @Test
    void mapsFlatColumnsIntoBuiltinSectionsWithOrderAndVisibility() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);

        assertEquals("张伟", document.path("basics").path("name").asText());
        assertEquals("高级 Java 工程师", document.path("basics").path("headline").asText());
        assertEquals(List.of("phone", "email"),
                document.path("basics").path("contacts").findValues("kind").stream()
                        .map(JsonNode::asText).toList());
        assertEquals("ATS_PROJECT_FOCUS", document.path("layout").path("templateCode").asText());
        assertEquals(List.of("summary", "projects", "skills", "experience", "education"),
                document.path("sections").findValues("builtinKey").stream()
                        .map(JsonNode::asText).toList());
        assertFalse(ResumeDocumentFixtures.section(mapper, document, "education").path("visible").asBoolean());

        ObjectNode summary = ResumeDocumentFixtures.section(mapper, document, "summary");
        assertEquals("text", summary.path("kind").asText());
        assertEquals(3, summary.path("content").path("blocks").size());
        assertEquals("sec-summary", summary.path("id").asText());
        assertEquals("sec-summary-b0", summary.path("content").path("blocks").get(0).path("id").asText());
        assertEquals("line", summary.path("content").path("blocks").get(0).path("kind").asText());

        ObjectNode skills = ResumeDocumentFixtures.section(mapper, document, "skills");
        assertEquals(4, skills.path("content").path("groups").size());
        assertEquals("语言与基础", skills.path("content").path("groups").get(0).path("label").asText());
        assertEquals("sec-skills-g0", skills.path("content").path("groups").get(0).path("id").asText());

        ObjectNode work = ResumeDocumentFixtures.section(mapper, document, "experience");
        assertEquals(2, ResumeDocumentFixtures.items(work).size());
        JsonNode firstWork = ResumeDocumentFixtures.items(work).get(0);
        assertEquals("sec-work-e0", firstWork.path("id").asText());
        assertEquals("字节跳动 · 后端开发", firstWork.path("heading").asText());
        assertEquals("2021.03-至今", firstWork.path("period").asText());
        assertEquals(2, firstWork.path("blocks").size());
        assertEquals("sec-work-e0-b0", firstWork.path("blocks").get(0).path("id").asText());
        assertEquals("bullet", firstWork.path("blocks").get(0).path("kind").asText());

        JsonNode project = ResumeDocumentFixtures.items(
                ResumeDocumentFixtures.section(mapper, document, "projects")).get(0);
        assertEquals("prj-101", project.path("id").asText());
        assertEquals(101, project.path("serverId").asInt());
        assertEquals(1, project.path("fields").path("background").size());
        assertEquals("prj-101-bg-0", project.path("fields").path("background").get(0)
                .path("id").asText());
        assertEquals("sec-edu-e0", ResumeDocumentFixtures.items(
                ResumeDocumentFixtures.section(mapper, document, "education")).get(0).path("id").asText());
    }

    @Test
    void migrationIsDeterministic() {
        assertEquals(ResumeDocumentFixtures.document(mapper), ResumeDocumentFixtures.document(mapper));
    }

    @Test
    void projectsBackIntoLegacyScalarsAndIsIdempotent() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);
        ObjectNode legacy = ResumeDocumentProjector.project(mapper, document);

        assertEquals("张伟", legacy.path("realName").asText());
        assertEquals("高级 Java 工程师", legacy.path("targetPosition").asText());
        assertEquals("zhangwei@example.com", legacy.path("email").asText());
        assertEquals("13800000000", legacy.path("phone").asText());
        assertEquals(List.of("summary", "projects", "skills", "experience", "education"),
                textList(legacy.path("sectionOrder")));
        assertEquals(List.of("education"), textList(legacy.path("hiddenSections")));
        assertEquals("CodeCoachAI 模拟面试", legacy.path("projects").get(0).path("projectName").asText());
        assertTrue(legacy.path("projects").get(0).path("optimizationResult").asText()
                .contains("面试完成率提升 25%"));
        assertEquals("Java、Spring、Vue、MySQL、Redis、Kafka、Docker、Kubernetes、ELK",
                legacy.path("skillStack").asText());

        ObjectNode again = ResumeDocumentProjector.project(mapper,
                ResumeDocumentMigrator.toDocument(mapper, rewrites(legacy), projects(legacy),
                        ResumeDocumentFixtures.presentation(mapper)));

        assertEquals(legacy.path("workExperience").asText(), again.path("workExperience").asText());
        assertEquals(legacy.path("skillStack").asText(), again.path("skillStack").asText());
        assertEquals(legacy.path("summary").asText(), again.path("summary").asText());
        assertEquals(legacy.path("educationExperience").asText(), again.path("educationExperience").asText());
        assertEquals(legacy.path("projects").toString(), again.path("projects").toString());
    }

    private ObjectNode rewrites(ObjectNode projected) {
        ObjectNode legacy = ResumeDocumentFixtures.legacy(mapper);
        legacy.put("skillStack", projected.path("skillStack").asText());
        legacy.put("summary", projected.path("summary").asText());
        legacy.put("workExperience", projected.path("workExperience").asText());
        legacy.put("educationExperience", projected.path("educationExperience").asText());
        return legacy;
    }

    private List<JsonNode> projects(ObjectNode projected) {
        return stream(projected.path("projects")).toList();
    }

    private List<String> textList(JsonNode node) {
        return stream(node).map(JsonNode::asText).toList();
    }

    private static Stream<JsonNode> stream(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }
}
