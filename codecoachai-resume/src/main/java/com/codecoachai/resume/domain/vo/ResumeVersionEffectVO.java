package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ResumeVersionEffectVO {
    private Long versionUsedCount = 0L;
    private Long currentVersionApplicationCount = 0L;
    private Long applicationsWithoutVersionCount = 0L;
    private List<ResumeVersionEffectItemVO> versions = new ArrayList<>();
}
