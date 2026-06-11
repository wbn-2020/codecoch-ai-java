package com.codecoachai.resume.service.extractor;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class DocxResumeTextExtractor extends AbstractResumeTextExtractor {

    @Override
    public boolean supports(String fileExt) {
        return matches(fileExt, "docx");
    }

    @Override
    public String extract(byte[] content) {
        requireContent(content);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return document.getParagraphs()
                    .stream()
                    .map(paragraph -> paragraph.getText())
                    .collect(Collectors.joining("\n"));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "DOCX 简历文本提取失败，请更换文件或转为文本后重试");
        }
    }
}
