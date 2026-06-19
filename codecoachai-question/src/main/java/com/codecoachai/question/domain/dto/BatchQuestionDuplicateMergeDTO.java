package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

/**
 * 批量确认（merge）去重审核单。
 */
@Data
public class BatchQuestionDuplicateMergeDTO {

    /** 待确认的审核单 id 列表。 */
    private List<Long> ids;

    /** 关系类型，默认 SAME_INTENT。 */
    private String relationType;

    /** 确认原因（可选）。 */
    private String reason;
    private Boolean confirm;
    private Boolean dryRun;
    private String idempotencyKey;
}
