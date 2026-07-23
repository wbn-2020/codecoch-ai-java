package com.codecoachai.ai.agent.service.support;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical scope representation used by new writes while keeping legacy reads safe.
 */
public final class AgentScopeKey {

    public static final String GLOBAL = "GLOBAL";
    private static final String TARGET_JOB_PREFIX = "TARGET_JOB:";
    private static final String LEGACY_JOB_PREFIX = "JOB:";

    private AgentScopeKey() {
    }

    public static String write(Long targetJobId) {
        return targetJobId == null ? GLOBAL : TARGET_JOB_PREFIX + targetJobId;
    }

    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return GLOBAL;
        }
        String raw = value.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        if (GLOBAL.equals(upper) || "ALL".equals(upper)) {
            return GLOBAL;
        }
        Long targetJobId = targetJobId(raw);
        return targetJobId == null ? raw : write(targetJobId);
    }

    public static Long targetJobId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.regionMatches(true, 0, TARGET_JOB_PREFIX, 0, TARGET_JOB_PREFIX.length())) {
            candidate = candidate.substring(TARGET_JOB_PREFIX.length());
        } else if (candidate.regionMatches(true, 0, LEGACY_JOB_PREFIX, 0, LEGACY_JOB_PREFIX.length())) {
            candidate = candidate.substring(LEGACY_JOB_PREFIX.length());
        }
        try {
            long parsed = Long.parseLong(candidate);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Set<String> readAliases(String value) {
        String normalized = normalize(value);
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(normalized);
        Long targetJobId = targetJobId(normalized);
        if (targetJobId == null) {
            aliases.add("ALL");
        } else {
            aliases.add(write(targetJobId));
            aliases.add(LEGACY_JOB_PREFIX + targetJobId);
            aliases.add(String.valueOf(targetJobId));
        }
        return aliases;
    }
}
