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

    TargetJobVO getTargetJobForUser(Long id, Long userId);

    TargetJobVO updateTargetJob(Long id, TargetJobSaveDTO dto);

    void deleteTargetJob(Long id);

    TargetJobVO setCurrent(Long id);

    TargetJobVO getCurrent();

    TargetJobVO getCurrentForUser(Long userId);

    JobDescriptionAnalysisVO parseJobDescription(Long id, JobDescriptionParseDTO dto);

    JobDescriptionAnalysisVO submitJobDescriptionParse(Long id, JobDescriptionParseDTO dto);

    JobDescriptionAnalysisVO parseJobDescriptionForUser(Long id, Long userId, JobDescriptionParseDTO dto);

    JobDescriptionAnalysisVO executeJobDescriptionParseForUser(Long id, Long userId, JobDescriptionParseDTO dto);

    JobDescriptionAnalysisVO getAnalysis(Long id);

    JobDescriptionAnalysisVO getAnalysisForUser(Long id, Long userId);
}
