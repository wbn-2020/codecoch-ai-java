package com.codecoachai.task.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionDraftVO {
    private List<QuestionDraftItem> drafts;

    @Data
    public static class QuestionDraftItem {
        private String title;
        private String content;
        private String referenceAnswer;
        private String analysis;
        private String difficulty;
    }
}
