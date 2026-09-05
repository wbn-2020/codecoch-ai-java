package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ResumeAnchorResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesIdentityPathsAcrossSectionKinds() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);
        ObjectNode work = ResumeDocumentFixtures.section(mapper, document, "experience");
        JsonNode item = ResumeDocumentFixtures.items(work).get(0);
        String workAnchor = "section:" + work.path("id").asText();

        assertEquals("字节跳动 · 后端开发", ResumeAnchorResolver.resolveText(document,
                workAnchor + "/item:" + item.path("id").asText() + "/field:heading"));
        assertTrue(ResumeAnchorResolver.resolveText(document, workAnchor
                + "/item:" + item.path("id").asText()
                + "/block:" + item.path("blocks").get(0).path("id").asText()).contains("P99"));
        assertTrue(ResumeAnchorResolver.resolveText(document, workAnchor).contains("美团"));
        assertTrue(ResumeAnchorResolver.resolveText(document,
                workAnchor + "/item:" + item.path("id").asText()).contains("2021.03-至今"));

        ObjectNode skills = ResumeDocumentFixtures.section(mapper, document, "skills");
        assertEquals("语言与基础、Java", ResumeAnchorResolver.resolveText(document,
                "section:" + skills.path("id").asText() + "/group:0"));

        ObjectNode projects = ResumeDocumentFixtures.section(mapper, document, "projects");
        assertEquals("面向求职者的 AI 面试训练平台。", ResumeAnchorResolver.resolveText(document,
                "section:" + projects.path("id").asText() + "/item:prj-101/blocks:background"));
    }

    @Test
    void resolvesBlockRangesInsideProjectFields() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);
        JsonNode block = ResumeDocumentFixtures.items(
                ResumeDocumentFixtures.section(mapper, document, "projects"))
                .get(0).path("fields").path("outcome").get(0);

        assertEquals("面试完成率提升 25%。", ResumeAnchorResolver.resolveText(document,
                "section:sec-projects/item:prj-101/blocks:outcome/block:" + block.path("id").asText()));
    }

    @Test
    void anchorsSurviveReordering() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);
        ArrayNode sections = (ArrayNode) document.path("sections");
        JsonNode experience = sections.get(3);
        String anchor = "section:" + experience.path("id").asText() + "/item:sec-work-e0/field:heading";
        assertEquals("字节跳动 · 后端开发", ResumeAnchorResolver.resolveText(document, anchor));

        sections.remove(3);
        sections.insert(0, experience);
        assertEquals("experience", sections.get(0).path("builtinKey").asText());
        assertEquals("字节跳动 · 后端开发", ResumeAnchorResolver.resolveText(document, anchor));
    }

    @Test
    void returnsNullForAnythingItCannotAddress() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);

        assertNull(ResumeAnchorResolver.resolveText(document, "section:missing"));
        assertNull(ResumeAnchorResolver.resolveText(document, "projects[0].techStack"));
        assertNull(ResumeAnchorResolver.resolveText(document, "sec-summary"));
        assertNull(ResumeAnchorResolver.resolveText(document, "section:/item:x"));
        assertNull(ResumeAnchorResolver.resolveText(document, "section:sec-skills/group:99"));
        assertNull(ResumeAnchorResolver.resolveText(document, "section:sec-experience/item:nope/field:heading"));
        assertNull(ResumeAnchorResolver.resolveText(document,
                "section:sec-experience/item:sec-work-e0/field:nope"));
        assertNull(ResumeAnchorResolver.resolveText(document,
                "section:sec-projects/item:prj-101/blocks:nope"));
    }

    @Test
    void appliesBlockAndFieldReplacementsWithoutMutatingTheSource() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);
        ObjectNode work = ResumeDocumentFixtures.section(mapper, document, "experience");
        JsonNode item = ResumeDocumentFixtures.items(work).get(0);
        String blockAnchor = "section:" + work.path("id").asText()
                + "/item:" + item.path("id").asText()
                + "/block:" + item.path("blocks").get(0).path("id").asText();

        ObjectNode replaced = ResumeAnchorResolver.applyReplacement(mapper, document, blockAnchor, "改写了职责描述");

        assertEquals("改写了职责描述", ResumeAnchorResolver.resolveText(replaced, blockAnchor));
        assertTrue(ResumeAnchorResolver.resolveText(document, blockAnchor).contains("P99"));

        String periodAnchor = "section:" + work.path("id").asText()
                + "/item:" + item.path("id").asText() + "/field:period";
        ObjectNode period = ResumeAnchorResolver.applyReplacement(mapper, replaced,
                periodAnchor, "2021.03-2024.06");

        assertEquals("2021.03-2024.06", ResumeAnchorResolver.resolveText(period, periodAnchor));
        assertEquals("2021.03-至今", ResumeAnchorResolver.resolveText(replaced, periodAnchor));
    }

    @Test
    void rewritesWholeBlockGroupsAndSkillGroups() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);

        ObjectNode withProject = ResumeAnchorResolver.applyReplacement(mapper, document,
                "section:sec-projects/item:prj-101/blocks:outcome",
                "- 转化率提升 30%\n- 成本下降 12%");
        JsonNode outcome = ResumeDocumentFixtures.items(
                ResumeDocumentFixtures.section(mapper, withProject, "projects"))
                .get(0).path("fields").path("outcome");

        assertEquals(List.of("转化率提升 30%", "成本下降 12%"), texts(outcome));
        assertEquals(List.of("bullet", "bullet"), fields(outcome, "kind"));
        assertEquals("面试完成率提升 25%。", texts(ResumeDocumentFixtures.items(
                ResumeDocumentFixtures.section(mapper, document, "projects"))
                .get(0).path("fields").path("outcome")).get(0));

        ObjectNode withSkills = ResumeAnchorResolver.applyReplacement(mapper, withProject,
                "section:sec-skills/group:0", "语言与基础、Java、Go");
        JsonNode group = ResumeDocumentFixtures.section(mapper, withSkills, "skills")
                .path("content").path("groups").get(0);

        assertEquals("语言与基础", group.path("label").asText());
        assertEquals(List.of("Java", "Go"), texts(group.path("items")));
    }

    @Test
    void ignoresPathsItDoesNotOwn() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);
        assertEquals(document, ResumeAnchorResolver.applyReplacement(mapper, document,
                "projects[0].techStack", "ignored"));
    }

    @Test
    void rendersSectionPlainTextForEveryKind() {
        ObjectNode document = ResumeDocumentFixtures.document(mapper);

        String skills = ResumeAnchorResolver.sectionPlainText(
                ResumeDocumentFixtures.section(mapper, document, "skills"));
        assertTrue(skills.contains("语言与基础"));
        assertTrue(skills.contains("Java"));

        String summary = ResumeAnchorResolver.sectionPlainText(
                ResumeDocumentFixtures.section(mapper, document, "summary"));

        assertEquals(3, summary.lines().count());
        assertTrue(summary.startsWith("八年后端"));
    }

    private List<String> texts(JsonNode nodes) {
        return StreamSupport.stream(nodes.spliterator(), false)
                .map(node -> node.isTextual() ? node.asText() : node.path("text").asText(""))
                .toList();
    }

    private List<String> fields(JsonNode nodes, String field) {
        return StreamSupport.stream(nodes.spliterator(), false)
                .map(node -> node.path(field).asText())
                .toList();
    }
}
