package com.codecoachai.task.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionDraftDTO {
    private String topic;
    private String difficulty;
    private Integer count;
    private List<String> tags;
    private String targetPosition;
    private String experienceLevel;
    private Long batchId;
}
