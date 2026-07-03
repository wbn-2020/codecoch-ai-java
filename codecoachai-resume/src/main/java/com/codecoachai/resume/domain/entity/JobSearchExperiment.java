package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_search_experiment")
public class JobSearchExperiment extends BaseEntity {

    private Long userId;
    private String title;
    private String goal;
    private String targetDirection;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer sampleCount;
    private String confidenceLevel;
    private String sampleWarning;
    private String summary;
    private String nextStrategy;
    private Integer demoFlag;
}
