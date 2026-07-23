package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class SubmitInterviewAnswerVO {

    private Long interviewId;
    private Long questionId;
    private Long answerId;
    private Long evaluationMessageId;
    private Long followUpMessageId;
    private Long aiCallLogId;
    private Long followUpAiCallLogId;
    private Integer score;
    private String comment;
    private String nextAction;
    private String knowledgePoints;
    private String followUpQuestion;
    private String followUpReason;
    private Boolean followUpValid;
    private CurrentQuestionVO nextQuestion;
    private Long voiceSubmissionId;
    private Long transcriptId;
    private BigDecimal transcriptConfidence;
    private String answerSource;
    private Boolean voiceLowConfidence;
    private Boolean voiceFallback;
    private String voiceTraceId;
}
