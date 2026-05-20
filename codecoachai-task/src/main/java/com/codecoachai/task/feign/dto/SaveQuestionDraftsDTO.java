package com.codecoachai.task.feign.dto;

import com.codecoachai.task.feign.vo.GenerateQuestionDraftVO;
import java.util.List;
import lombok.Data;

@Data
public class SaveQuestionDraftsDTO {

    private String batchId;
    private Long createdBy;
    private Long aiCallLogId;
    private String targetPosition;
    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private String difficulty;
    private Integer experienceYears;
    private String rawAiResultJson;
    private List<GenerateQuestionDraftVO.QuestionDraftItem> questions;
}
