package com.codecoachai.interview.audioretention;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_audio_cleanup_record")
public class InterviewAudioCleanupRecord extends BaseEntity {

    private Long retentionRecordId;
    private String cleanupTaskId;
    private Integer attemptNo;
    private String cleanupStatus;
    private String providerCode;
    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorCode;
    private String errorMessage;
}
