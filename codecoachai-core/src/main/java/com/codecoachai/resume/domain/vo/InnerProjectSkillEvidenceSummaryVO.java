package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class InnerProjectSkillEvidenceSummaryVO {

    private Long id;
    private String skillName;
    private String skillCategory;
    private String strengthLevel;
    private String jdKeyword;
    private String evidenceSummary;
    private String riskPoints;
}
