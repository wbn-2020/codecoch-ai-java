package com.codecoachai.ai.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiAdministrationDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void modelDraftReportsProviderEndpointAndRangeFields() {
        AiModelConfigSaveDTO dto = new AiModelConfigSaveDTO();
        dto.setProvider("invalid provider");
        dto.setModelName("model");
        dto.setApiBaseUrl("");
        dto.setTemperature(2.1D);
        dto.setMaxTokens(0);

        Set<ConstraintViolation<AiModelConfigSaveDTO>> violations = validator.validate(dto);

        assertTrue(hasField(violations, "provider"));
        assertTrue(hasField(violations, "apiBaseUrl"));
        assertTrue(hasField(violations, "temperature"));
        assertTrue(hasField(violations, "maxTokens"));
    }

    @Test
    void promptVersionRejectsDirectActiveCreation() {
        PromptTemplateVersionCreateDTO dto = new PromptTemplateVersionCreateDTO();
        dto.setVersionCode("v2");
        dto.setContent("input={{input}}");
        dto.setStatus("ACTIVE");

        Set<ConstraintViolation<PromptTemplateVersionCreateDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertTrue(hasField(violations, "status"));
    }

    private boolean hasField(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream()
                .anyMatch(violation -> field.equals(violation.getPropertyPath().toString()));
    }
}
