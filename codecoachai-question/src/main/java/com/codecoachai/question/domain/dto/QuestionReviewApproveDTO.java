package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionReviewApproveDTO {

    private String title;
    private String content;
    private String referenceAnswer;
    private String analysis;
    private String difficulty;
    private String questionType;
    private Long categoryId;
    private Long groupId;
    private List<Long> tagIds;
    private Integer status;
    private Integer isHighFrequency;
    private String experienceLevel;
    private String editedReason;
}
