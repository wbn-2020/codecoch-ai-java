package com.codecoachai.question.feign.dto;

import lombok.Data;

@Data
public class AgentBusinessActionCompleteDTO {

    private Long userId;

    private String taskType;

    private String relatedBizType;

    private Long relatedBizId;

    private String evidenceBizType;

    private Long evidenceBizId;

    private String note;
}
