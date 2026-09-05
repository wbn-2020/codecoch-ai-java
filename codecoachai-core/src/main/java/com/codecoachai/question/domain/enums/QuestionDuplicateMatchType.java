package com.codecoachai.question.domain.enums;

public enum QuestionDuplicateMatchType {
    HARD_TITLE_HASH,
    HARD_CONTENT_HASH,
    TITLE_EXACT,
    TITLE_NORMALIZED_EQUAL,
    TITLE_SIMILAR,
    CONTENT_SIMILAR,
    SEMANTIC_SIMILAR,
    MANUAL
}
