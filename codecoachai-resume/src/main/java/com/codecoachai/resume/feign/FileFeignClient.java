package com.codecoachai.resume.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "codecoachai-file")
public interface FileFeignClient {

    @PostMapping(value = "/inner/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<InnerFileUploadVO> upload(@RequestPart("file") MultipartFile file,
                                     @RequestParam("bizType") String bizType,
                                     @RequestParam("userId") Long userId);
}
