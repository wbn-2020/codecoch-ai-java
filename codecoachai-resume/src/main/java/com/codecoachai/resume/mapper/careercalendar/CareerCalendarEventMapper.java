package com.codecoachai.resume.mapper.careercalendar;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerCalendarEventMapper extends BaseMapper<CareerCalendarEvent> {

    @Select("""
            SELECT *
            FROM career_calendar_event
            WHERE user_id = #{userId}
              AND BINARY external_uid = BINARY #{externalUid}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    CareerCalendarEvent selectActiveByExternalUidBinaryForUpdate(
            @Param("userId") Long userId,
            @Param("externalUid") String externalUid);
}
