package com.codecoachai.interview.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "codecoachai-resume")
public interface ResumeFeignClient {

    @GetMapping("/inner/resumes/{id}")
    Result<InnerResumeDetailVO> getResume(@PathVariable("id") Long id);

    @GetMapping("/inner/resumes/default")
    Result<InnerResumeDetailVO> getDefaultResume();
}
