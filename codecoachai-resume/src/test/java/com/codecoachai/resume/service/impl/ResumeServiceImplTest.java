package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeOptimizeRecord;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordAgentEvidenceVO;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import com.codecoachai.resume.export.ResumeUploadAdmissionGuard;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeOptimizeRecordMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.ResumeMqDispatcher;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long RESUME_ID = 100L;
    private static final long TARGET_JOB_ID = 501L;
    private static final long OPTIMIZE_RECORD_ID = 7001L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 18, 9, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 6, 18, 9, 10);

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper projectMapper;
    @Mock
    private ResumeAnalysisRecordMapper analysisRecordMapper;
    @Mock
    private ResumeOptimizeRecordMapper optimizeRecordMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private FileFeignClient fileFeignClient;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ResumeMqDispatcher resumeMqDispatcher;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;
    @Mock
    private ResumeSearchSyncOutboxService resumeSearchSyncOutboxService;
    @Mock
    private ResumeUploadAdmissionGuard uploadAdmissionGuard;

    private ResumeServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(Resume.class);
        initTableInfo(ResumeProject.class);
        initTableInfo(ResumeAnalysisRecord.class);
        initTableInfo(ResumeOptimizeRecord.class);
        initTableInfo(TargetJob.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("resume-user").build());
        service = new ResumeServiceImpl(
                resumeMapper,
                projectMapper,
                analysisRecordMapper,
                optimizeRecordMapper,
                targetJobMapper,
                fileFeignClient,
                aiFeignClient,
                new ObjectMapper(),
                transactionTemplate,
                Optional.of(resumeMqDispatcher),
                agentBusinessActionNotifier,
                new ResumeTextExtractProperties(),
                resumeSearchSyncOutboxService,
                uploadAdmissionGuard);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void getResumeReturnsResourceNotFoundForMissingResume() {
        when(resumeMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResume(RESUME_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Resume>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(resumeMapper).selectOne(wrapperCaptor.capture());
        assertReadQueryById(wrapperCaptor.getValue(), RESUME_ID);
    }

    @Test
    void getResumeReturnsForbiddenForForeignResume() {
        Resume foreignResume = ownedResume();
        foreignResume.setUserId(USER_ID + 1);
        when(resumeMapper.selectOne(any())).thenReturn(foreignResume);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getResume(RESUME_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    void getResumeReturnsOwnedResume() {
        Resume resume = ownedResume();
        when(resumeMapper.selectOne(any())).thenReturn(resume);

        assertEquals(RESUME_ID, service.getResume(RESUME_ID).getId());
    }

    @Test
    void missingOrForeignResumeMutationsRemainParameterErrors() {
        when(resumeMapper.selectOne(any())).thenReturn(null);
        ResumeSaveDTO resume = new ResumeSaveDTO();
        ResumeProjectSaveDTO project = new ResumeProjectSaveDTO();
        project.setProjectName("CodeCoachAI");

        assertParamError(() -> service.updateResume(RESUME_ID, resume));
        assertParamError(() -> service.deleteResume(RESUME_ID));
        assertParamError(() -> service.setDefault(RESUME_ID));
        assertParamError(() -> service.createProject(RESUME_ID, project));
        assertParamError(() -> service.updateProject(RESUME_ID, 200L, project));
        assertParamError(() -> service.deleteProject(RESUME_ID, 200L));
    }

    @Test
    void getOptimizeRecordEvidenceReturnsOwnedSuccessfulTargetJobScopedRecord() {
        when(optimizeRecordMapper.selectOne(any())).thenReturn(successRecord());

        ResumeOptimizeRecordAgentEvidenceVO evidence =
                service.getOptimizeRecordEvidence(USER_ID, OPTIMIZE_RECORD_ID);

        assertEquals(OPTIMIZE_RECORD_ID, evidence.getId());
        assertEquals(USER_ID, evidence.getUserId());
        assertEquals(RESUME_ID, evidence.getResumeId());
        assertEquals(TARGET_JOB_ID, evidence.getTargetJobId());
        assertEquals(ResumeOptimizeStatus.SUCCESS.getCode(), evidence.getStatus());
        assertEquals(UPDATED_AT, evidence.getOptimizedAt());
        assertEquals(CREATED_AT, evidence.getCreatedAt());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeOptimizeRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verifySelectWrapper(wrapperCaptor);
    }

    @Test
    void getOptimizeRecordEvidenceRejectsMissingOrUnsuccessfulRecord() {
        when(optimizeRecordMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.getOptimizeRecordEvidence(USER_ID, OPTIMIZE_RECORD_ID));
    }

    @Test
    void validateUploadFileRejectsOversizedResume() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("resume.pdf");
        when(file.getSize()).thenReturn(10L * 1024L * 1024L + 1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> invokePrivate("validateUploadFile", new Class<?>[]{MultipartFile.class}, file));

        assertTrue(exception.getMessage().contains("10MB"));
    }

    @Test
    void uploadResumeUsesSharedAdmissionGuardBeforeFeignUpload() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("resume.pdf");
        when(file.getSize()).thenReturn(4L);
        InnerFileUploadVO uploaded = new InnerFileUploadVO();
        uploaded.setFileId(88L);
        when(uploadAdmissionGuard.execute(anyLong(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        when(fileFeignClient.upload(file, "RESUME", USER_ID)).thenReturn(Result.success(uploaded));

        service.uploadResume(file);

        verify(uploadAdmissionGuard).execute(org.mockito.ArgumentMatchers.eq(4L), any());
        verify(fileFeignClient).upload(file, "RESUME", USER_ID);
    }

    @Test
    void deleteResumeLocksParentBeforeDeletingProjectsAndResume() {
        Resume resume = ownedResume();
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.lockOwnedResume(RESUME_ID, USER_ID)).thenReturn(RESUME_ID);

        service.deleteResume(RESUME_ID);

        InOrder order = inOrder(resumeMapper, projectMapper);
        order.verify(resumeMapper).selectOne(any());
        order.verify(resumeMapper).lockOwnedResume(RESUME_ID, USER_ID);
        order.verify(projectMapper).delete(any());
        order.verify(resumeMapper).deleteById(RESUME_ID);
    }

    @Test
    void createProjectLocksParentBeforeProjectInsert() {
        Resume resume = ownedResume();
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.lockOwnedResume(RESUME_ID, USER_ID)).thenReturn(RESUME_ID);
        ResumeProjectSaveDTO dto = projectDto();

        service.createProject(RESUME_ID, dto);

        InOrder order = inOrder(resumeMapper, projectMapper);
        order.verify(resumeMapper).lockOwnedResume(RESUME_ID, USER_ID);
        order.verify(projectMapper).insert(any(ResumeProject.class));
    }

    @Test
    void resumeScopedProjectUpdateLocksParentBeforeReadingAndUpdatingProject() {
        Resume resume = ownedResume();
        ResumeProject project = ownedProject();
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.lockOwnedResume(RESUME_ID, USER_ID)).thenReturn(RESUME_ID);
        when(projectMapper.selectById(200L)).thenReturn(project);

        service.updateProject(RESUME_ID, 200L, projectDto());

        InOrder order = inOrder(resumeMapper, projectMapper);
        order.verify(resumeMapper).lockOwnedResume(RESUME_ID, USER_ID);
        order.verify(projectMapper).selectById(200L);
        order.verify(projectMapper).updateById(project);
    }

    @Test
    void projectIdOnlyDeleteLocksParentBeforeDeletingProject() {
        Resume resume = ownedResume();
        ResumeProject project = ownedProject();
        when(projectMapper.selectById(200L)).thenReturn(project);
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.lockOwnedResume(RESUME_ID, USER_ID)).thenReturn(RESUME_ID);

        service.deleteProject(200L);

        InOrder order = inOrder(resumeMapper, projectMapper);
        order.verify(resumeMapper).lockOwnedResume(RESUME_ID, USER_ID);
        order.verify(projectMapper).deleteById(200L);
    }

    @Test
    void applyProjectBackfillsCanonicalProjectBackgroundFromLegacyDescription() {
        ResumeProjectSaveDTO dto = new ResumeProjectSaveDTO();
        dto.setProjectName("CodeCoachAI");
        dto.setDescription("legacy project description");
        ResumeProject project = new ResumeProject();

        invokePrivate("applyProject",
                new Class<?>[]{ResumeProject.class, ResumeProjectSaveDTO.class},
                project, dto);

        assertEquals("legacy project description", project.getProjectBackground());
        assertEquals("legacy project description", project.getDescription());
    }

    private Resume ownedResume() {
        Resume resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);
        return resume;
    }

    private ResumeProject ownedProject() {
        ResumeProject project = new ResumeProject();
        project.setId(200L);
        project.setResumeId(RESUME_ID);
        project.setDeleted(0);
        return project;
    }

    private ResumeProjectSaveDTO projectDto() {
        ResumeProjectSaveDTO dto = new ResumeProjectSaveDTO();
        dto.setProjectName("CodeCoachAI");
        return dto;
    }

    private void verifySelectWrapper(ArgumentCaptor<Wrapper<ResumeOptimizeRecord>> wrapperCaptor) {
        org.mockito.Mockito.verify(optimizeRecordMapper).selectOne(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("optimize_status"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    private void assertReadQueryById(Wrapper<?> wrapper, Long resourceId) {
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("id"));
        assertTrue(sql.contains("deleted"));
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> query) {
            query.getSqlSegment();
            var values = query.getParamNameValuePairs().values();
            assertTrue(values.contains(resourceId));
            assertTrue(values.contains(0));
        }
    }

    private void assertParamError(org.junit.jupiter.api.function.Executable action) {
        BusinessException exception = assertThrows(BusinessException.class, action);
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    private void invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = ResumeServiceImpl.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(service, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private ResumeOptimizeRecord successRecord() {
        ResumeOptimizeRecord record = new ResumeOptimizeRecord();
        record.setId(OPTIMIZE_RECORD_ID);
        record.setUserId(USER_ID);
        record.setResumeId(RESUME_ID);
        record.setTargetJobId(TARGET_JOB_ID);
        record.setOptimizeStatus(ResumeOptimizeStatus.SUCCESS.getCode());
        record.setCreatedAt(CREATED_AT);
        record.setUpdatedAt(UPDATED_AT);
        return record;
    }
}
