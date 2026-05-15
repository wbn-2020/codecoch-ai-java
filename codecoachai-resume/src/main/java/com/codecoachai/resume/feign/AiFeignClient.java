package com.codecoachai.resume.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.dto.ParseResumeDTO;
import com.codecoachai.resume.feign.vo.ParseResumeVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai")
public interface AiFeignClient {

    @PostMapping("/inner/ai/resume/parse")
    Result<ParseResumeVO> parseResume(@RequestBody ParseResumeDTO dto);
}
