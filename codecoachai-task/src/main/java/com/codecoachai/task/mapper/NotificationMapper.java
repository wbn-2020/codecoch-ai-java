package com.codecoachai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.task.domain.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
