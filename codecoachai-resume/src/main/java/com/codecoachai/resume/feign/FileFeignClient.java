package com.codecoachai.resume.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.feign.multipart.StreamingMultipartFeignConfiguration;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
        name = "codecoachai-file",
        configuration = StreamingMultipartFeignConfiguration.class)
public interface FileFeignClient {

    @PostMapping(value = "/inner/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<InnerFileUploadVO> upload(@RequestPart("file") MultipartFile file,
                                     @RequestParam("bizType") String bizType,
                                     @RequestParam("userId") Long userId);

    @GetMapping("/inner/files/{id}/download")
    ResponseEntity<Resource> download(@PathVariable("id") Long id,
                                      @RequestParam("userId") Long userId,
                                      @RequestParam("bizType") String bizType);

    @DeleteMapping("/inner/files/{id}")
    Result<Void> delete(@PathVariable("id") Long id,
                        @RequestParam("userId") Long userId,
                        @RequestParam("bizType") String bizType);
}
