package com.codecoachai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.task.domain.entity.AsyncTask;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTask> {

    @Update("""
            UPDATE async_task
               SET status = 'RUNNING',
                   lease_token = #{newLeaseToken},
                   failure_reason = NULL,
                   result = NULL,
                   started_at = #{leaseStartedAt},
                   completed_at = NULL,
                   max_retry = GREATEST(max_retry, #{maxRetry}),
                   updated_at = #{now}
             WHERE id = #{id}
               AND deleted = 0
               AND status = #{expectedStatus}
               AND lease_token <=> #{expectedLeaseToken}
            """)
    int claimReadyTask(@Param("id") Long id,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("expectedLeaseToken") String expectedLeaseToken,
                       @Param("newLeaseToken") String newLeaseToken,
                       @Param("leaseStartedAt") LocalDateTime leaseStartedAt,
                       @Param("maxRetry") int maxRetry,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE async_task
               SET status = 'RUNNING',
                   lease_token = #{newLeaseToken},
                   failure_reason = NULL,
                   result = NULL,
                   started_at = #{leaseStartedAt},
                   completed_at = NULL,
                   max_retry = GREATEST(max_retry, #{maxRetry}),
                   updated_at = #{now}
             WHERE id = #{id}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{expectedLeaseToken}
               AND (started_at IS NULL OR started_at <= #{expiresBefore})
            """)
    int stealRunningTask(@Param("id") Long id,
                         @Param("expectedLeaseToken") String expectedLeaseToken,
                         @Param("newLeaseToken") String newLeaseToken,
                         @Param("leaseStartedAt") LocalDateTime leaseStartedAt,
                         @Param("expiresBefore") LocalDateTime expiresBefore,
                         @Param("maxRetry") int maxRetry,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(1)
              FROM async_task
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{leaseToken}
            """)
    int verifyLeaseOwner(@Param("messageId") String messageId,
                         @Param("leaseToken") String leaseToken);

    @Update("""
            UPDATE async_task
               SET started_at = #{now},
                   updated_at = #{now}
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{leaseToken}
            """)
    int renewLease(@Param("messageId") String messageId,
                   @Param("leaseToken") String leaseToken,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE async_task
               SET status = 'SUCCESS',
                   lease_token = NULL,
                   result = #{result},
                   failure_reason = NULL,
                   completed_at = #{completedAt},
                   updated_at = #{completedAt}
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{leaseToken}
            """)
    int markSuccess(@Param("messageId") String messageId,
                    @Param("leaseToken") String leaseToken,
                    @Param("result") String result,
                    @Param("completedAt") LocalDateTime completedAt);

    @Update("""
            UPDATE async_task
               SET status = 'FAILED',
                   lease_token = NULL,
                   failure_reason = #{reason},
                   completed_at = #{completedAt},
                   updated_at = #{completedAt}
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{leaseToken}
            """)
    int markTerminalFailed(@Param("messageId") String messageId,
                           @Param("leaseToken") String leaseToken,
                           @Param("reason") String reason,
                           @Param("completedAt") LocalDateTime completedAt);

    @Update("""
            UPDATE async_task
               SET status = 'PENDING',
                   lease_token = NULL,
                   retry_count = retry_count + 1,
                   failure_reason = #{reason},
                   started_at = NULL,
                   completed_at = NULL,
                   updated_at = #{failedAt}
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{leaseToken}
               AND retry_count < max_retry
            """)
    int markRetryableFailed(@Param("messageId") String messageId,
                            @Param("leaseToken") String leaseToken,
                            @Param("reason") String reason,
                            @Param("failedAt") LocalDateTime failedAt);

    @Update("""
            UPDATE async_task
               SET status = 'DEAD',
                   lease_token = NULL,
                   retry_count = retry_count + 1,
                   failure_reason = #{reason},
                   completed_at = #{failedAt},
                   updated_at = #{failedAt}
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{leaseToken}
               AND retry_count >= max_retry
            """)
    int markRetryExhaustedDead(@Param("messageId") String messageId,
                               @Param("leaseToken") String leaseToken,
                               @Param("reason") String reason,
                               @Param("failedAt") LocalDateTime failedAt);

    @Update("""
            UPDATE async_task
               SET status = 'DEAD',
                   lease_token = NULL,
                   failure_reason = #{reason},
                   completed_at = #{failedAt},
                   updated_at = #{failedAt}
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'RUNNING'
               AND lease_token = #{leaseToken}
            """)
    int markDead(@Param("messageId") String messageId,
                 @Param("leaseToken") String leaseToken,
                 @Param("reason") String reason,
                 @Param("failedAt") LocalDateTime failedAt);

    @Update("""
            UPDATE async_task
               SET status = 'PENDING',
                   lease_token = NULL,
                   retry_count = 0,
                   failure_reason = NULL,
                   result = NULL,
                   started_at = NULL,
                   completed_at = NULL,
                   updated_at = #{now}
             WHERE id = #{taskId}
               AND deleted = 0
               AND status IN ('FAILED', 'DEAD', 'ERROR', 'DEAD_LETTER')
               AND lease_token <=> #{expectedLeaseToken}
            """)
    int prepareManualRetry(@Param("taskId") Long taskId,
                           @Param("expectedLeaseToken") String expectedLeaseToken,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE async_task
               SET status = 'FAILED',
                   lease_token = NULL,
                   failure_reason = #{reason},
                   completed_at = #{failedAt},
                   updated_at = #{failedAt}
             WHERE id = #{taskId}
               AND deleted = 0
               AND status = 'PENDING'
               AND lease_token <=> #{expectedLeaseToken}
            """)
    int markManualRetryDispatchFailed(@Param("taskId") Long taskId,
                                      @Param("expectedLeaseToken") String expectedLeaseToken,
                                      @Param("reason") String reason,
                                      @Param("failedAt") LocalDateTime failedAt);
}
