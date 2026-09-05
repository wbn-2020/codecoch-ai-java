package com.codecoachai.resume.careerinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerInterviewProcessMapper extends BaseMapper<CareerInterviewProcess> {

    @Select("""
            SELECT process.*
              FROM career_interview_process process
              JOIN job_application application ON application.id = process.application_id
             WHERE process.id = #{processId}
               AND process.user_id = #{userId}
               AND application.user_id = #{userId}
               AND application.deleted = 0
               AND process.deleted = 0
            """)
    CareerInterviewProcess selectOwned(@Param("processId") Long processId, @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM career_interview_process
             WHERE application_id = #{applicationId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
               AND deleted = 0
             LIMIT 1
            """)
    CareerInterviewProcess selectActiveByApplication(@Param("applicationId") Long applicationId,
                                                     @Param("userId") Long userId);

    @Update("""
            UPDATE career_interview_process
               SET current_round_no = current_round_no + 1,
                   lock_version = lock_version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{processId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
               AND deleted = 0
               AND lock_version = #{expectedLockVersion}
            """)
    int claimNextRound(@Param("processId") Long processId,
                       @Param("userId") Long userId,
                       @Param("expectedLockVersion") Integer expectedLockVersion);
}
