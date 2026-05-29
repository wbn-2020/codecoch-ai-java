package com.codecoachai.ai.agent.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeEvalRunRequestDTO {

    private List<Long> caseIds;
    private Boolean onlyEnabled = true;
    private Integer limit;
    private Double minScore;
}
