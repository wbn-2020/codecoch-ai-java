package com.codecoachai.question.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestionRecommendationResultPayloadUtils {

    private QuestionRecommendationResultPayloadUtils() {
    }

    public static Map<String, Object> buildMinimizedMetadata(Long aiCallLogId, Long batchId, int questionCount) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storageMode", "MINIMIZED_METADATA");
        values.put("aiCallLogId", aiCallLogId);
        values.put("aiCallLogReferenceAvailable", aiCallLogId != null);
        values.put("batchId", batchId);
        values.put("questionCount", Math.max(questionCount, 0));
        values.put("questionRecommendationRawStored", false);
        return values;
    }
}
