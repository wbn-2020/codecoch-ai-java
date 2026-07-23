package com.codecoachai.resume.careerimport;

import com.codecoachai.resume.careerimport.CareerImportModels.DuplicateCandidate;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CareerImportCanonicalSupport {

    private static final int MAX_DUPLICATE_CANDIDATES = 5;

    public boolean hasReliableIdentity(JobApplication application) {
        return canonical(application).reliable();
    }

    public String guardIdentityHash(JobApplication application) {
        CanonicalApplication canonical = canonical(application);
        if (!canonical.reliable()) {
            return null;
        }
        return ResumeArtifactHashes.sha256(String.join(
                "|",
                "career-import-guard-v1",
                canonical.identityType(),
                canonical.identity(),
                canonical.title()));
    }

    public String importFingerprint(JobApplication application) {
        CanonicalApplication canonical = canonical(application);
        if (!canonical.reliable()) {
            return null;
        }
        String appliedDate = application.getAppliedAt() == null
                ? "undated"
                : application.getAppliedAt().toLocalDate().toString();
        return ResumeArtifactHashes.sha256(String.join(
                "|",
                "career-import-v2",
                canonical.identityType(),
                canonical.identity(),
                canonical.title(),
                appliedDate));
    }

    public boolean isDuplicate(JobApplication incoming, JobApplication existing) {
        CanonicalApplication left = canonical(incoming);
        CanonicalApplication right = canonical(existing);
        if (!left.reliable() || !left.equals(right)) {
            return false;
        }
        return incoming.getAppliedAt() == null
                || existing.getAppliedAt() == null
                || Math.abs(ChronoUnit.DAYS.between(
                        incoming.getAppliedAt().toLocalDate(),
                        existing.getAppliedAt().toLocalDate())) <= 7;
    }

    public List<DuplicateCandidate> findDuplicates(
            JobApplication incoming, List<JobApplication> applications) {
        List<DuplicateCandidate> candidates = new ArrayList<>();
        for (JobApplication application : applications) {
            if (!isDuplicate(incoming, application)) {
                continue;
            }
            DuplicateCandidate candidate = new DuplicateCandidate();
            candidate.setApplicationId(application.getId());
            candidate.setCompanyName(application.getCompanyName());
            candidate.setJobTitle(application.getJobTitle());
            candidate.setAppliedAt(application.getAppliedAt());
            candidate.setReason(incoming.getAppliedAt() == null || application.getAppliedAt() == null
                    ? "SAME_COMPANY_AND_JOB_TITLE"
                    : "SAME_COMPANY_AND_JOB_TITLE_WITHIN_7_DAYS");
            candidates.add(candidate);
            if (candidates.size() >= MAX_DUPLICATE_CANDIDATES) {
                break;
            }
        }
        return candidates;
    }

    public String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "")
                : "";
    }

    private CanonicalApplication canonical(JobApplication application) {
        String company = normalize(application.getCompanyName());
        String source = normalize(application.getSource());
        String title = normalize(application.getJobTitle());
        if (StringUtils.hasText(company)) {
            return new CanonicalApplication("company", company, title, true);
        }
        if (StringUtils.hasText(source)) {
            return new CanonicalApplication("source", source, title, true);
        }
        return new CanonicalApplication("", "", title, false);
    }

    private record CanonicalApplication(
            String identityType, String identity, String title, boolean reliable) {
    }
}
