package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.vo.JobRequirementMaterializationVO;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.domain.vo.JobRequirementVO;
import java.util.List;

public interface JobRequirementService {

    JobRequirementMaterializationVO materialize(Long targetJobId);

    List<JobRequirementVO> list(Long targetJobId);

    JobRequirementMatrixVO refreshMatrix(Long targetJobId);

    JobRequirementMatrixVO getMatrix(Long targetJobId);

    JobRequirementMatrixVO getMatrixForUser(Long userId, Long targetJobId);
}
