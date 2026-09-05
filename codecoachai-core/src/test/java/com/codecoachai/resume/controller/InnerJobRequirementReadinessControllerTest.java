package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.JobRequirementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InnerJobRequirementReadinessControllerTest {

    @Mock
    private JobRequirementService jobRequirementService;
    @Mock
    private JobReadinessService jobReadinessService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InnerJobRequirementReadinessController controller;

    @BeforeEach
    void setUp() {
        controller = new InnerJobRequirementReadinessController(
                jobRequirementService, jobReadinessService, objectMapper);
    }

    @Test
    void agentContextUsesExplicitUserOwnershipAndGatesInsufficientSample() {
        JobRequirementMatrixVO matrix = new JobRequirementMatrixVO();
        matrix.setTargetJobId(11L);
        matrix.setJdAnalysisId(21L);
        matrix.setRequirementCount(1);
        JobRequirementMatrixVO.RequirementItem requirement = new JobRequirementMatrixVO.RequirementItem();
        requirement.setRequirementId(101L);
        requirement.setRequirementKey("redis");
        requirement.setRequirementName("Redis");
        requirement.setRequirementType("SKILL");
        requirement.setPriority("MUST");
        requirement.setCoverageLevel("MISSING");
        requirement.setRequirementConfidence("HIGH");
        requirement.setRequirementFallback(false);
        matrix.setRequirements(List.of(requirement));
        JobReadinessSnapshotVO snapshot = new JobReadinessSnapshotVO();
        snapshot.setId(901L);
        snapshot.setTargetJobId(11L);
        snapshot.setJdAnalysisId(21L);
        snapshot.setReadinessLevel("READY");
        snapshot.setConfidenceLevel("HIGH");
        snapshot.setFallback(false);
        snapshot.setMatrix(objectMapper.valueToTree(matrix));
        when(jobRequirementService.getMatrixForUser(1001L, 11L)).thenReturn(matrix);
        when(jobReadinessService.latestForUser(1001L, 11L)).thenReturn(snapshot);

        var result = controller.agentContext(1001L, 11L).getData();

        verify(jobRequirementService).getMatrixForUser(1001L, 11L);
        verify(jobReadinessService).latestForUser(1001L, 11L);
        assertTrue(result.getFallback());
        assertTrue(result.getWarnings().contains("REQUIREMENT_SAMPLE_INSUFFICIENT"));
        assertEquals(1, result.getMissingRequirements().size());
    }
}
