package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.dto.JobApplicationSaveDTO;
import com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeApplyAiSuggestionDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCopyDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCreateDTO;
import com.codecoachai.resume.domain.vo.JobApplicationEventVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionAdoptionVO;
import com.codecoachai.resume.domain.vo.ResumeVersionDiffVO;
import com.codecoachai.resume.domain.vo.ResumeVersionVO;
import java.util.List;

public interface V4ResumeCareerService {
    ResumeVersionVO createVersion(Long resumeId, ResumeVersionCreateDTO dto);
    ResumeVersionVO copyVersion(Long resumeId, Long versionId, ResumeVersionCopyDTO dto);
    List<ResumeVersionVO> listVersions(Long resumeId);
    ResumeVersionVO getVersion(Long versionId);
    ResumeVersionDiffVO diffVersion(Long resumeId, Long versionId);
    ResumeVersionDiffVO diffVersions(Long sourceVersionId, Long targetVersionId);
    ResumeVersionVO rollbackVersion(Long resumeId, Long versionId);
    ResumeSuggestionAdoptionVO applyAiSuggestion(Long versionId, ResumeApplyAiSuggestionDTO dto);
    List<JobApplicationVO> listApplications(String status);
    JobApplicationVO createApplication(JobApplicationSaveDTO dto);
    JobApplicationVO updateApplication(Long id, JobApplicationSaveDTO dto);
    List<JobApplicationEventVO> listApplicationEvents(Long applicationId);
    JobApplicationEventVO createApplicationEvent(Long applicationId, JobApplicationEventSaveDTO dto);
}
