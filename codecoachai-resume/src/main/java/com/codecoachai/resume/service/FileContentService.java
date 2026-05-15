package com.codecoachai.resume.service;

import com.codecoachai.resume.feign.vo.InnerFileDownloadVO;

public interface FileContentService {

    InnerFileDownloadVO downloadResumeFile(Long fileId, Long userId);
}
