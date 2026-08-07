package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.ProjectJdCoverageRequestDTO;
import com.codecoachai.resume.domain.dto.ProjectStoryGenerateDTO;
import com.codecoachai.resume.domain.dto.ProjectStoryGenerationQueryDTO;
import com.codecoachai.resume.domain.vo.ProjectJdCoverageVO;
import com.codecoachai.resume.domain.vo.ProjectStoryGenerationVO;
import java.util.List;

public interface ProjectEvidenceMaterialService {

    ProjectStoryGenerationVO generate(Long projectEvidenceId, ProjectStoryGenerateDTO dto);

    List<ProjectStoryGenerationVO> listGenerations(Long projectEvidenceId, ProjectStoryGenerationQueryDTO query);

    ProjectStoryGenerationVO accept(Long projectEvidenceId, Long generationId);

    ProjectJdCoverageVO analyzeJdCoverage(Long projectEvidenceId, ProjectJdCoverageRequestDTO dto);
}
