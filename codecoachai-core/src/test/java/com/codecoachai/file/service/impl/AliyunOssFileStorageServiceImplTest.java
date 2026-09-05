package com.codecoachai.file.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.oss.config.OssProperties;
import com.codecoachai.common.oss.service.OssFileService;
import com.codecoachai.file.config.FileStorageProperties;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.mapper.FileInfoMapper;
import java.io.ByteArrayInputStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AliyunOssFileStorageServiceImplTest {

    @Mock
    private FileInfoMapper fileInfoMapper;
    @Mock
    private OssFileService ossFileService;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(FileInfo.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FileInfo.class);
        }
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteUserFileDeletesOssObjectOnlyAfterCommit() {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(5L);
        fileInfo.setUserId(10L);
        fileInfo.setBizType("RESUME");
        fileInfo.setStatus("AVAILABLE");
        fileInfo.setDeleted(0);
        fileInfo.setStoragePath("resume/10/2026/07/file.pdf");
        fileInfo.setOssKey("resume/10/2026/07/file.pdf");
        when(fileInfoMapper.selectOne(any())).thenReturn(fileInfo);
        when(fileInfoMapper.deleteById(5L)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        AliyunOssFileStorageServiceImpl service = new AliyunOssFileStorageServiceImpl(
                fileInfoMapper,
                new FileStorageProperties(),
                ossFileService,
                new OssProperties());

        service.deleteUserFile(5L, 10L, "RESUME");

        verify(ossFileService, never()).delete(any());
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        verify(ossFileService).delete("resume/10/2026/07/file.pdf");
    }

    @Test
    void adminDownloadStreamsVerifiedOssObjectWithMetadataInsteadOfRedirecting() throws Exception {
        byte[] content = "%PDF-demo".getBytes();
        FileInfo fileInfo = availableOssFile(content.length);
        when(fileInfoMapper.selectOne(any())).thenReturn(fileInfo);
        when(ossFileService.exists(fileInfo.getOssKey())).thenReturn(true);
        when(ossFileService.openStream(fileInfo.getOssKey()))
                .thenReturn(new ByteArrayInputStream(content));
        AliyunOssFileStorageServiceImpl service = new AliyunOssFileStorageServiceImpl(
                fileInfoMapper,
                new FileStorageProperties(),
                ossFileService,
                new OssProperties());

        ResponseEntity<Resource> response = service.adminDownload(5L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals(content.length, response.getHeaders().getContentLength());
        assertEquals("pdf", response.getHeaders().getFirst("X-File-Ext"));
        assertEquals(String.valueOf(content.length), response.getHeaders().getFirst("X-File-Size"));
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertNotNull(response.getBody());
        assertArrayEquals(content, response.getBody().getInputStream().readAllBytes());
        verify(ossFileService).exists(fileInfo.getOssKey());
        verify(ossFileService).openStream(fileInfo.getOssKey());
        verify(ossFileService, never()).signUrl(any(), any());
    }

    @Test
    void downloadUrlSignsOnlyAnExistingOssObject() {
        FileInfo fileInfo = availableOssFile(128);
        when(fileInfoMapper.selectOne(any())).thenReturn(fileInfo);
        when(ossFileService.exists(fileInfo.getOssKey())).thenReturn(true);
        when(ossFileService.signUrl(fileInfo.getOssKey(), null))
                .thenReturn("https://example.test/signed");
        AliyunOssFileStorageServiceImpl service = new AliyunOssFileStorageServiceImpl(
                fileInfoMapper,
                new FileStorageProperties(),
                ossFileService,
                new OssProperties());

        String url = service.downloadUrl(5L, 10L, "RESUME");

        assertEquals("https://example.test/signed", url);
        verify(ossFileService).exists(fileInfo.getOssKey());
        verify(ossFileService).signUrl(fileInfo.getOssKey(), null);
    }

    @Test
    void downloadUrlReportsMissingOssObjectBeforeSigning() {
        FileInfo fileInfo = availableOssFile(128);
        when(fileInfoMapper.selectOne(any())).thenReturn(fileInfo);
        when(ossFileService.exists(fileInfo.getOssKey())).thenReturn(false);
        AliyunOssFileStorageServiceImpl service = new AliyunOssFileStorageServiceImpl(
                fileInfoMapper,
                new FileStorageProperties(),
                ossFileService,
                new OssProperties());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.downloadUrl(5L, 10L, "RESUME"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus(), exception.getHttpStatus());
        verify(ossFileService).exists(fileInfo.getOssKey());
        verify(ossFileService, never()).signUrl(any(), any());
    }

    private static FileInfo availableOssFile(long fileSize) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(5L);
        fileInfo.setUserId(10L);
        fileInfo.setBizType("RESUME");
        fileInfo.setStatus("AVAILABLE");
        fileInfo.setDeleted(0);
        fileInfo.setOriginalFilename("acceptance-resume.pdf");
        fileInfo.setStoredFilename("stored.pdf");
        fileInfo.setFileExt("pdf");
        fileInfo.setMimeType("application/pdf");
        fileInfo.setFileSize(fileSize);
        fileInfo.setStorageProvider("ALIYUN_OSS");
        fileInfo.setStoragePath("resume/10/2026/08/acceptance-resume.pdf");
        fileInfo.setOssKey("resume/10/2026/08/acceptance-resume.pdf");
        return fileInfo;
    }
}
