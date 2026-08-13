package com.codecoachai.resume.experimentv2.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_experiment_assignment")
public class ExperimentAssignment extends BaseEntity {
    private Long userId;
    private Long hypothesisId;
    private Long variantId;
    private Long applicationId;
    private String assignmentKey;
    private String assignmentMethod;
    private LocalDateTime assignedAt;
    private String jobFamily;
    private String channel;
    private LocalDate timeBucket;
}
