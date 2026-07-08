package com.codecoachai.resume.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.resume.domain.dto.ApplicationPackageActionExecuteDTO;
import com.codecoachai.resume.domain.dto.ApplicationPackageCreateApplicationDTO;
import com.codecoachai.resume.domain.dto.ApplicationPackageSaveDTO;
import com.codecoachai.resume.domain.vo.ApplicationPackageActionExecuteVO;
import com.codecoachai.resume.domain.vo.JobApplicationPackageListItemVO;
import com.codecoachai.resume.domain.vo.JobApplicationPackageVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import java.util.List;

public interface JobApplicationPackageService {

    JobApplicationPackageVO preview(Long targetJobId, Long jdAnalysisId, Long resumeVersionId,
                                    Long matchReportId, List<Long> projectEvidenceIds);

    JobApplicationPackageVO save(ApplicationPackageSaveDTO dto);

    JobApplicationPackageVO detail(Long packageId);

    PageResult<JobApplicationPackageListItemVO> list(Long pageNo, Long pageSize, String status, String keyword);

    JobApplicationPackageVO refresh(Long packageId);

    ApplicationPackageActionExecuteVO executeAction(Long packageId, String actionCode,
                                                    ApplicationPackageActionExecuteDTO dto);

    JobApplicationVO createApplicationFromPreview(ApplicationPackageCreateApplicationDTO dto);
}
