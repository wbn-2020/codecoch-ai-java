package com.codecoachai.resume.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.resume.domain.dto.ResumeJobMatchCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeJobMatchQueryDTO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportDetailVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportListVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;

public interface ResumeJobMatchService {

    ResumeJobMatchSubmitVO createReport(ResumeJobMatchCreateDTO dto);

    PageResult<ResumeJobMatchReportListVO> listReports(ResumeJobMatchQueryDTO query);

    ResumeJobMatchReportDetailVO getReport(Long id);

    ResumeJobMatchReportDetailVO getLatest(Long resumeId, Long targetJobId, Long resumeVersionId);

    ResumeJobMatchReportDetailVO getInnerSuccessReport(Long id);

    ResumeJobMatchReportAgentEvidenceVO getReportEvidence(Long userId, Long reportId);

    ResumeJobMatchSubmitVO regenerate(Long id);

    ResumeJobMatchSubmitVO executeReport(Long id);
}
