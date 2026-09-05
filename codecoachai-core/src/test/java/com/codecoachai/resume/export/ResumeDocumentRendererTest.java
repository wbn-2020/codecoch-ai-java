package com.codecoachai.resume.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import org.apache.pdfbox.Loader;
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

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
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
    void configuredTtfPreservesChineseTextWithoutReplacementCharacters() throws Exception {
        assertChinesePdfRoundTrip(requiredChineseFont(".ttf"));
    }

    @Test
    void configuredOtfPreservesChineseTextWithoutReplacementCharacters() throws Exception {
        Path source = requiredChineseFont(".ttf");
        Path configuredOtf = tempDir.resolve("configured-chinese-font.otf");
        Files.copy(source, configuredOtf);

        assertChinesePdfRoundTrip(configuredOtf);
    }

    @Test
    void configuredTtcPreservesChineseTextWithoutReplacementCharacters() throws Exception {
        assertChinesePdfRoundTrip(requiredChineseFont(".ttc"));
    }

    @Test
    void configuredFontWithoutRequiredGlyphsFailsClosed() throws Exception {
        ResumeExportProperties properties = new ResumeExportProperties();
        properties.setPdfFontPath(requiredLatinOnlyFont().toString());

        IOException error = assertThrows(IOException.class, () -> {
            try (OutputStream output = Files.newOutputStream(tempDir.resolve("unsupported-glyph.pdf"))) {
                new PdfResumeDocumentRenderer(properties).render(chineseDocument(), output);
            }
        });

        assertTrue(error.getMessage().contains("No usable PDF font found for resume text"));
        assertTrue(error.getMessage().contains("cannot encode all resume characters"));
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

    @Test
    void snapshotPresentationConfigOverridesTemplateWithoutChangingFacts() throws Exception {
        ObjectNode snapshotNode = objectMapper.createObjectNode()
                .put("realName", "Alex Chen")
                .put("targetPosition", "Backend Engineer")
                .put("phone", "13800000000")
                .put("email", "alex@example.com")
                .put("summary", "Reliable Java engineer.")
                .put("skillStack", "Java, Spring Boot")
                .put("workExperience", "Built stable services.")
                .put("educationExperience", "B.Sc. Computer Science");
        snapshotNode.putArray("projects")
                .addObject()
                .put("projectName", "CodeCoachAI")
                .put("description", "Built a career coaching workflow.");
        ObjectNode presentation = snapshotNode.putObject("presentationConfig");
        presentation.put("fontFamily", "Microsoft YaHei");
        presentation.put("fontScale", 1.1);
        presentation.put("lineHeight", 1.4);
        presentation.put("pageMarginPt", 50);
        presentation.putArray("sectionOrder")
                .add("projects")
                .add("summary")
                .add("skills")
                .add("experience")
                .add("education");
        presentation.putArray("hiddenSections").add("skills");
        presentation.putObject("fieldVisibility").put("email", false);

        AtsResumeDocument resume = new AtsResumeDocumentFactory(objectMapper).fromSnapshot(
                objectMapper.writeValueAsString(snapshotNode),
                "{\"marginPt\":32,\"fontFamily\":\"Arial\",\"bodyFontPt\":10}");

        assertEquals("Microsoft YaHei", resume.getStyle().getFontFamily());
        assertEquals(50f, resume.getStyle().getMarginPt());
        assertEquals(11f, resume.getStyle().getBodyFontPt());
        assertEquals(1.4f, resume.getStyle().getLineSpacing());
        assertEquals("13800000000", resume.getContact());
        assertEquals(
                List.of("Projects", "Professional Summary", "Experience", "Education"),
                resume.getSections().stream().map(AtsResumeDocument.Section::getHeading).toList());
    }
    @Test
    void normalizedDefaultPresentationKeepsTemplateDefinitionDefaults() throws Exception {
        ObjectNode snapshotNode = objectMapper.createObjectNode()
                .put("realName", "Alex Chen")
                .put("summary", "Reliable Java engineer.")
                .put("skillStack", "Java, Spring Boot")
                .put("workExperience", "Built stable services.")
                .put("educationExperience", "B.Sc. Computer Science");
        snapshotNode.putObject("presentationConfig")
                .put("schemaVersion", 1)
                .put("templateCode", "ATS_COMPACT")
                .put("templateVersion", 1)
                .put("fontFamily", "Arial")
                .put("lineHeight", 1.2)
                .put("pageMarginPt", 42)
                .putObject("overrides");

        AtsResumeDocument resume = new AtsResumeDocumentFactory(objectMapper).fromSnapshot(
                objectMapper.writeValueAsString(snapshotNode),
                "{\"marginPt\":32,\"fontFamily\":\"Arial\",\"lineSpacing\":1.0,\"sectionOrder\":[\"SUMMARY\",\"SKILLS\",\"EXPERIENCE\",\"PROJECTS\",\"EDUCATION\"]}");

        assertEquals(32f, resume.getStyle().getMarginPt());
        assertEquals(1f, resume.getStyle().getLineSpacing());
        assertEquals(
                List.of("Professional Summary", "Skills", "Experience", "Education"),
                resume.getSections().stream().map(AtsResumeDocument.Section::getHeading).toList());
    }

    @Test
    void basicPresentationSettingsReachFormalDocumentModel() throws Exception {
        ObjectNode snapshotNode = objectMapper.createObjectNode()
                .put("realName", "Alex Chen")
                .put("targetPosition", "Backend Engineer")
                .put("phone", "13800000000")
                .put("email", "alex@example.com")
                .put("summary", "Reliable Java engineer.")
                .put("workExperience", "Built stable services.");
        ObjectNode presentation = snapshotNode.putObject("presentationConfig");
        presentation.put("schemaVersion", 1)
                .put("basicLayout", "LEFT")
                .put("sectionSpacing", 0.8)
                .put("autoOnePage", true);
        presentation.putObject("basicFieldVisibility")
                .put("phone", false)
                .put("email", true);
        presentation.putArray("basicFieldOrder").add("email").add("phone");
        presentation.put("iconMode", "TEXT");
        presentation.putObject("overrides")
                .put("basicLayout", true)
                .put("basicFieldVisibility", true)
                .put("basicFieldOrder", true)
                .put("iconMode", true)
                .put("sectionSpacing", true)
                .put("autoOnePage", true);

        AtsResumeDocument resume = new AtsResumeDocumentFactory(objectMapper).fromSnapshot(
                objectMapper.writeValueAsString(snapshotNode),
                "{\"marginPt\":42,\"lineSpacing\":1.2}");

        assertEquals("Alex Chen", resume.getName());
        assertEquals("Backend Engineer", resume.getHeadline());
        assertEquals("邮箱: alex@example.com", resume.getContact());
        assertEquals("LEFT", resume.getStyle().getIdentityAlignment());
        assertEquals(0.7f, resume.getStyle().getSectionSpacing());
        assertTrue(resume.getStyle().isAutoOnePage());
        assertTrue(resume.getStyle().getBodyFontPt() < 10f);
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

    private void assertChinesePdfRoundTrip(Path fontPath) throws Exception {
        ResumeExportProperties properties = new ResumeExportProperties();
        properties.setPdfFontPath(fontPath.toString());
        Path output = tempDir.resolve("resume-" + fontPath.getFileName() + ".pdf");
        try (OutputStream stream = Files.newOutputStream(output)) {
            new PdfResumeDocumentRenderer(properties).render(chineseDocument(), stream);
        }

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("张伟"));
            assertTrue(text.contains("高级后端工程师"));
            assertTrue(text.contains("负责核心交易链路稳定性建设"));
            assertFalse(text.contains("?"), "Chinese resume text must never be replaced with '?'");
        }
    }

    private AtsResumeDocument chineseDocument() {
        AtsResumeDocument resume = new AtsResumeDocument();
        resume.setName("张伟");
        resume.setHeadline("高级后端工程师");
        resume.setContact("zhangwei@example.com");
        resume.getSections().add(new AtsResumeDocument.Section(
                "工作经历",
                List.of("负责核心交易链路稳定性建设，接口延迟降低百分之三十五。")));
        return resume;
    }

    private Path requiredChineseFont(String extension) {
        List<Path> candidates = ".ttc".equals(extension)
                ? List.of(
                        Path.of("C:/Windows/Fonts/msyh.ttc"),
                        Path.of("C:/Windows/Fonts/simsun.ttc"),
                        Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
                        Path.of("/System/Library/Fonts/PingFang.ttc"))
                : List.of(
                        Path.of("C:/Windows/Fonts/simhei.ttf"),
                        Path.of("C:/Windows/Fonts/Deng.ttf"),
                        Path.of("C:/Windows/Fonts/simsunb.ttf"),
                        Path.of("/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf"));
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "A configured Chinese " + extension + " font is required for PDF export tests"));
    }

    private Path requiredLatinOnlyFont() {
        return List.of(
                        Path.of("C:/Windows/Fonts/arial.ttf"),
                        Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
                        Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"))
                .stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "A Latin-only font is required for PDF fail-closed tests"));
    }
}
