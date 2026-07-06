package com.codecoachai.resume.service.extractor;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfResumeTextExtractor extends AbstractResumeTextExtractor {

    private final ResumeTextExtractProperties properties;

    @Override
    public boolean supports(String fileExt) {
        return matches(fileExt, "pdf");
    }

    @Override
    public String extract(byte[] content) {
        requireContent(content);
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(content))) {
            int maxPages = properties.getMaxPdfPages() <= 0 ? 10 : properties.getMaxPdfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(document.getNumberOfPages(), maxPages));
            return stripper.getText(document);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "PDF 简历文本提取失败，请更换文件或转为文本后重试");
        }
    }
}
