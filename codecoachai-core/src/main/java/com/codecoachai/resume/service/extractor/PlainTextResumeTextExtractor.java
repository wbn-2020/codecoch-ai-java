package com.codecoachai.resume.service.extractor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlainTextResumeTextExtractor extends AbstractResumeTextExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "txt");

    @Override
    public boolean supports(String fileExt) {
        return StringUtils.hasText(fileExt) && SUPPORTED_EXTENSIONS.contains(fileExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public String extract(byte[] content) {
        requireContent(content);
        return new String(content, StandardCharsets.UTF_8);
    }
}
