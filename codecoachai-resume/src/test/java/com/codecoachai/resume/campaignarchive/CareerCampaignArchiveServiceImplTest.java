package com.codecoachai.resume.campaignarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.config.V8FeatureGate;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.feign.CampaignArchiveAiFeignClient;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerCampaignArchiveServiceImplTest {

    @Mock
    private CareerCampaignArchiveMapper archiveMapper;
    @Mock
    private CareerCampaignArchiveBuilder builder;
    @Mock
    private CampaignArchiveAiFeignClient aiFeignClient;
    @Mock
    private FileFeignClient fileFeignClient;

    private CareerCampaignArchiveServiceImpl service;
    private CareerCampaignArchiveProperties properties;
    private LocalDateTime cutoff;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(7L).username("owner").build());
        V8FeatureGate gate = new V8FeatureGate();
        gate.setCampaignExport(true);
        properties = new CareerCampaignArchiveProperties();
        cutoff = LocalDateTime.of(2026, 7, 22, 12, 0);
        service = new CareerCampaignArchiveServiceImpl(
                archiveMapper, builder, properties, aiFeignClient, fileFeignClient, gate,
                new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsCampaignNotOwnedByCurrentUserBeforeCallingFileService() {
        CareerCampaignArchiveModels.CreateRequest request = request(false);
        when(archiveMapper.selectByIdempotency(eq(7L), any())).thenReturn(null);
        when(archiveMapper.selectCampaign(7L, 99L, cutoff)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.create(99L, request));
        verify(fileFeignClient, never()).upload(any(), any(), any());
    }

    @Test
    void failedExportCanBeExplicitlyRetriedAndSameReadyResultIsIdempotent() throws Exception {
        CareerCampaignArchiveModels.CampaignRow campaign =
                new CareerCampaignArchiveModels.CampaignRow();
        campaign.setId(12L);
        campaign.setName("周期");
        campaign.setStatus("COMPLETED");
        when(archiveMapper.selectCampaign(7L, 12L, cutoff)).thenReturn(campaign);
        when(archiveMapper.selectApplications(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(archiveMapper.selectTimeline(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(archiveMapper.selectCalendar(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(archiveMapper.selectInterviews(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(archiveMapper.selectOffers(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(archiveMapper.selectContacts(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(archiveMapper.selectActivities(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(archiveMapper.selectResearchSnapshots(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(aiFeignClient.getSource(any(), any(), any())).thenReturn(Result.success(null));

        AtomicReference<CareerCampaignArchiveExport> state = new AtomicReference<>();
        doAnswer(invocation -> {
            CareerCampaignArchiveExport value = invocation.getArgument(0);
            value.setId(41L);
            state.set(value);
            return 1;
        }).when(archiveMapper).insert(any(CareerCampaignArchiveExport.class));
        when(archiveMapper.selectByIdempotency(eq(7L), any()))
                .thenAnswer(invocation -> state.get());
        when(archiveMapper.selectBySource(any(), any(), any(), any(), any())).thenReturn(null);
        when(archiveMapper.claimRetry(eq(7L), eq(41L), any(), any())).thenAnswer(invocation -> {
            state.get().setStatus("GENERATING");
            return 1;
        });
        when(archiveMapper.selectOwned(7L, 41L)).thenAnswer(invocation -> state.get());

        CareerCampaignArchiveModels.ArchiveResult archive =
                new CareerCampaignArchiveModels.ArchiveResult();
        archive.setZipBytes(new byte[] {1, 2, 3});
        archive.setFileSize(3L);
        archive.setManifestHash("m".repeat(64));
        when(builder.build(any(), eq(cutoff), any(), eq(properties))).thenReturn(archive);
        when(fileFeignClient.upload(any(), eq("CAREER_CAMPAIGN_ARCHIVE"), eq(7L)))
                .thenThrow(new IllegalStateException("file service unavailable"))
                .thenReturn(Result.success(upload(88L)));
        when(archiveMapper.updateById(any(CareerCampaignArchiveExport.class))).thenAnswer(invocation -> 1);

        assertThrows(BusinessException.class, () -> service.create(12L, request(false)));
        assertEquals("FAILED", state.get().getStatus());

        CareerCampaignArchiveModels.View retry = service.create(12L, request(true));
        assertEquals("READY", retry.getStatus());
        verify(archiveMapper).claimRetry(eq(7L), eq(41L), any(), any());

        state.get().setStatus("READY");
        CareerCampaignArchiveModels.View replay = service.create(12L, request(false));
        assertEquals("READY", replay.getStatus());
    }

    private CareerCampaignArchiveModels.CreateRequest request(boolean retry) {
        CareerCampaignArchiveModels.CreateRequest request =
                new CareerCampaignArchiveModels.CreateRequest();
        request.setDataCutoffAt(cutoff);
        request.setExportFormat("ZIP");
        request.setIdempotencyKey("retry-key");
        request.setRetryFailed(retry);
        return request;
    }

    private InnerFileUploadVO upload(Long fileId) {
        InnerFileUploadVO upload = new InnerFileUploadVO();
        upload.setFileId(fileId);
        return upload;
    }
}
