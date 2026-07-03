package com.codecoachai.resume.service.impl;

import com.codecoachai.resume.feign.NotificationFeignClient;
import com.codecoachai.resume.feign.dto.NotificationResolveByBizDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationBusinessResolver {

    private final NotificationFeignClient notificationFeignClient;

    public void resolveApplicationFollowUp(Long userId, Long applicationId, String reason) {
        if (userId == null || applicationId == null) {
            return;
        }
        NotificationResolveByBizDTO dto = new NotificationResolveByBizDTO();
        dto.setUserId(userId);
        dto.setType("APPLICATION_FOLLOW_UP_REMINDER");
        dto.setBizType("JOB_APPLICATION");
        dto.setBizId(String.valueOf(applicationId));
        dto.setReason(reason);
        try {
            notificationFeignClient.resolveByBiz(dto);
        } catch (RuntimeException ignored) {
            // Notification denoising is best-effort and must not block application events.
        }
    }
}
