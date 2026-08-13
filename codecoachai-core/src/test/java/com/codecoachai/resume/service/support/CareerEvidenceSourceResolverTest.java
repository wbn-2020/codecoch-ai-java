package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.careercontact.mapper.CareerActivityMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewProcessMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionMapper;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageCreateDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidenceVersion;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageSnapshotMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceVersionMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectStoryGenerationMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerEvidenceSourceResolverTest {

    private static final Long USER_ID = 1001L;
    private static final Long TARGET_JOB_ID = 11L;
    private static final Long PROJECT_ID = 31L;
    private static final Long SKILL_ID = 41L;

    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private ProjectEvidenceVersionMapper projectVersionMapper;
    @Mock
    private ProjectSkillEvidenceMapper skillEvidenceMapper;
    @Mock
    private ProjectStoryGenerationMapper storyGenerationMapper;
    @Mock
    private JobApplicationPackageMapper packageMapper;
    @Mock
    private JobApplicationPackageSnapshotMapper packageSnapshotMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private ResumeJobMatchReportMapper matchReportMapper;
    @Mock
    private JobApplicationEventMapper applicationEventMapper;
    @Mock
    private CareerInterviewProcessMapper interviewProcessMapper;
    @Mock
    private CareerInterviewRoundMapper interviewRoundMapper;
    @Mock
    private CareerOfferDecisionMapper offerDecisionMapper;
    @Mock
    private CareerActivityMapper activityMapper;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CareerEvidenceSourceResolver resolver;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProjectSkillEvidence.class);
        initTableInfo(ProjectEvidence.class);
    }

    @BeforeEach
    void setUp() {
        resolver = new CareerEvidenceSourceResolver(
                applicationMapper,
                projectEvidenceMapper,
                projectVersionMapper,
                skillEvidenceMapper,
                storyGenerationMapper,
                packageMapper,
                packageSnapshotMapper,
                resumeVersionMapper,
                matchReportMapper,
                applicationEventMapper,
                interviewProcessMapper,
                interviewRoundMapper,
                offerDecisionMapper,
                activityMapper,
                objectMapper);
        org.mockito.Mockito.lenient().when(skillEvidenceMapper.selectOne(any()))
                .thenReturn(currentSkill());
        org.mockito.Mockito.lenient().when(projectEvidenceMapper.selectOne(any()))
                .thenReturn(project());
    }

    @Test
    void historicalSkillVersionUsesTheSkillEmbeddedInProjectSnapshotJson() throws Exception {
        ProjectEvidenceVersion version = version("""
                {
                  "id": 31,
                  "title": "Historical project",
                  "skillEvidences": [
                    {
                      "id": 41,
                      "skillName": "Historical Java",
                      "evidenceText": "Built the original concurrency control"
                    }
                  ]
                }
                """);
        when(projectVersionMapper.selectOwnedVersion(PROJECT_ID, USER_ID, 2))
                .thenReturn(version);

        CareerEvidenceSourceResolver.AssetResolution resolved =
                resolver.resolveAsset(USER_ID, application(), request("2"));

        JsonNode historicalSkill = objectMapper.readTree(version.getSnapshotJson())
                .path("skillEvidences").get(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectVersionHash", version.getContentHash());
        payload.put("skill", historicalSkill);
        String expectedContentHash = ResumeArtifactHashes.sha256(
                objectMapper.writeValueAsString(payload));

        assertEquals("PROJECT_SKILL_EVIDENCE", resolved.assetType());
        assertEquals(SKILL_ID, resolved.assetId());
        assertEquals("2", resolved.assetVersion());
        assertEquals("project-version-2-hash", resolved.sourceHash());
        assertEquals(expectedContentHash, resolved.contentHash());
        assertEquals("Historical Java", resolved.summary());
        assertNotEquals(currentSkill().getSkillName(), resolved.summary());
    }

    @Test
    void historicalSkillVersionRejectsAProjectSnapshotThatDoesNotContainTheSkill() {
        when(projectVersionMapper.selectOwnedVersion(PROJECT_ID, USER_ID, 2))
                .thenReturn(version("""
                        {
                          "id": 31,
                          "skillEvidences": [
                            {
                              "id": 99,
                              "skillName": "Different skill"
                            }
                          ]
                        }
                        """));

        BusinessException error = assertThrows(BusinessException.class,
                () -> resolver.resolveAsset(USER_ID, application(), request("V2")));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("不属于指定的项目证据版本"));
    }

    @Test
    void ownedApplicationExcludesArchivedApplications() {
        when(applicationMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> resolver.ownedApplication(USER_ID, 81L));

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<JobApplication>> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(applicationMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("archived_at IS NULL"));
    }

    private CareerEvidenceUsageCreateDTO request(String version) {
        CareerEvidenceUsageCreateDTO request = new CareerEvidenceUsageCreateDTO();
        request.setAssetType("PROJECT_SKILL_EVIDENCE");
        request.setAssetId(SKILL_ID);
        request.setAssetVersion(version);
        request.setUsageScene("APPLICATION_SUBMISSION");
        request.setIdempotencyKey("skill-history-" + version);
        return request;
    }

    private JobApplication application() {
        JobApplication application = new JobApplication();
        application.setId(81L);
        application.setUserId(USER_ID);
        application.setTargetJobId(TARGET_JOB_ID);
        application.setDeleted(CommonConstants.NO);
        return application;
    }

    private ProjectSkillEvidence currentSkill() {
        ProjectSkillEvidence skill = new ProjectSkillEvidence();
        skill.setId(SKILL_ID);
        skill.setUserId(USER_ID);
        skill.setProjectEvidenceId(PROJECT_ID);
        skill.setSkillName("Current Redis");
        skill.setEvidenceText("Current mutable evidence");
        skill.setDeleted(CommonConstants.NO);
        return skill;
    }

    private ProjectEvidence project() {
        ProjectEvidence project = new ProjectEvidence();
        project.setId(PROJECT_ID);
        project.setUserId(USER_ID);
        project.setTargetJobId(TARGET_JOB_ID);
        project.setTitle("Current project");
        project.setDeleted(CommonConstants.NO);
        return project;
    }

    private ProjectEvidenceVersion version(String snapshotJson) {
        ProjectEvidenceVersion version = new ProjectEvidenceVersion();
        version.setId(201L);
        version.setProjectEvidenceId(PROJECT_ID);
        version.setUserId(USER_ID);
        version.setVersionNo(2);
        version.setSnapshotJson(snapshotJson);
        version.setContentHash("project-version-2-hash");
        version.setCreatedAt(LocalDateTime.of(2026, 7, 22, 9, 0));
        return version;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant =
                    new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
