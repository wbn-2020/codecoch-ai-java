package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownLiteTest {

    @Test
    void rendersTheSubsetAndEscapesEverythingElse() {
        assertEquals("用 <strong>Java</strong> 与 <em>Kafka</em> 构建系统",
                MarkdownLite.renderInline("用 **Java** 与 *Kafka* 构建系统"));
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;",
                MarkdownLite.renderInline("<script>alert(1)</script>"));
    }

    @Test
    void onlyAllowsHttpHttpsAndMailtoLinks() {
        assertEquals("<a href=\"https://example.com\" rel=\"noopener noreferrer nofollow\" "
                        + "target=\"_blank\">主页</a>",
                MarkdownLite.renderInline("[主页](https://example.com)"));
        assertFalse(MarkdownLite.renderInline("[坏](javascript:alert(1))").contains("<a"));
        assertTrue(MarkdownLite.renderInline("[邮](mailto:a@b.com)").contains("href=\"mailto:a@b.com\""));
        assertEquals("相对", MarkdownLite.renderInline("[相对](/docs)"));
        assertNull(MarkdownLite.safeHref("ftp://example.com"));
        assertEquals("https://example.com", MarkdownLite.safeHref("  https://example.com "));
    }

    @Test
    void fallsBackToTheUrlWhenALinkHasNoLabel() {
        assertTrue(MarkdownLite.renderInline("[](https://example.com)")
                .contains(">https://example.com</a>"));
    }

    @Test
    void stripsMarkersForAtsPlainText() {
        assertEquals("主导 交易系统 文档",
                MarkdownLite.stripInline("**主导** 交易系统 [文档](https://x.cn)"));
        assertEquals("主导 系统", MarkdownLite.stripInline("**主导** 系统", true));
        assertEquals("https://x.cn (https://x.cn)",
                MarkdownLite.stripInline("[https://x.cn](https://x.cn)", true));
        assertEquals("做 了 A", MarkdownLite.stripInline("**做** 了 A"));
        assertEquals("", MarkdownLite.stripInline(null));
    }

    @Test
    void keepsUnsafeLinkTargetOutOfPlainText() {
        assertEquals("点我", MarkdownLite.stripInline("[点我](javascript:alert)", true));
    }
}
