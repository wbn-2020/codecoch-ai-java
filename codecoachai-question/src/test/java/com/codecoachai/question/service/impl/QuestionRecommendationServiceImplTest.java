package com.codecoachai.question.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.question.domain.dto.QuestionRecommendationGenerateFromGapDTO;
import com.codecoachai.question.domain.dto.QuestionRecommendationQueryDTO;
import com.codecoachai.question.domain.entity.QuestionRecommendationBatch;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchDetailVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationBatchListVO;
import com.codecoachai.question.domain.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.question.domain.enums.QuestionRecommendationSourceType;
import com.codecoachai.question.feign.AiQuestionRecommendationFeignClient;
import com.codecoachai.question.feign.ResumeProfileFeignClient;
import com.codecoachai.question.feign.StudyPlanFeignClient;
import com.codecoachai.question.feign.dto.GenerateQuestionRecommendationDTO;
import com.codecoachai.question.feign.vo.GenerateQuestionRecommendationVO;
import com.codecoachai.question.feign.vo.InnerSkillGapItemVO;
import com.codecoachai.question.feign.vo.InnerSkillProfileVO;
import com.codecoachai.question.feign.vo.InnerStudyPlanSkillRelationVO;
import com.codecoachai.question.feign.vo.InnerStudyPlanVO;
import com.codecoachai.question.feign.vo.QuestionRecommendationDraftItemVO;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.mapper.QuestionRecommendationBatchMapper;
import com.codecoachai.question.mapper.QuestionRecommendationItemMapper;
import com.codecoachai.question.mq.QuestionMqDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codecoachai.question.util.QuestionRecommendationRequestPayloadUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRecommendationServiceImplTest {

    private static final long USER_ID = 7001L;

    @Mock
    private QuestionRecommendationBatchMapper batchMapper;
    @Mock
    private QuestionRecommendationItemMapper itemMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private ResumeProfileFeignClient resumeProfileFeignClient;
    @Mock
    private StudyPlanFeignClient studyPlanFeignClient;
    @Mock
    private AiQuestionRecommendationFeignClient aiRecommendationFeignClient;
    @Mock
    private QuestionMqDispatcher questionMqDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private QuestionRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("user").build());
        service = new QuestionRecommendationServiceImpl(
                batchMapper,
                itemMapper,
                questionMapper,
                resumeProfileFeignClient,
                studyPlanFeignClient,
                aiRecommendationFeignClient,
                objectMapper,
                Optional.empty());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void buildAiRequestOmitsRawSkillProfilePayloadFromSnapshot() throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8101L);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(3201L);
        batch.setJobTargetId(901L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setStrategy("GAP_PRIORITY");
        batch.setQuestionCount(5);

        InnerSkillProfileVO profile = new InnerSkillProfileVO();
        profile.setProfileId(903L);
        profile.setTargetJobId(901L);
        profile.setMatchReportId(902L);
        profile.setProfileName("Java backend readiness");
        profile.setOverallLevel(4);
        profile.setOverallScore(87);
        profile.setSummary("Focus on Redis consistency and transaction boundaries.");
        profile.setRawResultJson("{\"contact\":\"secret@example.com\",\"phone\":\"13812345678\",\"notes\":\"sensitive raw profile\"}");

        InnerSkillGapItemVO gap = new InnerSkillGapItemVO();
        gap.setId(4501L);
        gap.setSkillName("Redis");
        gap.setCategory("BACKEND");
        gap.setTargetLevel(5);
        gap.setCurrentLevel(3);
        gap.setGapLevel(2);
        gap.setSeverity("HIGH");

        Object request = newRecommendationRequest(profile, List.of(gap));

        GenerateQuestionRecommendationDTO dto = invokeBuildAiRequest(batch, request, 7001L);
        JsonNode skillProfileNode = objectMapper.readTree(dto.getSkillProfileJson());

        assertEquals("Java backend readiness", skillProfileNode.get("profileName").asText());
        assertEquals(4, skillProfileNode.get("overallLevel").asInt());
        assertEquals(87, skillProfileNode.get("overallScore").asInt());
        assertEquals("Focus on Redis consistency and transaction boundaries.",
                skillProfileNode.get("summary").asText());
        assertFalse(skillProfileNode.has("rawResult"));
        assertFalse(dto.getSkillProfileJson().contains("secret@example.com"));
        assertFalse(dto.getSkillProfileJson().contains("13812345678"));

        String persistedRequestJson = objectMapper.writeValueAsString(dto);
        assertFalse(persistedRequestJson.contains("rawResult"));
        assertFalse(persistedRequestJson.contains("secret@example.com"));
        assertFalse(persistedRequestJson.contains("13812345678"));
    }

    @Test
    void executeBatchPersistsMinimizedResultMetadataInsteadOfRawAiResponse() throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8201L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setQuestionCount(1);
        batch.setRequestJson(validAiRequestJson(batch));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9501L);
        aiResult.setRawResponse("{\"contact\":\"secret@example.com\",\"phone\":\"13812345678\",\"notes\":\"sensitive recommendation raw\"}");
        aiResult.setQuestions(List.of(draft));

        when(aiRecommendationFeignClient.generate(any())).thenReturn(Result.success(aiResult));
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9501L, resultNode.path("aiCallLogId").asLong());
        assertEquals(8201L, resultNode.path("batchId").asLong());
        assertEquals(1, resultNode.path("questionCount").asInt());
        assertFalse(resultNode.path("questionRecommendationRawStored").asBoolean());
        assertFalse(batch.getResultJson().contains("secret@example.com"));
        assertFalse(batch.getResultJson().contains("13812345678"));
    }

    @Test
    void executeBatchRebuildsAiRequestFromBatchMetadataWhenStoredRequestJsonIsMalformed() throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8211L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(903L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(1);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"sourceType\":\"JD_GAP\"");

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9511L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8211L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("JD_GAP", aiRequestHolder[0].getSourceType());
        assertEquals(903L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(1, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        assertFalse(aiRequestHolder[0].getSkillProfileJson().contains("secret@example.com"));
        assertFalse(aiRequestHolder[0].getSkillProfileJson().contains("13812345678"));
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9511L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchInfersStudyPlanSourceTypeFromBatchMetadataWhenStoredRequestJsonIsMalformed()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8212L);
        batch.setUserId(USER_ID);
        batch.setStudyPlanId(9901L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedStudyPlanReplaySnapshotJson(batch.getId(), batch.getUserId(), 9901L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9512L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan()));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8212L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9512L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchRebuildsStudyPlanRequestWhenOwnedPlanIsNoLongerActiveAfterSubmit()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8215L);
        batch.setUserId(USER_ID);
        batch.setStudyPlanId(9901L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedStudyPlanReplaySnapshotJson(batch.getId(), batch.getUserId(), 9901L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9515L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("COMPLETED")));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8215L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9515L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchRebuildsStudyPlanRequestFromPreservedSkillProfileWhenCurrentPlanLosesSkillProfile()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8218L);
        batch.setUserId(USER_ID);
        batch.setStudyPlanId(9901L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedStudyPlanReplaySnapshotJson(batch.getId(), batch.getUserId(), 9901L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9518L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", null)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8218L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9518L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchIgnoresInvalidBatchSourceTypeWhenStudyPlanMetadataStillProvesReplayContext()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8219L);
        batch.setUserId(USER_ID);
        batch.setSourceType("LEGACY_PLAN");
        batch.setStudyPlanId(9901L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedStudyPlanReplaySnapshotJson(batch.getId(), batch.getUserId(), 9901L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9519L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan()));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8219L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9519L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchPublicPathAllowsInvalidStudyPlanSourceTypeWhenDedicatedPlanMetadataStillProvesReplayContext()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8221L);
        batch.setUserId(USER_ID);
        batch.setSourceType("LEGACY_PLAN");
        batch.setStudyPlanId(9901L);
        batch.setMatchReportId(902L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedStudyPlanReplaySnapshotJson(batch.getId(), batch.getUserId(), 9901L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9521L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertEquals("SUCCESS", result.getStatus());
        assertNotNull(aiRequestHolder[0]);
        assertEquals(8221L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9521L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchPublicPathPrefersStudyPlanEvidenceWhenValidBatchSourceTypeTokenIsStale()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8229L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(903L);
        batch.setStudyPlanId(9901L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"studyPlanId\":9901");

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9528L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(), aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(), result.getSourceType());
        assertEquals(9901L, result.getSourceId());
    }

    @Test
    void batchDetailRejectsAmbiguousMetadataWhoseSourceIdAlignsWithMatchReportAndSkillProfile() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8222L);
        batch.setUserId(USER_ID);
        batch.setSourceType("LEGACY_AMBIGUOUS");
        batch.setSourceId(902L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(902L);
        batch.setQuestionCount(2);
        batch.setRequestJson("{\"matchReportId\":902");

        InnerSkillProfileVO ambiguousProfile = ownedProfileWithGap();
        ambiguousProfile.setProfileId(902L);
        ambiguousProfile.setMatchReportId(902L);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(902L)).thenReturn(Result.success(ambiguousProfile));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.batchDetail(batch.getId()));

        assertEquals("历史推荐依据待复核，请重新生成推荐题", ex.getMessage());
    }

    @Test
    void batchDetailRejectsAmbiguousMetadataEvenWhenBatchSourceTypeTokenLooksValid() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8223L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(902L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(902L);
        batch.setQuestionCount(2);
        batch.setRequestJson("{\"matchReportId\":902");

        InnerSkillProfileVO ambiguousProfile = ownedProfileWithGap();
        ambiguousProfile.setProfileId(902L);
        ambiguousProfile.setMatchReportId(902L);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(902L)).thenReturn(Result.success(ambiguousProfile));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.batchDetail(batch.getId()));

        assertEquals("历史推荐依据待复核，请重新生成推荐题", ex.getMessage());
    }

    @Test
    void listBatchesSkipsAmbiguousMalformedRowsWithoutSourceTypeFilter() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setPageNo(1L);
        query.setPageSize(10L);

        QuestionRecommendationBatch ambiguousBatch = new QuestionRecommendationBatch();
        ambiguousBatch.setId(8224L);
        ambiguousBatch.setUserId(USER_ID);
        ambiguousBatch.setSourceType("LEGACY_AMBIGUOUS");
        ambiguousBatch.setSourceId(902L);
        ambiguousBatch.setMatchReportId(902L);
        ambiguousBatch.setSkillProfileId(902L);
        ambiguousBatch.setStatus("SUCCESS");
        ambiguousBatch.setQuestionCount(2);
        ambiguousBatch.setRequestJson("{\"matchReportId\":902");

        QuestionRecommendationBatch trustedBatch = new QuestionRecommendationBatch();
        trustedBatch.setId(8225L);
        trustedBatch.setUserId(USER_ID);
        trustedBatch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        trustedBatch.setSourceId(903L);
        trustedBatch.setSkillProfileId(903L);
        trustedBatch.setStatus("SUCCESS");
        trustedBatch.setQuestionCount(2);

        Page<QuestionRecommendationBatch> page = Page.of(1L, 10L);
        page.setTotal(2L);
        page.setRecords(List.of(ambiguousBatch, trustedBatch));

        InnerSkillProfileVO ambiguousProfile = ownedProfileWithGap();
        ambiguousProfile.setProfileId(902L);
        ambiguousProfile.setMatchReportId(902L);

        when(batchMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(resumeProfileFeignClient.getSkillProfile(902L)).thenReturn(Result.success(ambiguousProfile));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> result = service.listBatches(query);

        assertEquals(1, result.getRecords().size());
        assertEquals(8225L, result.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesPaginatesAfterTrustedInferenceWithoutSourceTypeFilter() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setPageNo(2L);
        query.setPageSize(1L);

        QuestionRecommendationBatch ambiguousBatch = new QuestionRecommendationBatch();
        ambiguousBatch.setId(8901L);
        ambiguousBatch.setUserId(USER_ID);
        ambiguousBatch.setSourceType("LEGACY_AMBIGUOUS");
        ambiguousBatch.setSourceId(902L);
        ambiguousBatch.setMatchReportId(902L);
        ambiguousBatch.setSkillProfileId(902L);
        ambiguousBatch.setStatus("SUCCESS");
        ambiguousBatch.setQuestionCount(2);
        ambiguousBatch.setRequestJson("{\"matchReportId\":902");

        QuestionRecommendationBatch trustedBatchA = new QuestionRecommendationBatch();
        trustedBatchA.setId(8902L);
        trustedBatchA.setUserId(USER_ID);
        trustedBatchA.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        trustedBatchA.setSourceId(903L);
        trustedBatchA.setSkillProfileId(903L);
        trustedBatchA.setStatus("SUCCESS");
        trustedBatchA.setQuestionCount(2);

        QuestionRecommendationBatch trustedBatchB = new QuestionRecommendationBatch();
        trustedBatchB.setId(8903L);
        trustedBatchB.setUserId(USER_ID);
        trustedBatchB.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        trustedBatchB.setSourceId(904L);
        trustedBatchB.setSkillProfileId(904L);
        trustedBatchB.setStatus("SUCCESS");
        trustedBatchB.setQuestionCount(2);

        List<QuestionRecommendationBatch> orderedBatches = List.of(ambiguousBatch, trustedBatchA, trustedBatchB);
        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(orderedBatches.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= orderedBatches.size()
                    ? List.of()
                    : orderedBatches.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(orderedBatches.size());
            result.setRecords(pageRecords);
            return result;
        });

        InnerSkillProfileVO ambiguousProfile = ownedProfileWithGap();
        ambiguousProfile.setProfileId(902L);
        ambiguousProfile.setMatchReportId(902L);

        InnerSkillProfileVO trustedProfileB = ownedProfileWithGap();
        trustedProfileB.setProfileId(904L);
        trustedProfileB.setMatchReportId(904L);

        when(resumeProfileFeignClient.getSkillProfile(902L)).thenReturn(Result.success(ambiguousProfile));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(resumeProfileFeignClient.getSkillProfile(904L)).thenReturn(Result.success(trustedProfileB));

        PageResult<QuestionRecommendationBatchListVO> result = service.listBatches(query);

        assertEquals(1, result.getRecords().size());
        assertEquals(2L, result.getTotal());
        assertEquals(8903L, result.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesExposesInferredTrustedSourceMetadataWhenLegacyBatchTokenIsStale() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setPageNo(1L);
        query.setPageSize(10L);

        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8904L);
        batch.setUserId(USER_ID);
        batch.setSourceType("LEGACY_PLAN");
        batch.setStudyPlanId(9901L);
        batch.setSkillProfileId(903L);
        batch.setStatus("SUCCESS");
        batch.setQuestionCount(2);
        batch.setRequestJson("{\"studyPlanId\":9901");

        Page<QuestionRecommendationBatch> page = Page.of(1L, 10L);
        page.setTotal(1L);
        page.setRecords(List.of(batch));

        when(batchMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> result = service.listBatches(query);

        assertEquals(1, result.getRecords().size());
        assertEquals("STUDY_PLAN", result.getRecords().get(0).getSourceType());
        assertEquals(9901L, result.getRecords().get(0).getSourceId());
        assertFalse(result.getRecords().get(0).getFallback());
        assertTrue(result.getRecords().get(0).getEvidenceSummary().contains("来自学习计划"));
        assertTrue(result.getRecords().get(0).getEvidenceSummary().contains("推荐依据已绑定"));
    }

    @Test
    void executeBatchPublicPathAllowsParseableLegacyStudyPlanSnapshotWhenBatchMetadataIsSparse()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8223L);
        batch.setUserId(USER_ID);
        batch.setStatus("SUCCESS");
        batch.setQuestionCount(2);
        batch.setRequestJson(legacyStudyPlanAiRequestJson(batch.getId(), batch.getUserId(), 9901L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9523L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertEquals("SUCCESS", result.getStatus());
        assertNotNull(aiRequestHolder[0]);
        assertEquals(8223L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
    }

    @Test
    void executeBatchPublicPathAllowsStructuredMinimizedStudyPlanSnapshotWhenBatchMetadataIsSparse()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8226L);
        batch.setUserId(USER_ID);
        batch.setStatus("SUCCESS");
        batch.setRequestJson("""
                {
                  "storageMode":"MINIMIZED_METADATA",
                  "questionRecommendationRequestStored":true,
                  "batchId":8226,
                  "userId":7001,
                  "sourceType":"STUDY_PLAN",
                  "sourceId":9901,
                  "matchReportId":902,
                  "questionCount":3,
                  "difficultyPreference":"MEDIUM",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702],
                  "studyPlanId":9901
                }
                """);

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain idempotent MQ consumers");
        draft.setContent("Cover dedupe keys and replay safety.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9526L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertEquals("SUCCESS", result.getStatus());
        assertNotNull(aiRequestHolder[0]);
        assertEquals(8226L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(902L, aiRequestHolder[0].getMatchReportId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(3, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9526L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchPublicPathAllowsLegacySafeMinimizedStudyPlanSnapshotWithoutIdentityMarkersWhenBatchMetadataIsSparse()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8227L);
        batch.setUserId(USER_ID);
        batch.setStatus("SUCCESS");
        batch.setRequestJson("""
                {
                  "sourceType":"STUDY_PLAN",
                  "sourceId":9901,
                  "matchReportId":902,
                  "questionCount":3,
                  "difficultyPreference":"MEDIUM",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702],
                  "studyPlanId":9901
                }
                """);

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain deterministic replay boundaries");
        draft.setContent("Cover minimized snapshots and owned study-plan recovery.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9527L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertEquals("SUCCESS", result.getStatus());
        assertNotNull(aiRequestHolder[0]);
        assertEquals(8227L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("STUDY_PLAN", aiRequestHolder[0].getSourceType());
        assertEquals(9901L, aiRequestHolder[0].getSourceId());
        assertEquals(902L, aiRequestHolder[0].getMatchReportId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(9901L, aiRequestHolder[0].getStudyPlanId());
        assertEquals(3, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9527L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void listBatchesSourceTypeFilterPaginatesAfterTrustedInferenceForLegacyRows() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setSourceType(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
        query.setPageNo(1L);
        query.setPageSize(1L);

        List<QuestionRecommendationBatch> orderedBatches = new java.util.ArrayList<>();
        for (long batchId = 8501L; batchId <= 8510L; batchId++) {
            QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
            batch.setId(batchId);
            batch.setUserId(USER_ID);
            batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
            batch.setSourceId(903L);
            batch.setSkillProfileId(903L);
            batch.setStatus("SUCCESS");
            batch.setQuestionCount(1);
            orderedBatches.add(batch);
        }
        QuestionRecommendationBatch staleStudyPlanBatch = new QuestionRecommendationBatch();
        staleStudyPlanBatch.setId(8601L);
        staleStudyPlanBatch.setUserId(USER_ID);
        staleStudyPlanBatch.setSourceType("LEGACY_PLAN");
        staleStudyPlanBatch.setStudyPlanId(9901L);
        staleStudyPlanBatch.setSkillProfileId(903L);
        staleStudyPlanBatch.setStatus("SUCCESS");
        staleStudyPlanBatch.setQuestionCount(2);
        orderedBatches.add(staleStudyPlanBatch);

        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = invocation.getArgument(1);
            boolean rawStudyPlanFilter = wrapper.getParamNameValuePairs()
                    .containsValue(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
            List<QuestionRecommendationBatch> candidates = rawStudyPlanFilter
                    ? orderedBatches.stream()
                    .filter(batch -> QuestionRecommendationSourceType.STUDY_PLAN.getCode().equals(batch.getSourceType()))
                    .toList()
                    : orderedBatches;
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(candidates.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= candidates.size()
                    ? List.of()
                    : candidates.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(candidates.size());
            result.setRecords(pageRecords);
            return result;
        });
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan()));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> page = service.listBatches(query);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals(8601L, page.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesSourceTypeFilterAllowsParseableLegacySnapshotWhenBatchMetadataIsSparse() throws Exception {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setSourceType(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
        query.setPageNo(1L);
        query.setPageSize(1L);

        List<QuestionRecommendationBatch> orderedBatches = new java.util.ArrayList<>();
        for (long batchId = 8701L; batchId <= 8710L; batchId++) {
            QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
            batch.setId(batchId);
            batch.setUserId(USER_ID);
            batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
            batch.setSourceId(903L);
            batch.setSkillProfileId(903L);
            batch.setStatus("SUCCESS");
            batch.setQuestionCount(1);
            orderedBatches.add(batch);
        }
        QuestionRecommendationBatch sparseLegacyBatch = new QuestionRecommendationBatch();
        sparseLegacyBatch.setId(8801L);
        sparseLegacyBatch.setUserId(USER_ID);
        sparseLegacyBatch.setStatus("SUCCESS");
        sparseLegacyBatch.setQuestionCount(2);
        sparseLegacyBatch.setRequestJson(
                legacyStudyPlanAiRequestJson(sparseLegacyBatch.getId(), sparseLegacyBatch.getUserId(), 9901L, 903L));
        orderedBatches.add(sparseLegacyBatch);

        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = invocation.getArgument(1);
            boolean rawStudyPlanFilter = wrapper.getParamNameValuePairs()
                    .containsValue(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
            List<QuestionRecommendationBatch> candidates = rawStudyPlanFilter
                    ? orderedBatches.stream()
                    .filter(batch -> QuestionRecommendationSourceType.STUDY_PLAN.getCode().equals(batch.getSourceType()))
                    .toList()
                    : orderedBatches;
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(candidates.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= candidates.size()
                    ? List.of()
                    : candidates.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(candidates.size());
            result.setRecords(pageRecords);
            return result;
        });
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> page = service.listBatches(query);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals(8801L, page.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesSourceTypeFilterAllowsLegacySafeMinimizedSnapshotWithoutIdentityMarkersWhenBatchMetadataIsSparse() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setSourceType(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
        query.setPageNo(1L);
        query.setPageSize(1L);

        List<QuestionRecommendationBatch> orderedBatches = new java.util.ArrayList<>();
        for (long batchId = 8701L; batchId <= 8710L; batchId++) {
            QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
            batch.setId(batchId);
            batch.setUserId(USER_ID);
            batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
            batch.setSourceId(903L);
            batch.setSkillProfileId(903L);
            batch.setStatus("SUCCESS");
            batch.setQuestionCount(1);
            orderedBatches.add(batch);
        }
        QuestionRecommendationBatch sparseLegacySafeBatch = new QuestionRecommendationBatch();
        sparseLegacySafeBatch.setId(8802L);
        sparseLegacySafeBatch.setUserId(USER_ID);
        sparseLegacySafeBatch.setStatus("SUCCESS");
        sparseLegacySafeBatch.setQuestionCount(2);
        sparseLegacySafeBatch.setRequestJson("""
                {
                  "sourceType":"STUDY_PLAN",
                  "sourceId":9901,
                  "matchReportId":902,
                  "questionCount":2,
                  "difficultyPreference":"MEDIUM",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702],
                  "studyPlanId":9901
                }
                """);
        orderedBatches.add(sparseLegacySafeBatch);

        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = invocation.getArgument(1);
            boolean rawStudyPlanFilter = wrapper.getParamNameValuePairs()
                    .containsValue(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
            List<QuestionRecommendationBatch> candidates = rawStudyPlanFilter
                    ? orderedBatches.stream()
                    .filter(batch -> QuestionRecommendationSourceType.STUDY_PLAN.getCode().equals(batch.getSourceType()))
                    .toList()
                    : orderedBatches;
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(candidates.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= candidates.size()
                    ? List.of()
                    : candidates.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(candidates.size());
            result.setRecords(pageRecords);
            return result;
        });
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> page = service.listBatches(query);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals(8802L, page.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesStudyPlanIdFilterAllowsLegacySafeMinimizedSnapshotWhenBatchMetadataIsSparse() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setSourceType(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
        query.setStudyPlanId(9901L);
        query.setPageNo(1L);
        query.setPageSize(1L);

        List<QuestionRecommendationBatch> orderedBatches = new java.util.ArrayList<>();
        for (long batchId = 8701L; batchId <= 8710L; batchId++) {
            QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
            batch.setId(batchId);
            batch.setUserId(USER_ID);
            batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
            batch.setSourceId(903L);
            batch.setSkillProfileId(904L);
            batch.setStatus("SUCCESS");
            batch.setQuestionCount(1);
            orderedBatches.add(batch);
        }
        QuestionRecommendationBatch sparseLegacySafeBatch = new QuestionRecommendationBatch();
        sparseLegacySafeBatch.setId(8803L);
        sparseLegacySafeBatch.setUserId(USER_ID);
        sparseLegacySafeBatch.setStatus("SUCCESS");
        sparseLegacySafeBatch.setQuestionCount(2);
        sparseLegacySafeBatch.setRequestJson("""
                {
                  "sourceType":"STUDY_PLAN",
                  "sourceId":9901,
                  "matchReportId":902,
                  "questionCount":2,
                  "difficultyPreference":"MEDIUM",
                  "strategy":"PLAN_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702],
                  "studyPlanId":9901
                }
                """);
        orderedBatches.add(sparseLegacySafeBatch);

        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = invocation.getArgument(1);
            boolean rawStudyPlanIdFilter = wrapper.getParamNameValuePairs().containsValue(9901L);
            List<QuestionRecommendationBatch> candidates = rawStudyPlanIdFilter
                    ? orderedBatches.stream()
                    .filter(batch -> java.util.Objects.equals(9901L, batch.getStudyPlanId()))
                    .toList()
                    : orderedBatches;
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(candidates.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= candidates.size()
                    ? List.of()
                    : candidates.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(candidates.size());
            result.setRecords(pageRecords);
            return result;
        });
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> page = service.listBatches(query);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals(8803L, page.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesMatchReportIdFilterAllowsLegacySafeMinimizedSnapshotWhenBatchMetadataIsSparse() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setSourceType(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode());
        query.setMatchReportId(902L);
        query.setPageNo(1L);
        query.setPageSize(1L);

        List<QuestionRecommendationBatch> orderedBatches = new java.util.ArrayList<>();
        for (long batchId = 8711L; batchId <= 8720L; batchId++) {
            QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
            batch.setId(batchId);
            batch.setUserId(USER_ID);
            batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
            batch.setSourceId(904L);
            batch.setSkillProfileId(904L);
            batch.setStatus("SUCCESS");
            batch.setQuestionCount(1);
            orderedBatches.add(batch);
        }
        QuestionRecommendationBatch sparseLegacySafeBatch = new QuestionRecommendationBatch();
        sparseLegacySafeBatch.setId(8804L);
        sparseLegacySafeBatch.setUserId(USER_ID);
        sparseLegacySafeBatch.setStatus("SUCCESS");
        sparseLegacySafeBatch.setQuestionCount(2);
        sparseLegacySafeBatch.setRequestJson("""
                {
                  "sourceType":"RESUME_JOB_MATCH",
                  "sourceId":902,
                  "matchReportId":902,
                  "questionCount":2,
                  "difficultyPreference":"MEDIUM",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702]
                }
                """);
        orderedBatches.add(sparseLegacySafeBatch);

        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = invocation.getArgument(1);
            boolean rawMatchReportIdFilter = wrapper.getParamNameValuePairs().containsValue(902L);
            List<QuestionRecommendationBatch> candidates = rawMatchReportIdFilter
                    ? orderedBatches.stream()
                    .filter(batch -> java.util.Objects.equals(902L, batch.getMatchReportId()))
                    .toList()
                    : orderedBatches;
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(candidates.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= candidates.size()
                    ? List.of()
                    : candidates.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(candidates.size());
            result.setRecords(pageRecords);
            return result;
        });
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> page = service.listBatches(query);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals(8804L, page.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesSkillProfileIdFilterAllowsLegacySafeMinimizedSnapshotWhenBatchMetadataIsSparse() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        query.setSkillProfileId(903L);
        query.setPageNo(1L);
        query.setPageSize(1L);

        List<QuestionRecommendationBatch> orderedBatches = new java.util.ArrayList<>();
        for (long batchId = 8721L; batchId <= 8730L; batchId++) {
            QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
            batch.setId(batchId);
            batch.setUserId(USER_ID);
            batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
            batch.setSourceId(904L);
            batch.setSkillProfileId(904L);
            batch.setStatus("SUCCESS");
            batch.setQuestionCount(1);
            orderedBatches.add(batch);
        }
        QuestionRecommendationBatch sparseLegacySafeBatch = new QuestionRecommendationBatch();
        sparseLegacySafeBatch.setId(8805L);
        sparseLegacySafeBatch.setUserId(USER_ID);
        sparseLegacySafeBatch.setStatus("SUCCESS");
        sparseLegacySafeBatch.setQuestionCount(2);
        sparseLegacySafeBatch.setRequestJson("""
                {
                  "sourceType":"JD_GAP",
                  "sourceId":903,
                  "questionCount":2,
                  "difficultyPreference":"MEDIUM",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702]
                }
                """);
        orderedBatches.add(sparseLegacySafeBatch);

        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = invocation.getArgument(1);
            boolean rawSkillProfileIdFilter = wrapper.getParamNameValuePairs().containsValue(903L);
            List<QuestionRecommendationBatch> candidates = rawSkillProfileIdFilter
                    ? orderedBatches.stream()
                    .filter(batch -> java.util.Objects.equals(903L, batch.getSkillProfileId()))
                    .toList()
                    : orderedBatches;
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(candidates.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= candidates.size()
                    ? List.of()
                    : candidates.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(candidates.size());
            result.setRecords(pageRecords);
            return result;
        });
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedStandaloneProfileWithGap()));

        PageResult<QuestionRecommendationBatchListVO> page = service.listBatches(query);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals(8805L, page.getRecords().get(0).getBatchId());
    }

    @Test
    void listBatchesTargetJobIdAliasFilterAllowsLegacySafeMinimizedSnapshotWhenBatchMetadataIsSparse() {
        QuestionRecommendationQueryDTO query = new QuestionRecommendationQueryDTO();
        query.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        query.setTargetJobId(901L);
        query.setPageNo(1L);
        query.setPageSize(1L);

        List<QuestionRecommendationBatch> orderedBatches = new java.util.ArrayList<>();
        for (long batchId = 8731L; batchId <= 8740L; batchId++) {
            QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
            batch.setId(batchId);
            batch.setUserId(USER_ID);
            batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
            batch.setSourceId(904L);
            batch.setJobTargetId(999L);
            batch.setSkillProfileId(904L);
            batch.setStatus("SUCCESS");
            batch.setQuestionCount(1);
            orderedBatches.add(batch);
        }
        QuestionRecommendationBatch sparseLegacySafeBatch = new QuestionRecommendationBatch();
        sparseLegacySafeBatch.setId(8806L);
        sparseLegacySafeBatch.setUserId(USER_ID);
        sparseLegacySafeBatch.setStatus("SUCCESS");
        sparseLegacySafeBatch.setQuestionCount(2);
        sparseLegacySafeBatch.setRequestJson("""
                {
                  "sourceType":"JD_GAP",
                  "sourceId":903,
                  "questionCount":2,
                  "difficultyPreference":"MEDIUM",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702]
                }
                """);
        orderedBatches.add(sparseLegacySafeBatch);

        when(batchMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<QuestionRecommendationBatch> dbPage = invocation.getArgument(0);
            LambdaQueryWrapper<QuestionRecommendationBatch> wrapper = invocation.getArgument(1);
            boolean rawTargetJobIdFilter = wrapper.getParamNameValuePairs().containsValue(901L);
            List<QuestionRecommendationBatch> candidates = rawTargetJobIdFilter
                    ? orderedBatches.stream()
                    .filter(batch -> java.util.Objects.equals(901L, batch.getJobTargetId()))
                    .toList()
                    : orderedBatches;
            int from = Math.toIntExact((dbPage.getCurrent() - 1) * dbPage.getSize());
            int to = Math.min(candidates.size(), from + Math.toIntExact(dbPage.getSize()));
            List<QuestionRecommendationBatch> pageRecords = from >= candidates.size()
                    ? List.of()
                    : candidates.subList(from, to);
            Page<QuestionRecommendationBatch> result = Page.of(dbPage.getCurrent(), dbPage.getSize());
            result.setTotal(candidates.size());
            result.setRecords(pageRecords);
            return result;
        });
        InnerSkillProfileVO otherProfile = ownedStandaloneProfileWithGap();
        otherProfile.setProfileId(904L);
        otherProfile.setTargetJobId(999L);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedStandaloneProfileWithGap()));
        when(resumeProfileFeignClient.getSkillProfile(904L)).thenReturn(Result.success(otherProfile));

        PageResult<QuestionRecommendationBatchListVO> page = service.listBatches(query);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals(8806L, page.getRecords().get(0).getBatchId());
    }

    @Test
    void executeBatchInfersGapSourceTypeFromSkillProfileOnlyWhenSourceIdAndMatchReportIdAreMissing()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8220L);
        batch.setUserId(USER_ID);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"skillProfileId\":903");

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9520L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedStandaloneProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8220L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("JD_GAP", aiRequestHolder[0].getSourceType());
        assertEquals(903L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9520L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchPublicPathRejectsGapSkillProfileOnlyInferenceWhenSourceIdConflictsWithSkillProfile() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8226L);
        batch.setUserId(USER_ID);
        batch.setSourceId(904L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"skillProfileId\":903");

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9526L);
        aiResult.setQuestions(List.of(draft));

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedStandaloneProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenReturn(Result.success(aiResult));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.executeBatch(batch.getId(), USER_ID));

        assertEquals("历史推荐依据待复核，请重新生成推荐题", ex.getMessage());
    }

    @Test
    void executeBatchRejectsGapSkillProfileOnlyInferenceWhenSourceIdConflictsWithSkillProfile() throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8227L);
        batch.setUserId(USER_ID);
        batch.setSourceId(904L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"skillProfileId\":903");

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9527L);
        aiResult.setQuestions(List.of(draft));

        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedStandaloneProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenReturn(Result.success(aiResult));

        Exception ex = assertThrows(Exception.class, () -> invokeExecuteBatch(batch, true));
        assertNotNull(ex.getCause());
        assertEquals(BusinessException.class, ex.getCause().getClass());
    }

    @Test
    void executeBatchPublicPathRejectsGapSkillProfileOnlyInferenceWhenRecoveredProfileStillCarriesMatchReport() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8229L);
        batch.setUserId(USER_ID);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"skillProfileId\":903");

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9529L);
        aiResult.setQuestions(List.of(draft));

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenReturn(Result.success(aiResult));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.executeBatch(batch.getId(), USER_ID));

        assertEquals("鍘嗗彶鎺ㄨ崘渚濇嵁寰呭鏍革紝璇烽噸鏂扮敓鎴愭帹鑽愰", ex.getMessage());
    }

    @Test
    void executeBatchRejectsGapSkillProfileOnlyInferenceWhenRecoveredProfileStillCarriesMatchReport()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8230L);
        batch.setUserId(USER_ID);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"skillProfileId\":903");

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9530L);
        aiResult.setQuestions(List.of(draft));

        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenReturn(Result.success(aiResult));

        Exception ex = assertThrows(Exception.class, () -> invokeExecuteBatch(batch, true));
        assertNotNull(ex.getCause());
        assertEquals(BusinessException.class, ex.getCause().getClass());
        assertEquals("鍘嗗彶鎺ㄨ崘渚濇嵁蹇収宸蹭笉瓒充互瀹夊叏鎭㈠锛岃閲嶆柊鐢熸垚鎺ㄨ崘棰?", batch.getErrorMessage());
        assertEquals("鎺ㄨ崘棰樼敓鎴愭殏鏃跺け璐ワ細鍘嗗彶鎺ㄨ崘渚濇嵁蹇収宸蹭笉瓒充互瀹夊叏鎭㈠锛岃閲嶆柊鐢熸垚鎺ㄨ崘棰?",
                ex.getCause().getMessage());
        assertEquals("FAILED", batch.getStatus());
        verify(aiRecommendationFeignClient, never()).generate(any());
    }

    @Test
    void executeBatchRejectsResumeJobMatchReplayWhenStoredRequestJsonIsMalformedAndGapSelectionIsUnavailable()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8213L);
        batch.setUserId(USER_ID);
        batch.setSourceId(902L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"matchReportId\":902");

        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        Exception ex = assertThrows(Exception.class, () -> invokeExecuteBatch(batch, true));

        assertNotNull(ex.getCause());
        assertEquals(BusinessException.class, ex.getCause().getClass());
        assertEquals("历史推荐依据快照已不足以安全恢复，请重新生成推荐题", batch.getErrorMessage());
        assertEquals("推荐题生成暂时失败：历史推荐依据快照已不足以安全恢复，请重新生成推荐题",
                ex.getCause().getMessage());
        assertEquals("FAILED", batch.getStatus());
        verify(aiRecommendationFeignClient, never()).generate(any());
    }

    @Test
    void executeBatchRejectsMalformedMatchReportReplayBeforeLoadingProfileWhenGapSelectionIsUnavailable()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8234L);
        batch.setUserId(USER_ID);
        batch.setSourceId(902L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"matchReportId\":902");

        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertEquals("FAILED", result.getStatus());
        assertEquals("历史推荐依据快照已不足以安全恢复，请重新生成推荐题", batch.getErrorMessage());
        verify(resumeProfileFeignClient, never()).getSkillProfile(903L);
        verify(resumeProfileFeignClient, never()).getSuccessSkillProfileByMatchReport(902L);
        verify(aiRecommendationFeignClient, never()).generate(any());
    }

    @Test
    void executeBatchInfersMatchReportReplayWhenSourceIdentityIsMissingButReportProfileAndSelectionArePreserved()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8231L);
        batch.setUserId(USER_ID);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedMatchReportReplaySnapshotWithoutSourceIdentityJson(
                batch.getId(), batch.getUserId(), 902L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9531L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8231L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode(), aiRequestHolder[0].getSourceType());
        assertEquals(902L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        assertEquals(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode(), batch.getSourceType());
        assertEquals(902L, batch.getSourceId());
        assertEquals(902L, batch.getMatchReportId());
        assertEquals(903L, batch.getSkillProfileId());
    }

    @Test
    void executeBatchInfersMatchReportReplayWhenOnlyReportAndSelectionArePreserved()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8232L);
        batch.setUserId(USER_ID);
        batch.setMatchReportId(902L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedMatchReportReplaySnapshotWithoutSourceAndProfileIdentityJson(
                batch.getId(), batch.getUserId(), 902L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9532L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSuccessSkillProfileByMatchReport(902L))
                .thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8232L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode(), aiRequestHolder[0].getSourceType());
        assertEquals(902L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        assertEquals(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode(), batch.getSourceType());
        assertEquals(902L, batch.getSourceId());
        assertEquals(902L, batch.getMatchReportId());
        assertEquals(903L, batch.getSkillProfileId());
    }

    @Test
    void executeBatchFailureDoesNotPresentUntrustedWeakMatchReportInferenceAsTrustedSource()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8233L);
        batch.setUserId(USER_ID);
        batch.setMatchReportId(902L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"matchReportId\":902");

        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSuccessSkillProfileByMatchReport(902L))
                .thenThrow(new BusinessException(50000, "profile unavailable"));

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertEquals("FAILED", result.getStatus());
        assertEquals(902L, batch.getMatchReportId());
        assertEquals("profile unavailable", batch.getErrorMessage());
        assertEquals(batch.getSourceType(), result.getSourceType());
        assertEquals(batch.getSourceId(), result.getSourceId());
        verify(aiRecommendationFeignClient, never()).generate(any());
    }

    @Test
    void executeBatchRebuildsMatchReportRequestFromPreservedSkillProfileWhenCurrentMatchReportProfileIsMissing()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8216L);
        batch.setUserId(USER_ID);
        batch.setSourceId(902L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedMatchReportReplaySnapshotJson(batch.getId(), batch.getUserId(), 902L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9516L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8216L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("RESUME_JOB_MATCH", aiRequestHolder[0].getSourceType());
        assertEquals(902L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9516L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchFallsBackToAlignedCurrentMatchReportProfileWhenPreservedProfileBelongsToDifferentMatchReport()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8218L);
        batch.setUserId(USER_ID);
        batch.setSourceId(902L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedMatchReportReplaySnapshotJson(batch.getId(), batch.getUserId(), 902L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9518L);
        aiResult.setQuestions(List.of(draft));

        InnerSkillProfileVO preservedProfile = ownedProfileWithGap();
        preservedProfile.setMatchReportId(999L);
        InnerSkillProfileVO currentProfile = ownedProfileWithGap();
        currentProfile.setProfileId(904L);

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(preservedProfile));
        when(resumeProfileFeignClient.getSuccessSkillProfileByMatchReport(902L))
                .thenReturn(Result.success(currentProfile));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        invokeExecuteBatch(batch, true);

        assertNotNull(aiRequestHolder[0]);
        assertEquals(8218L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("RESUME_JOB_MATCH", aiRequestHolder[0].getSourceType());
        assertEquals(902L, aiRequestHolder[0].getSourceId());
        assertEquals(904L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(904L, batch.getSkillProfileId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9518L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchUsesPreservedSkillProfileEvidenceWhenCurrentMatchReportProfileIsMissing() throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8217L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode());
        batch.setSourceId(902L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson(minimizedMatchReportReplaySnapshotJson(batch.getId(), batch.getUserId(), 902L, 903L));

        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9517L);
        aiResult.setQuestions(List.of(draft));

        GenerateQuestionRecommendationDTO[] aiRequestHolder = new GenerateQuestionRecommendationDTO[1];
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(aiRecommendationFeignClient.generate(any())).thenAnswer(invocation -> {
            aiRequestHolder[0] = invocation.getArgument(0);
            return Result.success(aiResult);
        });

        QuestionRecommendationGenerateVO result = service.executeBatch(batch.getId(), USER_ID);

        assertEquals("SUCCESS", result.getStatus());
        assertNotNull(aiRequestHolder[0]);
        assertEquals(8217L, aiRequestHolder[0].getBatchId());
        assertEquals(USER_ID, aiRequestHolder[0].getUserId());
        assertEquals("RESUME_JOB_MATCH", aiRequestHolder[0].getSourceType());
        assertEquals(902L, aiRequestHolder[0].getSourceId());
        assertEquals(903L, aiRequestHolder[0].getSkillProfileId());
        assertEquals(2, aiRequestHolder[0].getQuestionCount());
        assertEquals("MEDIUM", aiRequestHolder[0].getDifficultyPreference());
        assertEquals("GAP_PRIORITY", aiRequestHolder[0].getStrategy());
        JsonNode resultNode = objectMapper.readTree(batch.getResultJson());
        assertEquals("MINIMIZED_METADATA", resultNode.path("storageMode").asText());
        assertEquals(9517L, resultNode.path("aiCallLogId").asLong());
    }

    @Test
    void executeBatchRejectsGapReplayWhenStoredRequestJsonIsMalformedAndGapSelectionIsUnavailable()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8214L);
        batch.setUserId(USER_ID);
        batch.setSourceId(903L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"skillProfileId\":903");

        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedStandaloneProfileWithGap()));
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        Exception ex = assertThrows(Exception.class, () -> invokeExecuteBatch(batch, true));

        assertNotNull(ex.getCause());
        assertEquals(BusinessException.class, ex.getCause().getClass());
        assertEquals("历史推荐依据快照已不足以安全恢复，请重新生成推荐题", batch.getErrorMessage());
        assertEquals("推荐题生成暂时失败：历史推荐依据快照已不足以安全恢复，请重新生成推荐题",
                ex.getCause().getMessage());
        assertEquals("FAILED", batch.getStatus());
        verify(aiRecommendationFeignClient, never()).generate(any());
    }

    @Test
    void executeBatchRejectsStudyPlanReplayWhenStoredRequestJsonIsMalformedAndGapSelectionIsUnavailable()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8228L);
        batch.setUserId(USER_ID);
        batch.setStudyPlanId(9901L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("{\"studyPlanId\":9901");

        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(batchMapper.selectById(batch.getId())).thenReturn(batch);

        Exception ex = assertThrows(Exception.class, () -> invokeExecuteBatch(batch, true));

        assertNotNull(ex.getCause());
        assertEquals(BusinessException.class, ex.getCause().getClass());
        assertEquals("历史推荐依据快照已不足以安全恢复，请重新生成推荐题", batch.getErrorMessage());
        assertEquals("推荐题生成暂时失败：历史推荐依据快照已不足以安全恢复，请重新生成推荐题",
                ex.getCause().getMessage());
        assertEquals("FAILED", batch.getStatus());
        verify(aiRecommendationFeignClient, never()).generate(any());
    }

    @Test
    void batchDetailReplacesLegacyRawResultPayloadWithMinimizedMetadata() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8301L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setStatus("SUCCESS");
        batch.setAiCallLogId(9601L);
        batch.setQuestionCount(2);
        batch.setResultJson("{\"contact\":\"secret@example.com\",\"phone\":\"13812345678\",\"notes\":\"legacy raw detail\"}");

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertNotNull(detail.getResult());
        assertEquals("MINIMIZED_METADATA", detail.getResult().path("storageMode").asText());
        assertEquals(9601L, detail.getResult().path("aiCallLogId").asLong());
        assertEquals(8301L, detail.getResult().path("batchId").asLong());
        assertEquals(2, detail.getResult().path("questionCount").asInt());
        assertFalse(detail.getResult().has("contact"));
        assertFalse(detail.getResult().has("phone"));
        assertFalse(detail.getResult().has("notes"));
    }

    @Test
    void batchDetailReplacesLegacyRichRequestPayloadWithMinimizedMetadata() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8401L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(903L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(3);
        batch.setRequestJson("""
                {
                  "sourceType":"JD_GAP",
                  "sourceId":903,
                  "targetJobJson":"{\\"jobTitle\\":\\"Java backend\\",\\"contact\\":\\"secret@example.com\\"}",
                  "skillProfileJson":"{\\"phone\\":\\"13812345678\\"}",
                  "contact":"secret@example.com",
                  "phone":"13812345678"
                }
                """);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertNotNull(detail.getRequest());
        assertEquals("MINIMIZED_METADATA", detail.getRequest().path("storageMode").asText());
        assertEquals(8401L, detail.getRequest().path("batchId").asLong());
        assertEquals(QuestionRecommendationSourceType.JD_GAP.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(903L, detail.getRequest().path("sourceId").asLong());
        assertEquals(3, detail.getRequest().path("questionCount").asInt());
        assertFalse(detail.getRequest().path("questionRecommendationRequestStored").asBoolean());
        assertFalse(detail.getRequest().has("contact"));
        assertFalse(detail.getRequest().has("phone"));
        assertFalse(detail.getRequest().has("targetJobJson"));
        assertFalse(detail.getRequest().has("skillProfileJson"));
    }

    @Test
    void batchDetailKeepsStructuredMinimizedRequestSnapshotFieldsWhenStoredSnapshotIsAlreadySafe() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8402L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(903L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(3);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("""
                {
                  "storageMode":"MINIMIZED_METADATA",
                  "questionRecommendationRequestStored":true,
                  "batchId":8402,
                  "userId":7001,
                  "sourceType":"JD_GAP",
                  "sourceId":903,
                  "matchReportId":902,
                  "questionCount":3,
                  "difficultyPreference":"HARD",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702]
                }
                """);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertNotNull(detail.getRequest());
        assertEquals("MINIMIZED_METADATA", detail.getRequest().path("storageMode").asText());
        assertEquals(8402L, detail.getRequest().path("batchId").asLong());
        assertEquals(QuestionRecommendationSourceType.JD_GAP.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(903L, detail.getRequest().path("sourceId").asLong());
        assertEquals(902L, detail.getRequest().path("matchReportId").asLong());
        assertEquals(3, detail.getRequest().path("questionCount").asInt());
        assertEquals("HARD", detail.getRequest().path("difficultyPreference").asText());
        assertEquals("GAP_PRIORITY", detail.getRequest().path("strategy").asText());
        assertEquals(903L, detail.getRequest().path("skillProfileId").asLong());
        assertTrue(detail.getRequest().path("questionRecommendationRequestStored").asBoolean());
        assertEquals(2, detail.getRequest().path("gapItemIds").size());
        assertEquals(7701L, detail.getRequest().path("gapItemIds").get(0).asLong());
        assertEquals(7702L, detail.getRequest().path("gapItemIds").get(1).asLong());
        assertFalse(detail.getRequest().has("userId"));
        assertFalse(detail.getRequest().has("targetJobJson"));
        assertFalse(detail.getRequest().has("skillProfileJson"));
        assertFalse(detail.getRequest().has("studyPlanJson"));
    }

    @Test
    void batchDetailKeepsStructuredSafeRequestSnapshotFieldsWhenLegacyStoredSnapshotHasNoIdentityMarkers() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8403L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(903L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(3);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("""
                {
                  "sourceType":"JD_GAP",
                  "sourceId":903,
                  "matchReportId":902,
                  "questionCount":3,
                  "difficultyPreference":"HARD",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702]
                }
                """);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertNotNull(detail.getRequest());
        assertEquals("MINIMIZED_METADATA", detail.getRequest().path("storageMode").asText());
        assertEquals(8403L, detail.getRequest().path("batchId").asLong());
        assertEquals(QuestionRecommendationSourceType.JD_GAP.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(903L, detail.getRequest().path("sourceId").asLong());
        assertEquals(902L, detail.getRequest().path("matchReportId").asLong());
        assertEquals(3, detail.getRequest().path("questionCount").asInt());
        assertEquals("HARD", detail.getRequest().path("difficultyPreference").asText());
        assertEquals("GAP_PRIORITY", detail.getRequest().path("strategy").asText());
        assertEquals(903L, detail.getRequest().path("skillProfileId").asLong());
        assertTrue(detail.getRequest().path("questionRecommendationRequestStored").asBoolean());
        assertEquals(2, detail.getRequest().path("gapItemIds").size());
        assertEquals(7701L, detail.getRequest().path("gapItemIds").get(0).asLong());
        assertEquals(7702L, detail.getRequest().path("gapItemIds").get(1).asLong());
    }

    @Test
    void batchDetailFallsBackWhenStructuredMinimizedSnapshotIdentityConflictsWithBatchOwnership() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8404L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(903L);
        batch.setMatchReportId(902L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(3);
        batch.setStrategy("GAP_PRIORITY");
        batch.setRequestJson("""
                {
                  "storageMode":"MINIMIZED_METADATA",
                  "questionRecommendationRequestStored":true,
                  "batchId":9999,
                  "userId":9999,
                  "sourceType":"JD_GAP",
                  "sourceId":903,
                  "matchReportId":902,
                  "questionCount":3,
                  "difficultyPreference":"HARD",
                  "strategy":"GAP_PRIORITY",
                  "skillProfileId":903,
                  "gapItemIds":[7701,7702]
                }
                """);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertNotNull(detail.getRequest());
        assertEquals("MINIMIZED_METADATA", detail.getRequest().path("storageMode").asText());
        assertEquals(8404L, detail.getRequest().path("batchId").asLong());
        assertEquals(QuestionRecommendationSourceType.JD_GAP.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(903L, detail.getRequest().path("sourceId").asLong());
        assertEquals(902L, detail.getRequest().path("matchReportId").asLong());
        assertEquals(3, detail.getRequest().path("questionCount").asInt());
        assertEquals("GAP_PRIORITY", detail.getRequest().path("strategy").asText());
        assertFalse(detail.getRequest().path("questionRecommendationRequestStored").asBoolean());
        assertFalse(detail.getRequest().has("gapItemIds"));
        assertFalse(detail.getRequest().has("difficultyPreference"));
        assertFalse(detail.getRequest().has("userId"));
    }

    @Test
    void batchDetailUsesInferredTrustedSourceMetadataWhenFallbackRequestSnapshotBuildsFromLegacyBatch() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8405L);
        batch.setUserId(USER_ID);
        batch.setSourceType("LEGACY_PLAN");
        batch.setStudyPlanId(9901L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStatus("SUCCESS");
        batch.setRequestJson("{\"studyPlanId\":9901");

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(), detail.getSourceType());
        assertEquals(9901L, detail.getSourceId());
        assertNotNull(detail.getRequest());
        assertEquals("MINIMIZED_METADATA", detail.getRequest().path("storageMode").asText());
        assertEquals(8405L, detail.getRequest().path("batchId").asLong());
        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(9901L, detail.getRequest().path("sourceId").asLong());
        assertEquals(2, detail.getRequest().path("questionCount").asInt());
        assertEquals(903L, detail.getRequest().path("skillProfileId").asLong());
        assertEquals(9901L, detail.getRequest().path("studyPlanId").asLong());
        assertFalse(detail.getRequest().path("questionRecommendationRequestStored").asBoolean());
    }

    @Test
    void batchDetailPreservesTrustedPresentationSourceMetadataWhenSafeSnapshotStillCarriesLegacyTokens() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8406L);
        batch.setUserId(USER_ID);
        batch.setSourceType("LEGACY_PLAN");
        batch.setStudyPlanId(9901L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("PLAN_PRIORITY");
        batch.setRequestJson("""
                {
                  "storageMode":"MINIMIZED_METADATA",
                  "questionRecommendationRequestStored":true,
                  "batchId":8406,
                  "userId":7001,
                  "sourceType":"LEGACY_PLAN",
                  "sourceId":903,
                  "questionCount":2,
                  "strategy":"PLAN_PRIORITY",
                  "skillProfileId":903,
                  "studyPlanId":9901
                }
                """);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(), detail.getSourceType());
        assertEquals(9901L, detail.getSourceId());
        assertNotNull(detail.getRequest());
        assertEquals("MINIMIZED_METADATA", detail.getRequest().path("storageMode").asText());
        assertEquals(8406L, detail.getRequest().path("batchId").asLong());
        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(9901L, detail.getRequest().path("sourceId").asLong());
        assertEquals("PLAN_PRIORITY", detail.getRequest().path("strategy").asText());
        assertEquals(903L, detail.getRequest().path("skillProfileId").asLong());
        assertEquals(9901L, detail.getRequest().path("studyPlanId").asLong());
        assertTrue(detail.getRequest().path("questionRecommendationRequestStored").asBoolean());
        assertFalse(detail.getRequest().has("userId"));
    }

    @Test
    void batchDetailPrefersStudyPlanPresentationWhenValidStoredSourceTypeTokenIsStale() {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8407L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSourceId(903L);
        batch.setStudyPlanId(9901L);
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("PLAN_PRIORITY");
        batch.setStatus("SUCCESS");
        batch.setRequestJson("""
                {
                  "storageMode":"MINIMIZED_METADATA",
                  "questionRecommendationRequestStored":true,
                  "batchId":8407,
                  "userId":7001,
                  "sourceType":"JD_GAP",
                  "sourceId":903,
                  "questionCount":2,
                  "strategy":"PLAN_PRIORITY",
                  "skillProfileId":903,
                  "studyPlanId":9901
                }
                """);

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(studyPlanFeignClient.getStudyPlan(9901L)).thenReturn(Result.success(ownedStudyPlan("ACTIVE", 903L)));
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(), detail.getSourceType());
        assertEquals(9901L, detail.getSourceId());
        assertNotNull(detail.getRequest());
        assertEquals(QuestionRecommendationSourceType.STUDY_PLAN.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(9901L, detail.getRequest().path("sourceId").asLong());
        assertEquals(9901L, detail.getRequest().path("studyPlanId").asLong());
        assertEquals(903L, detail.getRequest().path("skillProfileId").asLong());
    }

    @Test
    void batchDetailPrefersStoredResumeJobMatchPresentationWhenWeakGapTokenIsUntrustedButStoredReplayIsTrusted()
            throws Exception {
        QuestionRecommendationBatch batch = new QuestionRecommendationBatch();
        batch.setId(8408L);
        batch.setUserId(USER_ID);
        batch.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        batch.setSkillProfileId(903L);
        batch.setQuestionCount(2);
        batch.setStrategy("GAP_PRIORITY");
        batch.setStatus("SUCCESS");
        batch.setRequestJson(minimizedMatchReportReplaySnapshotJson(batch.getId(), batch.getUserId(), 902L, 903L));

        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        QuestionRecommendationBatchDetailVO detail = service.batchDetail(batch.getId());

        assertEquals(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode(), detail.getSourceType());
        assertEquals(902L, detail.getSourceId());
        assertNotNull(detail.getRequest());
        assertEquals("MINIMIZED_METADATA", detail.getRequest().path("storageMode").asText());
        assertEquals(QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode(),
                detail.getRequest().path("sourceType").asText());
        assertEquals(902L, detail.getRequest().path("sourceId").asLong());
        assertEquals(902L, detail.getRequest().path("matchReportId").asLong());
        assertEquals(903L, detail.getRequest().path("skillProfileId").asLong());
        assertTrue(detail.getRequest().path("questionRecommendationRequestStored").asBoolean());
    }

    @Test
    void generateFromGapKeepsMinimizedRequestSnapshotWhileExecutingBatch() throws Exception {
        QuestionRecommendationGenerateFromGapDTO dto = new QuestionRecommendationGenerateFromGapDTO();
        dto.setSkillProfileId(903L);
        dto.setGapItemIds(List.of(4501L));
        dto.setQuestionCount(1);
        dto.setDifficultyPreference("HARD");
        dto.setStrategy("GAP_PRIORITY");

        InnerSkillProfileVO profile = ownedProfileWithGap();
        QuestionRecommendationDraftItemVO draft = new QuestionRecommendationDraftItemVO();
        draft.setTitle("Explain Redis cache consistency");
        draft.setContent("Compare delayed double delete and binlog repair.");
        GenerateQuestionRecommendationVO aiResult = new GenerateQuestionRecommendationVO();
        aiResult.setAiCallLogId(9701L);
        aiResult.setQuestions(List.of(draft));

        QuestionRecommendationBatch[] holder = new QuestionRecommendationBatch[1];
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(profile));
        when(batchMapper.insert(any(QuestionRecommendationBatch.class))).thenAnswer(invocation -> {
            QuestionRecommendationBatch batch = invocation.getArgument(0);
            batch.setId(8501L);
            holder[0] = batch;
            return 1;
        });
        when(batchMapper.selectById(8501L)).thenAnswer(invocation -> holder[0]);
        when(aiRecommendationFeignClient.generate(any())).thenReturn(Result.success(aiResult));

        QuestionRecommendationGenerateVO result = service.generateFromGap(dto);

        assertEquals("SUCCESS", result.getStatus());
        assertNotNull(holder[0]);
        JsonNode storedRequest = objectMapper.readTree(holder[0].getRequestJson());
        assertEquals("MINIMIZED_METADATA", storedRequest.path("storageMode").asText());
        assertEquals(8501L, storedRequest.path("batchId").asLong());
        assertEquals(USER_ID, storedRequest.path("userId").asLong());
        assertEquals("JD_GAP", storedRequest.path("sourceType").asText());
        assertEquals(903L, storedRequest.path("sourceId").asLong());
        assertEquals(902L, storedRequest.path("matchReportId").asLong());
        assertEquals(1, storedRequest.path("questionCount").asInt());
        assertEquals("HARD", storedRequest.path("difficultyPreference").asText());
        assertEquals("GAP_PRIORITY", storedRequest.path("strategy").asText());
        assertEquals(903L, storedRequest.path("skillProfileId").asLong());
        assertEquals(901L, storedRequest.path("targetJobId").asLong());
        assertTrue(storedRequest.path("questionRecommendationRequestStored").asBoolean());
        assertEquals(1, storedRequest.path("gapItemIds").size());
        assertEquals(4501L, storedRequest.path("gapItemIds").get(0).asLong());
        assertFalse(storedRequest.has("targetJobJson"));
        assertFalse(storedRequest.has("skillProfileJson"));
        assertFalse(storedRequest.has("skillGapsJson"));
        assertFalse(holder[0].getRequestJson().contains("secret@example.com"));
        assertFalse(holder[0].getRequestJson().contains("13812345678"));
    }

    @Test
    void submitFromGapKeepsMinimizedRequestSnapshotBeforeDispatch() throws Exception {
        QuestionRecommendationServiceImpl asyncService = new QuestionRecommendationServiceImpl(
                batchMapper,
                itemMapper,
                questionMapper,
                resumeProfileFeignClient,
                studyPlanFeignClient,
                aiRecommendationFeignClient,
                objectMapper,
                Optional.of(questionMqDispatcher));

        QuestionRecommendationGenerateFromGapDTO dto = new QuestionRecommendationGenerateFromGapDTO();
        dto.setSkillProfileId(903L);
        dto.setGapItemIds(List.of(4501L));
        dto.setQuestionCount(2);
        dto.setDifficultyPreference("MEDIUM");
        dto.setStrategy("GAP_PRIORITY");

        QuestionRecommendationBatch[] holder = new QuestionRecommendationBatch[1];
        when(resumeProfileFeignClient.getSkillProfile(903L)).thenReturn(Result.success(ownedProfileWithGap()));
        when(batchMapper.insert(any(QuestionRecommendationBatch.class))).thenAnswer(invocation -> {
            QuestionRecommendationBatch batch = invocation.getArgument(0);
            batch.setId(8601L);
            holder[0] = batch;
            return 1;
        });
        when(batchMapper.selectById(8601L)).thenAnswer(invocation -> holder[0]);
        when(questionMqDispatcher.dispatchRecommendationGenerateWithReceipt(8601L, USER_ID))
                .thenReturn(MqDispatchReceipt.builder()
                        .messageId("msg-8601")
                        .traceId("trace-8601")
                        .bizType("QUESTION_RECOMMENDATION_GENERATE")
                        .bizId("8601")
                        .sendStatus("SENT")
                        .build());

        QuestionRecommendationGenerateVO result = asyncService.submitFromGap(dto);

        assertEquals("GENERATING", result.getStatus());
        assertEquals("msg-8601", result.getAsyncMessageId());
        assertNotNull(holder[0]);
        JsonNode storedRequest = objectMapper.readTree(holder[0].getRequestJson());
        assertEquals("MINIMIZED_METADATA", storedRequest.path("storageMode").asText());
        assertEquals(8601L, storedRequest.path("batchId").asLong());
        assertEquals(USER_ID, storedRequest.path("userId").asLong());
        assertEquals("JD_GAP", storedRequest.path("sourceType").asText());
        assertEquals(903L, storedRequest.path("sourceId").asLong());
        assertEquals(902L, storedRequest.path("matchReportId").asLong());
        assertEquals(2, storedRequest.path("questionCount").asInt());
        assertEquals("MEDIUM", storedRequest.path("difficultyPreference").asText());
        assertEquals("GAP_PRIORITY", storedRequest.path("strategy").asText());
        assertEquals(903L, storedRequest.path("skillProfileId").asLong());
        assertEquals(901L, storedRequest.path("targetJobId").asLong());
        assertTrue(storedRequest.path("questionRecommendationRequestStored").asBoolean());
        assertEquals(1, storedRequest.path("gapItemIds").size());
        assertEquals(4501L, storedRequest.path("gapItemIds").get(0).asLong());
        assertFalse(storedRequest.has("targetJobJson"));
        assertFalse(storedRequest.has("skillProfileJson"));
        assertFalse(storedRequest.has("skillGapsJson"));
        assertFalse(holder[0].getRequestJson().contains("secret@example.com"));
        assertFalse(holder[0].getRequestJson().contains("13812345678"));
    }

    private Object newRecommendationRequest(InnerSkillProfileVO profile, List<InnerSkillGapItemVO> gaps)
            throws Exception {
        Class<?> requestClass = recommendationRequestClass();
        Constructor<?> constructor = requestClass.getDeclaredConstructor(
                QuestionRecommendationSourceType.class,
                Long.class,
                InnerSkillProfileVO.class,
                List.class,
                Class.forName("com.codecoachai.question.feign.vo.InnerStudyPlanVO"),
                Integer.class,
                String.class,
                String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                QuestionRecommendationSourceType.JD_GAP,
                3201L,
                profile,
                gaps,
                null,
                5,
                "MEDIUM",
                "GAP_PRIORITY");
    }

    private GenerateQuestionRecommendationDTO invokeBuildAiRequest(
            QuestionRecommendationBatch batch,
            Object request,
            Long userId) throws Exception {
        Method method = QuestionRecommendationServiceImpl.class.getDeclaredMethod(
                "buildAiRequest",
                QuestionRecommendationBatch.class,
                recommendationRequestClass(),
                Long.class);
        method.setAccessible(true);
        return (GenerateQuestionRecommendationDTO) method.invoke(service, batch, request, userId);
    }

    private Object invokeExecuteBatch(QuestionRecommendationBatch batch, boolean failFast) throws Exception {
        Method method = QuestionRecommendationServiceImpl.class.getDeclaredMethod(
                "executeBatch",
                QuestionRecommendationBatch.class,
                boolean.class);
        method.setAccessible(true);
        return method.invoke(service, batch, failFast);
    }

    private String validAiRequestJson(QuestionRecommendationBatch batch) throws Exception {
        GenerateQuestionRecommendationDTO dto = new GenerateQuestionRecommendationDTO();
        dto.setBatchId(batch.getId());
        dto.setUserId(batch.getUserId());
        dto.setSourceType(batch.getSourceType());
        dto.setQuestionCount(batch.getQuestionCount());
        return objectMapper.writeValueAsString(dto);
    }

    private String legacyStudyPlanAiRequestJson(Long batchId, Long userId, Long planId, Long profileId)
            throws Exception {
        GenerateQuestionRecommendationDTO dto = new GenerateQuestionRecommendationDTO();
        dto.setBatchId(batchId);
        dto.setUserId(userId);
        dto.setSourceType(QuestionRecommendationSourceType.STUDY_PLAN.getCode());
        dto.setSourceId(planId);
        dto.setStudyPlanId(planId);
        dto.setSkillProfileId(profileId);
        dto.setQuestionCount(2);
        dto.setDifficultyPreference("MEDIUM");
        dto.setStrategy("GAP_PRIORITY");
        dto.setTargetJobJson("{\"jobTitle\":\"Java backend\"}");
        dto.setSkillProfileJson("{\"profileId\":903}");
        dto.setStudyPlanJson("{\"planId\":9901}");
        return objectMapper.writeValueAsString(dto);
    }

    private String minimizedStudyPlanReplaySnapshotJson(Long batchId, Long userId, Long planId, Long profileId)
            throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceType", QuestionRecommendationSourceType.STUDY_PLAN.getCode());
        snapshot.put("sourceId", planId);
        snapshot.put("matchReportId", 902L);
        snapshot.put("questionCount", 2);
        snapshot.put("difficultyPreference", "MEDIUM");
        snapshot.put("strategy", "GAP_PRIORITY");
        snapshot.put("skillProfileId", profileId);
        snapshot.put("gapItemIds", List.of(4501L));
        snapshot.put("studyPlanId", planId);
        return objectMapper.writeValueAsString(
                QuestionRecommendationRequestPayloadUtils.withStoredRequestMarker(snapshot, batchId, userId));
    }

    private String minimizedMatchReportReplaySnapshotJson(Long batchId, Long userId, Long matchReportId, Long profileId)
            throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceType", QuestionRecommendationSourceType.RESUME_JOB_MATCH.getCode());
        snapshot.put("sourceId", matchReportId);
        snapshot.put("matchReportId", matchReportId);
        snapshot.put("questionCount", 2);
        snapshot.put("difficultyPreference", "MEDIUM");
        snapshot.put("strategy", "GAP_PRIORITY");
        snapshot.put("skillProfileId", profileId);
        snapshot.put("gapItemIds", List.of(4501L));
        return objectMapper.writeValueAsString(
                QuestionRecommendationRequestPayloadUtils.withStoredRequestMarker(snapshot, batchId, userId));
    }

    private String minimizedMatchReportReplaySnapshotWithoutSourceIdentityJson(Long batchId, Long userId,
                                                                              Long matchReportId,
                                                                              Long profileId)
            throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("matchReportId", matchReportId);
        snapshot.put("questionCount", 2);
        snapshot.put("difficultyPreference", "MEDIUM");
        snapshot.put("strategy", "GAP_PRIORITY");
        snapshot.put("skillProfileId", profileId);
        snapshot.put("gapItemIds", List.of(4501L));
        return objectMapper.writeValueAsString(
                QuestionRecommendationRequestPayloadUtils.withStoredRequestMarker(snapshot, batchId, userId));
    }

    private String minimizedMatchReportReplaySnapshotWithoutSourceAndProfileIdentityJson(Long batchId, Long userId,
                                                                                        Long matchReportId)
            throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("matchReportId", matchReportId);
        snapshot.put("questionCount", 2);
        snapshot.put("difficultyPreference", "MEDIUM");
        snapshot.put("strategy", "GAP_PRIORITY");
        snapshot.put("gapItemIds", List.of(4501L));
        return objectMapper.writeValueAsString(
                QuestionRecommendationRequestPayloadUtils.withStoredRequestMarker(snapshot, batchId, userId));
    }

    private InnerSkillProfileVO ownedProfileWithGap() {
        InnerSkillProfileVO profile = new InnerSkillProfileVO();
        profile.setProfileId(903L);
        profile.setUserId(USER_ID);
        profile.setTargetJobId(901L);
        profile.setMatchReportId(902L);
        profile.setProfileName("Java backend readiness");
        profile.setOverallLevel(4);
        profile.setOverallScore(87);
        profile.setSummary("Focus on Redis consistency and transaction boundaries.");
        profile.setStatus("SUCCESS");
        profile.setRawResultJson("{\"contact\":\"secret@example.com\",\"phone\":\"13812345678\"}");

        InnerSkillGapItemVO gap = new InnerSkillGapItemVO();
        gap.setId(4501L);
        gap.setSkillName("Redis");
        gap.setCategory("BACKEND");
        gap.setTargetLevel(5);
        gap.setCurrentLevel(3);
        gap.setGapLevel(2);
        gap.setSeverity("HIGH");
        profile.setGapItems(List.of(gap));
        return profile;
    }

    private InnerSkillProfileVO ownedStandaloneProfileWithGap() {
        InnerSkillProfileVO profile = ownedProfileWithGap();
        profile.setMatchReportId(null);
        return profile;
    }

    private InnerStudyPlanVO ownedStudyPlan() {
        return ownedStudyPlan("ACTIVE");
    }

    private InnerStudyPlanVO ownedStudyPlan(String planStatus) {
        return ownedStudyPlan(planStatus, 903L);
    }

    private InnerStudyPlanVO ownedStudyPlan(String planStatus, Long skillProfileId) {
        InnerStudyPlanSkillRelationVO relation = new InnerStudyPlanSkillRelationVO();
        relation.setSkillGapItemId(4501L);

        InnerStudyPlanVO plan = new InnerStudyPlanVO();
        plan.setPlanId(9901L);
        plan.setUserId(USER_ID);
        plan.setSourceType(QuestionRecommendationSourceType.JD_GAP.getCode());
        plan.setSourceId(903L);
        plan.setTargetJobId(901L);
        plan.setSkillProfileId(skillProfileId);
        plan.setMatchReportId(902L);
        plan.setPlanTitle("Redis consistency repair plan");
        plan.setPlanSummary("Focus on caching, binlog, and eventual consistency drills.");
        plan.setPlanStatus(planStatus);
        plan.setDurationDays(14);
        plan.setDailyMinutes(45);
        plan.setSkillRelations(List.of(relation));
        return plan;
    }

    private Class<?> recommendationRequestClass() {
        for (Class<?> declaredClass : QuestionRecommendationServiceImpl.class.getDeclaredClasses()) {
            if ("RecommendationRequest".equals(declaredClass.getSimpleName())) {
                return declaredClass;
            }
        }
        throw new IllegalStateException("RecommendationRequest not found");
    }
}
