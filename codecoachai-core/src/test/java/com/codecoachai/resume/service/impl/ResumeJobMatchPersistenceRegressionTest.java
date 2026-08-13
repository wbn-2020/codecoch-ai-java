package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeJobMatchDetail;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.vo.AnalyzeResumeJobMatchVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchDetailMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.support.ResumeJobMatchTrustPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ResumeJobMatchPersistenceRegressionTest {

    private static final long REPORT_ID = 9_701_410L;
    private static final long USER_ID = 10L;
    private static final long RESUME_ID = 11L;
    private static final long TARGET_JOB_ID = 12L;
    private static final long JD_ANALYSIS_ID = 13L;
    private static final long AI_CALL_LOG_ID = 14L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 10, 0);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper projectMapper;
    @Mock
    private ResumeAnalysisRecordMapper analysisRecordMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private ResumeJobMatchReportMapper reportMapper;
    @Mock
    private ResumeJobMatchDetailMapper detailMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ResumeJobMatchServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(Resume.class);
        initTableInfo(ResumeProject.class);
        initTableInfo(ResumeAnalysisRecord.class);
        initTableInfo(TargetJob.class);
        initTableInfo(JobDescriptionAnalysis.class);
        initTableInfo(ResumeJobMatchReport.class);
        initTableInfo(ResumeJobMatchDetail.class);
        initTableInfo(ResumeVersion.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @BeforeEach
    void setUp() {
        service = new ResumeJobMatchServiceImpl(
                resumeMapper,
                projectMapper,
                analysisRecordMapper,
                targetJobMapper,
                jobDescriptionAnalysisMapper,
                reportMapper,
                detailMapper,
                resumeVersionMapper,
                aiFeignClient,
                JSON,
                transactionTemplate,
                Optional.empty(),
                new ResumeJobMatchTrustPolicy(JSON));
        stubTransactions();
        stubMatchContext();
    }

    @Test
    void executeReportPersistsExpandedDetailFieldsWithoutTruncation() throws Exception {
        String dimension = "D".repeat(100);
        String skillName = "S".repeat(300);
        String evidence = "E".repeat(70_000);
        String gapDescription = "G".repeat(70_000);
        String action = "A".repeat(70_000);
        ObjectNode partialResult = (ObjectNode) JSON.readTree(validMatchJson(
                dimension, skillName, evidence, gapDescription, action));
        partialResult.put("trustStatus", "PARTIAL");
        partialResult.put("fallback", false);
        ObjectNode warning = JSON.createObjectNode();
        warning.put("field", "evidenceBoundary");
        warning.put("message", "unsupported evidence removed");
        partialResult.set("schemaWarnings", JSON.createArrayNode().add(warning));
        String resultJson = partialResult.toString();
        ResumeJobMatchReport persisted = report(ResumeJobMatchStatus.SUCCESS);
        persisted.setOverallScore(82);
        persisted.setAiCallLogId(AI_CALL_LOG_ID);
        persisted.setGapsJson("[{\"skillName\":\"expanded\"}]");
        persisted.setRawResultJson(resultJson);
        stubSuccessfulReportLifecycle(persisted);
        stubAiResult(resultJson);
        when(detailMapper.insert(any(ResumeJobMatchDetail.class))).thenReturn(1);

        ResumeJobMatchSubmitVO result = service.executeReport(REPORT_ID);

        assertEquals(ResumeJobMatchStatus.SUCCESS.getCode(), result.getStatus());
        assertEquals("PARTIAL", result.getTrustStatus());
        assertFalse(result.getFallback());
        assertEquals(1, result.getSchemaWarningCount());
        ArgumentCaptor<ResumeJobMatchDetail> captor =
                ArgumentCaptor.forClass(ResumeJobMatchDetail.class);
        verify(detailMapper).insert(captor.capture());
        ResumeJobMatchDetail inserted = captor.getValue();
        assertEquals(dimension, inserted.getDimension());
        assertEquals(skillName, inserted.getSkillName());
        assertEquals(evidence, inserted.getEvidence());
        assertEquals(gapDescription, inserted.getGapDescription());
        assertEquals(JSON.createArrayNode().add(action).toString(), inserted.getSuggestion());
        assertTrue(inserted.getSkillName().length() > 128);
        assertTrue(inserted.getEvidence().length() > 65_535);
    }

    @Test
    void executeReportClassifiesMapperDataTruncationAsPersistenceFailure()
            throws Exception {
        String resultJson = validMatchJson(
                "TECH_STACK",
                "Spring Cloud 微服务治理与复杂分布式系统架构能力",
                "岗位要求与简历项目证据存在可核对差距",
                "需要补充更完整的项目证据",
                "补充项目职责、技术方案和结果指标");
        ResumeJobMatchReport failed = stubFailedReportLifecycle();
        stubAiResult(resultJson);
        when(detailMapper.insert(any(ResumeJobMatchDetail.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Data too long for column 'skill_name' at row 1"));

        ResumeJobMatchSubmitVO result = service.executeReport(REPORT_ID);

        assertEquals(ResumeJobMatchStatus.FAILED.getCode(), result.getStatus());
        assertTrue(result.getErrorMessage().contains("保存失败"));
        assertFalse(result.getErrorMessage().contains("资料不完整"));
        JsonNode diagnostic = JSON.readTree(failed.getRawResultJson())
                .path("errorDiagnostic");
        assertEquals(
                "MATCH_RESULT_PERSISTENCE_FAILED",
                diagnostic.path("category").asText());
        assertFalse(diagnostic.path("message").asText().contains("Data too long"));
    }

    @Test
    void executeReportRejectsDetailBeyondStorageContractBeforeInsert()
            throws Exception {
        String resultJson = validMatchJson(
                "TECH_STACK",
                "X".repeat(65_536),
                "岗位要求与简历项目证据存在可核对差距",
                "需要补充更完整的项目证据",
                "补充项目职责、技术方案和结果指标");
        ResumeJobMatchReport failed = stubFailedReportLifecycle();
        stubAiResult(resultJson);

        ResumeJobMatchSubmitVO result = service.executeReport(REPORT_ID);

        assertEquals(ResumeJobMatchStatus.FAILED.getCode(), result.getStatus());
        assertTrue(result.getErrorMessage().contains("保存失败"));
        verify(detailMapper, never()).insert(any(ResumeJobMatchDetail.class));
        JsonNode diagnostic = JSON.readTree(failed.getRawResultJson())
                .path("errorDiagnostic");
        assertEquals(
                "MATCH_RESULT_PERSISTENCE_FAILED",
                diagnostic.path("category").asText());
        assertTrue(diagnostic.path("message").asText().contains("skill_name"));
    }

    private void stubSuccessfulReportLifecycle(ResumeJobMatchReport persisted) {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(
                report(ResumeJobMatchStatus.PROCESSING),
                report(ResumeJobMatchStatus.RUNNING),
                report(ResumeJobMatchStatus.RUNNING),
                persisted);
        when(reportMapper.update(
                nullable(ResumeJobMatchReport.class),
                any(Wrapper.class))).thenReturn(1);
    }

    private ResumeJobMatchReport stubFailedReportLifecycle() {
        ResumeJobMatchReport failed = report(ResumeJobMatchStatus.RUNNING);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(
                report(ResumeJobMatchStatus.PROCESSING),
                report(ResumeJobMatchStatus.RUNNING),
                report(ResumeJobMatchStatus.RUNNING),
                failed,
                failed);
        when(reportMapper.update(
                nullable(ResumeJobMatchReport.class),
                any(Wrapper.class))).thenReturn(1);
        return failed;
    }

    private void stubAiResult(String resultJson) {
        AnalyzeResumeJobMatchVO response = new AnalyzeResumeJobMatchVO();
        response.setResultJson(resultJson);
        response.setAiCallLogId(AI_CALL_LOG_ID);
        when(aiFeignClient.analyzeResumeJobMatch(any())).thenReturn(Result.success(response));
    }

    @SuppressWarnings("unchecked")
    private void stubTransactions() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @SuppressWarnings("unchecked")
    private void stubMatchContext() {
        when(resumeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resume());
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(analysisRecordMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(resumeAnalysis());
        when(targetJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(targetJob());
        when(jobDescriptionAnalysisMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(jdAnalysis());
    }

    private String validMatchJson(
            String dimension,
            String skillName,
            String evidence,
            String gapDescription,
            String action) {
        ObjectNode root = JSON.createObjectNode();
        root.put("overallScore", 82);
        ObjectNode scores = JSON.createObjectNode();
        scores.put("techStack", 80);
        scores.put("projectExperience", 78);
        scores.put("businessFit", 85);
        scores.put("communication", 86);
        root.set("dimensionScores", scores);
        root.set("strengths", JSON.createArrayNode());
        ObjectNode gap = JSON.createObjectNode();
        gap.put("skillName", skillName);
        gap.put("category", dimension);
        gap.put("severity", "HIGH");
        gap.put("targetLevel", 4);
        gap.put("currentLevel", 2);
        gap.put("description", gapDescription);
        gap.put("evidence", evidence);
        gap.set("recommendedActions", JSON.createArrayNode().add(action));
        root.set("gaps", JSON.createArrayNode().add(gap));
        root.set("resumeRisks", JSON.createArrayNode());
        root.set("optimizationSuggestions", JSON.createArrayNode());
        root.set("recommendedLearningTopics", JSON.createArrayNode());
        root.set("recommendedInterviewTopics", JSON.createArrayNode());
        root.put("summary", "匹配结果可用，差距证据需要按原文保存。");
        return root.toString();
    }

    private Resume resume() {
        Resume resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);
        resume.setTitle("Java 后端工程师简历");
        resume.setRealName("张伟");
        resume.setTargetPosition("Java 后端工程师");
        resume.setSummary("具备微服务项目经验");
        resume.setCreatedAt(NOW.minusDays(2));
        resume.setUpdatedAt(NOW.minusDays(1));
        return resume;
    }

    private ResumeAnalysisRecord resumeAnalysis() {
        ResumeAnalysisRecord analysis = new ResumeAnalysisRecord();
        analysis.setId(21L);
        analysis.setResumeId(RESUME_ID);
        analysis.setUserId(USER_ID);
        analysis.setParseStatus(ResumeParseStatus.SUCCESS.getCode());
        analysis.setStructuredJson("{}");
        analysis.setCreatedAt(NOW.minusDays(2));
        analysis.setUpdatedAt(NOW.minusDays(1));
        return analysis;
    }

    private TargetJob targetJob() {
        TargetJob job = new TargetJob();
        job.setId(TARGET_JOB_ID);
        job.setUserId(USER_ID);
        job.setJobTitle("Java 后端工程师");
        job.setJdText("需要微服务项目经验");
        job.setCreatedAt(NOW.minusDays(2));
        job.setUpdatedAt(NOW.minusDays(1));
        return job;
    }

    private JobDescriptionAnalysis jdAnalysis() {
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(JD_ANALYSIS_ID);
        analysis.setTargetJobId(TARGET_JOB_ID);
        analysis.setUserId(USER_ID);
        analysis.setParseStatus(JobDescriptionParseStatus.PARSED.getCode());
        analysis.setSummary("岗位要求微服务项目经验");
        analysis.setCreatedAt(NOW.minusDays(2));
        analysis.setUpdatedAt(NOW.minusDays(1));
        return analysis;
    }

    private ResumeJobMatchReport report(ResumeJobMatchStatus status) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setId(REPORT_ID);
        report.setUserId(USER_ID);
        report.setResumeId(RESUME_ID);
        report.setTargetJobId(TARGET_JOB_ID);
        report.setJdAnalysisId(JD_ANALYSIS_ID);
        report.setStatus(status.getCode());
        report.setCreatedAt(NOW.minusMinutes(5));
        report.setUpdatedAt(NOW);
        return report;
    }
}
