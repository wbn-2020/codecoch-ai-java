package com.codecoachai.question.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionDraftVO {

    private String batchId;
    private Long aiCallLogId;
    private List<QuestionDraftItemVO> questions;
    private String rawResponse;
}
