package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.NotificationFeignClient;
import com.codecoachai.resume.feign.dto.NotificationResolveByBizDTO;
import com.codecoachai.task.controller.InnerNotificationController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalNotificationFeignClient implements NotificationFeignClient {

    private final InnerNotificationController innerNotificationController;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<Integer> resolveByBiz(NotificationResolveByBizDTO dto) {
        return resultMapper.value(
                innerNotificationController.resolveByBiz(
                        resultMapper.convert(dto, InnerNotificationController.InnerNotificationResolveDTO.class)),
                Integer.class);
    }
}
