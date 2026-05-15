package com.codecoachai.resume.service.extractor;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfResumeTextExtractor extends AbstractResumeTextExtractor {

    @Override
    public boolean supports(String fileExt) {
        return matches(fileExt, "pdf");
    }

    @Override
    public String extract(byte[] content) {
        requireContent(content);
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(content))) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "PDF text extraction failed");
        }
    }
}
