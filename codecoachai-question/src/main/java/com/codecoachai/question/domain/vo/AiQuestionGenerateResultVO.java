package com.codecoachai.question.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class AiQuestionGenerateResultVO {

    private String batchId;
    private Integer generatedCount;
    private List<Long> reviewIds;
    private Long aiCallLogId;
}
