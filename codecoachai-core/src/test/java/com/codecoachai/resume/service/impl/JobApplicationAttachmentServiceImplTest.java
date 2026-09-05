package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationAttachment;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import com.codecoachai.resume.domain.vo.JobApplicationAttachmentVO;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.mapper.JobApplicationAttachmentMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class JobApplicationAttachmentServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long APPLICATION_ID = 20L;
    private static final long PACKAGE_ID = 30L;

    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private JobApplicationPackageMapper packageMapper;
    @Mock
    private JobApplicationAttachmentMapper attachmentMapper;
    @Mock
    private FileFeignClient fileFeignClient;

    private JobApplicationAttachmentServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        var configuration = new MybatisConfiguration();
        init(configuration, JobApplication.class);
        init(configuration, JobApplicationPackage.class);
        init(configuration, JobApplicationAttachment.class);
    }

    private static void init(MybatisConfiguration configuration, Class<?> type) {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), type);
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(
                LoginUser.builder().userId(USER_ID).username("attachment-owner").build());
        service = new JobApplicationAttachmentServiceImpl(
                applicationMapper, packageMapper, attachmentMapper, fileFeignClient);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadForApplicationPersistsOwnedRelationAndMetadata() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        when(attachmentMapper.selectOne(any())).thenReturn(null);
        AtomicReference<JobApplicationAttachment> persisted = new AtomicReference<>();
        doAnswer(invocation -> {
            JobApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setId(41L);
            attachment.setDeleted(0);
            persisted.set(attachment);
            return 1;
        }).when(attachmentMapper).insert(any(JobApplicationAttachment.class));
        when(attachmentMapper.selectById(41L)).thenAnswer(invocation -> persisted.get());
        when(fileFeignClient.upload(any(), eq("ATTACHMENT"), eq(USER_ID)))
                .thenReturn(Result.success(upload(51L, USER_ID, "resume.pdf", "application/pdf", 7L)));

        JobApplicationAttachmentVO result = service.uploadForApplication(
                APPLICATION_ID, file("resume.pdf"), "resume", "投递简历");

        assertEquals(41L, result.getId());
        assertEquals(APPLICATION_ID, result.getApplicationId());
        assertEquals("RESUME", result.getAttachmentType());
        assertEquals("投递简历", result.getDisplayName());
        assertEquals("/applications/20/attachments/41/download", result.getDownloadUrl());
        verify(fileFeignClient).upload(any(), eq("ATTACHMENT"), eq(USER_ID));
        verify(attachmentMapper).insert(any(JobApplicationAttachment.class));
    }

    @Test
    void rejectsFilesAboveTwentyMiBBeforeExternalUpload() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.uploadForApplication(
                        APPLICATION_ID, oversized, "RESUME", "large.pdf"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        verifyNoInteractions(fileFeignClient, attachmentMapper);
    }

    @Test
    void externalUploadFailureNeverCreatesAttachmentRelation() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        when(fileFeignClient.upload(any(), eq("ATTACHMENT"), eq(USER_ID)))
                .thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "storage unavailable"));

        assertThrows(
                BusinessException.class,
                () -> service.uploadForApplication(
                        APPLICATION_ID, file("resume.pdf"), "RESUME", "投递简历"));

        verify(attachmentMapper, never()).insert(any(JobApplicationAttachment.class));
    }

    @Test
    void databaseInsertFailureDeletesNewFileWhenNoTransactionSynchronizationExists() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        when(attachmentMapper.selectOne(any())).thenReturn(null);
        when(fileFeignClient.upload(any(), eq("ATTACHMENT"), eq(USER_ID)))
                .thenReturn(Result.success(upload(52L, USER_ID, "resume.pdf", "application/pdf", 7L)));
        when(attachmentMapper.insert(any(JobApplicationAttachment.class)))
                .thenThrow(new IllegalStateException("insert failed"));
        when(fileFeignClient.delete(52L, USER_ID, "ATTACHMENT")).thenReturn(Result.success());

        assertThrows(
                IllegalStateException.class,
                () -> service.uploadForApplication(
                        APPLICATION_ID, file("resume.pdf"), "RESUME", "投递简历"));

        verify(fileFeignClient).delete(52L, USER_ID, "ATTACHMENT");
    }

    @Test
    void packageRouteRejectsCrossUserApplicationRelationshipBeforeUpload() {
        JobApplicationPackage applicationPackage = applicationPackage(PACKAGE_ID, USER_ID, APPLICATION_ID);
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(applicationPackage);
        when(applicationMapper.selectById(APPLICATION_ID))
                .thenReturn(application(APPLICATION_ID, USER_ID + 1));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.upload(
                        PACKAGE_ID, file("resume.pdf"), "RESUME", "投递简历"));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(fileFeignClient, attachmentMapper);
    }

    @Test
    void applicationListIncludesAttachmentsUploadedBeforePackageWasLinked() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        when(packageMapper.selectList(any()))
                .thenReturn(List.of(applicationPackage(PACKAGE_ID, USER_ID, APPLICATION_ID)));
        JobApplicationAttachment attachment = attachment(61L, USER_ID, PACKAGE_ID, null, 71L);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment));

        List<JobApplicationAttachmentVO> result = service.listForApplication(APPLICATION_ID);

        assertEquals(1, result.size());
        assertEquals(PACKAGE_ID, result.get(0).getPackageId());
        assertEquals("/application-packages/30/attachments/61/download",
                result.get(0).getDownloadUrl());
    }

    @Test
    void applicationDownloadAcceptsPackageAttachmentOnlyWhenPackageIsLinked() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        when(packageMapper.selectById(PACKAGE_ID))
                .thenReturn(applicationPackage(PACKAGE_ID, USER_ID, APPLICATION_ID));
        JobApplicationAttachment attachment = attachment(62L, USER_ID, PACKAGE_ID, null, 72L);
        when(attachmentMapper.selectById(62L)).thenReturn(attachment);
        ResponseEntity<org.springframework.core.io.Resource> response =
                ResponseEntity.ok(new ByteArrayResource("file".getBytes(StandardCharsets.UTF_8)));
        when(fileFeignClient.download(72L, USER_ID, "ATTACHMENT")).thenReturn(response);

        assertEquals(response, service.downloadForApplication(APPLICATION_ID, 62L));
        verify(fileFeignClient).download(72L, USER_ID, "ATTACHMENT");
    }

    @Test
    void packageRouteRejectsAttachmentWithConflictingApplicationRelationship() {
        when(packageMapper.selectById(PACKAGE_ID))
                .thenReturn(applicationPackage(PACKAGE_ID, USER_ID, APPLICATION_ID));
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        when(attachmentMapper.selectById(63L))
                .thenReturn(attachment(63L, USER_ID, PACKAGE_ID, APPLICATION_ID + 1, 73L));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.download(PACKAGE_ID, 63L));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), error.getCode());
        verify(fileFeignClient, never()).download(any(), any(), any());
    }

    @Test
    void replacementDeletesOldFileOnlyAfterCommit() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        JobApplicationAttachment attachment =
                attachment(64L, USER_ID, null, APPLICATION_ID, 74L);
        when(attachmentMapper.selectById(64L)).thenReturn(attachment);
        when(attachmentMapper.updateById(attachment)).thenReturn(1);
        when(fileFeignClient.upload(any(), eq("ATTACHMENT"), eq(USER_ID)))
                .thenReturn(Result.success(upload(75L, USER_ID, "new.pdf", "application/pdf", 9L)));
        when(fileFeignClient.delete(any(), eq(USER_ID), eq("ATTACHMENT")))
                .thenReturn(Result.success());
        TransactionSynchronizationManager.initSynchronization();

        service.replaceForApplication(
                APPLICATION_ID, 64L, file("new.pdf"), "RESUME", "新版简历");

        verify(fileFeignClient, never()).delete(any(), any(), any());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(fileFeignClient).delete(74L, USER_ID, "ATTACHMENT");
        verify(fileFeignClient, never()).delete(75L, USER_ID, "ATTACHMENT");
    }

    @Test
    void replacementRollbackDeletesNewFileAndPreservesOldFile() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        JobApplicationAttachment attachment =
                attachment(65L, USER_ID, null, APPLICATION_ID, 76L);
        when(attachmentMapper.selectById(65L)).thenReturn(attachment);
        when(attachmentMapper.updateById(attachment)).thenReturn(1);
        when(fileFeignClient.upload(any(), eq("ATTACHMENT"), eq(USER_ID)))
                .thenReturn(Result.success(upload(77L, USER_ID, "new.pdf", "application/pdf", 9L)));
        when(fileFeignClient.delete(any(), eq(USER_ID), eq("ATTACHMENT")))
                .thenReturn(Result.success());
        TransactionSynchronizationManager.initSynchronization();

        service.replaceForApplication(
                APPLICATION_ID, 65L, file("new.pdf"), "RESUME", "新版简历");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileFeignClient).delete(77L, USER_ID, "ATTACHMENT");
        verify(fileFeignClient, never()).delete(76L, USER_ID, "ATTACHMENT");
    }

    @Test
    void deleteCleansPhysicalFileOnlyAfterCommit() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        JobApplicationAttachment attachment =
                attachment(66L, USER_ID, null, APPLICATION_ID, 78L);
        when(attachmentMapper.selectById(66L)).thenReturn(attachment);
        when(attachmentMapper.updateById(attachment)).thenReturn(1);
        when(fileFeignClient.delete(78L, USER_ID, "ATTACHMENT")).thenReturn(Result.success());
        TransactionSynchronizationManager.initSynchronization();

        service.deleteForApplication(APPLICATION_ID, 66L);

        verify(fileFeignClient, never()).delete(any(), any(), any());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(fileFeignClient).delete(78L, USER_ID, "ATTACHMENT");
    }

    @Test
    void failedDeleteDoesNotRemovePhysicalFile() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));
        JobApplicationAttachment attachment =
                attachment(67L, USER_ID, null, APPLICATION_ID, 79L);
        when(attachmentMapper.selectById(67L)).thenReturn(attachment);
        when(attachmentMapper.updateById(attachment)).thenReturn(0);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.deleteForApplication(APPLICATION_ID, 67L));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), error.getCode());
        verify(fileFeignClient, never()).delete(any(), any(), any());
    }

    @Test
    void unsupportedAttachmentTypeIsRejectedBeforeUpload() {
        when(applicationMapper.selectById(APPLICATION_ID)).thenReturn(application(APPLICATION_ID, USER_ID));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.uploadForApplication(
                        APPLICATION_ID, file("resume.pdf"), "EXECUTABLE", "附件"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        verifyNoInteractions(fileFeignClient, attachmentMapper);
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile(
                "file", filename, "application/pdf", "content".getBytes(StandardCharsets.UTF_8));
    }

    private JobApplication application(Long id, Long userId) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setUserId(userId);
        application.setDeleted(0);
        return application;
    }

    private JobApplicationPackage applicationPackage(
            Long id, Long userId, Long applicationId) {
        JobApplicationPackage applicationPackage = new JobApplicationPackage();
        applicationPackage.setId(id);
        applicationPackage.setUserId(userId);
        applicationPackage.setApplicationId(applicationId);
        applicationPackage.setDeleted(0);
        return applicationPackage;
    }

    private JobApplicationAttachment attachment(
            Long id, Long userId, Long packageId, Long applicationId, Long fileId) {
        JobApplicationAttachment attachment = new JobApplicationAttachment();
        attachment.setId(id);
        attachment.setUserId(userId);
        attachment.setPackageId(packageId);
        attachment.setApplicationId(applicationId);
        attachment.setFileId(fileId);
        attachment.setAttachmentType("RESUME");
        attachment.setDisplayName("投递简历");
        attachment.setOriginalFilename("resume.pdf");
        attachment.setMimeType("application/pdf");
        attachment.setFileSize(7L);
        attachment.setSortOrder(0);
        attachment.setDeleted(0);
        return attachment;
    }

    private InnerFileUploadVO upload(
            Long fileId, Long userId, String filename, String mimeType, Long size) {
        InnerFileUploadVO upload = new InnerFileUploadVO();
        upload.setFileId(fileId);
        upload.setUserId(userId);
        upload.setOriginalFilename(filename);
        upload.setMimeType(mimeType);
        upload.setFileSize(size);
        return upload;
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
