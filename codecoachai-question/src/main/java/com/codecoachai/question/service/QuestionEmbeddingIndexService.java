package com.codecoachai.question.service;

import java.util.List;
import java.util.Map;

public interface QuestionEmbeddingIndexService {

    String QUESTION_COLLECTION = "question_embedding";

    Map<String, Object> rebuild(Integer limit);

    Map<String, Object> stats();

    Map<String, Object> retryFailed(Integer limit);

    void indexQuestion(Long questionId);

    void indexQuestions(List<Long> questionIds);

    void deleteQuestion(Long questionId);

    List<Map<String, Object>> searchSimilar(Long questionId, Integer limit);

    List<SemanticHit> searchSimilarIndexed(Long questionId, Integer limit, Double scoreThreshold);

    record SemanticHit(Long questionId, double score) {
    }
}
