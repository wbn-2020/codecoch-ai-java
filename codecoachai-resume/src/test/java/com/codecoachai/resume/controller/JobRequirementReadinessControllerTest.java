package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.web.handler.GlobalExceptionHandler;
import com.codecoachai.resume.domain.dto.JobReadinessQueryDTO;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.JobRequirementService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class JobRequirementReadinessControllerTest {

    @Mock
    private JobRequirementService jobRequirementService;
    @Mock
    private JobReadinessService jobReadinessService;

    private JobRequirementReadinessController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new JobRequirementReadinessController(jobRequirementService, jobReadinessService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1001L);
        LoginUserContext.setLoginUser(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void getsSnapshotByTargetAndSnapshotId() {
        JobReadinessSnapshotVO snapshot = new JobReadinessSnapshotVO();
        snapshot.setId(901L);
        when(jobReadinessService.getSnapshot(11L, 901L)).thenReturn(snapshot);

        var result = controller.snapshot(11L, 901L);

        assertEquals(901L, result.getData().getId());
        verify(jobReadinessService).getSnapshot(11L, 901L);
    }

    @Test
    void returnsPagedSnapshotHistoryWithUniformResponseFields() throws Exception {
        JobReadinessSnapshotVO snapshot = snapshot(901L);
        when(jobReadinessService.page(11L, 2L, 10L))
                .thenReturn(PageResult.of(List.of(snapshot), 25L, 2L, 10L));

        mockMvc.perform(get("/job-targets/11/readiness-snapshots/page")
                        .param("pageNo", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(25))
                .andExpect(jsonPath("$.data.pages").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(901))
                .andExpect(jsonPath("$.data.records[0].targetJobId").value(11));

        verify(jobReadinessService).page(11L, 2L, 10L);
    }

    @Test
    void rejectsInvalidSnapshotHistoryPageParameters() throws Exception {
        mockMvc.perform(get("/job-targets/11/readiness-snapshots/page")
                        .param("pageNo", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
        mockMvc.perform(get("/job-targets/11/readiness-snapshots/page")
                        .param("pageNo", "1")
                        .param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(jobReadinessService, never()).page(any(), any(), any());
    }

    @Test
    void keepsLegacySnapshotHistoryAsListResponse() throws Exception {
        when(jobReadinessService.list(eq(11L), any(JobReadinessQueryDTO.class)))
                .thenReturn(List.of(snapshot(902L)));

        mockMvc.perform(get("/job-targets/11/readiness-snapshots").param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(902))
                .andExpect(jsonPath("$.data.records").doesNotExist());

        verify(jobReadinessService).list(eq(11L), argThat(query -> query.getLimit() == 8));
    }

    private JobReadinessSnapshotVO snapshot(Long id) {
        JobReadinessSnapshotVO snapshot = new JobReadinessSnapshotVO();
        snapshot.setId(id);
        snapshot.setTargetJobId(11L);
        return snapshot;
    }
}
