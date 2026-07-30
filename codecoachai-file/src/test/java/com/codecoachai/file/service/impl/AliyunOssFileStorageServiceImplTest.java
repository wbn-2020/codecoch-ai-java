package com.codecoachai.file.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.oss.config.OssProperties;
import com.codecoachai.common.oss.service.OssFileService;
import com.codecoachai.file.config.FileStorageProperties;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.mapper.FileInfoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
}
