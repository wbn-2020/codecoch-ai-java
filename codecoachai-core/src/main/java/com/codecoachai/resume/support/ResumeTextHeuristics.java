package com.codecoachai.resume.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Narrative parsing heuristics for legacy flat resume columns. Mirrors the client
 * {@code resume-document.ts} rules so a server-synthesized document v2 matches the browser preview.
 */
public final class ResumeTextHeuristics {

    /** Below this length a paragraph is kept as a single sentence. */
    private static final int SENTENCE_SPLIT_MIN_LENGTH = 58;

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
    private static final Pattern BLANK_LINE_RUN = Pattern.compile("\\n{2,}");
    private static final Pattern LINE_RUN = Pattern.compile("\\n+");
    private static final Pattern LIST_MARKER =
            Pattern.compile("^\\s*(?:(?:[-*•·])\\s*|(?:\\d+[.)、])\\s+)");
    private static final Pattern SENTENCE_TAIL = Pattern.compile("(?<=[。！？；;])\\s*");
    private static final Pattern SKILL_DELIMITERS = Pattern.compile("[，,、\\n/|；;]+");
    private static final Pattern SECTION_HEADING_PUNCTUATION = Pattern.compile("[：:]");
    private static final Pattern DATE_RANGE = Pattern.compile(
            "((?:19|20)\\d{2}(?:[./-]\\d{1,2})?\\s*(?:-|–|—|至|~)\\s*"
                    + "(?:(?:19|20)\\d{2}(?:[./-]\\d{1,2})?|至今|现在|Present))$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_SEPARATOR = Pattern.compile("[|·｜]\\s*$");
    private static final Pattern NARRATIVE_SENTENCE = Pattern.compile("[。！？；;]");
    private static final Pattern NARRATIVE_LEAD_IN = Pattern.compile(
            "^(?:负责|参与|主导|推动|协助|就读|毕业(?:于)?|主修|获得|担任|"
                    + "在[^|｜·]{1,24}(?:(?:期间|任职(?:期间)?|就读(?:期间)?|学习(?:期间)?)"
                    + "(?:主修|负责|参与|担任|学习|工作|获得)"
                    + "|工作(?:期间|时|中)?(?:负责|参与|担任|完成|实现|推动|协助))"
                    + "|完成(?:了|某|项目|系统)|实现(?:了|某|系统|功能|项目)"
                    + "|优化(?:了|某|系统|性能|流程|项目)|维护(?:了|过|某|系统|服务|项目)"
                    + "|支持(?:了|某|系统|项目))");
    private static final Pattern SKILL_LANGUAGE = Pattern.compile(
            "\\b(?:java|kotlin|go|golang|python|c\\+\\+|c#|javascript|typescript|sql|html|css)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_COMPUTER_BASICS =
            Pattern.compile("算法|数据结构|计算机网络|操作系统");
    private static final Pattern SKILL_FRAMEWORK = Pattern.compile(
            "spring|vue|react|angular|node|nestjs|django|flask|微服务|分布式|ddd|架构",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_DATA = Pattern.compile(
            "mysql|postgres|oracle|redis|mongo|kafka|rabbit|rocket|mq|elasticsearch|clickhouse|数据库",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPANY_HINT =
            Pattern.compile("公司|集团|科技|银行|研究院|事务所|工作室|实验室");
    private static final Pattern ROLE_HINT =
            Pattern.compile("工程师|开发|架构|产品|运营|设计|经理|主管|顾问|实习|负责人");
    private static final Pattern SCHOOL_HINT = Pattern.compile("大学|学院|学校|研究院");
    private static final Pattern SEPARATOR = Pattern.compile("[|｜·]");

    private static final String WORK_EXPERIENCE_TITLE = "工作经历";

    private ResumeTextHeuristics() {
    }

    public record SkillGroup(String label, List<String> items) {
    }

    public record NarrativeEntry(String key, String title, String period, List<String> bullets) {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ').trim();
    }

    public static List<String> splitLines(String value) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String raw : LINE_RUN.split(normalized)) {
            String line = LIST_MARKER.matcher(raw).replaceFirst("").trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    public static List<String> splitSentences(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<String> explicit = splitLines(value);
        if (explicit.size() > 1) {
            return explicit;
        }
        // List markers must not survive: projecting a document back onto the flat columns prefixes
        // "- " again, so keeping them would grow the text on every save.
        String single = explicit.isEmpty() ? "" : explicit.get(0);
        if (single.isEmpty()) {
            return List.of();
        }
        if (single.length() < SENTENCE_SPLIT_MIN_LENGTH) {
            return List.of(single);
        }
        List<String> sentences = new ArrayList<>();
        for (String raw : SENTENCE_TAIL.split(single)) {
            String sentence = raw.trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    public static List<String> splitSkills(String value) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> skills = new ArrayList<>();
        for (String raw : SKILL_DELIMITERS.split(normalized)) {
            String skill = raw.trim();
            if (!skill.isEmpty()) {
                skills.add(skill);
            }
        }
        return skills;
    }

    public static List<SkillGroup> groupSkills(List<String> skills) {
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        buckets.put("语言与基础", new ArrayList<>());
        buckets.put("框架与架构", new ArrayList<>());
        buckets.put("数据与中间件", new ArrayList<>());
        buckets.put("工程实践", new ArrayList<>());

        for (String skill : skills) {
            if (SKILL_LANGUAGE.matcher(skill).find() || SKILL_COMPUTER_BASICS.matcher(skill).find()) {
                buckets.get("语言与基础").add(skill);
            } else if (SKILL_FRAMEWORK.matcher(skill).find()) {
                buckets.get("框架与架构").add(skill);
            } else if (SKILL_DATA.matcher(skill).find()) {
                buckets.get("数据与中间件").add(skill);
            } else {
                buckets.get("工程实践").add(skill);
            }
        }

        List<SkillGroup> groups = new ArrayList<>();
        buckets.forEach((label, items) -> {
            if (!items.isEmpty()) {
                groups.add(new SkillGroup(label, List.copyOf(items)));
            }
        });
        return groups;
    }

    /**
     * Free-text narrative → titled entries. A leading line counts as an entry title only when it
     * reads like one; otherwise the whole block becomes bullet evidence.
     */
    public static List<NarrativeEntry> buildNarrativeEntries(String value, String fallbackTitle, String prefix) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<NarrativeEntry> entries = new ArrayList<>();
        String[] blocks = BLANK_LINE_RUN.split(normalized);
        for (int index = 0; index < blocks.length; index++) {
            List<String> lines = new ArrayList<>();
            for (String line : splitLines(blocks[index])) {
                if (!isSectionHeading(line, fallbackTitle)) {
                    lines.add(line);
                }
            }
            if (lines.isEmpty()) {
                continue;
            }

            TitlePeriod first = splitTitleAndPeriod(lines.get(0));
            boolean hasDistinctTitle = isLikelyEntryTitle(first.title(), first.period(), fallbackTitle);
            boolean hasStandalonePeriod = !first.period().isEmpty() && first.title().isEmpty();
            TitlePeriod following = hasDistinctTitle && first.period().isEmpty() && lines.size() > 1
                    ? splitTitleAndPeriod(lines.get(1))
                    : new TitlePeriod("", "");
            boolean hasFollowingPeriod = !following.period().isEmpty() && following.title().isEmpty();
            int bodyStart = hasDistinctTitle
                    ? (hasFollowingPeriod ? 2 : 1)
                    : hasStandalonePeriod ? 1 : 0;
            List<String> bodyLines = new ArrayList<>(lines.subList(bodyStart, lines.size()));
            String period = "";
            if (hasDistinctTitle || hasStandalonePeriod) {
                period = first.period().isEmpty() ? following.period() : first.period();
            }

            entries.add(new NarrativeEntry(
                    prefix + "-" + index,
                    hasDistinctTitle ? first.title() : "",
                    period,
                    splitBullets(bodyLines)));
        }
        return entries;
    }

    static TitlePeriod splitTitleAndPeriod(String value) {
        String normalized = WHITESPACE_RUN.matcher(value == null ? "" : value).replaceAll(" ").trim();
        var matcher = DATE_RANGE.matcher(normalized);
        if (!matcher.find()) {
            return new TitlePeriod(normalized, "");
        }
        return new TitlePeriod(
                TRAILING_SEPARATOR.matcher(normalized.substring(0, matcher.start()).trim()).replaceAll(""),
                matcher.group(1).replace('—', '-'));
    }

    static boolean isLikelyEntryTitle(String value, String period, String fallbackTitle) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (NARRATIVE_LEAD_IN.matcher(value).find()) {
            return false;
        }
        if (!period.isEmpty() || SEPARATOR.matcher(value).find()) {
            return true;
        }
        if (value.length() > 45 || NARRATIVE_SENTENCE.matcher(value).find()) {
            return false;
        }
        if (WORK_EXPERIENCE_TITLE.equals(fallbackTitle)) {
            return COMPANY_HINT.matcher(value).find() && ROLE_HINT.matcher(value).find();
        }
        return SCHOOL_HINT.matcher(value).find();
    }

    private static List<String> splitBullets(List<String> bodyLines) {
        List<String> bullets = new ArrayList<>();
        bodyLines.forEach(line -> bullets.addAll(splitSentences(line)));
        return bullets;
    }

    private static boolean isSectionHeading(String value, String fallbackTitle) {
        return SECTION_HEADING_PUNCTUATION.matcher(WHITESPACE_RUN.matcher(value).replaceAll("")).replaceAll("")
                .equals(fallbackTitle);
    }

    record TitlePeriod(String title, String period) {

        TitlePeriod {
            title = title == null ? "" : title;
            period = period == null ? "" : period;
        }
    }
}
