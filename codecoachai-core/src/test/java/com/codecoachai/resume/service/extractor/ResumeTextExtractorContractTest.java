package com.codecoachai.resume.service.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResumeTextExtractorContractTest {

    private ResumeTextExtractProperties properties;
    private ResumeTextExtractorDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new ResumeTextExtractProperties();
        dispatcher = new ResumeTextExtractorDispatcher(
                List.of(
                        new PlainTextResumeTextExtractor(),
                        new DocxResumeTextExtractor(properties),
                        new PdfResumeTextExtractor(properties)),
                properties);
    }

    @Test
    void extractsTxtDocxAndPdfContent() throws Exception {
        assertEquals(
                "Java backend resume",
                dispatcher.extract("txt", "Java backend resume".getBytes(StandardCharsets.UTF_8)));
        assertTrue(dispatcher.extract("docx", docx("Spring Boot project")).contains("Spring Boot project"));
        assertTrue(dispatcher.extract("pdf", pdf("MySQL performance")).contains("MySQL performance"));
    }

    @Test
    void rejectsEmptyUnsupportedAndOversizedSourceFiles() {
        assertThrows(BusinessException.class, () -> dispatcher.extract("txt", new byte[0]));
        assertThrows(
                BusinessException.class,
                () -> dispatcher.extract("exe", "resume".getBytes(StandardCharsets.UTF_8)));

        properties.setMaxSourceFileSizeMb(1);
        assertThrows(
                BusinessException.class,
                () -> dispatcher.extract("txt", new byte[1024 * 1024 + 1]));
    }

    @Test
    void truncatesLargeExtractedTextAtConfiguredBoundary() {
        properties.setMaxExtractedTextChars(100);
        String text = "Java".repeat(100);

        String extracted = dispatcher.extract("txt", text.getBytes(StandardCharsets.UTF_8));

        assertEquals(100, extracted.length());
    }

    private byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
