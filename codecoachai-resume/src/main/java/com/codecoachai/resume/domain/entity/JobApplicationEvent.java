package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_application_event")
public class JobApplicationEvent extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private String eventType;
    private LocalDateTime eventTime;
    private String summary;
    private String reviewJson;
}
