package com.codecoachai.resume.applicationworkspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.StatusTransitionRequest;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.StatusTransitionView;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.service.JobApplicationLifecycleService;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationWorkspaceControllerTest {

    @Mock
    private ApplicationWorkspaceService workspaceService;
    @Mock
    private JobApplicationLifecycleService lifecycleService;
    @Mock
    private V7FeatureGate featureGate;

    private ApplicationWorkspaceController controller;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .username("workspace-user")
                .build());
        controller = new ApplicationWorkspaceController(workspaceService, lifecycleService, featureGate);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void returnsAllowedTransitionsForTheLatestApplicationStatus() {
        JobApplication updated = application("INTERVIEWING", 4);
        when(lifecycleService.transition(88L, "INTERVIEWING", 3, "body-key", "备注"))
                .thenReturn(updated);
        when(lifecycleService.allowedTransitions("INTERVIEWING"))
                .thenReturn(Set.of("OFFER", "REJECTED"));

        StatusTransitionRequest request = request("INTERVIEWING", 3, "body-key", "备注");
        Result<StatusTransitionView> response = controller.transition(88L, request, "header-key");

        assertSame(updated, response.getData().getApplication());
        assertEquals(Set.of("OFFER", "REJECTED"),
                Set.copyOf(response.getData().getAllowedTransitions()));
        verify(lifecycleService).allowedTransitions("INTERVIEWING");
    }

    @Test
    void prefersBodyIdempotencyKeyOverHeader() {
        JobApplication updated = application("INTERVIEWING", 4);
        when(lifecycleService.transition(88L, "INTERVIEWING", 3, "body-key", "备注"))
                .thenReturn(updated);
        when(lifecycleService.allowedTransitions("INTERVIEWING")).thenReturn(Set.of("OFFER"));

        controller.transition(88L, request("INTERVIEWING", 3, "body-key", "备注"), "header-key");

        verify(lifecycleService).transition(88L, "INTERVIEWING", 3, "body-key", "备注");
    }

    @Test
    void passesTransitionNoteThroughToLifecycleService() {
        JobApplication updated = application("INTERVIEWING", 4);
        when(lifecycleService.transition(88L, "INTERVIEWING", 3, "body-key", "面试反馈已记录"))
                .thenReturn(updated);
        when(lifecycleService.allowedTransitions("INTERVIEWING")).thenReturn(Set.of("OFFER"));

        controller.transition(88L,
                request("INTERVIEWING", 3, "body-key", "面试反馈已记录"), null);

        verify(lifecycleService).transition(
                88L, "INTERVIEWING", 3, "body-key", "面试反馈已记录");
    }

    private static StatusTransitionRequest request(String targetStatus, int lockVersion,
                                                   String idempotencyKey, String note) {
        StatusTransitionRequest request = new StatusTransitionRequest();
        request.setTargetStatus(targetStatus);
        request.setExpectedLockVersion(lockVersion);
        request.setIdempotencyKey(idempotencyKey);
        request.setNote(note);
        return request;
    }

    private static JobApplication application(String status, int lockVersion) {
        JobApplication application = new JobApplication();
        application.setId(88L);
        application.setUserId(10L);
        application.setStatus(status);
        application.setLockVersion(lockVersion);
        return application;
    }
}
