package com.codecoachai.resume.export;

import com.codecoachai.resume.config.ResumeExportProperties;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PdfResumeDocumentRenderer implements ResumeDocumentRenderer {

    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/NotoSansSC-VF.ttf",
            "C:/Windows/Fonts/simhei.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
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
            PDFont font = loadFont(document);
            AtsResumeDocument.Style style = resume.getStyle() == null
                    ? new AtsResumeDocument.Style()
                    : resume.getStyle();
            PageWriter writer = new PageWriter(document, font, style.getMarginPt());
            writer.center(resume.getName(), style.getNameFontPt(), style.getNameFontPt() * 1.28f);
            writer.center(resume.getHeadline(), style.getHeadlineFontPt(), style.getHeadlineFontPt() * 1.36f);
            writer.center(resume.getContact(), style.getContactFontPt(), style.getContactFontPt() * 1.5f);
            for (AtsResumeDocument.Section section : resume.getSections()) {
                writer.line(section.getHeading().toUpperCase(), style.getHeadingFontPt(),
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

    private PDFont loadFont(PDDocument document) throws IOException {
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(properties.getPdfFontPath())) {
            candidates.add(properties.getPdfFontPath());
        }
        candidates.addAll(FONT_CANDIDATES);
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path) && !candidate.toLowerCase().endsWith(".ttc")) {
                try {
                    return PDType0Font.load(document, new File(candidate));
                } catch (IOException ignored) {
                    // Try the next configured/system font.
                }
            }
        }
        return PDType1Font.HELVETICA;
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

        private String safeText(String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            StringBuilder safe = new StringBuilder();
            value.codePoints().forEach(codePoint -> {
                String character = new String(Character.toChars(codePoint));
                try {
                    font.encode(character);
                    safe.append(character);
                } catch (Exception ignored) {
                    safe.append('?');
                }
            });
            return safe.toString().replace('\n', ' ').replace('\r', ' ');
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
