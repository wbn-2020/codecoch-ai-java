package com.codecoachai.resume.mapper;

import com.codecoachai.resume.domain.vo.JobRequirementEvidenceSourceRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobRequirementEvidenceSourceMapper {

    @Select("""
            SELECT 'RESUME_MATCH' AS evidence_type,
                   r.id AS evidence_id,
                   d.id AS evidence_sub_id,
                   COALESCE(NULLIF(d.skill_name, ''), CONCAT('Resume match #', r.id)) AS title,
                   COALESCE(NULLIF(d.evidence, ''), NULLIF(d.gap_description, ''), r.summary) AS excerpt,
                   UPPER(TRIM(r.status)) AS result_source,
                   COALESCE(d.score, r.overall_score) AS score,
                   CASE WHEN UPPER(TRIM(r.status)) = 'SUCCESS' THEN 1 ELSE 0 END AS confirmed,
                   CASE WHEN UPPER(TRIM(r.status)) = 'SUCCESS' THEN 0 ELSE 1 END AS fallback,
                   COALESCE(r.updated_at, r.created_at) AS occurred_at
              FROM resume_job_match_report r
              JOIN resume_job_match_detail d
                ON d.report_id = r.id
               AND d.user_id = r.user_id
               AND d.deleted = 0
             WHERE r.user_id = #{userId}
               AND r.target_job_id = #{targetJobId}
               AND r.deleted = 0
             ORDER BY r.updated_at DESC, r.id DESC, d.id DESC
            """)
    List<JobRequirementEvidenceSourceRow> selectResumeMatchEvidence(
            @Param("userId") Long userId, @Param("targetJobId") Long targetJobId);

    @Select("""
            SELECT 'INTERVIEW_REPORT' AS evidence_type,
                   r.id AS evidence_id,
                   s.id AS evidence_sub_id,
                   COALESCE(NULLIF(s.title, ''), NULLIF(s.target_position, ''),
                            CONCAT('Interview report #', r.id)) AS title,
                   COALESCE(NULLIF(r.summary, ''), NULLIF(r.strengths, ''),
                            NULLIF(r.weak_points, ''), NULLIF(r.report_content, '')) AS excerpt,
                   UPPER(TRIM(r.status)) AS result_source,
                   r.total_score AS score,
                   CASE WHEN UPPER(TRIM(r.status)) = 'GENERATED'
                              AND r.total_score > 0
                              AND r.generated_at IS NOT NULL
                              AND NULLIF(TRIM(r.summary), '') IS NOT NULL
                              AND NULLIF(TRIM(r.report_content), '') IS NOT NULL
                              AND NULLIF(TRIM(r.failure_reason), '') IS NULL
                              AND LOWER(CONCAT_WS(' ', r.qa_review, r.rubric_scores,
                                      r.advice_evidence, r.ability_profile_updates))
                                  NOT LIKE '%"fallback":true%'
                              AND LOWER(CONCAT_WS(' ', r.rubric_scores,
                                      r.advice_evidence, r.ability_profile_updates))
                                  NOT LIKE '%"sampleinsufficient":true%'
                         THEN 1 ELSE 0 END AS confirmed,
                   CASE WHEN UPPER(TRIM(r.status)) = 'GENERATED'
                              AND r.total_score > 0
                              AND r.generated_at IS NOT NULL
                              AND NULLIF(TRIM(r.summary), '') IS NOT NULL
                              AND NULLIF(TRIM(r.report_content), '') IS NOT NULL
                              AND NULLIF(TRIM(r.failure_reason), '') IS NULL
                              AND LOWER(CONCAT_WS(' ', r.qa_review, r.rubric_scores,
                                      r.advice_evidence, r.ability_profile_updates))
                                  NOT LIKE '%"fallback":true%'
                              AND LOWER(CONCAT_WS(' ', r.rubric_scores,
                                      r.advice_evidence, r.ability_profile_updates))
                                  NOT LIKE '%"sampleinsufficient":true%'
                         THEN 0 ELSE 1 END AS fallback,
                   COALESCE(r.generated_at, r.updated_at, r.created_at) AS occurred_at
              FROM interview_report r
              JOIN interview_session s
                ON s.id = r.session_id
               AND s.user_id = r.user_id
               AND s.target_job_id = #{targetJobId}
               AND s.deleted = 0
             WHERE r.user_id = #{userId}
               AND r.deleted = 0
             ORDER BY COALESCE(r.generated_at, r.updated_at) DESC, r.id DESC
            """)
    List<JobRequirementEvidenceSourceRow> selectInterviewReportEvidence(
            @Param("userId") Long userId, @Param("targetJobId") Long targetJobId);

    @Select("""
            SELECT 'APPLICATION_RESULT' AS evidence_type,
                   a.id AS evidence_id,
                   NULL AS evidence_sub_id,
                   CONCAT(COALESCE(NULLIF(a.company_name, ''), 'Application'),
                          ' - ', a.job_title) AS title,
                   CONCAT('Application status: ', UPPER(TRIM(a.status)),
                          CASE WHEN NULLIF(TRIM(a.note), '') IS NULL
                               THEN '' ELSE CONCAT('. ', a.note) END) AS excerpt,
                   UPPER(TRIM(a.status)) AS result_source,
                   CASE UPPER(TRIM(a.status))
                       WHEN 'OFFER' THEN 100
                       WHEN 'INTERVIEWING' THEN 75
                       WHEN 'APPLIED' THEN 50
                       WHEN 'REJECTED' THEN 30
                       WHEN 'CLOSED' THEN 20
                       ELSE 10
                   END AS score,
                   CASE WHEN UPPER(TRIM(a.status)) IN
                             ('APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'CLOSED')
                        THEN 1 ELSE 0 END AS confirmed,
                   0 AS fallback,
                   COALESCE(a.applied_at, a.updated_at, a.created_at) AS occurred_at
              FROM job_application a
             WHERE a.user_id = #{userId}
               AND a.target_job_id = #{targetJobId}
               AND a.deleted = 0
             ORDER BY COALESCE(a.applied_at, a.updated_at) DESC, a.id DESC
            """)
    List<JobRequirementEvidenceSourceRow> selectApplicationResultEvidence(
            @Param("userId") Long userId, @Param("targetJobId") Long targetJobId);
}
