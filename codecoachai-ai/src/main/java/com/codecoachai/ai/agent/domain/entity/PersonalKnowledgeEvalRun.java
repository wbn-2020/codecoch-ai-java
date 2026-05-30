package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("personal_knowledge_eval_run")
public class PersonalKnowledgeEvalRun extends BaseEntity {

    private Long userId;
    private String runNo;
    private String status;
    private Integer sampleCount;
    private Integer evaluatedCount;
    private Integer passedCount;
    private Integer failedCount;
    private Double passRate;
    private Integer limitCount;
    private Double minScore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
}
