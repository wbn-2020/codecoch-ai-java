package com.codecoachai.resume.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

@Component
public class DocxResumeDocumentRenderer implements ResumeDocumentRenderer {

    @Override
    public String format() {
        return "DOCX";
    }

    @Override
    public void render(AtsResumeDocument resume, OutputStream output) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            AtsResumeDocument.Style style = resume.getStyle() == null
                    ? new AtsResumeDocument.Style()
                    : resume.getStyle();
            configurePage(document, style);
            paragraph(document, resume.getName(), style.getNameFontPt(), true,
                    ParagraphAlignment.CENTER, false, style);
            paragraph(document, resume.getHeadline(), style.getHeadlineFontPt(), false,
                    ParagraphAlignment.CENTER, false, style);
            paragraph(document, resume.getContact(), style.getContactFontPt(), false,
                    ParagraphAlignment.CENTER, false, style);
            for (AtsResumeDocument.Section section : resume.getSections()) {
                paragraph(document, section.getHeading().toUpperCase(), style.getHeadingFontPt(), true,
                        ParagraphAlignment.LEFT, false, style);
                for (String line : section.getLines()) {
                    paragraph(document, line, style.getBodyFontPt(), false,
                            ParagraphAlignment.LEFT, true, style);
                }
            }
            document.write(output);
        }
    }

    private void configurePage(XWPFDocument document, AtsResumeDocument.Style style) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        BigInteger margin = BigInteger.valueOf(Math.round(style.getMarginPt() * 20));
        margins.setTop(margin);
        margins.setBottom(margin);
        margins.setLeft(margin);
        margins.setRight(margin);
    }

    private void paragraph(XWPFDocument document, String text, float size, boolean bold,
                           ParagraphAlignment alignment, boolean bullet, AtsResumeDocument.Style style) {
        if (text == null || text.isBlank()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(bold ? 80 : 40);
        paragraph.setSpacingBetween(style.getLineSpacing());
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(style.getFontFamily());
        run.setFontSize(Math.max(8, Math.round(size)));
        run.setBold(bold);
        run.setText(bullet ? "- " + text : text);
    }
}
