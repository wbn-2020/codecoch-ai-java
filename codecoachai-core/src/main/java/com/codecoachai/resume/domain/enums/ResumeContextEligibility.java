package com.codecoachai.resume.domain.enums;

import com.codecoachai.resume.domain.entity.Resume;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Keeps obviously malformed resume content out of default, matching and interview contexts.
 * Incomplete drafts remain editable and are reported as NEEDS_REVIEW rather than rejected.
 */
public enum ResumeContextEligibility {
    ELIGIBLE,
    NEEDS_REVIEW,
    BLOCKED;

    public static Assessment assess(Resume resume) {
        if (resume == null) {
            return new Assessment(BLOCKED, "RESUME_NOT_FOUND", "简历不存在或已不可用");
        }
        return assess(resume.getTitle(), resume.getRealName(), resume.getTargetPosition(),
                resume.getSummary(), resume.getWorkExperience());
    }

    public static Assessment assess(String title, String realName, String targetPosition,
                                    String summary, String workExperience) {
        if (isClearlyMalformed(title)
                || isClearlyMalformed(realName)
                || isClearlyMalformed(targetPosition)
                || isClearlyMalformed(summary)
                || isClearlyMalformed(workExperience)) {
            return new Assessment(BLOCKED, "MALFORMED_CONTENT", "简历包含无法用于求职上下文的异常内容，请先检查并修正");
        }
        if (!StringUtils.hasText(title)
                || !StringUtils.hasText(realName)
                || !StringUtils.hasText(targetPosition)
                || (!StringUtils.hasText(summary)
                && !StringUtils.hasText(workExperience))) {
            return new Assessment(NEEDS_REVIEW, "INCOMPLETE_PROFILE", "请补充姓名、目标岗位和个人经历后再用于匹配或面试");
        }
        return new Assessment(ELIGIBLE, null, null);
    }

    private static boolean isClearlyMalformed(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.matches("\\d+")) {
            return true;
        }
        int punctuation = 0;
        int meaningful = 0;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isLetterOrDigit(current) || Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                meaningful++;
            } else if (!Character.isWhitespace(current)) {
                punctuation++;
            }
        }
        if (meaningful < 2) {
            return true;
        }
        if (normalized.length() >= 5 && punctuation * 100 / normalized.length() > 40) {
            return true;
        }
        String[] latinTokens = normalized.toLowerCase(Locale.ROOT).split("\\s+");
        if (latinTokens.length >= 3) {
            int shortTokenCount = 0;
            for (String token : latinTokens) {
                if (token.matches("[a-z]{1,2}")) {
                    shortTokenCount++;
                }
            }
            return shortTokenCount == latinTokens.length;
        }
        return false;
    }

    public record Assessment(ResumeContextEligibility status, String reasonCode, String message) {

        public boolean isEligible() {
            return status == ELIGIBLE;
        }
    }
}
