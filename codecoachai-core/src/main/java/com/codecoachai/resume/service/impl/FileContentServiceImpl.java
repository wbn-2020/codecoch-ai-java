package com.codecoachai.resume.service.impl;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileDownloadVO;
import com.codecoachai.resume.service.FileContentService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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
    private final ResumeTextExtractProperties textExtractProperties;

    @Override
    public InnerFileDownloadVO downloadResumeFile(Long fileId, Long userId) {
        ResponseEntity<Resource> response = fileFeignClient.download(fileId, userId, BIZ_TYPE_RESUME);
        Resource resource = response == null ? null : response.getBody();
        HttpHeaders headers = response == null ? null : response.getHeaders();
        long declaredSize = parseLong(firstHeader(headers, HEADER_FILE_SIZE), 0L);
        validateResumeFileSize(declaredSize, 0);
        byte[] content = readContent(resource, declaredSize);
        validateResumeFileSize(declaredSize, content.length);

        InnerFileDownloadVO vo = new InnerFileDownloadVO();
        vo.setFileId(fileId);
        vo.setOriginalFilename(decodeHeaderValue(firstHeader(headers, HEADER_ORIGINAL_FILENAME)));
        vo.setFileExt(firstHeader(headers, HEADER_FILE_EXT));
        vo.setMimeType(firstHeader(headers, HEADER_MIME_TYPE));
        vo.setFileSize(declaredSize > 0 ? declaredSize : (long) content.length);
        vo.setContent(content);
        return vo;
    }

    private byte[] readContent(Resource resource, long declaredSize) {
        if (resource == null) {
            throw emptyFileException();
        }
        long maxBytes = textExtractProperties.maxSourceFileBytes();
        try (InputStream input = resource.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(initialBufferSize(declaredSize, maxBytes))) {
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw oversizedResumeException();
                }
                output.write(buffer, 0, read);
            }
            byte[] content = output.toByteArray();
            if (content.length == 0) {
                throw emptyFileException();
            }
            return content;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "简历文件读取失败，请重新上传后再试");
        }
    }

    private int initialBufferSize(long declaredSize, long maxBytes) {
        long expected = declaredSize > 0 ? Math.min(declaredSize, maxBytes) : Math.min(maxBytes, 8192L);
        return (int) Math.max(1024L, Math.min(expected, 8192L));
    }

    private void validateResumeFileSize(long declaredSize, int actualBytes) {
        long maxBytes = textExtractProperties.maxSourceFileBytes();
        if (declaredSize > maxBytes || actualBytes > maxBytes) {
            throw oversizedResumeException();
        }
    }

    private BusinessException oversizedResumeException() {
        return new BusinessException(ErrorCode.PARAM_ERROR,
                "简历文件过大，当前在线解析仅支持较小的 PDF/DOCX 文件");
    }

    private BusinessException emptyFileException() {
        return new BusinessException(ErrorCode.PARAM_ERROR, "文件内容为空，请更换文件后重试");
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
