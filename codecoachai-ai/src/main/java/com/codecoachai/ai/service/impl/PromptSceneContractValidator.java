package com.codecoachai.ai.service.impl;

import com.codecoachai.ai.service.PromptSceneContracts;
import com.codecoachai.ai.service.PromptSceneContracts.PromptSceneContract;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.Set;

final class PromptSceneContractValidator {

    private PromptSceneContractValidator() {
    }

    static Compatibility evaluate(
            String scene,
            String versionCode,
            String content,
            String variablesDeclaration) {
        PromptSceneContract contract = PromptSceneContracts.find(scene).orElse(null);
        if (contract == null) {
            return Compatibility.ok();
        }

        Set<String> violations = new LinkedHashSet<>();
        if (!contract.acceptsVersionCode(versionCode)) {
            violations.add("versionCode must start with " + contract.managedVersionPrefix());
        }

        Set<String> placeholders;
        Set<String> declaredVariables;
        try {
            PromptTemplateVariableValidator.validateDefinition(content, variablesDeclaration);
            placeholders = PromptTemplateVariableValidator.placeholderNames(content);
            declaredVariables = PromptTemplateVariableValidator.declaredVariables(variablesDeclaration);
        } catch (BusinessException ex) {
            violations.add("invalid variable definition: " + ex.getMessage());
            placeholders = Set.of();
            declaredVariables = Set.of();
        }
        Set<String> missingPlaceholders = missing(contract.requiredVariables(), placeholders);
        if (!missingPlaceholders.isEmpty()) {
            violations.add("missing placeholders " + missingPlaceholders);
        }
        Set<String> missingDeclarations = missing(contract.requiredVariables(), declaredVariables);
        if (!missingDeclarations.isEmpty()) {
            violations.add("missing variable declarations " + missingDeclarations);
        }
        String effectiveContent = content == null ? "" : content;
        if (!contract.enforcedPromptSuffix().isBlank()) {
            effectiveContent = effectiveContent + "\n" + contract.enforcedPromptSuffix();
        }
        for (String fragment : contract.requiredContentFragments()) {
            if (!effectiveContent.contains(fragment)) {
                violations.add("missing content fragment " + fragment);
            }
        }
        return violations.isEmpty()
                ? Compatibility.ok()
                : Compatibility.rejected(String.join("; ", violations));
    }

    static void requireCompatible(
            String scene,
            String versionCode,
            String content,
            String variablesDeclaration) {
        Compatibility compatibility = evaluate(scene, versionCode, content, variablesDeclaration);
        if (!compatibility.compatible()) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "Prompt version is incompatible with scene contract " + scene + ": "
                            + compatibility.reason());
        }
    }

    private static Set<String> missing(Set<String> required, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(actual);
        return missing;
    }

    record Compatibility(boolean compatible, String reason) {

        private static Compatibility ok() {
            return new Compatibility(true, "");
        }

        private static Compatibility rejected(String reason) {
            return new Compatibility(false, reason);
        }
    }
}
