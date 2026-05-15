package com.codecoachai.ai.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionDraftVO {

    private String batchId;
    private Long aiCallLogId;
    private List<QuestionDraftItemVO> questions;
    private String rawResponse;
}
