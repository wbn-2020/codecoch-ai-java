package com.codecoachai.resume.feign;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.dto.NotificationResolveByBizDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "codecoachai-task")
public interface NotificationFeignClient {

    @PostMapping("/inner/notifications/resolve-by-biz")
    Result<Integer> resolveByBiz(@RequestBody NotificationResolveByBizDTO dto);
}
