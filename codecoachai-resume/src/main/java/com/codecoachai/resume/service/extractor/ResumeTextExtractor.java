package com.codecoachai.resume.service.extractor;

public interface ResumeTextExtractor {

    boolean supports(String fileExt);

    String extract(byte[] content);
}
