package com.codecoachai.resume.applicationworkspace;

import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.WorkspaceView;

public interface ApplicationWorkspaceService {

    WorkspaceView get(Long applicationId);
}
