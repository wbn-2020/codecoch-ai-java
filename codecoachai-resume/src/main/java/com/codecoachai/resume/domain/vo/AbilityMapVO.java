package com.codecoachai.resume.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class AbilityMapVO {

    private Long userId;
    private Integer totalSkillCount;
    private Integer assessedSkillCount;
    private Integer weakSkillCount;
    private Integer strongSkillCount;
    private Boolean hasTrainingData;
    private List<AbilityDomainVO> domains;
}
