package com.codecoachai.resume.applicationworkspace;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.StatusTransitionRequest;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.StatusTransitionView;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.WorkspaceView;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.service.JobApplicationLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import java.util.ArrayList;

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
        WorkspaceView view = workspaceService.get(applicationId);
        if (view.getApplication() != null) {
            view.setAllowedTransitions(new ArrayList<>(
                    lifecycleService.allowedTransitions(view.getApplication().getStatus())));
        }
        return Result.success(view);
    }

    @PostMapping("/applications/{applicationId}/status-transitions")
    public Result<StatusTransitionView> transition(@PathVariable Long applicationId,
                                             @RequestBody(required = false) StatusTransitionRequest request,
                                             @RequestHeader(value = "Idempotency-Key", required = false)
                                             String idempotencyKeyHeader) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignWorkspace();
        StatusTransitionRequest body = request == null ? new StatusTransitionRequest() : request;
        String idempotencyKey = StringUtils.hasText(body.getIdempotencyKey())
                ? body.getIdempotencyKey() : idempotencyKeyHeader;
        JobApplication application = lifecycleService.transition(applicationId, body.getTargetStatus(),
                body.getExpectedLockVersion(), idempotencyKey, body.getNote());
        StatusTransitionView result = new StatusTransitionView();
        result.setApplication(application);
        result.setAllowedTransitions(new ArrayList<>(lifecycleService.allowedTransitions(application.getStatus())));
        return Result.success(result);
    }
}
