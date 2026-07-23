package com.codecoachai.resume.applicationworkspace;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.StatusTransitionRequest;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.WorkspaceView;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.service.JobApplicationLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationWorkspaceController {

    private final ApplicationWorkspaceService workspaceService;
    private final JobApplicationLifecycleService lifecycleService;
    private final V7FeatureGate featureGate;

    @GetMapping("/applications/{applicationId}/workspace")
    public Result<WorkspaceView> get(@PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(workspaceService.get(applicationId));
    }

    @PostMapping("/applications/{applicationId}/status-transitions")
    public Result<JobApplication> transition(@PathVariable Long applicationId,
                                             @RequestBody StatusTransitionRequest request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        return Result.success(lifecycleService.transition(applicationId, request.getTargetStatus(),
                request.getExpectedLockVersion(), request.getIdempotencyKey()));
    }
}
