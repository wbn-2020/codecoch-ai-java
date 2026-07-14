package com.codecoachai.resume.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumeZipBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void zipContainsResumeFilesAndCorrectManifest() throws Exception {
        Path pdf = tempDir.resolve("resume.pdf");
        Path docx = tempDir.resolve("resume.docx");
        Files.writeString(pdf, "pdf-content", StandardCharsets.UTF_8);
        Files.writeString(docx, "docx-content", StandardCharsets.UTF_8);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("sourceResumeVersionId", 42L);
        manifest.put("templateVersion", 1);
        manifest.put("files", List.of(
                Map.of("name", "resume.pdf", "sha256", ResumeArtifactHashes.sha256(pdf)),
                Map.of("name", "resume.docx", "sha256", ResumeArtifactHashes.sha256(docx))));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ResumeZipBuilder(new ObjectMapper()).write(output, List.of(
                new ResumeZipBuilder.SourceEntry("resume.pdf", pdf),
                new ResumeZipBuilder.SourceEntry("resume.docx", docx)), manifest, 5);

        Map<String, byte[]> entries = unzip(output.toByteArray());
        assertEquals(3, entries.size());
        assertTrue(entries.containsKey("resume.pdf"));
        assertTrue(entries.containsKey("resume.docx"));
        JsonNode parsed = new ObjectMapper().readTree(entries.get("manifest.json"));
        assertEquals(42L, parsed.path("sourceResumeVersionId").asLong());
        assertEquals(1, parsed.path("templateVersion").asInt());
        assertEquals(ResumeArtifactHashes.sha256(pdf), parsed.path("files").get(0).path("sha256").asText());
    }

    @Test
    void zipAllowsCompleteApplicationPackageWithinDefaultEntryLimit() throws Exception {
        List<String> names = List.of(
                "resume.pdf",
                "resume.docx",
                "cover-letter-draft.txt",
                "email-draft.txt",
                "project-case-study.txt",
                "interview-cheatsheet.txt",
                "preflight-checklist.txt");
        List<ResumeZipBuilder.SourceEntry> sources = new java.util.ArrayList<>();
        List<Map<String, Object>> files = new java.util.ArrayList<>();
        for (String name : names) {
            Path path = tempDir.resolve(name);
            Files.writeString(path, "content-for-" + name, StandardCharsets.UTF_8);
            sources.add(new ResumeZipBuilder.SourceEntry(name, path));
            files.add(Map.of(
                    "name", name,
                    "size", Files.size(path),
                    "sha256", ResumeArtifactHashes.sha256(path)));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ResumeZipBuilder(new ObjectMapper()).write(output, sources, Map.of("files", files), 12);

        Map<String, byte[]> entries = unzip(output.toByteArray());
        assertEquals(8, entries.size());
        names.forEach(name -> assertTrue(entries.containsKey(name), name));
        JsonNode manifest = new ObjectMapper().readTree(entries.get("manifest.json"));
        assertEquals(7, manifest.path("files").size());
    }

    private Map<String, byte[]> unzip(byte[] content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
