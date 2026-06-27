package com.codecoachai.file.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.file.config.FileStorageProperties;
import com.codecoachai.file.domain.dto.AdminFileQueryDTO;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.mapper.FileInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceImplTest {

    @Mock
    private FileInfoMapper fileInfoMapper;

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
}
