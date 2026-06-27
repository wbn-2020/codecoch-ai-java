package com.codecoachai.ai.agent.domain.vo.growth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SkillGrowthSnapshotVO {
    private Long id;
    private LocalDate snapshotDate;
    private String skillCode;
    private String skillName;
    private Integer score;
    private Integer taskCount;
    private Integer doneCount;
    private String confidenceLevel;
    private Integer evidenceCount;
    private String timeWindow;
    private List<String> dataSourceLabels = new ArrayList<>();
    private String coldStartReason;
    private List<String> nextEvidenceActions = new ArrayList<>();
}
