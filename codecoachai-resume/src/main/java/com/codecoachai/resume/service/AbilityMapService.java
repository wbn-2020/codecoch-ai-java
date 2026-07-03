package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.vo.AbilityMapVO;
import com.codecoachai.resume.domain.vo.InnerAbilityProfileSummaryVO;
import java.util.List;

public interface AbilityMapService {

    AbilityMapVO getCurrentUserAbilityMap();

    List<InnerAbilityProfileSummaryVO> listProfileSummary(Long userId, List<String> skillCodes);
}
