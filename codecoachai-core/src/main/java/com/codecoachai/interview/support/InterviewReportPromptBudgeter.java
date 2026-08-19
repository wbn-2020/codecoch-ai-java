package com.codecoachai.interview.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Bounds report prompt input without modifying the persisted interview messages.
 */
public final class InterviewReportPromptBudgeter {

    static final int MAX_MESSAGE_COUNT = 96;
    static final int MAX_TOTAL_CHARACTERS = 24_000;
    private static final int MIN_BUCKET_CHARACTERS = 180;
    private static final int MAX_BUCKET_PREFIX_CHARACTERS = 48;
    private static final String COMPACTION_NOTICE =
            "对话较长，以下内容按固定预算分段压缩；完整问答仍以已保存记录为准，逐题复盘由后端回填。";

    private InterviewReportPromptBudgeter() {
    }

    public static List<String> budget(List<String> rawMessages) {
        List<String> normalized = normalize(rawMessages);
        if (normalized.isEmpty()) {
            return List.of();
        }
        int originalCharacters = normalized.stream().mapToInt(String::length).sum();
        if (normalized.size() <= MAX_MESSAGE_COUNT && originalCharacters <= MAX_TOTAL_CHARACTERS) {
            return normalized;
        }

        int bucketCount = Math.min(MAX_MESSAGE_COUNT - 1, normalized.size());
        int budgetPerBucket = Math.max(MIN_BUCKET_CHARACTERS,
                (MAX_TOTAL_CHARACTERS - COMPACTION_NOTICE.length()
                        - MAX_BUCKET_PREFIX_CHARACTERS * bucketCount) / bucketCount);
        List<String> compacted = new ArrayList<>(bucketCount + 1);
        compacted.add(COMPACTION_NOTICE);
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            int start = bucket * normalized.size() / bucketCount;
            int endExclusive = (bucket + 1) * normalized.size() / bucketCount;
            String summary = summarizeBucket(normalized.subList(start, endExclusive), budgetPerBucket);
            compacted.add("对话分段 " + (bucket + 1) + "/" + bucketCount
                    + "（原记录 " + (start + 1) + "-" + endExclusive + "/" + normalized.size() + "）："
                    + summary);
        }
        return compacted;
    }

    private static List<String> normalize(List<String> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }
        return rawMessages.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().replaceAll("\\s+", " "))
                .toList();
    }

    private static String summarizeBucket(List<String> entries, int maxCharacters) {
        if (entries == null || entries.isEmpty()) {
            return "无有效内容";
        }
        if (entries.size() == 1) {
            return abbreviate(entries.get(0), maxCharacters);
        }
        String omission = "；中间省略 " + Math.max(0, entries.size() - 2) + " 条；";
        int contentBudget = Math.max(2, maxCharacters - omission.length());
        int firstBudget = Math.max(1, contentBudget * 3 / 5);
        int tailBudget = Math.max(1, contentBudget - firstBudget);
        String first = abbreviate(entries.get(0), firstBudget);
        String tail = abbreviate(entries.get(entries.size() - 1), tailBudget);
        return first + omission + tail;
    }

    private static String abbreviate(String value, int maxCharacters) {
        if (!StringUtils.hasText(value) || value.length() <= maxCharacters) {
            return value;
        }
        return value.substring(0, Math.max(1, maxCharacters - 3)) + "...";
    }
}
