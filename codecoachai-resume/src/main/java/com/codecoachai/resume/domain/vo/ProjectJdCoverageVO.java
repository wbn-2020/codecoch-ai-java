package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ProjectJdCoverageVO {

    private Long projectEvidenceId;
    private Long targetJobId;
    private Integer coverageScore;
    private List<String> jdSkills = new ArrayList<>();
    private List<String> coveredSkills = new ArrayList<>();
    private List<String> weakCoveredSkills = new ArrayList<>();
    private List<String> missingSkills = new ArrayList<>();
    private List<String> expressionSuggestions = new ArrayList<>();
}
