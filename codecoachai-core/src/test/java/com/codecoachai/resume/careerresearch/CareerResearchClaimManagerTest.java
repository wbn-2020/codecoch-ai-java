package com.codecoachai.resume.careerresearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshot;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchReportMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotSourceMapper;
import com.codecoachai.resume.careerresearch.service.CareerResearchClaimManager;
import com.codecoachai.resume.careerresearch.service.CareerResearchClaimManager.Claim;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class CareerResearchClaimManagerTest {
    @Mock
    private CareerResearchReportMapper reportMapper;
    @Mock
    private CareerResearchSnapshotMapper snapshotMapper;
    @Mock
    private CareerResearchSnapshotSourceMapper snapshotSourceMapper;

    @Test
    void duplicateSnapshotInsertReturnsWinnerForSameReportAndSourceSet() {
        CareerResearchClaimManager manager =
                new CareerResearchClaimManager(reportMapper, snapshotMapper, snapshotSourceMapper);
        CareerResearchSnapshot winner = new CareerResearchSnapshot();
        winner.setId(99L);
        when(snapshotMapper.insert(any(CareerResearchSnapshot.class)))
                .thenThrow(new DuplicateKeyException("source set race"));
        when(snapshotMapper.selectWinner(10L, 5L, "source-set")).thenReturn(winner);

        Long snapshotId = manager.complete(new Claim(5L, "claim"), 10L, 7L,
                "source-set", "{}", "LOW", "AI unavailable", null, List.of());

        assertEquals(99L, snapshotId);
        verify(reportMapper).completeGeneration(5L, 10L, "claim", 99L);
    }
}
