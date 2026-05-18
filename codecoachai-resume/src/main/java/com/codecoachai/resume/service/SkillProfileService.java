package com.codecoachai.resume.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.resume.domain.dto.SkillProfileGenerateDTO;
import com.codecoachai.resume.domain.dto.SkillProfileQueryDTO;
import com.codecoachai.resume.domain.dto.SkillProfileRefreshDTO;
import com.codecoachai.resume.domain.vo.SkillProfileDetailVO;
import com.codecoachai.resume.domain.vo.SkillProfileGenerateVO;
import com.codecoachai.resume.domain.vo.SkillProfileListVO;
import com.codecoachai.resume.domain.vo.SkillProfileOverviewVO;

public interface SkillProfileService {

    SkillProfileGenerateVO generate(SkillProfileGenerateDTO dto);

    SkillProfileDetailVO getByTargetJob(Long targetJobId);

    SkillProfileOverviewVO getOverview(Long targetJobId);

    PageResult<SkillProfileListVO> listProfiles(SkillProfileQueryDTO query);

    SkillProfileGenerateVO refresh(SkillProfileRefreshDTO dto);
}
