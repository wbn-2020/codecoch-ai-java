package com.codecoachai.file.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.file.config.FileStorageProperties;
import com.codecoachai.file.domain.dto.AdminFileQueryDTO;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.mapper.FileInfoMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceImplTest {

    @Mock
    private FileInfoMapper fileInfoMapper;

    @TempDir
    Path tempDir;

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
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pageAdminFilesClampsPageSizeBeforeCreatingDatabasePage() {
        when(fileInfoMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocalFileStorageServiceImpl service = new LocalFileStorageServiceImpl(fileInfoMapper, new FileStorageProperties());
        AdminFileQueryDTO query = new AdminFileQueryDTO();
        query.setPageSize(10_000L);

        service.pageAdminFiles(query);

        ArgumentCaptor<Page<FileInfo>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        org.mockito.Mockito.verify(fileInfoMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        IPage<FileInfo> page = pageCaptor.getValue();
        assertEquals(AdminFileQueryDTO.MAX_PAGE_SIZE, page.getSize());
    }

    @Test
    void deleteUserFileDeletesPhysicalFileOnlyAfterCommit() throws Exception {
        Path storedFile = createStoredFile("resume/2026/07/file.pdf");
        FileInfo fileInfo = availableFile(1L, "resume/2026/07/file.pdf");
        when(fileInfoMapper.selectOne(any())).thenReturn(fileInfo);
        when(fileInfoMapper.deleteById(1L)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        service().deleteUserFile(1L, 10L, "RESUME");

        assertTrue(Files.exists(storedFile));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void deleteUserFileKeepsPhysicalFileWhenTransactionRollsBack() throws Exception {
        Path storedFile = createStoredFile("resume/2026/07/rollback.pdf");
        FileInfo fileInfo = availableFile(2L, "resume/2026/07/rollback.pdf");
        when(fileInfoMapper.selectOne(any())).thenReturn(fileInfo);
        when(fileInfoMapper.deleteById(2L)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        service().deleteUserFile(2L, 10L, "RESUME");

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertTrue(Files.exists(storedFile));
    }

    @Test
    void deleteUserFileRejectsConcurrentMetadataChangeWithoutDeletingPhysicalFile() throws Exception {
        Path storedFile = createStoredFile("resume/2026/07/race.pdf");
        FileInfo fileInfo = availableFile(3L, "resume/2026/07/race.pdf");
        when(fileInfoMapper.selectOne(any())).thenReturn(fileInfo);
        when(fileInfoMapper.deleteById(3L)).thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> service().deleteUserFile(3L, 10L, "RESUME"));

        assertTrue(Files.exists(storedFile));
        verify(fileInfoMapper).deleteById(3L);
    }

    private LocalFileStorageServiceImpl service() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setRootPath(tempDir.toString());
        return new LocalFileStorageServiceImpl(fileInfoMapper, properties);
    }

    private Path createStoredFile(String relativePath) throws Exception {
        Path target = tempDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        return Files.writeString(target, "content");
    }

    private FileInfo availableFile(Long id, String storagePath) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(id);
        fileInfo.setUserId(10L);
        fileInfo.setBizType("RESUME");
        fileInfo.setStatus("AVAILABLE");
        fileInfo.setDeleted(0);
        fileInfo.setStoragePath(storagePath);
        return fileInfo;
    }

    private void completeTransaction(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
