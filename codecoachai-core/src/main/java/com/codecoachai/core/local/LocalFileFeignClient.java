package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.file.controller.InnerFileController;
import com.codecoachai.file.service.FileStorageService;
import com.codecoachai.file.util.FileBizTypes;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class LocalFileFeignClient implements com.codecoachai.resume.feign.FileFeignClient,
        com.codecoachai.interview.feign.FileFeignClient {

    private final FileStorageService fileStorageService;
    private final InnerFileController innerFileController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<InnerFileUploadVO> upload(MultipartFile file, String bizType, Long userId) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(file, "file");
            resultMapper.requireParameter(bizType, "bizType");
            resultMapper.requireParameter(userId, "userId");
            return resultMapper.value(
                    Result.success(fileStorageService.upload(
                            file,
                            FileBizTypes.requireAllowed(bizType),
                            userId)),
                    InnerFileUploadVO.class);
        });
    }

    @Override
    public ResponseEntity<Resource> download(Long id, Long userId, String bizType) {
        resultMapper.requireParameter(id, "id");
        resultMapper.requireParameter(userId, "userId");
        resultMapper.requireParameter(bizType, "bizType");
        return fileStorageService.download(id, userId, FileBizTypes.requireAllowed(bizType));
    }

    @Override
    public Result<Void> delete(Long id, Long userId, String bizType) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            resultMapper.requireParameter(userId, "userId");
            resultMapper.requireParameter(bizType, "bizType");
            fileStorageService.deleteUserFile(id, userId, FileBizTypes.requireAllowed(bizType));
            return Result.success();
        });
    }

    @Override
    public Result<com.codecoachai.interview.feign.vo.InnerFileInfoVO> detail(
            Long id, Long userId, String bizType) {
        return resultMapper.invoke(() -> {
            resultMapper.requireParameter(id, "id");
            resultMapper.requireParameter(userId, "userId");
            resultMapper.requireParameter(bizType, "bizType");
            return resultMapper.value(
                    innerFileController.detail(id, userId, bizType),
                    com.codecoachai.interview.feign.vo.InnerFileInfoVO.class);
        });
    }
}
