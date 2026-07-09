package com.codecoachai.interview.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.vo.InnerFileInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "codecoachai-file")
public interface FileFeignClient {

    @GetMapping("/inner/files/{id}")
    Result<InnerFileInfoVO> detail(@PathVariable("id") Long id,
                                   @RequestParam("userId") Long userId,
                                   @RequestParam("bizType") String bizType);

    @GetMapping("/inner/files/{id}/download")
    ResponseEntity<Resource> download(@PathVariable("id") Long id,
                                      @RequestParam("userId") Long userId,
                                      @RequestParam("bizType") String bizType);
}
