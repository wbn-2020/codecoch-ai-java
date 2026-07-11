package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class InterviewVoiceDiscardDTO {

    @Pattern(
            regexp = "USER_CANCELLED|MODE_SWITCH|QUESTION_CHANGED|PAGE_UNLOAD|REPLACED|STALE",
            message = "discard reason is not supported")
    private String reason;
}
