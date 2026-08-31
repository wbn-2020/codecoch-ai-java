package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.support.ResumeTextHeuristics.NarrativeEntry;
import com.codecoachai.resume.support.ResumeTextHeuristics.SkillGroup;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeTextHeuristicsTest {

    @Test
    void normalizesLineEndingsAndNonBreakingSpaces() {
        assertEquals("a\nb c", ResumeTextHeuristics.normalizeText("a\r\nb c  "));
        assertEquals("", ResumeTextHeuristics.normalizeText(null));
    }

    @Test
    void splitLinesRemovesListMarkers() {
        assertEquals(List.of("做 A", "做 B", "做 C", "普通"),
                ResumeTextHeuristics.splitLines("- 做 A\n* 做 B\n1. 做 C\n普通"));
    }

    @Test
    void shortSentencesStayTogetherAndHeadingsAreNotSplit() {
        assertEquals(List.of("八年后端经验"), ResumeTextHeuristics.splitSentences("八年后端经验"));
        assertEquals(List.of(), ResumeTextHeuristics.splitSentences(""));
    }

    @Test
    void splitsLongNarrativeParagraphsOnChineseSentenceTails() {
        assertEquals(3, ResumeTextHeuristics.splitSentences(ResumeDocumentFixtures.SUMMARY).size());
    }

    @Test
    void splitsSkillsOnEveryDelimiter() {
        assertEquals(List.of("Java", "Spring", "MySQL"),
                ResumeTextHeuristics.splitSkills("Java, Spring、MySQL"));
    }

    @Test
    void groupsSkillsIntoTheFourBuckets() {
        List<SkillGroup> groups = ResumeTextHeuristics.groupSkills(
                ResumeTextHeuristics.splitSkills(ResumeDocumentFixtures.SKILLS));

        assertEquals(List.of("语言与基础", "框架与架构", "数据与中间件", "工程实践"),
                groups.stream().map(SkillGroup::label).toList());
        assertEquals(List.of("Java"), groups.get(0).items());
        assertEquals(List.of("Spring", "Vue"), groups.get(1).items());
        assertEquals(List.of("MySQL", "Redis", "Kafka"), groups.get(2).items());
        assertEquals(List.of("Docker", "Kubernetes", "ELK"), groups.get(3).items());
    }

    @Test
    void extractsEntryTitleAndDateRange() {
        var parsed = ResumeTextHeuristics.splitTitleAndPeriod("字节跳动 · 后端开发    2021.03-至今");
        assertEquals("字节跳动 · 后端开发", parsed.title());
        assertEquals("2021.03-至今", parsed.period());

        var emDash = ResumeTextHeuristics.splitTitleAndPeriod("美团 2018.07—2021.02");
        assertEquals("美团", emDash.title());
        assertEquals("2018.07-2021.02", emDash.period());

        var none = ResumeTextHeuristics.splitTitleAndPeriod("负责交易系统稳定性建设");
        assertEquals("负责交易系统稳定性建设", none.title());
        assertEquals("", none.period());
    }

    @Test
    void buildsNarrativeEntriesFromFlatWorkExperience() {
        List<NarrativeEntry> entries = ResumeTextHeuristics.buildNarrativeEntries(
                ResumeDocumentFixtures.WORK_EXPERIENCE, "工作经历", "sec-work");

        assertEquals(2, entries.size());
        assertEquals("字节跳动 · 后端开发", entries.get(0).title());
        assertEquals("2021.03-至今", entries.get(0).period());
        assertEquals(List.of("负责交易系统稳定性建设，核心接口 P99 降低 40%。",
                        "搭建可观测平台，故障定位时间缩短 60%。"),
                entries.get(0).bullets());
        assertEquals("美团 · Java 开发", entries.get(1).title());
        assertEquals(1, entries.get(1).bullets().size());
    }

    @Test
    void narrativeLeadInLinesNeverBecomeTitles() {
        List<NarrativeEntry> entries = ResumeTextHeuristics.buildNarrativeEntries(
                "工作经历\n负责交易系统稳定性建设，核心接口 P99 降低 40%。", "工作经历", "sec-work");

        assertEquals(1, entries.size());
        assertEquals("", entries.get(0).title());
        assertEquals(1, entries.get(0).bullets().size());
    }

    @Test
    void ambiguousTitlesNeedBothCompanyAndRoleHints() {
        assertTrue(ResumeTextHeuristics.isLikelyEntryTitle("字节跳动 · 后端开发", "", "工作经历"));
        assertFalse(ResumeTextHeuristics.isLikelyEntryTitle("自由职业", "", "工作经历"));
        assertTrue(ResumeTextHeuristics.isLikelyEntryTitle("华中科技大学", "", "教育经历"));
        assertFalse(ResumeTextHeuristics.isLikelyEntryTitle("就读华中科技大学", "", "教育经历"));
    }

    @Test
    void sentenceBulletsSplitWhenALineCarriesMultipleSentences() {
        List<String> bullets = ResumeTextHeuristics.splitSentences(
                "负责交易系统稳定性建设，核心接口 P99 降低 40%。搭建可观测平台，故障定位时间缩短 60%。"
                        + "推动代码评审制度落地，缺陷密度下降三分之一。");

        assertEquals(3, bullets.size());
        assertTrue(bullets.get(0).endsWith("40%。"));
    }
}
