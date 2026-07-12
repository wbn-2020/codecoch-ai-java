package com.codecoachai.resume.careerimport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.domain.entity.JobApplication;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CareerImportCanonicalSupportTest {

    private final CareerImportCanonicalSupport support = new CareerImportCanonicalSupport();

    @Test
    void exactSlidingWindowTreatsZeroOneAndSevenDaysAsDuplicatesButAllowsEightDays() {
        JobApplication incoming = application("Acme, Inc.", "Backend Engineer", "LinkedIn", "2026-07-10");

        assertTrue(support.isDuplicate(incoming,
                application(" acme inc ", "Backend-Engineer", "Other", "2026-07-10")));
        assertTrue(support.isDuplicate(incoming,
                application("ACME INC", "Backend Engineer", "Other", "2026-07-09")));
        assertTrue(support.isDuplicate(incoming,
                application("Acme Inc", "Backend Engineer", "Other", "2026-07-03")));
        assertFalse(support.isDuplicate(incoming,
                application("Acme Inc", "Backend Engineer", "Other", "2026-07-02")));
    }

    @Test
    void datesOnOppositeSidesOfASevenDayBucketBoundaryStillDuplicate() {
        JobApplication beforeBoundary =
                application("Acme", "Backend Engineer", null, "2026-01-07");
        JobApplication afterBoundary =
                application("Acme", "Backend Engineer", null, "2026-01-08");

        assertTrue(support.isDuplicate(beforeBoundary, afterBoundary));
        assertNotEquals(
                support.importFingerprint(beforeBoundary),
                support.importFingerprint(afterBoundary));
    }

    @Test
    void undatedRowDuplicatesAnyDateForTheSameCanonicalIdentityAndTitle() {
        JobApplication undated = application(null, "Backend Engineer", "Referral", null);
        JobApplication dated = application(null, "Backend Engineer", " referral ", "2020-01-01");

        assertTrue(support.isDuplicate(undated, dated));
        assertNotNull(support.importFingerprint(undated));
    }

    @Test
    void companyTakesPrecedenceOverSourceAndIdentityKindIsPartOfTheGuardKey() {
        JobApplication companyIdentity =
                application("Acme", "Backend Engineer", "Referral", "2026-07-10");
        JobApplication sameCompanyDifferentSource =
                application("ACME", "Backend Engineer", "LinkedIn", "2026-07-10");
        JobApplication sourceIdentity =
                application(null, "Backend Engineer", "Acme", "2026-07-10");

        assertTrue(support.isDuplicate(companyIdentity, sameCompanyDifferentSource));
        assertFalse(support.isDuplicate(companyIdentity, sourceIdentity));
        assertNotEquals(
                support.guardIdentityHash(companyIdentity),
                support.guardIdentityHash(sourceIdentity));
    }

    @Test
    void missingCompanyAndSourceHasNoReliableIdentityFingerprintOrGuardKey() {
        JobApplication first = application(null, "Backend Engineer", " ", "2026-07-10");
        JobApplication second = application("", "Backend Engineer", null, "2026-07-10");

        assertFalse(support.hasReliableIdentity(first));
        assertFalse(support.isDuplicate(first, second));
        assertNull(support.guardIdentityHash(first));
        assertNull(support.importFingerprint(first));
    }

    private static JobApplication application(
            String company, String title, String source, String appliedDate) {
        JobApplication application = new JobApplication();
        application.setCompanyName(company);
        application.setJobTitle(title);
        application.setSource(source);
        application.setAppliedAt(appliedDate == null
                ? null
                : LocalDateTime.parse(appliedDate + "T09:00:00"));
        return application;
    }
}
