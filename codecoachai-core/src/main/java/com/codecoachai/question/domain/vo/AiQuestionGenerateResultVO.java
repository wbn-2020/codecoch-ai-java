package com.codecoachai.question.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class AiQuestionGenerateResultVO {

    private String batchId;
    private Integer generatedCount;
    private List<Long> reviewIds;
    private Long aiCallLogId;
    private String asyncMessageId;
    private String asyncTraceId;
    private String asyncBizType;
    private String asyncBizId;
    private String asyncSendStatus;
}
