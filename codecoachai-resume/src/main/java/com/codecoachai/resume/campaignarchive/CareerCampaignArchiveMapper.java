package com.codecoachai.resume.campaignarchive;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CareerCampaignArchiveMapper extends BaseMapper<CareerCampaignArchiveExport> {

    @Select("""
            SELECT *
              FROM career_campaign_archive_export
             WHERE id = #{exportId}
               AND user_id = #{userId}
               AND deleted = 0
            """)
    CareerCampaignArchiveExport selectOwned(@Param("userId") Long userId,
                                            @Param("exportId") Long exportId);

    @Select("""
            SELECT *
              FROM career_campaign_archive_export
             WHERE user_id = #{userId}
               AND campaign_id = #{campaignId}
               AND data_cutoff_at = #{dataCutoffAt}
               AND export_format = #{exportFormat}
               AND BINARY source_hash = BINARY #{sourceHash}
               AND deleted = 0
             ORDER BY id DESC
             LIMIT 1
            """)
    CareerCampaignArchiveExport selectBySource(@Param("userId") Long userId,
                                               @Param("campaignId") Long campaignId,
                                               @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
                                               @Param("exportFormat") String exportFormat,
                                               @Param("sourceHash") String sourceHash);

    @Select("""
            SELECT *
              FROM career_campaign_archive_export
             WHERE user_id = #{userId}
               AND BINARY idempotency_key_hash = BINARY #{idempotencyKeyHash}
               AND deleted = 0
             ORDER BY id DESC
             LIMIT 1
            """)
    CareerCampaignArchiveExport selectByIdempotency(@Param("userId") Long userId,
                                                    @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Update("""
            UPDATE career_campaign_archive_export
               SET status = 'GENERATING',
                   source_hash = #{sourceHash},
                   idempotency_key_hash = #{idempotencyKeyHash},
                   file_id = NULL,
                   file_size = NULL,
                   manifest_hash = NULL,
                   error_code = NULL,
                   error_message = NULL,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{exportId}
               AND user_id = #{userId}
               AND status = 'FAILED'
               AND deleted = 0
            """)
    int claimRetry(@Param("userId") Long userId,
                   @Param("exportId") Long exportId,
                   @Param("sourceHash") String sourceHash,
                   @Param("idempotencyKeyHash") String idempotencyKeyHash);

    @Select("""
            SELECT id, user_id, name, goal, status, started_at, completed_at, archived_at,
                   created_at, updated_at
              FROM career_campaign
             WHERE id = #{campaignId}
               AND user_id = #{userId}
               AND deleted = 0
               AND COALESCE(created_at, updated_at) <= #{dataCutoffAt}
               AND (updated_at IS NULL OR updated_at <= #{dataCutoffAt})
            """)
    CareerCampaignArchiveModels.CampaignRow selectCampaign(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt);

    @Select("""
            SELECT id, target_job_id, resume_version_id, match_report_id, company_name, job_title,
                   source, status, stage_changed_at, priority_level, opportunity_outcome,
                   applied_at, next_follow_up_at, created_at, updated_at
              FROM job_application
             WHERE user_id = #{userId}
               AND campaign_id = #{campaignId}
               AND deleted = 0
               AND COALESCE(created_at, applied_at, updated_at) <= #{dataCutoffAt}
               AND (updated_at IS NULL OR updated_at <= #{dataCutoffAt})
             ORDER BY COALESCE(applied_at, created_at, updated_at), id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.ApplicationRow> selectApplications(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT source_type, source_id, application_id, event_type, event_at, summary, sort_id
              FROM (
                    SELECT 'CAMPAIGN' AS source_type, e.id AS source_id, NULL AS application_id,
                           e.event_type AS event_type, e.occurred_at AS event_at,
                           e.summary AS summary, e.id AS sort_id
                      FROM career_campaign_event e
                     WHERE e.user_id = #{userId}
                       AND e.campaign_id = #{campaignId}
                       AND e.deleted = 0
                       AND e.occurred_at <= #{dataCutoffAt}
                    UNION ALL
                    SELECT 'APPLICATION' AS source_type, e.id AS source_id, e.application_id AS application_id,
                           e.event_type AS event_type, e.event_time AS event_at,
                           e.summary AS summary, e.id AS sort_id
                      FROM job_application_event e
                      JOIN job_application a ON a.id = e.application_id
                       AND a.user_id = #{userId}
                       AND a.campaign_id = #{campaignId}
                       AND a.deleted = 0
                     WHERE e.user_id = #{userId}
                       AND e.deleted = 0
                       AND e.event_time <= #{dataCutoffAt}
                       AND (e.created_at IS NULL OR e.created_at <= #{dataCutoffAt})
                   ) timeline
             ORDER BY event_at, sort_id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.TimelineRow> selectTimeline(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT e.id, e.application_id, e.title, e.event_type, e.starts_at_utc, e.ends_at_utc,
                   e.timezone, e.all_day_flag, e.location, e.status, e.source_type, e.source_ref
              FROM career_calendar_event e
              JOIN job_application a ON a.id = e.application_id
               AND a.user_id = #{userId}
               AND a.campaign_id = #{campaignId}
               AND a.deleted = 0
             WHERE e.user_id = #{userId}
               AND e.deleted = 0
               AND COALESCE(e.created_at, e.starts_at_utc, e.updated_at) <= #{dataCutoffAt}
               AND (e.updated_at IS NULL OR e.updated_at <= #{dataCutoffAt})
             ORDER BY e.starts_at_utc, e.id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.CalendarRow> selectCalendar(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT p.id AS process_id, r.id AS round_id, p.application_id, r.round_no, r.round_type,
                   r.title, r.timezone, r.scheduled_starts_at_utc, r.scheduled_ends_at_utc,
                   r.status, r.result_summary, r.next_step
              FROM career_interview_process p
              JOIN career_interview_round r ON r.process_id = p.id AND r.deleted = 0
              JOIN job_application a ON a.id = p.application_id
               AND a.user_id = #{userId}
               AND a.campaign_id = #{campaignId}
               AND a.deleted = 0
             WHERE p.user_id = #{userId}
               AND p.deleted = 0
               AND COALESCE(r.created_at, r.scheduled_starts_at_utc, r.updated_at) <= #{dataCutoffAt}
               AND (r.updated_at IS NULL OR r.updated_at <= #{dataCutoffAt})
             ORDER BY p.application_id, r.round_no, r.id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.InterviewRow> selectInterviews(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT o.id AS offer_id, o.application_id, o.status, o.decision_deadline, o.finalized_at,
                   v.version_no, v.currency, v.annual_base_salary, v.annual_bonus,
                   v.sign_on_bonus, v.annual_equity_value, v.other_annual_compensation,
                   v.paid_leave_days, v.location, v.work_mode, v.start_date
              FROM career_offer o
              LEFT JOIN career_offer_version v ON v.id = o.current_version_id
               AND v.user_id = #{userId}
               AND v.deleted = 0
              JOIN job_application a ON a.id = o.application_id
               AND a.user_id = #{userId}
               AND a.campaign_id = #{campaignId}
               AND a.deleted = 0
             WHERE o.user_id = #{userId}
               AND o.deleted = 0
               AND COALESCE(o.created_at, o.updated_at) <= #{dataCutoffAt}
               AND (o.updated_at IS NULL OR o.updated_at <= #{dataCutoffAt})
             ORDER BY o.application_id, o.id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.OfferRow> selectOffers(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT c.id AS contact_id, ca.application_id, c.display_name, c.role_type, c.channel_type,
                   c.masked_contact_hint, c.relationship_summary, ca.relationship_type
              FROM career_contact_application ca
              JOIN career_contact c ON c.id = ca.contact_id
               AND c.user_id = #{userId}
               AND c.deleted = 0
              JOIN job_application a ON a.id = ca.application_id
               AND a.user_id = #{userId}
               AND a.campaign_id = #{campaignId}
               AND a.deleted = 0
             WHERE ca.user_id = #{userId}
               AND ca.deleted = 0
               AND ca.created_at <= #{dataCutoffAt}
             ORDER BY ca.application_id, c.id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.ContactRow> selectContacts(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT a.id, a.application_id, a.contact_id, a.activity_type, a.channel_type,
                   a.subject, a.summary, a.occurred_at, a.next_follow_up_at, a.status
              FROM career_activity a
              JOIN job_application j ON j.id = a.application_id
               AND j.user_id = #{userId}
               AND j.campaign_id = #{campaignId}
               AND j.deleted = 0
             WHERE a.user_id = #{userId}
               AND a.deleted = 0
               AND COALESCE(a.created_at, a.occurred_at, a.updated_at) <= #{dataCutoffAt}
               AND (a.updated_at IS NULL OR a.updated_at <= #{dataCutoffAt})
             ORDER BY a.occurred_at, a.id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.ActivityRow> selectActivities(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT id, report_id, application_id, source_set_hash, confidence_level,
                   fallback, snapshot_json, created_at
              FROM career_research_snapshot
             WHERE user_id = #{userId}
               AND deleted = 0
               AND application_id IN (
                    SELECT id FROM job_application
                     WHERE user_id = #{userId}
                       AND campaign_id = #{campaignId}
                       AND deleted = 0
               )
               AND created_at <= #{dataCutoffAt}
               AND (updated_at IS NULL OR updated_at <= #{dataCutoffAt})
             ORDER BY application_id, created_at, id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.ResearchSnapshotRow> selectResearchSnapshots(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT u.id, u.application_id, u.target_job_id, u.asset_type, u.asset_id,
                   u.asset_version, u.package_snapshot_id, u.source_hash, u.content_hash,
                   u.usage_scene, u.used_at, u.hypothesis_id, u.variant_id, u.assignment_id,
                   u.created_at
              FROM career_evidence_usage u
             WHERE u.user_id = #{userId}
               AND u.campaign_id = #{campaignId}
               AND u.deleted = 0
               AND u.used_at <= #{dataCutoffAt}
               AND u.created_at <= #{dataCutoffAt}
             ORDER BY u.used_at, u.id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.EvidenceUsageRow> selectEvidenceUsages(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);

    @Select("""
            SELECT r.id, r.usage_id, r.application_id, r.event_type, r.event_id,
                   s.status AS status,
                   s.id AS snapshot_id, s.snapshot_version, s.outcome_code, s.known_facts_json,
                   s.external_feedback_text, s.user_interpretation_text, s.unknowns_json,
                   s.limits_json, s.source_type, s.source_id, s.source_version, s.source_hash,
                   s.occurred_at, s.confirmed_at, s.content_hash, s.supersedes_snapshot_id,
                   s.created_at AS snapshot_created_at, r.created_at
              FROM career_evidence_usage_result r
              JOIN career_evidence_usage u ON u.id = r.usage_id
               AND u.user_id = #{userId}
               AND u.campaign_id = #{campaignId}
               AND u.application_id = r.application_id
               AND u.deleted = 0
               AND u.used_at <= #{dataCutoffAt}
               AND u.created_at <= #{dataCutoffAt}
              JOIN career_evidence_usage_result_snapshot s
                ON s.id = (
                     SELECT s2.id
                       FROM career_evidence_usage_result_snapshot s2
                      WHERE s2.result_id = r.id
                        AND s2.user_id = #{userId}
                        AND s2.created_at <= #{dataCutoffAt}
                      ORDER BY s2.snapshot_version DESC, s2.id DESC
                      LIMIT 1
                   )
             WHERE r.user_id = #{userId}
               AND r.deleted = 0
               AND r.created_at <= #{dataCutoffAt}
             ORDER BY COALESCE(s.occurred_at, r.created_at), r.id
             LIMIT #{limit}
            """)
    List<CareerCampaignArchiveModels.EvidenceUsageResultRow> selectEvidenceUsageResults(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("dataCutoffAt") LocalDateTime dataCutoffAt,
            @Param("limit") int limit);
}
