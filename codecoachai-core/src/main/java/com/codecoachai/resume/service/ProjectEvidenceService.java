package com.codecoachai.resume.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.resume.domain.dto.ProjectEvidenceFromResumeProjectDTO;
import com.codecoachai.resume.domain.dto.ProjectEvidenceQueryDTO;
import com.codecoachai.resume.domain.dto.ProjectEvidenceSaveDTO;
import com.codecoachai.resume.domain.dto.ProjectSkillEvidenceSaveDTO;
import com.codecoachai.resume.domain.vo.InnerProjectEvidenceAgentContextVO;
import com.codecoachai.resume.domain.vo.InnerProjectEvidenceTrainingContextVO;
import com.codecoachai.resume.domain.vo.ProjectEvidenceDetailVO;
import com.codecoachai.resume.domain.vo.ProjectEvidenceListVO;
import com.codecoachai.resume.domain.vo.ProjectSkillEvidenceVO;
import java.util.List;

public interface ProjectEvidenceService {

    PageResult<ProjectEvidenceListVO> list(ProjectEvidenceQueryDTO query);

    ProjectEvidenceDetailVO create(ProjectEvidenceSaveDTO dto);

    ProjectEvidenceDetailVO importFromResumeProject(ProjectEvidenceFromResumeProjectDTO dto);

    ProjectEvidenceDetailVO detail(Long id);

    ProjectEvidenceDetailVO update(Long id, ProjectEvidenceSaveDTO dto);

    void delete(Long id);

    ProjectSkillEvidenceVO addSkillEvidence(Long projectEvidenceId, ProjectSkillEvidenceSaveDTO dto);

    ProjectSkillEvidenceVO updateSkillEvidence(Long projectEvidenceId, Long evidenceId, ProjectSkillEvidenceSaveDTO dto);

    void deleteSkillEvidence(Long projectEvidenceId, Long evidenceId);

    List<InnerProjectEvidenceAgentContextVO> listAgentContextForUser(Long userId);

    List<InnerProjectEvidenceTrainingContextVO> listTrainingContextForUser(Long userId, List<Long> ids);
}
