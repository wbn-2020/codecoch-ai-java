package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

/**
 * 批量忽略去重审核单。
 */
@Data
public class BatchQuestionDuplicateIgnoreDTO {

    /** 待忽略的审核单 id 列表。 */
    private List<Long> ids;

    /** 忽略原因（可选）。 */
    private String ignoredReason;
}
