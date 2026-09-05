package com.codecoachai.interview.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InterviewReportPromptBudgeterTest {

    @Test
    void preservesShortDialogueWithoutRewritingIt() {
        List<String> input = List.of("Role:AI Question:Q1", "Role:USER CandidateAnswer:A1");

        List<String> result = InterviewReportPromptBudgeter.budget(input);

        assertEquals(input, result);
    }

    @Test
    void compactsLongDialogueIntoBoundedSegmentSummaries() {
        List<String> input = IntStream.range(0, 160)
                .mapToObj(index -> "Role:USER CandidateAnswer:" + "x".repeat(1_000) + index)
                .toList();

        List<String> result = InterviewReportPromptBudgeter.budget(input);

        assertTrue(result.size() <= InterviewReportPromptBudgeter.MAX_MESSAGE_COUNT);
        assertTrue(result.get(0).contains("固定预算分段压缩"));
        assertTrue(result.stream().skip(1).allMatch(item -> item.contains("原记录")));
        assertTrue(result.stream().mapToInt(String::length).sum()
                <= InterviewReportPromptBudgeter.MAX_TOTAL_CHARACTERS);
        assertEquals(160, input.size(), "The persisted input collection must not be mutated.");
    }
}
