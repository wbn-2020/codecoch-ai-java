package com.codecoachai.resume.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.resume.domain.dto.JobSearchExperimentQueryDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentRelationSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentReviewSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentSaveDTO;
import com.codecoachai.resume.domain.vo.JobExperimentAgentContextVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentDetailVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentListVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentMetricsVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentRelationVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentReviewVO;

import java.util.List;

public interface JobSearchExperimentService {

    PageResult<JobSearchExperimentListVO> list(JobSearchExperimentQueryDTO query);

    JobSearchExperimentDetailVO create(JobSearchExperimentSaveDTO dto);

    JobSearchExperimentDetailVO detail(Long id);

    JobSearchExperimentDetailVO update(Long id, JobSearchExperimentSaveDTO dto);

    void delete(Long id);

    JobSearchExperimentRelationVO addRelation(Long experimentId, JobSearchExperimentRelationSaveDTO dto);

    void deleteRelation(Long experimentId, Long relationId);

    JobSearchExperimentMetricsVO metrics(Long id);

    JobSearchExperimentReviewVO createReview(Long experimentId, JobSearchExperimentReviewSaveDTO dto);

    JobSearchExperimentReviewVO generateReview(Long experimentId);

    List<JobSearchExperimentReviewVO> listReviews(Long experimentId);

    List<JobExperimentAgentContextVO> listAgentContextForUser(Long userId, Long targetJobId);
}
