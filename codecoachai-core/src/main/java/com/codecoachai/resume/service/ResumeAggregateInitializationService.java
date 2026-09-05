package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.entity.ResumeVersion;

public interface ResumeAggregateInitializationService {

    ResumeVersion initializeCreatedResume(
            Long resumeId, Long userId, String sourceType, Long sourceId);
}
