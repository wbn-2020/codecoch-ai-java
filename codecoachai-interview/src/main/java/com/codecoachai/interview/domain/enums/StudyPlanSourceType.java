package com.codecoachai.interview.domain.enums;

import lombok.Getter;

@Getter
public enum StudyPlanSourceType {

    MANUAL("MANUAL", "Manual"),
    JD_GAP("JD_GAP", "Job target skill gap"),
    RESUME_JOB_MATCH("RESUME_JOB_MATCH", "Resume job match"),
    INTERVIEW_REPORT("INTERVIEW_REPORT", "Interview report"),
    PRACTICE_WEAKNESS("PRACTICE_WEAKNESS", "Practice weakness"),
    SYSTEM_RECOMMEND("SYSTEM_RECOMMEND", "System recommend");

    private final String code;
    private final String description;

    StudyPlanSourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
