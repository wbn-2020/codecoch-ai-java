package com.codecoachai.question.service;

import java.util.List;
import java.util.Map;

public interface QuestionEmbeddingIndexService {

    Map<String, Object> rebuild(Integer limit);

    void indexQuestion(Long questionId);

    void deleteQuestion(Long questionId);

    List<Map<String, Object>> searchSimilar(Long questionId, Integer limit);
}
