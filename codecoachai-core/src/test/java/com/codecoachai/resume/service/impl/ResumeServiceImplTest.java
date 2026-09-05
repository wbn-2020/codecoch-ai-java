package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.domain.vo.ResumeUploadDecisionVO;
import com.codecoachai.file.mapper.FileInfoMapper;
import com.codecoachai.file.service.FileStorageService;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeOptimizeRecord;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordAgentEvidenceVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.config.ResumeTextExtractProperties;
import com.codecoachai.resume.export.ResumeUploadAdmissionGuard;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeOptimizeRecordMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.ResumeMqDispatcher;
import com.codecoachai.resume.service.ResumeAggregateInitializationService;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.codecoachai.resume.service.support.ResumeImportNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
    private FileInfoMapper fileInfoMapper;
    @Mock
    private FileStorageService fileStorageService;
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
    @Mock
    private ResumeAggregateInitializationService aggregateInitializationService;

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
                fileInfoMapper,
                fileStorageService,
                fileFeignClient,
                aiFeignClient,
                new ObjectMapper(),
                transactionTemplate,
                Optional.of(resumeMqDispatcher),
                agentBusinessActionNotifier,
                new ResumeTextExtractProperties(),
                resumeSearchSyncOutboxService,
                uploadAdmissionGuard,
                new ResumeImportNormalizer(new ObjectMapper()),
                aggregateInitializationService);
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
    void createResumeInitializesVersionListVisibilityAndSearchOutboxAsOneAggregate() {
        when(resumeMapper.selectCount(any())).thenReturn(0L);
        AtomicReference<Resume> insertedResume = new AtomicReference<>();
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume inserted = invocation.getArgument(0);
            inserted.setId(RESUME_ID);
            inserted.setCreatedAt(CREATED_AT);
            inserted.setUpdatedAt(UPDATED_AT);
            insertedResume.set(inserted);
            return 1;
        });
        when(resumeMapper.selectOne(any())).thenAnswer(invocation -> insertedResume.get());
        when(projectMapper.selectList(any())).thenReturn(List.of());
        ResumeSaveDTO dto = new ResumeSaveDTO();
        dto.setResumeName("Java 后端工程师简历");
        dto.setRealName("李明");
        dto.setTargetPosition("Java 后端工程师");
        dto.setSummary("负责交易平台核心服务设计与交付。");

        assertEquals(RESUME_ID, service.createResume(dto).getId());

        verify(aggregateInitializationService)
                .initializeCreatedResume(RESUME_ID, USER_ID, null, RESUME_ID);
    }

    @Test
    void pagedResumeListKeepsTrueTotalAndRequestedPage() {
        List<ResumeListVO> source = java.util.stream.LongStream.rangeClosed(1, 23)
                .mapToObj(id -> {
                    ResumeListVO item = new ResumeListVO();
                    item.setId(id);
                    item.setTitle("Java resume " + id);
                    return item;
                })
                .toList();
        when(resumeMapper.selectResumeList(USER_ID, "%Java%", null, null)).thenReturn(source);

        PageResult<ResumeListVO> result = service.listResumes(2, 10, "Java");

        assertEquals(23L, result.getTotal());
        assertEquals(2L, result.getPageNo());
        assertEquals(10L, result.getPageSize());
        assertEquals(
                java.util.stream.LongStream.rangeClosed(11, 20).boxed().toList(),
                result.getRecords().stream().map(ResumeListVO::getId).toList());
        verify(resumeMapper).selectResumeList(USER_ID, "%Java%", null, null);
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
        assertParamError(() -> service.clearDefault(RESUME_ID));
        assertParamError(() -> service.createProject(RESUME_ID, project));
        assertParamError(() -> service.updateProject(RESUME_ID, 200L, project));
        assertParamError(() -> service.deleteProject(RESUME_ID, 200L));
    }

    @Test
    void malformedResumeCannotBecomeDefault() {
        Resume resume = ownedResume();
        resume.setTitle("223");
        resume.setRealName("李明");
        resume.setTargetPosition("Java 后端工程师");
        resume.setSummary("负责订单与库存服务的设计和交付。");
        when(resumeMapper.selectOne(any())).thenReturn(resume);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setDefault(RESUME_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("异常内容"));
    }

    @Test
    void savingAsDraftRemovesDefaultEligibilityAndReturnsCompleteness() {
        Resume resume = ownedResume();
        resume.setIsDefault(1);
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        ResumeSaveDTO dto = new ResumeSaveDTO();
        dto.setResumeName("Java 后端简历");
        dto.setSaveAsDraft(true);

        var result = service.updateResume(RESUME_ID, dto);

        assertEquals(0, resume.getIsDefault());
        assertTrue(result.getDraft());
        assertEquals(14, result.getCompletionPercent());
        assertTrue(result.getMissingSections().contains("核心技术栈"));
        verify(resumeMapper).updateById(resume);
    }

    @Test
    void clearDefaultMarksOwnedDefaultResumeAsNonDefault() {
        Resume resume = ownedResume();
        resume.setIsDefault(1);
        when(resumeMapper.selectOne(any())).thenReturn(resume);

        var result = service.clearDefault(RESUME_ID);

        assertEquals(0, resume.getIsDefault());
        assertEquals(0, result.getIsDefault());
        verify(resumeMapper).updateById(resume);
    }

    @Test
    void clearDefaultIsIdempotentForOwnedNonDefaultResume() {
        Resume resume = ownedResume();
        resume.setIsDefault(0);
        when(resumeMapper.selectOne(any())).thenReturn(resume);

        var result = service.clearDefault(RESUME_ID);

        assertEquals(0, result.getIsDefault());
        verify(resumeMapper, org.mockito.Mockito.never()).updateById(any(Resume.class));
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
        when(file.getSize()).thenReturn(20L * 1024L * 1024L + 1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> invokePrivate("validateUploadFile", new Class<?>[]{MultipartFile.class}, file));

        assertTrue(exception.getMessage().contains("20MB"));
    }

    @Test
    void uploadResumeUsesSharedAdmissionGuardBeforeStorageUpload() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        String contentSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("resume.pdf");
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(content));
        when(fileInfoMapper.acquireResumeUploadGuard(USER_ID, contentSha256)).thenReturn(1);
        when(fileInfoMapper.bindResumeUploadGuard(USER_ID, contentSha256, 88L)).thenReturn(1);
        InnerFileUploadVO uploaded = new InnerFileUploadVO();
        uploaded.setFileId(88L);
        uploaded.setContentSha256(contentSha256);
        when(uploadAdmissionGuard.executeSourceUpload(anyLong(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        when(fileStorageService.upload(file, "RESUME", USER_ID)).thenReturn(uploaded);
        when(analysisRecordMapper.insert(any(ResumeAnalysisRecord.class))).thenAnswer(invocation -> {
            ResumeAnalysisRecord record = invocation.getArgument(0);
            record.setId(301L);
            return 1;
        });

        service.uploadResume(file);

        verify(uploadAdmissionGuard).executeSourceUpload(
                org.mockito.ArgumentMatchers.eq((long) content.length), any());
        verify(fileStorageService).upload(file, "RESUME", USER_ID);
        verify(fileFeignClient, org.mockito.Mockito.never()).upload(any(), any(), anyLong());
    }

    @Test
    void concurrentSameContentUploadReturnsTheCommittedWinnerWithoutUploadingAgain() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = "same-resume".getBytes(StandardCharsets.UTF_8);
        String contentSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("renamed-resume.pdf");
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(content));
        when(fileInfoMapper.acquireResumeUploadGuard(USER_ID, contentSha256)).thenReturn(0);
        FileInfo winner = new FileInfo();
        winner.setId(77L);
        winner.setUserId(USER_ID);
        winner.setBizType("RESUME");
        winner.setOriginalFilename("winner.pdf");
        winner.setFileExt("pdf");
        winner.setFileSize((long) content.length);
        winner.setContentSha256(contentSha256);
        when(fileInfoMapper.selectLatestAvailableByContentSha256(
                USER_ID, "RESUME", contentSha256)).thenReturn(winner);
        when(fileInfoMapper.bindResumeUploadGuard(USER_ID, contentSha256, 77L)).thenReturn(1);

        ResumeUploadDecisionVO result = service.uploadResume(file);

        assertTrue(result.getDuplicate());
        assertTrue(result.getDecisionRequired());
        assertEquals(77L, result.getFileId());
        verify(fileStorageService, org.mockito.Mockito.never()).upload(any(), any(), anyLong());
        verify(analysisRecordMapper, org.mockito.Mockito.never())
                .insert(any(ResumeAnalysisRecord.class));
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
        ResumeProject persisted = ownedProject();
        persisted.setCreatedAt(CREATED_AT);
        persisted.setUpdatedAt(UPDATED_AT);
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.lockOwnedResume(RESUME_ID, USER_ID)).thenReturn(RESUME_ID);
        when(projectMapper.insert(any(ResumeProject.class))).thenAnswer(invocation -> {
            ResumeProject project = invocation.getArgument(0);
            project.setId(200L);
            return 1;
        });
        when(projectMapper.selectById(200L)).thenReturn(persisted);
        ResumeProjectSaveDTO dto = projectDto();

        var result = service.createProject(RESUME_ID, dto);

        InOrder order = inOrder(resumeMapper, projectMapper);
        order.verify(resumeMapper).lockOwnedResume(RESUME_ID, USER_ID);
        order.verify(projectMapper).insert(any(ResumeProject.class));
        order.verify(projectMapper).selectById(200L);
        assertEquals(CREATED_AT, result.getCreatedAt());
        assertEquals(UPDATED_AT, result.getUpdatedAt());
    }

    @Test
    void resumeScopedProjectUpdateLocksParentBeforeReadingAndUpdatingProject() {
        Resume resume = ownedResume();
        ResumeProject project = ownedProject();
        ResumeProject persisted = ownedProject();
        persisted.setUpdatedAt(UPDATED_AT);
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.lockOwnedResume(RESUME_ID, USER_ID)).thenReturn(RESUME_ID);
        when(projectMapper.selectById(200L)).thenReturn(project, persisted);

        var result = service.updateProject(RESUME_ID, 200L, projectDto());

        InOrder order = inOrder(resumeMapper, projectMapper);
        order.verify(resumeMapper).lockOwnedResume(RESUME_ID, USER_ID);
        order.verify(projectMapper).selectById(200L);
        order.verify(projectMapper).updateById(project);
        order.verify(projectMapper).selectById(200L);
        assertEquals(UPDATED_AT, result.getUpdatedAt());
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

    @Test
    void confirmAnalysisPersistsNormalizedPlainTextInsteadOfJsonEncodedCollections() {
        ResumeAnalysisRecord record = new ResumeAnalysisRecord();
        record.setId(301L);
        record.setUserId(USER_ID);
        record.setDeleted(0);
        record.setParseStatus("WAIT_CONFIRM");
        record.setRawText("张伟 Java 后端工程师");
        record.setStructuredJson(confirmableStructuredJson());
        AtomicReference<Resume> insertedResume = new AtomicReference<>();
        when(analysisRecordMapper.selectOne(any()))
                .thenReturn(record)
                .thenReturn((ResumeAnalysisRecord) null);
        when(resumeMapper.selectCount(any())).thenReturn(1L);
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(RESUME_ID);
            insertedResume.set(resume);
            return 1;
        });
        when(resumeMapper.selectOne(any())).thenAnswer(invocation -> insertedResume.get());
        when(analysisRecordMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
        when(projectMapper.selectList(any())).thenReturn(List.of());

        service.confirmAnalysis(301L);

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        ArgumentCaptor<ResumeProject> projectCaptor = ArgumentCaptor.forClass(ResumeProject.class);
        verify(resumeMapper).insert(resumeCaptor.capture());
        verify(projectMapper).insert(projectCaptor.capture());

        Resume persistedResume = resumeCaptor.getValue();
        ResumeProject persistedProject = projectCaptor.getValue();
        assertEquals("Java、Spring Boot", persistedResume.getSkillStack());
        assertFalse(persistedResume.getSkillStack().startsWith("["));
        assertFalse(persistedResume.getWorkExperience().startsWith("["));
        assertFalse(persistedResume.getEducationExperience().startsWith("["));
        assertEquals("Java、Spring Boot、Redis", persistedProject.getTechStack());
        assertFalse(persistedProject.getTechStack().startsWith("["));
        assertFalse(persistedProject.getResponsibility().startsWith("["));
        verify(aggregateInitializationService)
                .initializeCreatedResume(RESUME_ID, USER_ID, "RESUME_IMPORT", 301L);
    }

    @Test
    void reparseClearsAllStaleStructuredResultMetadataBeforeDispatch() {
        ResumeAnalysisRecord failed = new ResumeAnalysisRecord();
        failed.setId(301L);
        failed.setUserId(USER_ID);
        failed.setFileId(88L);
        failed.setDeleted(0);
        failed.setParseStatus("FAILED");
        failed.setStructuredJson("{\"stale\":true}");
        failed.setSchemaVersion("old-schema");
        failed.setQualityReportJson("{\"confirmable\":true}");
        failed.setGeneratedAt(CREATED_AT);
        ResumeAnalysisRecord pending = new ResumeAnalysisRecord();
        pending.setId(301L);
        pending.setUserId(USER_ID);
        pending.setFileId(88L);
        pending.setDeleted(0);
        pending.setParseStatus("PENDING");
        FileInfo file = new FileInfo();
        file.setId(88L);
        file.setUserId(USER_ID);
        file.setBizType("RESUME");
        file.setDeleted(0);
        file.setStatus("AVAILABLE");
        file.setOriginalFilename("resume.pdf");
        file.setStoredFilename("stored.pdf");
        file.setFileExt("pdf");
        file.setMimeType("application/pdf");
        file.setFileSize(1024L);
        file.setOssKey("resume/10/stored.pdf");
        when(analysisRecordMapper.selectOne(any())).thenReturn(failed, pending);
        when(analysisRecordMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
        when(fileInfoMapper.selectById(88L)).thenReturn(file);

        service.reparse(301L);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(analysisRecordMapper).update(org.mockito.ArgumentMatchers.isNull(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("structured_json"));
        assertTrue(sqlSet.contains("schema_version"));
        assertTrue(sqlSet.contains("policy_version"));
        assertTrue(sqlSet.contains("source_hash"));
        assertTrue(sqlSet.contains("validation_status"));
        assertTrue(sqlSet.contains("quality_report_json"));
        assertTrue(sqlSet.contains("generated_at"));
        assertTrue(sqlSet.contains("repair_batch_id"));
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

    private String confirmableStructuredJson() {
        return """
                {
                  "schemaVersion":"resume-import-v1",
                  "basicInfo":{"name":"张伟","phone":"","email":"","location":"上海"},
                  "targetPosition":"Java 后端工程师",
                  "summary":"负责订单服务的设计、交付和稳定性治理。",
                  "skills":["Java","Spring Boot"],
                  "workExperiences":[{
                    "company":"澄明云科",
                    "position":"Java 后端工程师",
                    "period":"2023.01-至今",
                    "description":"负责订单服务建设。",
                    "responsibilities":["服务设计","稳定性治理"],
                    "achievements":["保障核心链路交付"]
                  }],
                  "projectExperiences":[{
                    "projectName":"订单稳定性治理",
                    "period":"2025.01-2026.01",
                    "background":"订单核心链路治理。",
                    "role":"后端负责人",
                    "description":"负责方案设计与交付。",
                    "techStack":["Java","Spring Boot","Redis"],
                    "responsibilities":["拆分服务边界"],
                    "coreFeatures":["订单状态机"],
                    "technicalDifficulties":["幂等消费"],
                    "optimizationResults":["降低重复写入"],
                    "achievements":["完成治理闭环"]
                  }],
                  "educationExperiences":[{
                    "school":"华东理工大学",
                    "degree":"本科",
                    "major":"软件工程",
                    "period":"2016.09-2020.06",
                    "description":""
                  }]
                }
                """;
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
