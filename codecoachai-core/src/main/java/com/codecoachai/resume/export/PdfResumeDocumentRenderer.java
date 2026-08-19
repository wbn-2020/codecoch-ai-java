package com.codecoachai.resume.export;

import com.codecoachai.resume.config.ResumeExportProperties;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PdfResumeDocumentRenderer implements ResumeDocumentRenderer {

    private static final List<String> FONT_CANDIDATES = List.of(
            "/opt/codecoachai/fonts/NotoSansCJK-Regular.ttc",
            "C:/Windows/Fonts/NotoSansSC-VF.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsun.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf",
            "/System/Library/Fonts/PingFang.ttc",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");

    private final ResumeExportProperties properties;

    public PdfResumeDocumentRenderer(ResumeExportProperties properties) {
        this.properties = properties;
    }

    @Override
    public String format() {
        return "PDF";
    }

    @Override
    public void render(AtsResumeDocument resume, OutputStream output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            try (LoadedFont loadedFont = loadFont(document, resume)) {
                PDFont font = loadedFont.font();
                AtsResumeDocument.Style style = resume.getStyle() == null
                        ? new AtsResumeDocument.Style()
                        : resume.getStyle();
                PageWriter writer = new PageWriter(document, font, style.getMarginPt());
                writer.center(resume.getName(), style.getNameFontPt(), style.getNameFontPt() * 1.28f);
                writer.center(resume.getHeadline(), style.getHeadlineFontPt(), style.getHeadlineFontPt() * 1.36f);
                writer.center(resume.getContact(), style.getContactFontPt(), style.getContactFontPt() * 1.5f);
                for (AtsResumeDocument.Section section : resume.getSections()) {
                    writer.line(section.getHeading().toUpperCase(Locale.ROOT), style.getHeadingFontPt(),
                            style.getHeadingFontPt() * 1.55f, false);
                    for (String line : section.getLines()) {
                        writer.wrapped("- " + line, style.getBodyFontPt(),
                                style.getBodyFontPt() * style.getLineSpacing() * 1.2f);
                    }
                }
                writer.close();
                document.save(output);
            }
        }
    }

    private LoadedFont loadFont(PDDocument document, AtsResumeDocument resume) throws IOException {
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(properties.getPdfFontPath())) {
            candidates.add(properties.getPdfFontPath().trim());
        } else {
            candidates.addAll(FONT_CANDIDATES);
        }
        String requiredText = documentText(resume);
        List<String> failures = new ArrayList<>();
        for (String candidate : candidates) {
            Path path;
            try {
                path = Path.of(candidate);
            } catch (InvalidPathException ex) {
                failures.add(candidate + " (invalid path)");
                continue;
            }
            if (!Files.isRegularFile(path)) {
                failures.add(candidate + " (file not found)");
                continue;
            }
            String lower = candidate.toLowerCase(Locale.ROOT);
            try {
                if (lower.endsWith(".ttc")) {
                    LoadedFont font = loadTrueTypeCollection(document, path, requiredText);
                    if (font != null) {
                        return font;
                    }
                    failures.add(candidate + " (no collection face can encode all resume characters)");
                } else if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
                    PDFont font = PDType0Font.load(document, path.toFile());
                    if (canEncode(font, requiredText)) {
                        return new LoadedFont(font, null);
                    }
                    failures.add(candidate + " (font cannot encode all resume characters)");
                } else {
                    failures.add(candidate + " (unsupported font format)");
                }
            } catch (IOException | RuntimeException ex) {
                failures.add(candidate + " (load failed: " + failureMessage(ex) + ")");
            }
        }
        throw new IOException(
                "No usable PDF font found for resume text. Checked candidates: "
                        + String.join("; ", failures));
    }

    private LoadedFont loadTrueTypeCollection(PDDocument document,
                                              Path path,
                                              String requiredText) throws IOException {
        TrueTypeCollection collection = new TrueTypeCollection(path.toFile());
        PDFont[] selected = new PDFont[1];
        try {
            collection.processAllFonts(trueTypeFont -> {
                if (selected[0] != null) {
                    return;
                }
                PDFont font = PDType0Font.load(document, trueTypeFont, true);
                if (canEncode(font, requiredText)) {
                    selected[0] = font;
                }
            });
            if (selected[0] == null) {
                collection.close();
                return null;
            }
            return new LoadedFont(selected[0], collection);
        } catch (IOException | RuntimeException ex) {
            try {
                collection.close();
            } catch (IOException closeFailure) {
                ex.addSuppressed(closeFailure);
            }
            throw ex;
        }
    }

    private String failureMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage())
                ? ex.getMessage().replace('\n', ' ').replace('\r', ' ')
                : ex.getClass().getSimpleName();
    }

    private String documentText(AtsResumeDocument resume) {
        StringBuilder text = new StringBuilder();
        appendText(text, resume.getName());
        appendText(text, resume.getHeadline());
        appendText(text, resume.getContact());
        for (AtsResumeDocument.Section section : resume.getSections()) {
            appendText(text, section.getHeading());
            for (String line : section.getLines()) {
                appendText(text, line);
            }
        }
        return text.toString();
    }

    private void appendText(StringBuilder target, String value) {
        if (StringUtils.hasText(value)) {
            target.append(value).append('\n');
        }
    }

    private boolean canEncode(PDFont font, String value) {
        try {
            String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
            for (int offset = 0; offset < normalized.length();) {
                int codePoint = normalized.codePointAt(offset);
                font.encode(new String(Character.toChars(codePoint)));
                offset += Character.charCount(codePoint);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private record LoadedFont(PDFont font, Closeable resource) implements Closeable {

        @Override
        public void close() throws IOException {
            if (resource != null) {
                resource.close();
            }
        }
    }

    private static final class PageWriter implements AutoCloseable {
        private final PDDocument document;
        private final PDFont font;
        private final float margin;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        private PageWriter(PDDocument document, PDFont font, float margin) throws IOException {
            this.document = document;
            this.font = font;
            this.margin = margin;
            newPage();
        }

        private void center(String text, float size, float leading) throws IOException {
            String safe = safeText(text);
            if (safe.isBlank()) {
                return;
            }
            ensureSpace(leading);
            float width = font.getStringWidth(safe) / 1000f * size;
            write(safe, size, Math.max(margin, (PDRectangle.A4.getWidth() - width) / 2f));
            y -= leading;
        }

        private void line(String text, float size, float leading, boolean indent) throws IOException {
            String safe = safeText(text);
            if (safe.isBlank()) {
                return;
            }
            ensureSpace(leading);
            write(safe, size, margin + (indent ? 10f : 0f));
            y -= leading;
        }

        private void wrapped(String text, float size, float leading) throws IOException {
            String safe = safeText(text);
            float maxWidth = PDRectangle.A4.getWidth() - (margin * 2f) - 10f;
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < safe.length();) {
                int codePoint = safe.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                String candidate = line + character;
                if (line.length() > 0 && width(candidate, size) > maxWidth) {
                    line(line.toString().trim(), size, leading, true);
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty()) {
                line(line.toString().trim(), size, leading, true);
            }
        }

        private float width(String value, float size) throws IOException {
            return font.getStringWidth(value) / 1000f * size;
        }

        private void write(String value, float size, float x) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(value);
            content.endText();
        }

        private String safeText(String value) throws IOException {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            String normalized = value.replace('\n', ' ').replace('\r', ' ');
            for (int offset = 0; offset < normalized.length();) {
                int codePoint = normalized.codePointAt(offset);
                try {
                    font.encode(new String(Character.toChars(codePoint)));
                } catch (Exception ex) {
                    throw new IOException(
                            "PDF font cannot encode resume character U+"
                                    + Integer.toHexString(codePoint).toUpperCase(),
                            ex);
                }
                offset += Character.charCount(codePoint);
            }
            return normalized;
        }

        private void ensureSpace(float leading) throws IOException {
            if (y - leading < margin) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            closeContent();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - margin;
        }

        private void closeContent() throws IOException {
            if (content != null) {
                content.close();
            }
        }

        @Override
        public void close() throws IOException {
            closeContent();
        }
    }
}
