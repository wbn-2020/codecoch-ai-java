package com.codecoachai.resume.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class AbilityDomainVO {

    private String domainCode;
    private String domainName;
    private Integer totalCount;
    private Integer assessedCount;
    private Integer weakCount;
    private List<AbilitySkillNodeVO> skills;
}
