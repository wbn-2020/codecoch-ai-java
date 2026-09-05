package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.vo.JobRequirementMaterializationVO;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.domain.vo.JobRequirementVO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import java.util.List;

public interface JobRequirementService {

    JobRequirementMaterializationVO materialize(Long targetJobId);

    JobRequirementMaterializationVO materializeForUser(
            Long targetJobId, Long userId, JobDescriptionAnalysis analysis);

    List<JobRequirementVO> list(Long targetJobId);

    JobRequirementMatrixVO refreshMatrix(Long targetJobId);

    JobRequirementMatrixVO getMatrix(Long targetJobId);

    JobRequirementMatrixVO getMatrixForUser(Long userId, Long targetJobId);
}
