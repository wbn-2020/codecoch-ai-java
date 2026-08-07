package com.codecoachai.resume.careerresearch.service;

import com.codecoachai.resume.careerresearch.entity.CareerResearchSourceVersion;
import com.codecoachai.resume.careerresearch.vo.CareerResearchDraft;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CareerResearchCitationValidator {
    public ValidationResult validate(Long userId, CareerResearchDraft draft,
                                     List<CareerResearchSourceVersion> allowedVersions) {
        if (draft == null) {
            return ValidationResult.rejected("研究结果为空");
        }
        Set<Long> allowedIds = new HashSet<>();
        for (CareerResearchSourceVersion version : allowedVersions) {
            if (!Objects.equals(version.getUserId(), userId) || version.getId() == null) {
                return ValidationResult.rejected("研究来源版本不属于当前用户");
            }
            allowedIds.add(version.getId());
        }
        Set<Long> used = new HashSet<>();
        if (draft.getFacts() == null) {
            return ValidationResult.rejected("研究事实集合为空");
        }
        for (CareerResearchDraft.Fact fact : draft.getFacts()) {
            if (fact == null || !StringUtils.hasText(fact.getStatement())
                    || fact.getSourceVersionIds() == null || fact.getSourceVersionIds().isEmpty()) {
                return ValidationResult.rejected("每条研究事实都必须引用来源版本");
            }
            for (Long sourceVersionId : fact.getSourceVersionIds()) {
                if (!allowedIds.contains(sourceVersionId)) {
                    return ValidationResult.rejected("研究事实引用了不可用的来源版本");
                }
                used.add(sourceVersionId);
            }
        }
        if (draft.getSourceRefs() != null) {
            for (Long sourceRef : draft.getSourceRefs()) {
                if (!allowedIds.contains(sourceRef)) {
                    return ValidationResult.rejected("来源引用中包含不可用的来源版本");
                }
                used.add(sourceRef);
            }
        }
        draft.setSourceRefs(used.stream().sorted().toList());
        return ValidationResult.accepted();
    }

    public record ValidationResult(boolean valid, String reason) {
        static ValidationResult accepted() {
            return new ValidationResult(true, null);
        }

        static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
