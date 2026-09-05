package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_application_package_event")
public class JobApplicationPackageEvent extends BaseEntity {

    private Long userId;
    private Long packageId;
    private String eventType;
    private LocalDateTime eventTime;
    private String actionCode;
    private String relatedBizType;
    private Long relatedBizId;
    private String summary;
    private String eventPayloadJson;
    private String traceId;
    private String resultSource;
    private Integer fallback;
    private String fallbackReason;
    private Integer snapshotVersion;
}
