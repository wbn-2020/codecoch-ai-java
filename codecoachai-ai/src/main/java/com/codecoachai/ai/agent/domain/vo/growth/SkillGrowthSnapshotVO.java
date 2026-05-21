package com.codecoachai.ai.agent.domain.vo.growth;

import java.time.LocalDate;
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
}
