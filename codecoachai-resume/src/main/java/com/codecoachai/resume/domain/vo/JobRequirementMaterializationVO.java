package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class JobRequirementMaterializationVO {

    private Long targetJobId;
    private Long jdAnalysisId;
    private Integer requirementCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer deactivatedCount;
    private List<JobRequirementVO> requirements = new ArrayList<>();
}
