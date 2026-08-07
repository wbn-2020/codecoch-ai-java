package com.codecoachai.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResultSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerEvidenceUsageResultSnapshotMapper
        extends BaseMapper<CareerEvidenceUsageResultSnapshot> {

    @Select("""
            SELECT *
              FROM career_evidence_usage_result_snapshot
             WHERE result_id = #{resultId}
               AND user_id = #{userId}
               AND idempotency_key_hash = #{idempotencyKeyHash}
             LIMIT 1
            """)
    CareerEvidenceUsageResultSnapshot selectByIdempotencyKey(@Param("resultId") Long resultId,
                                                             @Param("userId") Long userId,
                                                             @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT *
              FROM career_evidence_usage_result_snapshot
             WHERE result_id = #{resultId}
               AND user_id = #{userId}
             ORDER BY snapshot_version DESC, id DESC
             LIMIT 1 FOR UPDATE
            """)
    CareerEvidenceUsageResultSnapshot selectLatestForUpdate(@Param("resultId") Long resultId,
                                                             @Param("userId") Long userId);

    @Select("""
            SELECT *
              FROM career_evidence_usage_result_snapshot
             WHERE result_id = #{resultId}
               AND user_id = #{userId}
               AND created_at <= #{dataCutoffAt}
             ORDER BY snapshot_version DESC, id DESC
             LIMIT 1
            """)
    CareerEvidenceUsageResultSnapshot selectLatestAtCutoff(
            @Param("resultId") Long resultId,
            @Param("userId") Long userId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt);

    @Select("""
            SELECT *
              FROM career_evidence_usage_result_snapshot
             WHERE result_id = #{resultId}
               AND user_id = #{userId}
             ORDER BY snapshot_version ASC, id ASC
            """)
    List<CareerEvidenceUsageResultSnapshot> selectByResult(@Param("resultId") Long resultId,
                                                           @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT r.id
              FROM career_evidence_usage_result r
              JOIN career_evidence_usage_result_snapshot s
                ON s.id =
                <choose>
                  <when test="dataCutoffAt != null">
                    (
                      SELECT s2.id
                        FROM career_evidence_usage_result_snapshot s2
                       WHERE s2.result_id = r.id
                         AND s2.user_id = r.user_id
                         AND s2.created_at &lt;= #{dataCutoffAt}
                       ORDER BY s2.snapshot_version DESC, s2.id DESC
                       LIMIT 1
                    )
                  </when>
                  <otherwise>
                    r.current_snapshot_id
                  </otherwise>
                </choose>
             WHERE r.user_id = #{userId}
               AND r.deleted = 0
               <if test="dataCutoffAt != null">
                 AND r.created_at &lt;= #{dataCutoffAt}
               </if>
                <if test="outcomeCode != null and outcomeCode != ''">
                  AND s.outcome_code = #{outcomeCode}
                </if>
                <if test="status != null and status != ''">
                  AND s.status = #{status}
                </if>
             ORDER BY r.id
            </script>
            """)
    List<Long> selectResultIdsByOutcome(
            @Param("userId") Long userId,
            @Param("outcomeCode") String outcomeCode,
            @Param("status") String status,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt);

    @Select("""
            SELECT *
              FROM career_evidence_usage_result_snapshot
             WHERE id = #{snapshotId}
               AND result_id = #{resultId}
               AND user_id = #{userId}
             LIMIT 1
            """)
    CareerEvidenceUsageResultSnapshot selectOwned(@Param("snapshotId") Long snapshotId,
                                                   @Param("resultId") Long resultId,
                                                   @Param("userId") Long userId);
}
