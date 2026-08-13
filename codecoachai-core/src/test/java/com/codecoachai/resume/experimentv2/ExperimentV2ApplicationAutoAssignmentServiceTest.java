package com.codecoachai.resume.experimentv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AssignmentCreate;
import com.codecoachai.resume.experimentv2.entity.ExperimentHypothesis;
import com.codecoachai.resume.experimentv2.entity.ExperimentVariant;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentHypothesisMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentVariantMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentV2ApplicationAutoAssignmentServiceTest {

    private static final long USER_ID = 10L;

    @Mock
    private ExperimentHypothesisMapper hypothesisMapper;
    @Mock
    private ExperimentVariantMapper variantMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private ExperimentV2Service experimentV2Service;

    private ExperimentV2ApplicationAutoAssignmentService service;

    @BeforeEach
    void setUp() {
        initTableInfo(ExperimentHypothesis.class);
        initTableInfo(ExperimentVariant.class);
        initTableInfo(ResumeVersion.class);
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("auto-assignment-user")
                .build());
        service = new ExperimentV2ApplicationAutoAssignmentService(
                hypothesisMapper,
                variantMapper,
                resumeVersionMapper,
                experimentV2Service,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void exactTargetJobMatchSelectsLatestHypothesisAndDelegatesStableAssignment() {
        ExperimentHypothesis older = hypothesis(7L, LocalDateTime.of(2026, 7, 1, 10, 0));
        ExperimentHypothesis latest = hypothesis(8L, LocalDateTime.of(2026, 7, 2, 10, 0));
        ExperimentHypothesis latestWithHigherId = hypothesis(9L, LocalDateTime.of(2026, 7, 2, 10, 0));
        when(hypothesisMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(older, latest, latestWithHigherId));
        when(variantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                variant(71L, 7L, "{\"targetJobIds\":[88]}"),
                variant(81L, 8L, "{\"targetJobIds\":[88]}"),
                variant(91L, 9L, "{\"targetJobIds\":[88]}")));
        JobApplication application = application(USER_ID);
        application.setTargetJobId(88L);
        application.setJobTitle("Backend Engineer");
        application.setSource("LinkedIn");

        service.autoAssign(application);

        ArgumentCaptor<AssignmentCreate> requestCaptor = ArgumentCaptor.forClass(AssignmentCreate.class);
        verify(experimentV2Service).assign(org.mockito.ArgumentMatchers.eq(9L), requestCaptor.capture());
        AssignmentCreate request = requestCaptor.getValue();
        assertEquals(41L, request.getApplicationId());
        assertEquals("Backend Engineer", request.getJobFamily());
        assertEquals("LinkedIn", request.getChannel());
        assertNull(request.getVariantId());

        ArgumentCaptor<LambdaQueryWrapper<ExperimentHypothesis>> hypothesisQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(hypothesisMapper).selectList(hypothesisQueryCaptor.capture());
        String hypothesisSql = hypothesisQueryCaptor.getValue().getSqlSegment();
        assertTrue(hypothesisSql.contains("user_id"), hypothesisSql);
        assertTrue(hypothesisSql.contains("status"), hypothesisSql);
    }

    @Test
    void exactResumeMatchDelegatesAssignment() {
        when(hypothesisMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(hypothesis(7L, LocalDateTime.of(2026, 7, 1, 10, 0))));
        when(variantMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(variant(71L, 7L, "{\"resumeIds\":[5]}")));
        ResumeVersion version = new ResumeVersion();
        version.setId(77L);
        version.setUserId(USER_ID);
        version.setResumeId(5L);
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(version);
        JobApplication application = application(USER_ID);
        application.setResumeVersionId(77L);

        service.autoAssign(application);

        verify(experimentV2Service).assign(org.mockito.ArgumentMatchers.eq(7L), any(AssignmentCreate.class));
        ArgumentCaptor<LambdaQueryWrapper<ResumeVersion>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(resumeVersionMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("user_id"));
    }

    @Test
    void noExactMatchDoesNotForceAssignment() {
        when(hypothesisMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(hypothesis(7L, LocalDateTime.of(2026, 7, 1, 10, 0))));
        when(variantMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(variant(71L, 7L, "{\"targetJobIds\":[99],\"resumeIds\":[6]}")));
        ResumeVersion version = new ResumeVersion();
        version.setId(77L);
        version.setUserId(USER_ID);
        version.setResumeId(5L);
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(version);
        JobApplication application = application(USER_ID);
        application.setTargetJobId(88L);
        application.setResumeVersionId(77L);

        service.autoAssign(application);

        verify(experimentV2Service, never()).assign(any(), any());
    }

    @Test
    void otherUsersApplicationIsIgnored() {
        service.autoAssign(application(20L));

        verifyNoInteractions(hypothesisMapper, variantMapper, resumeVersionMapper, experimentV2Service);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    private ExperimentHypothesis hypothesis(Long id, LocalDateTime updatedAt) {
        ExperimentHypothesis hypothesis = new ExperimentHypothesis();
        hypothesis.setId(id);
        hypothesis.setUserId(USER_ID);
        hypothesis.setStatus("RUNNING");
        hypothesis.setUpdatedAt(updatedAt);
        return hypothesis;
    }

    private ExperimentVariant variant(Long id, Long hypothesisId, String treatmentJson) {
        ExperimentVariant variant = new ExperimentVariant();
        variant.setId(id);
        variant.setUserId(USER_ID);
        variant.setHypothesisId(hypothesisId);
        variant.setTreatmentJson(treatmentJson);
        return variant;
    }

    private JobApplication application(Long userId) {
        JobApplication application = new JobApplication();
        application.setId(41L);
        application.setUserId(userId);
        return application;
    }
}
