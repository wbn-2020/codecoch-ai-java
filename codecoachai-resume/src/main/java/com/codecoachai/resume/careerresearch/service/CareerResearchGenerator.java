package com.codecoachai.resume.careerresearch.service;

import com.codecoachai.resume.careerresearch.vo.CareerResearchDraft;
import java.util.List;

public interface CareerResearchGenerator {
    String SCENE = "CAREER_OPPORTUNITY_RESEARCH";

    CareerResearchDraft generate(GenerationRequest request);

    record GenerationRequest(Long userId, Long applicationId, List<SourceInput> sources) {
    }

    record SourceInput(Long sourceId, Long sourceVersionId, String sourceType,
                       String title, String contentSummary, String content) {
    }
}
