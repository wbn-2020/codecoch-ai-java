package com.codecoachai.interview.scenario;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_scenario_binding")
public class InterviewScenarioBinding extends BaseEntity {

    private Long userId;
    private Long sessionId;
    private Long scenarioVersionId;
    private Long rubricVersionId;
    private String bindingSource;
}
