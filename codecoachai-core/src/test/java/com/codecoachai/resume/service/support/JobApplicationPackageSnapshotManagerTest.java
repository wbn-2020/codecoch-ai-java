package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.domain.entity.JobApplicationPackageSnapshot;
import com.codecoachai.resume.domain.vo.JobApplicationPackageVO;
import com.codecoachai.resume.mapper.JobApplicationPackageSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobApplicationPackageSnapshotManagerTest {

    private static final long USER_ID = 10L;
    private static final long PACKAGE_ID = 51L;

    @Mock
    private JobApplicationPackageSnapshotMapper snapshotMapper;

    private JobApplicationPackageSnapshotManager manager;

    @BeforeEach
    void setUp() {
        manager = new JobApplicationPackageSnapshotManager(
                snapshotMapper, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void unchangedContentReturnsExistingSnapshotWithoutCreatingAnotherHistoryRow() {
        JobApplicationPackageSnapshot existing = snapshot(7L, 4, "existing-hash");
        when(snapshotMapper.selectByContentHash(eq(PACKAGE_ID), eq(USER_ID), anyString()))
                .thenReturn(existing);

        JobApplicationPackageSnapshot result =
                manager.capture(USER_ID, PACKAGE_ID, packageSnapshot(), "SAVE");

        assertSame(existing, result);
        verify(snapshotMapper).selectByContentHash(eq(PACKAGE_ID), eq(USER_ID), anyString());
        verify(snapshotMapper, never()).selectLatestForUpdate(any(), any());
        verify(snapshotMapper, never()).insert(any(JobApplicationPackageSnapshot.class));
    }

    @Test
    void captureAppendsHistoryAndLeavesCallerCurrentValuesForRootUpdate() {
        JobApplicationPackageVO callerSnapshot = packageSnapshot();
        callerSnapshot.setSnapshotVersion(99);
        callerSnapshot.setCurrentSnapshotId(1234L);
        JobApplicationPackageSnapshot previous = snapshot(7L, 4, "previous-hash");
        when(snapshotMapper.selectByContentHash(eq(PACKAGE_ID), eq(USER_ID), anyString()))
                .thenReturn(null);
        when(snapshotMapper.selectLatestForUpdate(PACKAGE_ID, USER_ID)).thenReturn(previous);
        when(snapshotMapper.insert(any(JobApplicationPackageSnapshot.class))).thenAnswer(invocation -> {
            JobApplicationPackageSnapshot inserted = invocation.getArgument(0);
            inserted.setId(8L);
            return 1;
        });

        JobApplicationPackageSnapshot result =
                manager.capture(USER_ID, PACKAGE_ID, callerSnapshot, "SAVE");

        assertEquals(8L, result.getId());
        assertEquals(5, result.getSnapshotVersion());
        assertEquals(PACKAGE_ID, result.getPackageId());
        assertEquals(USER_ID, result.getUserId());
        assertEquals(99, callerSnapshot.getSnapshotVersion());
        assertEquals(1234L, callerSnapshot.getCurrentSnapshotId());
        assertEquals(4, previous.getSnapshotVersion());
        assertEquals("previous-hash", previous.getContentHash());

        ArgumentCaptor<JobApplicationPackageSnapshot> captor =
                ArgumentCaptor.forClass(JobApplicationPackageSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        assertEquals(5, captor.getValue().getSnapshotVersion());
        assertEquals("SAVE", captor.getValue().getCaptureSource());
        verify(snapshotMapper, never()).updateById(any(JobApplicationPackageSnapshot.class));
    }

    private JobApplicationPackageVO packageSnapshot() {
        JobApplicationPackageVO snapshot = new JobApplicationPackageVO();
        snapshot.setPackageNo("PKG-51");
        snapshot.setUserId(USER_ID);
        snapshot.setTargetJobId(88L);
        snapshot.setJobApplicationId(71L);
        snapshot.setRecommendedResumeVersionId(12L);
        snapshot.setMatchReportId(13L);
        snapshot.setProjectEvidenceIds(List.of(31L, 32L));
        snapshot.setReadinessLevel("READY");
        snapshot.setReadinessScore(90);
        snapshot.setPackageStatus("READY");
        snapshot.setRefreshedAt(LocalDateTime.of(2026, 7, 23, 8, 0));
        return snapshot;
    }

    private JobApplicationPackageSnapshot snapshot(Long id, int versionNo, String contentHash) {
        JobApplicationPackageSnapshot snapshot = new JobApplicationPackageSnapshot();
        snapshot.setId(id);
        snapshot.setPackageId(PACKAGE_ID);
        snapshot.setUserId(USER_ID);
        snapshot.setSnapshotVersion(versionNo);
        snapshot.setContentHash(contentHash);
        return snapshot;
    }
}
