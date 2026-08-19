package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AbilitySkillNodeVO {

    private Long id;
    private String code;
    private String name;
    private String domainCode;
    private String domainName;
    private String description;
    private Integer sortOrder;
    private String status;
    private Integer evidenceCount;
    private LocalDateTime lastEvaluatedAt;
    private String confidence;
    private String summary;
    private List<String> evidenceSources;
    private List<String> sourceLabels;
    private String syncStatus;
    private String syncMessage;
    private LocalDateTime updatedAt;
}
