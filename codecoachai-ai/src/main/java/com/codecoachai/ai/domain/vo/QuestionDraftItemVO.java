package com.codecoachai.ai.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDraftItemVO {

    private String title;
    private String content;
    private String referenceAnswer;
    private String analysis;
    private List<String> followUpQuestions;
    private List<String> tagSuggestions;
    private String categorySuggestion;
    private String difficulty;
    private String questionType;
    private String groupSuggestion;
}
