package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeDocumentStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writeProjectsRebindsServerIdsWithoutReplacingCustomDocumentContent() {
        Resume resume = new Resume();
        resume.setTitle("Backend resume");
        resume.setSummary("Canonical summary");

        ResumeProject original = project(11L, "Original project");
        ObjectNode document = ResumeDocumentMigrator.toDocument(
                objectMapper,
                ResumeDocumentStore.legacyNode(objectMapper, resume),
                ResumeDocumentStore.projectNodes(objectMapper, List.of(original)),
                null);
        document.withArray("sections").addObject()
                .put("id", "custom-certificates")
                .put("title", "Certificates")
                .put("visible", true)
                .put("kind", "custom")
                .put("variant", "text")
                .putObject("content")
                .putArray("blocks")
                .addObject()
                .put("id", "cert-1")
                .put("kind", "line")
                .put("text", "AWS");
        ResumeDocumentStore.writeDocument(objectMapper, resume, document);

        ResumeProject copied = project(99L, "Copied project");
        ObjectNode updated = ResumeDocumentStore.writeProjects(objectMapper, resume, List.of(copied));

        assertNotNull(updated);
        JsonNode projects = builtin(updated, "projects").path("content").path("items");
        assertEquals(99L, projects.get(0).path("serverId").asLong());
        assertEquals("Copied project", projects.get(0).path("name").asText());
        assertEquals("Canonical summary",
                ResumeDocumentProjector.project(objectMapper, updated).path("summary").asText());
        assertEquals("AWS", updated.path("sections").get(updated.path("sections").size() - 1)
                .path("content").path("blocks").get(0).path("text").asText());
    }

    private ResumeProject project(Long id, String name) {
        ResumeProject project = new ResumeProject();
        project.setId(id);
        project.setProjectName(name);
        project.setSort(0);
        project.setSortOrder(0);
        return project;
    }

    private JsonNode builtin(JsonNode document, String key) {
        for (JsonNode section : document.path("sections")) {
            if (key.equals(section.path("builtinKey").asText())) {
                return section;
            }
        }
        return objectMapper.createObjectNode();
    }
}
