package com.codecoachai.question.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 批量去重操作结果。单条失败不影响其他条目。
 */
@Data
public class BatchQuestionDuplicateResultVO {

    private int requestedCount;
    private int successCount;
    private int failureCount;
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private Long id;
        private String reason;

        public Failure() {
        }

        public Failure(Long id, String reason) {
            this.id = id;
            this.reason = reason;
        }
    }
}
