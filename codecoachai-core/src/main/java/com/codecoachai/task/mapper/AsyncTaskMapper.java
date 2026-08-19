package com.codecoachai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.task.domain.entity.AsyncTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTask> {

    List<String> ADMIN_FAILURE_STATUSES = List.of("FAILED", "DEAD", "ERROR", "DEAD_LETTER");
    String ADMIN_FAILURE_STATUS_FILTER = "FAILED,DEAD,ERROR,DEAD_LETTER";
    String ADMIN_STATS_TIMEZONE = "Asia/Shanghai";

    @Select("""
            <script>
            SELECT COUNT(1)
              FROM async_task
             WHERE deleted = 0
            <if test="statuses != null and statuses.size() &gt; 0">
               AND status IN
               <foreach collection="statuses" item="status" open="(" separator="," close=")">
                   #{status}
               </foreach>
            </if>
            <if test="createdFrom != null">
               AND created_at &gt;= #{createdFrom}
            </if>
            <if test="createdBefore != null">
               AND created_at &lt; #{createdBefore}
            </if>
            </script>
            """)
    long countAdminTasks(@Param("statuses") List<String> statuses,
                         @Param("createdFrom") LocalDateTime createdFrom,
                         @Param("createdBefore") LocalDateTime createdBefore);

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
                   governance_status = 'RESOLVED',
                   governance_reason = 'Task completed successfully',
                   governance_updated_at = #{completedAt},
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
                   governance_status = 'MANUAL_ACTION_REQUIRED',
                   governance_reason = 'Terminal task failure requires manual review',
                   governance_updated_at = #{completedAt},
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
                   governance_status = 'UNASSESSED',
                   governance_reason = 'Retryable failure awaiting reassessment',
                   governance_updated_at = #{failedAt},
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
                   governance_status = 'MANUAL_ACTION_REQUIRED',
                   governance_reason = 'Retry budget exhausted; manual action required',
                   governance_updated_at = #{failedAt},
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
                   governance_status = 'MANUAL_ACTION_REQUIRED',
                   governance_reason = 'Task moved to dead-letter handling',
                   governance_updated_at = #{failedAt},
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
                   governance_status = 'RETRYING',
                   governance_reason = 'Manual retry dispatch is in progress',
                   governance_updated_at = #{now},
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
               SET execution_id = COALESCE(NULLIF(execution_id, ''), #{parentExecutionId}),
                   retry_count = retry_count + 1,
                   governance_status = 'RETRYING',
                   governance_reason = CONCAT(
                       'Manual retry dispatched as child execution ',
                       #{childExecutionId},
                       ' attempt ',
                       #{childAttemptNo}),
                   governance_updated_at = #{now},
                   retry_preview_hash = NULL,
                   updated_at = #{now}
             WHERE id = #{taskId}
               AND message_id = #{expectedMessageId}
               AND deleted = 0
               AND status IN ('FAILED', 'DEAD', 'ERROR', 'DEAD_LETTER')
               AND lease_token IS NULL
               AND governance_status = 'RETRY_APPROVED'
               AND retry_preview_hash = #{retryPreviewHash}
               AND updated_at <=> #{expectedUpdatedAt}
            """)
    int markManualRetryParentDispatched(@Param("taskId") Long taskId,
                                        @Param("expectedMessageId") String expectedMessageId,
                                        @Param("retryPreviewHash") String retryPreviewHash,
                                        @Param("parentExecutionId") String parentExecutionId,
                                        @Param("childExecutionId") String childExecutionId,
                                        @Param("childAttemptNo") int childAttemptNo,
                                        @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
                                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE async_task
               SET governance_status = 'MANUAL_ACTION_REQUIRED',
                   governance_reason = CONCAT(
                       'Manual retry child execution ',
                       #{childExecutionId},
                       ' could not be dispatched'),
                   governance_updated_at = #{failedAt},
                   updated_at = #{failedAt}
             WHERE id = #{taskId}
               AND deleted = 0
               AND governance_status = 'RETRYING'
            """)
    int markManualRetryParentDispatchFailed(@Param("taskId") Long taskId,
                                            @Param("childExecutionId") String childExecutionId,
                                            @Param("failedAt") LocalDateTime failedAt);

    @Update("""
            UPDATE async_task
               SET status = 'FAILED',
                   lease_token = NULL,
                   failure_reason = #{reason},
                   governance_status = 'MANUAL_ACTION_REQUIRED',
                   governance_reason = 'Manual retry dispatch failed; investigate before retrying again',
                   governance_updated_at = #{failedAt},
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

    @Update("""
            UPDATE async_task
               SET status = #{status},
                   lease_token = NULL,
                   failure_reason = #{failureReason},
                   result = #{result},
                   governance_status = CASE WHEN #{status} = 'SUCCESS'
                       THEN 'RESOLVED' ELSE 'MANUAL_ACTION_REQUIRED' END,
                   governance_reason = CASE WHEN #{status} = 'SUCCESS'
                       THEN 'Pending task completed successfully'
                       ELSE 'Pending task failed and requires manual review' END,
                   governance_updated_at = #{completedAt},
                   completed_at = #{completedAt},
                   updated_at = #{completedAt}
             WHERE message_id = #{messageId}
               AND deleted = 0
               AND status = 'PENDING'
               AND lease_token IS NULL
            """)
    int completePendingTask(@Param("messageId") String messageId,
                            @Param("status") String status,
                            @Param("failureReason") String failureReason,
                            @Param("result") String result,
                            @Param("completedAt") LocalDateTime completedAt);

    @Update("""
            UPDATE async_task
               SET governance_status = #{governanceStatus},
                   governance_reason = #{governanceReason},
                   governance_owner = #{governanceOwner},
                   governance_updated_at = #{governanceUpdatedAt},
                   retry_preview_hash = #{previewHash},
                   updated_at = #{governanceUpdatedAt}
             WHERE id = #{taskId}
               AND deleted = 0
               AND updated_at <=> #{expectedUpdatedAt}
            """)
    int updateGovernance(@Param("taskId") Long taskId,
                         @Param("governanceStatus") String governanceStatus,
                         @Param("governanceReason") String governanceReason,
                         @Param("governanceOwner") String governanceOwner,
                         @Param("previewHash") String previewHash,
                         @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
                         @Param("governanceUpdatedAt") LocalDateTime governanceUpdatedAt);
}
