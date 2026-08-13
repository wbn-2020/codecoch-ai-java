package com.codecoachai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageDeadLetterMapper extends BaseMapper<MessageDeadLetter> {

    @Insert("""
            INSERT INTO message_dead_letter (
                message_id, biz_type, biz_id, user_id, trace_id, payload,
                last_failure_reason, total_retry, handle_status
            ) VALUES (
                #{messageId}, #{bizType}, #{bizId}, #{userId}, #{traceId}, #{payload},
                #{lastFailureReason}, #{totalRetry}, #{handleStatus}
            )
            ON DUPLICATE KEY UPDATE
                last_failure_reason = VALUES(last_failure_reason),
                total_retry = GREATEST(total_retry, VALUES(total_retry)),
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(MessageDeadLetter deadLetter);
}
