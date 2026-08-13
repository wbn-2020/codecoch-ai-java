package com.codecoachai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.task.domain.entity.Notification;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    Page<Notification> selectUserNotificationPage(Page<Notification> page,
                                                  @Param("userId") Long userId,
                                                  @Param("readStatus") Integer readStatus,
                                                  @Param("type") String type);

    Long countUnreadForUser(@Param("userId") Long userId);

    int insertMissingBroadcastReads(@Param("userId") Long userId);

    int upsertBroadcastRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    int insertDailyReminderIfAbsent(@Param("userId") Long userId,
                                    @Param("type") String type,
                                    @Param("title") String title,
                                    @Param("content") String content,
                                    @Param("bizType") String bizType,
                                    @Param("bizId") String bizId,
                                    @Param("reminderDate") LocalDate reminderDate,
                                    @Param("sentAt") LocalDateTime sentAt);
}
