package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.NotificationFeignClient;
import com.codecoachai.resume.feign.dto.NotificationResolveByBizDTO;
import com.codecoachai.task.service.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalNotificationFeignClient implements NotificationFeignClient {

    private final NotificationCommandService notificationCommandService;
    private final LocalResultMapper resultMapper;

    @Override
    public Result<Integer> resolveByBiz(NotificationResolveByBizDTO dto) {
        return resultMapper.invoke(() -> {
            NotificationResolveByBizDTO request =
                    resultMapper.convertRequiredBody(dto, NotificationResolveByBizDTO.class);
            return resultMapper.value(
                    Result.success(notificationCommandService.resolveByBiz(
                            request.getUserId(),
                            request.getType(),
                            request.getBizType(),
                            request.getBizId(),
                            request.getReason())),
                    Integer.class);
        });
    }
}
