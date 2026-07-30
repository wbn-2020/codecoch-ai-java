package com.codecoachai.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.system.domain.entity.LoginLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    @Select("""
            SELECT DATE(login_time) AS activityDate,
                   COUNT(DISTINCT user_id) AS activityCount
              FROM login_log
             WHERE login_status = 'SUCCESS'
               AND login_time >= #{startTime}
               AND login_time < #{endTime}
             GROUP BY DATE(login_time)
             ORDER BY activityDate
            """)
    List<DailyActivityCount> selectDailyActiveUserCounts(@Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT DATE(login_time) AS activityDate,
                   COUNT(*) AS activityCount
              FROM login_log
             WHERE login_type = 'REGISTER'
               AND login_time >= #{startTime}
               AND login_time < #{endTime}
             GROUP BY DATE(login_time)
             ORDER BY activityDate
            """)
    List<DailyActivityCount> selectDailyRegistrationCounts(@Param("startTime") LocalDateTime startTime,
                                                            @Param("endTime") LocalDateTime endTime);

    @Data
    class DailyActivityCount {
        private LocalDate activityDate;
        private Long activityCount;
    }
}
