package com.codecoachai.interview.support;

import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Copies an interview session's creation configuration into a new create request. Shared by
 * remediation and same-config replay so the copied field set stays in one place. Deliberately
 * leaves out title, practiceMode, recommendation fields and scenarioVersionId — each caller
 * sets those (the scenario version lives in the binding table, see
 * {@link com.codecoachai.interview.scenario.InterviewScenarioBindingResolver}).
 */
public final class InterviewSessionConfigCopier {

    private InterviewSessionConfigCopier() {
    }

    public static CreateInterviewDTO copyCreationConfig(
            InterviewSession source, ObjectMapper objectMapper) {
        CreateInterviewDTO request = new CreateInterviewDTO();
        request.setMode(source.getMode());
        request.setInterviewMode(source.getMode());
        request.setResumeId(source.getResumeId());
        request.setApplicationId(source.getApplicationId());
        request.setApplicationPackageId(source.getApplicationPackageId() == null
                ? null : source.getApplicationPackageId().toString());
        request.setTargetJobId(source.getTargetJobId());
        request.setJdAnalysisId(source.getJdAnalysisId());
        request.setResumeVersionId(source.getResumeVersionId());
        request.setSkillProfileId(source.getSkillProfileId());
        request.setMatchReportId(source.getMatchReportId());
        request.setMaxQuestionCount(source.getMaxQuestionCount());
        request.setTargetPosition(source.getTargetPosition());
        request.setExperienceLevel(source.getExperienceLevel());
        request.setIndustryTemplateId(source.getIndustryTemplateId());
        request.setIndustryDirection(source.getIndustryDirection());
        request.setDifficulty(source.getDifficulty());
        request.setInterviewerStyle(source.getInterviewerStyle());
        request.setBasedOnResume(source.getBasedOnResume());
        request.setTrainingScene(source.getTrainingScene());
        request.setTargetSkillDomain(source.getTargetSkillDomain());
        request.setTargetSkillCodes(
                readList(source.getTargetSkillCodes(), objectMapper, new TypeReference<>() {
                }));
        request.setTargetLevel(source.getTargetLevel());
        request.setProjectEvidenceIds(
                readList(source.getProjectEvidenceIds(), objectMapper, new TypeReference<>() {
                }));
        request.setFollowUpIntensity(source.getFollowUpIntensity());
        return request;
    }

    private static <T> List<T> readList(
            String value, ObjectMapper objectMapper, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<T> result = objectMapper.readValue(value, type);
            return result == null ? List.of() : new ArrayList<>(result);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
