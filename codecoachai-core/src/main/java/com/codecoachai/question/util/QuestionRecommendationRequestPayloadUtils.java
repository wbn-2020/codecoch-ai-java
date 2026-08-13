package com.codecoachai.question.util;

import com.codecoachai.question.domain.entity.QuestionRecommendationBatch;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestionRecommendationRequestPayloadUtils {

    public static final String MINIMIZED_METADATA_STORAGE_MODE = "MINIMIZED_METADATA";

    private QuestionRecommendationRequestPayloadUtils() {
    }

    public static Map<String, Object> buildMinimizedMetadata(QuestionRecommendationBatch batch) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storageMode", MINIMIZED_METADATA_STORAGE_MODE);
        values.put("batchId", batch == null ? null : batch.getId());
        values.put("sourceType", batch == null ? null : batch.getSourceType());
        values.put("sourceId", batch == null ? null : batch.getSourceId());
        values.put("questionCount", batch == null || batch.getQuestionCount() == null
                ? 0
                : Math.max(batch.getQuestionCount(), 0));
        values.put("matchReportId", batch == null ? null : batch.getMatchReportId());
        values.put("skillProfileId", batch == null ? null : batch.getSkillProfileId());
        values.put("studyPlanId", batch == null ? null : batch.getStudyPlanId());
        values.put("strategy", batch == null ? null : batch.getStrategy());
        values.put("questionRecommendationRequestStored", false);
        return values;
    }

    public static Map<String, Object> withStoredRequestMarker(Map<String, Object> values) {
        Map<String, Object> marked = new LinkedHashMap<>();
        if (values != null && !values.isEmpty()) {
            marked.putAll(values);
        }
        marked.put("storageMode", MINIMIZED_METADATA_STORAGE_MODE);
        marked.put("questionRecommendationRequestStored", true);
        return marked;
    }

    public static Map<String, Object> withStoredRequestMarker(Map<String, Object> values,
                                                              Long batchId,
                                                              Long userId) {
        Map<String, Object> marked = withStoredRequestMarker(values);
        marked.put("batchId", batchId);
        marked.put("userId", userId);
        return marked;
    }

    public static Map<String, Object> withStoredRequestMarker(Map<String, Object> values,
                                                              QuestionRecommendationBatch batch) {
        return withStoredRequestMarker(
                values,
                batch == null ? null : batch.getId(),
                batch == null ? null : batch.getUserId());
    }
}
