package com.codecoachai.resume.service;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.resume.domain.dto.JobReadinessQueryDTO;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import java.util.List;

public interface JobReadinessService {

    JobReadinessSnapshotVO createSnapshot(Long targetJobId);

    JobReadinessSnapshotVO latest(Long targetJobId);

    JobReadinessSnapshotVO latestForUser(Long userId, Long targetJobId);

    JobReadinessSnapshotVO getSnapshot(Long targetJobId, Long snapshotId);

    PageResult<JobReadinessSnapshotVO> page(Long targetJobId, Long pageNo, Long pageSize);

    List<JobReadinessSnapshotVO> list(Long targetJobId, JobReadinessQueryDTO query);
}
