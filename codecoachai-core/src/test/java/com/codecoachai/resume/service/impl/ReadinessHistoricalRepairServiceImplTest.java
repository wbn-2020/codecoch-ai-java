package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.ReadinessRepairRequestDTO;
import com.codecoachai.resume.domain.entity.JobReadinessSnapshot;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.mapper.JobReadinessSnapshotMapper;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.support.ReadinessDimensionCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadinessHistoricalRepairServiceImplTest {

    @Mock
    private JobReadinessSnapshotMapper snapshotMapper;
    @Mock
    private JobReadinessService jobReadinessService;

    private ReadinessHistoricalRepairServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReadinessHistoricalRepairServiceImpl(
                snapshotMapper,
                jobReadinessService,
                new ReadinessDimensionCodec(new ObjectMapper()));
    }

    @Test
    void dryRunClassifiesInvalidSnapshotWithoutRegenerating() {
        when(snapshotMapper.selectList(any())).thenReturn(List.of(invalidSnapshot()));

        var result = service.repair(request());

        assertEquals(true, result.isDryRun());
        assertEquals(1, result.getMatchedRecords());
        assertEquals("WOULD_REGENERATE", result.getRecords().get(0).getStatus());
        verify(jobReadinessService, never()).regenerateForRepair(any(), any(), any());
    }

    @Test
    void executionRegeneratesInvalidSnapshotWithRequestedBatch() {
        when(snapshotMapper.selectList(any())).thenReturn(List.of(invalidSnapshot()));
        JobReadinessSnapshotVO regenerated = new JobReadinessSnapshotVO();
        regenerated.setId(2002L);
        regenerated.setSnapshotHash("new-snapshot-hash");
        regenerated.setValidationStatus("VALID");
        when(jobReadinessService.regenerateForRepair(
                11L, 22L, "readiness-repair-1001")).thenReturn(regenerated);

        ReadinessRepairRequestDTO request = request();
        request.setDryRun(false);
        var result = service.repair(request);

        assertEquals(1, result.getChangedRecords());
        assertEquals("REGENERATED", result.getRecords().get(0).getStatus());
        assertEquals(2002L, result.getRecords().get(0).getRegeneratedSnapshotId());
        verify(jobReadinessService).regenerateForRepair(11L, 22L, "readiness-repair-1001");
    }

    @Test
    void rejectsUnboundedOrOversizedRequests() {
        ReadinessRepairRequestDTO missingScope = request();
        missingScope.setSnapshotIds(List.of());
        assertThrows(BusinessException.class, () -> service.repair(missingScope));

        ReadinessRepairRequestDTO oversized = request();
        oversized.setMaxRecords(101);
        assertThrows(BusinessException.class, () -> service.repair(oversized));
    }

    private ReadinessRepairRequestDTO request() {
        ReadinessRepairRequestDTO request = new ReadinessRepairRequestDTO();
        request.setRepairBatchId("readiness-repair-1001");
        request.setSnapshotIds(List.of(1001L));
        return request;
    }

    private JobReadinessSnapshot invalidSnapshot() {
        JobReadinessSnapshot snapshot = new JobReadinessSnapshot();
        snapshot.setId(1001L);
        snapshot.setUserId(11L);
        snapshot.setTargetJobId(22L);
        snapshot.setSnapshotHash("old-snapshot-hash");
        snapshot.setSchemaVersion(ReadinessDimensionCodec.SCHEMA_VERSION);
        snapshot.setDimensionJson("{");
        snapshot.setDeleted(0);
        return snapshot;
    }
}
