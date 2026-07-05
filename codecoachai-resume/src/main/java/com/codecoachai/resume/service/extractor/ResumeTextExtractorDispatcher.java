package com.codecoachai.resume.service.extractor;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeTextExtractorDispatcher {

    private final List<ResumeTextExtractor> extractors;
    private final ResumeTextExtractProperties properties;

    public String extract(String fileExt, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容为空，请更换文件后重试");
        }
        if (content.length > properties.maxSourceFileBytes()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Resume file is too large for online parsing. Please upload a smaller PDF/DOCX file.");
        }
        ResumeTextExtractor extractor = extractors.stream()
                .filter(item -> item.supports(fileExt))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "unsupported resume file type"));
        String text = extractor.extract(content);
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未能提取到简历正文，请更换文件或转为文本后重试");
        }
        return truncateIfNecessary(text.trim(), fileExt);
    }

    private String truncateIfNecessary(String text, String fileExt) {
        int maxChars = properties.getMaxExtractedTextChars();
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        log.warn("Resume extracted text truncated, fileExt={}, originalChars={}, maxChars={}",
                fileExt, text.length(), maxChars);
        return text.substring(0, maxChars);
    }
}
