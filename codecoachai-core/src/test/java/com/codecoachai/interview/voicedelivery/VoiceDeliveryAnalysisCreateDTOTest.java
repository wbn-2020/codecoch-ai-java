package com.codecoachai.interview.voicedelivery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VoiceDeliveryAnalysisCreateDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsOmittedClientEvidenceFieldsWhenVoiceSubmissionExists() {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();
        dto.setVoiceSubmissionId(41L);

        assertEquals(Set.of(), validator.validate(dto).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void requiresVoiceSubmissionForVoiceDeliveryAnalysis() {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();

        Set<String> invalidFields = validator.validate(dto).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("voiceSubmissionId"), invalidFields);
    }

    @Test
    void ignoresValidationOfCompatibilityWordTimingElements() {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();
        dto.setVoiceSubmissionId(41L);
        VoiceWordTimingDTO timing = new VoiceWordTimingDTO();
        timing.setStartMs(-1L);
        dto.setWordTimings(java.util.List.of(timing));

        assertEquals(Set.of(), validator.validate(dto).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet()));
    }
}
