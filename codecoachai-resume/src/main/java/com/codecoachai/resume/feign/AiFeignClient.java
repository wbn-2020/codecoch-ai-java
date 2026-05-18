package com.codecoachai.resume.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.dto.ParseResumeDTO;
import com.codecoachai.resume.feign.dto.ParseJobDescriptionDTO;
import com.codecoachai.resume.feign.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.resume.feign.vo.ParseJobDescriptionVO;
import com.codecoachai.resume.feign.vo.ParseResumeVO;
import com.codecoachai.resume.feign.vo.ResumeOptimizeAiResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-ai")
public interface AiFeignClient {

    @PostMapping("/inner/ai/resume/parse")
    Result<ParseResumeVO> parseResume(@RequestBody ParseResumeDTO dto);

    @PostMapping("/inner/ai/resume/optimize")
    Result<ResumeOptimizeAiResponseVO> optimizeResume(@RequestBody ResumeOptimizeAiRequestDTO dto);

    @PostMapping("/inner/ai/job-descriptions/parse")
    Result<ParseJobDescriptionVO> parseJobDescription(@RequestBody ParseJobDescriptionDTO dto);
}
