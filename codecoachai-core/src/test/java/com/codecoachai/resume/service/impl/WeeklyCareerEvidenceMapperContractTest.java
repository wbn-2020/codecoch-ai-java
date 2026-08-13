package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentRelationMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WeeklyCareerEvidenceMapperContractTest {

    @Test
    void applicationQueryUsesOwnerCutoffTargetAndUtcHalfOpenWindow() throws Exception {
        String sql = sql(
                JobApplicationMapper.class,
                "selectWeeklyEvidenceApplications",
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Long.class,
                Integer.class);

        assertTrue(sql.contains("user_id = #{userid}"), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains(
                "coalesce(applied_at, created_at, updated_at) >= #{rangestartutc}"), sql);
        assertTrue(sql.contains(
                "coalesce(applied_at, created_at, updated_at) < #{rangeendutc}"), sql);
        assertTrue(sql.contains(
                "coalesce(created_at, applied_at, updated_at) <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains(
                "(updated_at is null or updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains("target_job_id = #{targetjobid}"), sql);
        assertTrue(sql.contains("limit #{limit}"), sql);
    }

    @Test
    void ownedApplicationLookupRejectsRowsUpdatedAfterTheCutoff() throws Exception {
        String sql = sql(
                JobApplicationMapper.class,
                "selectWeeklyEvidenceOwnedApplications",
                Long.class,
                List.class,
                LocalDateTime.class);

        assertTrue(sql.contains("user_id = #{userid}"), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains(
                "coalesce(created_at, applied_at, updated_at) <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains(
                "(updated_at is null or updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains("id in"), sql);
    }

    @Test
    void applicationEventQueryJoinsAnOwnedApplicationAndUsesEventTimeWindow() throws Exception {
        String sql = sql(
                JobApplicationEventMapper.class,
                "selectWeeklyEvidenceEvents",
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Long.class,
                Integer.class);

        assertTrue(sql.contains("from job_application_event app_event"), sql);
        assertTrue(sql.contains("join job_application application"), sql);
        assertTrue(sql.contains("application.user_id = #{userid}"), sql);
        assertTrue(sql.contains("application.deleted = 0"), sql);
        assertTrue(sql.contains("app_event.user_id = #{userid}"), sql);
        assertTrue(sql.contains("app_event.deleted = 0"), sql);
        assertTrue(sql.contains("app_event.event_time >= #{rangestartutc}"), sql);
        assertTrue(sql.contains("app_event.event_time < #{rangeendutc}"), sql);
        assertTrue(sql.contains("application.target_job_id = #{targetjobid}"), sql);
        assertTrue(sql.contains(
                "coalesce(application.created_at, application.applied_at, application.updated_at)"
                        + " <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains(
                "(application.updated_at is null or application.updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains(
                "coalesce(app_event.created_at, app_event.event_time, app_event.updated_at)"
                        + " <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains(
                "(app_event.updated_at is null or app_event.updated_at <= #{sourcecutoffat})"), sql);
    }

    @Test
    void calendarQueryKeepsUnlinkedAllScopeButRequiresOwnedApplicationForTargetScope()
            throws Exception {
        String sql = sql(
                CareerCalendarEventMapper.class,
                "selectWeeklyEvidenceEvents",
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Long.class,
                Integer.class);

        assertTrue(sql.contains("calendar.user_id = #{userid}"), sql);
        assertTrue(sql.contains("calendar.deleted = 0"), sql);
        assertTrue(sql.contains("calendar.starts_at_utc >= #{rangestartutc}"), sql);
        assertTrue(sql.contains("calendar.starts_at_utc < #{rangeendutc}"), sql);
        assertTrue(sql.contains(
                "(calendar.application_id is null or application.id is not null)"), sql);
        assertTrue(sql.contains("application.target_job_id = #{targetjobid}"), sql);
        assertTrue(sql.contains(
                "coalesce(application.created_at, application.applied_at, application.updated_at)"
                        + " <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains(
                "(application.updated_at is null or application.updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains(
                "coalesce(calendar.created_at, calendar.starts_at_utc, calendar.updated_at)"
                        + " <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains(
                "(calendar.updated_at is null or calendar.updated_at <= #{sourcecutoffat})"), sql);
    }

    @Test
    void experimentQueryUsesOwnerDateOverlapOptionalIdsAndTargetRelationScope()
            throws Exception {
        String sql = sql(
                JobSearchExperimentMapper.class,
                "selectWeeklyEvidenceExperiments",
                Long.class,
                LocalDate.class,
                LocalDate.class,
                LocalDateTime.class,
                Long.class,
                List.class,
                Integer.class);

        assertTrue(sql.contains("experiment.user_id = #{userid}"), sql);
        assertTrue(sql.contains("experiment.deleted = 0"), sql);
        assertTrue(sql.contains("experiment.demo_flag = 0"), sql);
        assertTrue(sql.contains("experiment.start_date < #{rangeenddateexclusive}"), sql);
        assertTrue(sql.contains("experiment.end_date >= #{rangestartdate}"), sql);
        assertTrue(sql.contains("experiment.id in"), sql);
        assertTrue(sql.contains("relation_item.user_id = #{userid}"), sql);
        assertTrue(sql.contains("relation_item.deleted = 0"), sql);
        assertTrue(sql.contains("relation_item.relation_id = #{targetjobid}"), sql);
        assertTrue(sql.contains("application.target_job_id = #{targetjobid}"), sql);
        assertTrue(sql.contains("analysis.target_job_id = #{targetjobid}"), sql);
        assertTrue(sql.contains(
                "(experiment.updated_at is null or experiment.updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains(
                "(application.updated_at is null or application.updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains(
                "(analysis.updated_at is null or analysis.updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains(
                "(relation_item.updated_at is null or relation_item.updated_at <= #{sourcecutoffat})"), sql);
    }

    @Test
    void relationQueryIsOwnerScopedCutoffBoundedAndBatchLimited() throws Exception {
        String sql = sql(
                JobSearchExperimentRelationMapper.class,
                "selectWeeklyEvidenceRelations",
                Long.class,
                List.class,
                LocalDateTime.class,
                Integer.class);

        assertTrue(sql.contains("user_id = #{userid}"), sql);
        assertTrue(sql.contains("deleted = 0"), sql);
        assertTrue(sql.contains("demo_flag = 0"), sql);
        assertTrue(sql.contains("experiment_id in"), sql);
        assertTrue(sql.contains("<= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains(
                "(updated_at is null or updated_at <= #{sourcecutoffat})"), sql);
        assertTrue(sql.contains("limit #{limit}"), sql);
    }

    private static String sql(
            Class<?> mapperType,
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        Select select = mapperType
                .getMethod(methodName, parameterTypes)
                .getAnnotation(Select.class);
        return String.join(" ", select.value())
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
