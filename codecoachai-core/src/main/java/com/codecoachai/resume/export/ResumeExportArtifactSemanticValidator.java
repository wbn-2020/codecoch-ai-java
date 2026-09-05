package com.codecoachai.resume.export;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.util.StringUtils;

/**
 * Verifies that a rendered resume can be opened and still carries facts from its immutable snapshot.
 */
public final class ResumeExportArtifactSemanticValidator {

    private static final String PDF_INVALID = "RESUME_EXPORT_PDF_INVALID";
    private static final String DOCX_INVALID = "RESUME_EXPORT_DOCX_INVALID";
    private static final String SOURCE_EMPTY = "RESUME_EXPORT_SOURCE_EMPTY";
    private static final String SEMANTIC_MISMATCH = "RESUME_EXPORT_SEMANTIC_MISMATCH";

    public void validate(String format, Path artifact, AtsResumeDocument document) {
        ValidationFacts facts = ValidationFacts.from(document);
        if (facts.sectionHeadings().isEmpty() || facts.substantiveLines().isEmpty()) {
            fail(SOURCE_EMPTY, "Resume snapshot does not contain exportable section content");
        }
        String text = switch (format) {
            case "PDF" -> pdfText(artifact);
            case "DOCX" -> docxText(artifact);
            default -> throw new IllegalArgumentException("Unsupported resume artifact format: " + format);
        };
        verifyFacts(text, facts);
    }

    private String pdfText(Path artifact) {
        try (PDDocument document = Loader.loadPDF(artifact.toFile())) {
            if (document.getNumberOfPages() < 1) {
                fail(PDF_INVALID, "PDF has no pages");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder allText = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                if (!StringUtils.hasText(pageText)) {
                    fail(PDF_INVALID, "PDF page " + page + " has no extractable text");
                }
                allText.append(pageText).append('\n');
            }
            return allText.toString();
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            fail(PDF_INVALID, "PDF cannot be opened");
            return "";
        }
    }

    private String docxText(Path artifact) {
        try (XWPFDocument document = new XWPFDocument(java.nio.file.Files.newInputStream(artifact))) {
            if (document.getDocument() == null || document.getDocument().getBody() == null
                    || document.getParagraphs().isEmpty()) {
                fail(DOCX_INVALID, "DOCX has no document body paragraphs");
            }
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText())
                    .filter(StringUtils::hasText)
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (!StringUtils.hasText(text)) {
                fail(DOCX_INVALID, "DOCX has no extractable text");
            }
            return text;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            fail(DOCX_INVALID, "DOCX cannot be opened");
            return "";
        }
    }

    private void verifyFacts(String text, ValidationFacts facts) {
        String normalizedText = comparisonKey(text);
        List<String> missingIdentityFacts = facts.identityFacts().stream()
                .map(this::comparisonKey)
                .filter(StringUtils::hasText)
                .filter(identity -> !normalizedText.contains(identity))
                .toList();
        if (!missingIdentityFacts.isEmpty()) {
            fail(SEMANTIC_MISMATCH, "Rendered artifact is missing expected resume identity");
        }

        List<String> missingHeadings = facts.sectionHeadings().stream()
                .map(this::comparisonKey)
                .filter(StringUtils::hasText)
                .filter(heading -> !normalizedText.contains(heading))
                .toList();
        if (!missingHeadings.isEmpty()) {
            fail(SEMANTIC_MISMATCH, "Rendered artifact is missing expected resume section headings");
        }

        List<String> missingLines = facts.substantiveLines().stream()
                .map(this::comparisonKey)
                .filter(StringUtils::hasText)
                .filter(line -> !normalizedText.contains(line))
                .toList();
        if (!missingLines.isEmpty()) {
            fail(SEMANTIC_MISMATCH, "Rendered artifact is missing expected resume content");
        }
    }

    private String comparisonKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private void fail(String code, String detail) {
        throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR, code + ": " + detail);
    }

    private record ValidationFacts(
            List<String> identityFacts,
            List<String> sectionHeadings,
            List<String> substantiveLines) {

        private static ValidationFacts from(AtsResumeDocument document) {
            if (document == null) {
                return new ValidationFacts(List.of(), List.of(), List.of());
            }
            List<String> identity = new ArrayList<>();
            List<String> headings = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            if (StringUtils.hasText(document.getName())) {
                identity.add(document.getName().trim());
            }
            if (StringUtils.hasText(document.getHeadline())) {
                identity.add(document.getHeadline().trim());
            }
            for (AtsResumeDocument.Section section : document.getSections()) {
                if (section == null) {
                    continue;
                }
                if (StringUtils.hasText(section.getHeading())) {
                    headings.add(section.getHeading().trim());
                }
                if (section.getLines() != null) {
                    section.getLines().stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .forEach(lines::add);
                }
            }
            return new ValidationFacts(
                    List.copyOf(identity),
                    List.copyOf(headings),
                    List.copyOf(lines));
        }
    }
}
