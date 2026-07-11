package com.codecoachai.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateVariableValidatorTest {

    @Test
    void definitionRejectsDeclaredVariablesThatDoNotMatchBodyPlaceholders() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> PromptTemplateVariableValidator.validateDefinition(
                        "Question: {{questionContent}}", "questionContent,userAnswer"));

        assertTrue(exception.getMessage().contains("unused=[userAnswer]"));
    }

    @Test
    void renderFailsFastWhenRequiredVariableIsMissing() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> PromptTemplateVariableValidator.render(
                        "Question: {{questionContent}}", "questionContent", Map.of()));

        assertTrue(exception.getMessage().contains("questionContent"));
    }

    @Test
    void optionalVariableMayBeOmittedWithoutLeavingPlaceholder() {
        String rendered = PromptTemplateVariableValidator.render(
                "Required={{requiredValue}}, optional={{optionalValue}}",
                "{\"required\":[\"requiredValue\"],\"optional\":[\"optionalValue\"]}",
                Map.of("requiredValue", "sentinel-required"));

        assertEquals("Required=sentinel-required, optional=", rendered);
    }

    @Test
    void legacyJsonObjectDescriptionsRemainRequiredDeclarations() {
        PromptTemplateVariableValidator.validateDefinition(
                "Question={{questionContent}}, answer={{userAnswer}}",
                "{\"questionContent\":\"question text\",\"userAnswer\":\"candidate answer\"}");

        String rendered = PromptTemplateVariableValidator.render(
                "Question={{questionContent}}, answer={{userAnswer}}",
                "{\"questionContent\":\"question text\",\"userAnswer\":\"candidate answer\"}",
                Map.of("questionContent", "sentinel-question", "userAnswer", "sentinel-answer"));

        assertTrue(rendered.contains("sentinel-question"));
        assertTrue(rendered.contains("sentinel-answer"));
    }
}
