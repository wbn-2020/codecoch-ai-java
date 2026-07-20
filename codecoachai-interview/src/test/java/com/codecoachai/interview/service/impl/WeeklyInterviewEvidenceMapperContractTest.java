package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WeeklyInterviewEvidenceMapperContractTest {

    @Test
    void sessionQueryUsesOwnerDeletionTargetHalfOpenWindowAndCutoff() throws Exception {
        String sql = sql(
                InterviewSessionMapper.class,
                "selectWeeklyEvidenceSessions",
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Long.class,
                Integer.class);

        assertTrue(sql.contains("session_item.user_id = #{userid}"), sql);
        assertTrue(sql.contains("session_item.deleted = 0"), sql);
        assertTrue(sql.contains("session_item.end_time >= #{rangestartutc}"), sql);
        assertTrue(sql.contains("session_item.end_time < #{rangeendutc}"), sql);
        assertTrue(sql.contains("session_item.end_time <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains("session_item.created_at <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains("session_item.updated_at <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains("session_item.target_job_id = #{targetjobid}"), sql);
        assertTrue(sql.contains("limit #{limit}"), sql);
    }

    @Test
    void linkedSessionQueryKeepsOwnerDeletionTargetAndCutoffScope() throws Exception {
        String sql = sql(
                InterviewSessionMapper.class,
                "selectWeeklyEvidenceSessionsByIds",
                Long.class,
                List.class,
                LocalDateTime.class,
                Long.class);

        assertTrue(sql.contains("session_item.user_id = #{userid}"), sql);
        assertTrue(sql.contains("session_item.deleted = 0"), sql);
        assertTrue(sql.contains("session_item.created_at <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains("session_item.updated_at <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains("session_item.target_job_id = #{targetjobid}"), sql);
        assertTrue(sql.contains("session_item.id in"), sql);
    }

    @Test
    void reportQueryScopesBothReportAndSessionAndUsesEitherBusinessTime() throws Exception {
        String sql = sql(
                InterviewReportMapper.class,
                "selectWeeklyEvidenceReports",
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Long.class,
                Integer.class);

        assertTrue(sql.contains("from interview_report report_item"), sql);
        assertTrue(sql.contains("join interview_session session_item"), sql);
        assertTrue(sql.contains("report_item.user_id = #{userid}"), sql);
        assertTrue(sql.contains("report_item.deleted = 0"), sql);
        assertTrue(sql.contains("session_item.user_id = #{userid}"), sql);
        assertTrue(sql.contains("session_item.deleted = 0"), sql);
        assertTrue(sql.contains("session_item.end_time >= #{rangestartutc}"), sql);
        assertTrue(sql.contains("session_item.end_time < #{rangeendutc}"), sql);
        assertTrue(sql.contains(
                "coalesce(report_item.generated_at, report_item.created_at) >= #{rangestartutc}"),
                sql);
        assertTrue(sql.contains(
                "coalesce(report_item.generated_at, report_item.created_at) < #{rangeendutc}"),
                sql);
        assertTrue(sql.contains("report_item.created_at <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains("report_item.updated_at <= #{sourcecutoffat}"), sql);
        assertTrue(sql.contains("session_item.target_job_id = #{targetjobid}"), sql);
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
