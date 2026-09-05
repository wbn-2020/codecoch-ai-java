package com.codecoachai.resume.support;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown-lite subset used by resume document v2 rich text: {@code **bold**}, {@code *italic*},
 * {@code [label](url)}. Everything outside the subset stays plain text.
 * Preview renders the subset to escaped HTML; ATS export strips it back to plain text.
 */
public final class MarkdownLite {

    public static final Set<String> ALLOWED_LINK_PROTOCOLS = Set.of("http:", "https:", "mailto:");

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\(([^)\\s]*)\\)");
    private static final Pattern STRONG = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern EMPHASIS_STAR =
            Pattern.compile("(^|[\\s(（【])\\*([^*\\n]+)\\*(?=[\\s)）】.,，。;；!！?？:]|$)");
    private static final Pattern EMPHASIS_UNDERSCORE =
            Pattern.compile("(^|[\\s(（【])_([^_\\n]+)_(?=[\\s)）】.,，。;；!！?？:]|$)");
    private static final Pattern SCHEME_PREFIX = Pattern.compile("^[A-Za-z]+:");

    private MarkdownLite() {
    }

    /** ATS-safe plain text: markdown-lite markers removed, link label kept. */
    public static String stripInline(String text) {
        return stripInline(text, false);
    }

    public static String stripInline(String text, boolean includeLinkTarget) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return EMPHASIS_UNDERSCORE.matcher(EMPHASIS_STAR.matcher(
                STRONG.matcher(replaceLinks(text, includeLinkTarget)).replaceAll("$1"))
                .replaceAll("$1$2"))
                .replaceAll("$1$2");
    }

    /** Preview HTML: escaped text plus the markdown-lite subset as markup. */
    public static String renderInline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        Matcher matcher = LINK.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            html.append(renderEmphasis(escapeHtml(text.substring(cursor, matcher.start()))));
            String label = matcher.group(1);
            String url = matcher.group(2);
            html.append(renderAnchor(
                    label == null || label.isEmpty() ? url : label,
                    safeHref(url == null ? "" : url)));
            cursor = matcher.end();
        }
        html.append(renderEmphasis(escapeHtml(text.substring(cursor))));
        return html.toString();
    }

    /** Returns the trimmed url when its protocol is allowed, otherwise null. */
    public static String safeHref(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty() || !SCHEME_PREFIX.matcher(trimmed).find()) {
            return null;
        }
        int schemeEnd = trimmed.indexOf(':');
        String protocol = trimmed.substring(0, schemeEnd + 1).toLowerCase(java.util.Locale.ROOT);
        return ALLOWED_LINK_PROTOCOLS.contains(protocol) ? trimmed : null;
    }

    public static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }

    private static String replaceLinks(String text, boolean includeLinkTarget) {
        Matcher matcher = LINK.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        int cursor = 0;
        while (matcher.find()) {
            result.append(text, cursor, matcher.start());
            String label = matcher.group(1) == null ? "" : matcher.group(1);
            String url = matcher.group(2) == null ? "" : matcher.group(2);
            String visible = label.isEmpty() ? url : label;
            boolean appendTarget = includeLinkTarget && safeHref(url) != null;
            result.append(appendTarget && !visible.isEmpty() && !url.isEmpty()
                    ? visible + " (" + url + ")"
                    : visible);
            cursor = matcher.end();
        }
        result.append(text.substring(cursor));
        return result.toString();
    }

    private static String renderAnchor(String label, String href) {
        String escapedLabel = renderEmphasis(escapeHtml(label == null ? "" : label));
        if (href == null) {
            return escapedLabel;
        }
        return "<a href=\"" + escapeHtml(href) + "\" rel=\"noopener noreferrer nofollow\" target=\"_blank\">"
                + escapedLabel + "</a>";
    }

    private static String renderEmphasis(String escaped) {
        return EMPHASIS_UNDERSCORE.matcher(EMPHASIS_STAR.matcher(
                STRONG.matcher(escaped).replaceAll("<strong>$1</strong>"))
                .replaceAll("$1<em>$2</em>"))
                .replaceAll("$1<em>$2</em>");
    }
}
