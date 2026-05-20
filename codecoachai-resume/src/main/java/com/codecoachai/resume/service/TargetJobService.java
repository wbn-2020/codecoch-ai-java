package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.JobDescriptionParseDTO;
import com.codecoachai.resume.domain.dto.TargetJobQueryDTO;
import com.codecoachai.resume.domain.dto.TargetJobSaveDTO;
import com.codecoachai.resume.domain.vo.JobDescriptionAnalysisVO;
import com.codecoachai.resume.domain.vo.TargetJobVO;
import java.util.List;

public interface TargetJobService {

    List<TargetJobVO> listTargetJobs(TargetJobQueryDTO query);

    TargetJobVO createTargetJob(TargetJobSaveDTO dto);

    TargetJobVO getTargetJob(Long id);

    TargetJobVO updateTargetJob(Long id, TargetJobSaveDTO dto);

    void deleteTargetJob(Long id);

    TargetJobVO setCurrent(Long id);

    TargetJobVO getCurrent();

    JobDescriptionAnalysisVO parseJobDescription(Long id, JobDescriptionParseDTO dto);

    JobDescriptionAnalysisVO getAnalysis(Long id);
}
