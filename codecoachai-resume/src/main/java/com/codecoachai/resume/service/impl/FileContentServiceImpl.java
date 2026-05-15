package com.codecoachai.resume.service.impl;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileDownloadVO;
import com.codecoachai.resume.service.FileContentService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FileContentServiceImpl implements FileContentService {

    private static final String BIZ_TYPE_RESUME = "RESUME";
    private static final String HEADER_ORIGINAL_FILENAME = "X-Original-Filename";
    private static final String HEADER_FILE_EXT = "X-File-Ext";
    private static final String HEADER_FILE_SIZE = "X-File-Size";
    private static final String HEADER_MIME_TYPE = "X-Mime-Type";

    private final FileFeignClient fileFeignClient;

    @Override
    public InnerFileDownloadVO downloadResumeFile(Long fileId, Long userId) {
        ResponseEntity<byte[]> response = fileFeignClient.download(fileId, userId, BIZ_TYPE_RESUME);
        byte[] content = response == null ? null : response.getBody();
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file content is empty");
        }
        HttpHeaders headers = response.getHeaders();
        InnerFileDownloadVO vo = new InnerFileDownloadVO();
        vo.setFileId(fileId);
        vo.setOriginalFilename(decodeHeaderValue(firstHeader(headers, HEADER_ORIGINAL_FILENAME)));
        vo.setFileExt(firstHeader(headers, HEADER_FILE_EXT));
        vo.setMimeType(firstHeader(headers, HEADER_MIME_TYPE));
        vo.setFileSize(parseLong(firstHeader(headers, HEADER_FILE_SIZE), (long) content.length));
        vo.setContent(content);
        return vo;
    }

    private String firstHeader(HttpHeaders headers, String name) {
        if (headers == null || !StringUtils.hasText(name)) {
            return null;
        }
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private Long parseLong(String value, Long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String decodeHeaderValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
