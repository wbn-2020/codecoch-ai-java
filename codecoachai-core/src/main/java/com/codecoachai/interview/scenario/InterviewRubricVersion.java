package com.codecoachai.interview.scenario;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_rubric_version")
public class InterviewRubricVersion extends BaseEntity {

    private String rubricCode;
    private Integer versionNo;
    private String rubricName;
    private String description;
    private String locale;
    private String dimensionsJson;
    private String versionStatus;
    private Long createdBy;
    private LocalDateTime publishedAt;
}
