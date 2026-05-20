package com.codecoachai.task.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionDraftVO {
    private String batchId;
    private Long aiCallLogId;
    private List<QuestionDraftItem> questions;
    private String rawResponse;

    @Data
    public static class QuestionDraftItem {
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
}
