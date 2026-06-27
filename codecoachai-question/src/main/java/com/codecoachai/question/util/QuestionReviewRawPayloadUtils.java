package com.codecoachai.question.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestionReviewRawPayloadUtils {

    private QuestionReviewRawPayloadUtils() {
    }

    public static Map<String, Object> buildMinimizedMetadata(Long aiCallLogId, String batchId, int questionCount) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storageMode", "MINIMIZED_METADATA");
        values.put("aiCallLogId", aiCallLogId);
        values.put("aiCallLogReferenceAvailable", aiCallLogId != null);
        values.put("batchId", batchId);
        values.put("questionCount", Math.max(questionCount, 0));
        values.put("questionReviewRawStored", false);
        return values;
    }
}
