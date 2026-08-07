package com.codecoachai.resume.service.extractor;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

@Component
public class DocResumeTextExtractor extends AbstractResumeTextExtractor {

    @Override
    public boolean supports(String fileExt) {
        return matches(fileExt, "doc");
    }

    @Override
    public String extract(byte[] content) {
        requireContent(content);
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "DOC 简历文本提取失败，请更换文件或转为文本后重试");
        }
    }
}
