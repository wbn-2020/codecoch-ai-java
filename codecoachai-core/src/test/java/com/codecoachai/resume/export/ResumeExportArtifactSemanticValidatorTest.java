package com.codecoachai.resume.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeExportProperties;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumeExportArtifactSemanticValidatorTest {

    @TempDir
    Path tempDir;

    private final ResumeExportArtifactSemanticValidator validator =
            new ResumeExportArtifactSemanticValidator();

    @Test
    void acceptsRealPdfWithSnapshotFacts() throws Exception {
        Path output = renderPdf(document());

        assertDoesNotThrow(() -> validator.validate("PDF", output, document()));
    }

    @Test
    void acceptsRealDocxWithSnapshotFacts() throws Exception {
        Path output = renderDocx(document());

        assertDoesNotThrow(() -> validator.validate("DOCX", output, document()));
    }

    @Test
    void rejectsReadablePdfWhenItDoesNotContainSnapshotFacts() throws Exception {
        Path output = renderPdf(unrelatedDocument());

        BusinessException error = assertThrows(BusinessException.class,
                () -> validator.validate("PDF", output, document()));

        assertDiagnostic(error, "RESUME_EXPORT_SEMANTIC_MISMATCH");
    }

    @Test
    void rejectsReadablePdfWhenOnlyOneSnapshotSectionWasRendered() throws Exception {
        Path output = renderPdf(partialDocument());

        BusinessException error = assertThrows(BusinessException.class,
                () -> validator.validate("PDF", output, document()));

        assertDiagnostic(error, "RESUME_EXPORT_SEMANTIC_MISMATCH");
    }

    @Test
    void rejectsReadableDocxWhenOnlyOneSnapshotSectionWasRendered() throws Exception {
        Path output = renderDocx(partialDocument());

        BusinessException error = assertThrows(BusinessException.class,
                () -> validator.validate("DOCX", output, document()));

        assertDiagnostic(error, "RESUME_EXPORT_SEMANTIC_MISMATCH");
    }

    @Test
    void rejectsPdfWhenSectionsMatchButResumeIdentityDoesNot() throws Exception {
        Path output = renderPdf(wrongIdentityDocument());

        BusinessException error = assertThrows(BusinessException.class,
                () -> validator.validate("PDF", output, document()));

        assertDiagnostic(error, "RESUME_EXPORT_SEMANTIC_MISMATCH");
    }

    @Test
    void rejectsDocxWhenSectionsMatchButResumeIdentityDoesNot() throws Exception {
        Path output = renderDocx(wrongIdentityDocument());

        BusinessException error = assertThrows(BusinessException.class,
                () -> validator.validate("DOCX", output, document()));

        assertDiagnostic(error, "RESUME_EXPORT_SEMANTIC_MISMATCH");
    }

    @Test
    void acceptsPdfWhenLongSnapshotLineWrapsAcrossRenderedLines() throws Exception {
        AtsResumeDocument wrapped = document();
        wrapped.getSections().add(new AtsResumeDocument.Section(
                "Experience",
                List.of("Designed and operated a production-grade distributed Java platform with observability, "
                        + "idempotent message processing, resilient database migrations, and measurable reliability outcomes.")));
        Path output = renderPdf(wrapped);

        assertDoesNotThrow(() -> validator.validate("PDF", output, wrapped));
    }

    @Test
    void rejectsTruncatedDocxBeforeItCanBeMarkedReady() throws Exception {
        Path valid = renderDocx(document());
        byte[] bytes = Files.readAllBytes(valid);
        Path truncated = tempDir.resolve("resume-truncated.docx");
        Files.write(truncated, java.util.Arrays.copyOf(bytes, Math.max(1, bytes.length / 2)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> validator.validate("DOCX", truncated, document()));

        assertDiagnostic(error, "RESUME_EXPORT_DOCX_INVALID");
    }

    private Path renderPdf(AtsResumeDocument document) throws Exception {
        Path output = tempDir.resolve("resume-" + System.nanoTime() + ".pdf");
        try (OutputStream stream = Files.newOutputStream(output)) {
            new PdfResumeDocumentRenderer(new ResumeExportProperties()).render(document, stream);
        }
        return output;
    }

    private Path renderDocx(AtsResumeDocument document) throws Exception {
        Path output = tempDir.resolve("resume-" + System.nanoTime() + ".docx");
        try (OutputStream stream = Files.newOutputStream(output)) {
            new DocxResumeDocumentRenderer().render(document, stream);
        }
        return output;
    }

    private AtsResumeDocument document() {
        AtsResumeDocument document = new AtsResumeDocument();
        document.setName("Alex Chen");
        document.setHeadline("Backend Engineer");
        document.getSections().add(new AtsResumeDocument.Section(
                "Professional Summary", List.of("Built reliable Java services for production teams.")));
        document.getSections().add(new AtsResumeDocument.Section(
                "Skills", List.of("Java, Spring Boot, MySQL")));
        return document;
    }

    private AtsResumeDocument unrelatedDocument() {
        AtsResumeDocument document = new AtsResumeDocument();
        document.setName("Placeholder");
        document.getSections().add(new AtsResumeDocument.Section(
                "Professional Summary", List.of("This artifact contains unrelated placeholder prose.")));
        return document;
    }

    private AtsResumeDocument partialDocument() {
        AtsResumeDocument document = new AtsResumeDocument();
        document.setName("Alex Chen");
        document.setHeadline("Backend Engineer");
        document.getSections().add(new AtsResumeDocument.Section(
                "Professional Summary", List.of("Built reliable Java services for production teams.")));
        return document;
    }

    private AtsResumeDocument wrongIdentityDocument() {
        AtsResumeDocument document = document();
        document.setName("Jordan Lee");
        document.setHeadline("Product Designer");
        return document;
    }

    private void assertDiagnostic(BusinessException error, String diagnosticCode) {
        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains(diagnosticCode));
    }
}
