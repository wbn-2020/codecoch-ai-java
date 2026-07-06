package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/files")
public class InnerFileController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<InnerFileUploadVO> upload(@RequestPart("file") MultipartFile file,
                                            @RequestParam("bizType") String bizType,
                                            @RequestParam("userId") Long userId) {
        return Result.success(fileStorageService.upload(file, bizType, userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id,
                                             @RequestParam("userId") Long userId,
                                             @RequestParam("bizType") String bizType) {
        return fileStorageService.download(id, userId, bizType);
    }
}
