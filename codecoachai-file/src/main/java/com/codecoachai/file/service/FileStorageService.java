package com.codecoachai.file.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.file.domain.dto.AdminFileQueryDTO;
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.domain.vo.FileInfoVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    InnerFileUploadVO upload(MultipartFile file, String bizType, Long userId);

    ResponseEntity<byte[]> download(Long fileId, Long userId, String bizType);

    String downloadUrl(Long fileId, Long userId, String bizType);

    FileInfoVO getUserFile(Long fileId, Long userId);

    ResponseEntity<Resource> adminDownload(Long fileId);

    PageResult<FileInfoVO> pageAdminFiles(AdminFileQueryDTO query);

    FileInfoVO getAdminFile(Long fileId);
}
