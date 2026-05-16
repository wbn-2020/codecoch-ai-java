package com.codecoachai.question.util;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class QuestionTextNormalizeUtils {

    private static final Set<String> PREFIXES = Set.of(
            "请解释", "请说明", "说一下", "谈谈", "如何理解", "请问", "简述", "描述一下"
    );

    private QuestionTextNormalizeUtils() {
    }

    public static String normalizeTitle(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().toLowerCase();
        boolean removed = true;
        while (removed) {
            removed = false;
            for (String prefix : PREFIXES) {
                if (normalized.startsWith(prefix)) {
                    normalized = normalized.substring(prefix.length()).trim();
                    removed = true;
                }
            }
        }
        return normalized.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）【】《》]+", "");
    }

    public static Set<String> tokens(String text) {
        String normalized = normalizeForTokens(text);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        return Arrays.stream(normalized.split("\\s+"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    public static double jaccard(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0D;
        }
        long intersection = leftTokens.stream().filter(rightTokens::contains).count();
        long union = leftTokens.size() + rightTokens.size() - intersection;
        return union == 0 ? 0D : (double) intersection / union;
    }

    public static double levenshteinSimilarity(String left, String right) {
        String safeLeft = normalizeTitle(left);
        String safeRight = normalizeTitle(right);
        if (!StringUtils.hasText(safeLeft) || !StringUtils.hasText(safeRight)) {
            return 0D;
        }
        int max = Math.max(safeLeft.length(), safeRight.length());
        if (max == 0) {
            return 1D;
        }
        return 1D - ((double) levenshteinDistance(safeLeft, safeRight) / max);
    }

    public static String snapshot(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static String normalizeForTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .toLowerCase()
                .replaceAll("[\\p{Punct}，。！？、；：“”‘’（）【】《》]+", " ")
                .replaceAll("\\s+", " ");
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }
}
