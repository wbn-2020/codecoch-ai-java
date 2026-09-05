package com.codecoachai.resume.careerresearch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.careerresearch.entity.CareerResearchSourceVersion;
import com.codecoachai.resume.careerresearch.service.CareerResearchCitationValidator;
import com.codecoachai.resume.careerresearch.vo.CareerResearchDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerResearchCitationValidatorTest {
    private final CareerResearchCitationValidator validator = new CareerResearchCitationValidator();

    @Test
    void acceptsFactsCitingOwnedSelectedVersions() {
        CareerResearchSourceVersion version = version(31L, 10L);
        CareerResearchDraft.Fact fact = new CareerResearchDraft.Fact();
        fact.setStatement("The role uses Java");
        fact.setSourceVersionIds(List.of(31L));
        CareerResearchDraft draft = new CareerResearchDraft();
        draft.setFacts(List.of(fact));

        CareerResearchCitationValidator.ValidationResult result =
                validator.validate(10L, draft, List.of(version));

        assertTrue(result.valid());
        assertTrue(draft.getSourceRefs().contains(31L));
    }

    @Test
    void rejectsUncitedFactsAndCrossUserReferences() {
        CareerResearchDraft.Fact fact = new CareerResearchDraft.Fact();
        fact.setStatement("Uncited");
        CareerResearchDraft draft = new CareerResearchDraft();
        draft.setFacts(List.of(fact));
        assertFalse(validator.validate(10L, draft, List.of(version(31L, 10L))).valid());

        CareerResearchDraft.Fact crossUserFact = new CareerResearchDraft.Fact();
        crossUserFact.setStatement("Cross user");
        crossUserFact.setSourceVersionIds(List.of(31L));
        draft.setFacts(List.of(crossUserFact));
        assertFalse(validator.validate(10L, draft, List.of(version(31L, 11L))).valid());
    }

    private CareerResearchSourceVersion version(Long id, Long userId) {
        CareerResearchSourceVersion version = new CareerResearchSourceVersion();
        version.setId(id);
        version.setUserId(userId);
        version.setSourceId(41L);
        version.setContentHash("hash");
        return version;
    }
}
