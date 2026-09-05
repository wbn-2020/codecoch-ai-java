package com.codecoachai.question.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Question bank governance statistics")
public class QuestionStatisticsVO {

    @Schema(description = "Formal question count. Pending review drafts are not included.")
    private Long formalQuestionCount;

    @Schema(description = "Enabled formal questions available for training.")
    private Long trainableQuestionCount;

    @Schema(description = "Disabled formal questions unavailable for training.")
    private Long disabledQuestionCount;

    @Schema(description = "AI-generated question drafts waiting for review.")
    private Long pendingReviewCount;
}
