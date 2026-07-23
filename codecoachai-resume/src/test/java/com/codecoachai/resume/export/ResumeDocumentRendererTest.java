package com.codecoachai.resume.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.config.ResumeExportProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumeDocumentRendererTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void docxCanBeOpenedAndTextExtracted() throws Exception {
        AtsResumeDocument resume = document();
        Path output = tempDir.resolve("resume.docx");
        try (OutputStream file = Files.newOutputStream(output);
             LimitedOutputStream limited = new LimitedOutputStream(file, 2_000_000L)) {
            new DocxResumeDocumentRenderer().render(resume, limited);
        }

        try (InputStream input = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertTrue(text.contains("Alex Chen"));
            assertTrue(text.contains("reduced API latency by 35%"));
            assertTrue(text.contains("SKILLS"));
        }
    }

    @Test
    void pdfCanBeOpenedAndTextExtracted() throws Exception {
        AtsResumeDocument resume = document();
        Path output = tempDir.resolve("resume.pdf");
        ResumeExportProperties properties = new ResumeExportProperties();
        try (OutputStream file = Files.newOutputStream(output);
             LimitedOutputStream limited = new LimitedOutputStream(file, 2_000_000L)) {
            new PdfResumeDocumentRenderer(properties).render(resume, limited);
        }

        try (PDDocument document = PDDocument.load(output.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            assertTrue(text.contains("Alex Chen"));
            assertTrue(text.contains("reduced API latency by 35%"));
            assertTrue(text.contains("EXPERIENCE"));
            assertTrue(document.getNumberOfPages() >= 1);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertTrue(!stripper.getText(document).isBlank(), "PDF page " + page + " must not be blank");
            }
        }
    }

    @Test
    void outputLimitStopsOversizedArtifact() throws Exception {
        Path output = tempDir.resolve("limited.bin");
        try (OutputStream file = Files.newOutputStream(output);
             LimitedOutputStream limited = new LimitedOutputStream(file, 3)) {
            assertThrows(IOException.class, () -> limited.write(new byte[] {1, 2, 3, 4}));
        }
    }

    @Test
    void templateDefinitionControlsStyleOrderAndHiddenSections() throws Exception {
        String template = """
                {
                  "marginPt": 32,
                  "fontFamily": "Arial",
                  "nameFontPt": 17,
                  "headlineFontPt": 10,
                  "contactFontPt": 8,
                  "headingFontPt": 10,
                  "bodyFontPt": 9,
                  "lineSpacing": 1.0,
                  "sectionOrder": ["PROJECTS", "EXPERIENCE", "SUMMARY", "SKILLS", "EDUCATION"],
                  "hiddenSections": ["SKILLS"]
                }
                """;

        AtsResumeDocument resume = document(template);

        assertEquals(32f, resume.getStyle().getMarginPt());
        assertEquals("Arial", resume.getStyle().getFontFamily());
        assertEquals(17f, resume.getStyle().getNameFontPt());
        assertEquals(9f, resume.getStyle().getBodyFontPt());
        assertEquals(
                java.util.List.of("Projects", "Experience", "Professional Summary", "Education"),
                resume.getSections().stream().map(AtsResumeDocument.Section::getHeading).toList());
    }

    @Test
    void templateValuesAreBoundedBeforeRendering() throws Exception {
        AtsResumeDocument resume = document("""
                {
                  "marginPt": 500,
                  "nameFontPt": 2,
                  "bodyFontPt": 100,
                  "lineSpacing": 9
                }
                """);

        assertEquals(72f, resume.getStyle().getMarginPt());
        assertEquals(14f, resume.getStyle().getNameFontPt());
        assertEquals(14f, resume.getStyle().getBodyFontPt());
        assertEquals(1.6f, resume.getStyle().getLineSpacing());
    }

    private AtsResumeDocument document() throws Exception {
        return document(null);
    }

    private AtsResumeDocument document(String templateDefinition) throws Exception {
        ObjectNode snapshotNode = objectMapper.createObjectNode()
                .put("realName", "Alex Chen")
                .put("targetPosition", "Backend Engineer")
                .put("phone", "13800000000")
                .put("email", "alex@example.com")
                .put("summary", "Reliable Java engineer focused on measurable delivery.")
                .put("skillStack", "Java, Spring Boot, MySQL")
                .put("workExperience", "Built a service that reduced API latency by 35%.")
                .put("educationExperience", "B.Sc. Computer Science");
        snapshotNode.putArray("projects")
                .addObject()
                .put("projectName", "CodeCoachAI")
                .put("description", "Built an evidence-driven career coaching workflow.");
        String snapshot = objectMapper.writeValueAsString(snapshotNode);
        return new AtsResumeDocumentFactory(objectMapper).fromSnapshot(snapshot, templateDefinition);
    }
}
