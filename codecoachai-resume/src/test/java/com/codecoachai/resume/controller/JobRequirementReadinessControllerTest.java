package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.JobRequirementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobRequirementReadinessControllerTest {

    @Mock
    private JobRequirementService jobRequirementService;
    @Mock
    private JobReadinessService jobReadinessService;

    private JobRequirementReadinessController controller;

    @BeforeEach
    void setUp() {
        controller = new JobRequirementReadinessController(jobRequirementService, jobReadinessService);
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
}
