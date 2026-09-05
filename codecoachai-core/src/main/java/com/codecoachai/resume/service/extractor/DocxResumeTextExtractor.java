package com.codecoachai.resume.service.extractor;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocxResumeTextExtractor extends AbstractResumeTextExtractor {

    private static final long MAX_ZIP_ENTRY_BYTES = 20L * 1024L * 1024L;

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(MAX_ZIP_ENTRY_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_ZIP_ENTRY_BYTES);
    }

    private final ResumeTextExtractProperties properties;

    @Override
    public boolean supports(String fileExt) {
        return matches(fileExt, "docx");
    }

    @Override
    public String extract(byte[] content) {
        requireContent(content);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            int maxChars = Math.max(1, properties.getMaxExtractedTextChars());
            StringBuilder text = new StringBuilder(Math.min(maxChars, 8192));
            for (var paragraph : document.getParagraphs()) {
                String value = paragraph.getText();
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                int remaining = maxChars - text.length();
                if (remaining <= 0) {
                    break;
                }
                text.append(value, 0, Math.min(value.length(), remaining));
                if (text.length() >= maxChars) {
                    break;
                }
            }
            return text.toString();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "DOCX 简历文本提取失败，请更换文件或转为文本后重试");
        }
    }
}
