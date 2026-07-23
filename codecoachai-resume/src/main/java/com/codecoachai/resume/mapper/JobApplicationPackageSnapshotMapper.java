package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.JobApplicationPackageSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobApplicationPackageSnapshotMapper extends BaseMapper<JobApplicationPackageSnapshot> {

    @Select("""
            SELECT *
              FROM job_application_package_snapshot
             WHERE id = #{snapshotId}
               AND user_id = #{userId}
               AND deleted = 0
             LIMIT 1
            """)
    JobApplicationPackageSnapshot selectOwned(@Param("snapshotId") Long snapshotId,
                                              @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM job_application_package_snapshot
             WHERE package_id = #{packageId}
               AND user_id = #{userId}
               AND content_hash = #{contentHash}
               AND deleted = 0
             LIMIT 1
            """)
    JobApplicationPackageSnapshot selectByContentHash(@Param("packageId") Long packageId,
                                                      @Param("userId") Long userId,
                                                      @Param("contentHash") String contentHash);

    @Select("""
            SELECT *
              FROM job_application_package_snapshot
             WHERE package_id = #{packageId}
               AND user_id = #{userId}
               AND deleted = 0
             ORDER BY snapshot_version DESC, id DESC
             LIMIT 1 FOR UPDATE
            """)
    JobApplicationPackageSnapshot selectLatestForUpdate(@Param("packageId") Long packageId,
                                                        @Param("userId") Long userId);
}
