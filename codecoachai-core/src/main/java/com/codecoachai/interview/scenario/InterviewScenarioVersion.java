package com.codecoachai.interview.scenario;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_scenario_version")
public class InterviewScenarioVersion extends BaseEntity {

    private String scenarioCode;
    private Integer versionNo;
    private String scenarioName;
    private String description;
    private String locale;
    private String scriptJson;
    private Long rubricVersionId;
    private String versionStatus;
    private Long createdBy;
    private LocalDateTime publishedAt;
}
