package com.codecoachai.resume.service.extractor;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.Locale;
import org.springframework.util.StringUtils;

abstract class AbstractResumeTextExtractor implements ResumeTextExtractor {

    protected boolean matches(String fileExt, String expected) {
        return StringUtils.hasText(fileExt) && expected.equals(fileExt.toLowerCase(Locale.ROOT));
    }

    protected void requireContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file content is empty");
        }
    }
}
