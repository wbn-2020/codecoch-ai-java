package com.codecoachai.file.service;

import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    InnerFileUploadVO upload(MultipartFile file, String bizType, Long userId);
}
