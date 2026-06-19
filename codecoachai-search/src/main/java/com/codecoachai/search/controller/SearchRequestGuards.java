package com.codecoachai.search.controller;

import org.springframework.util.StringUtils;

final class SearchRequestGuards {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RESULT_WINDOW = 2000;
    private static final int MIN_FUZZY_KEYWORD_LENGTH = 3;

    private SearchRequestGuards() {
    }

    static PageWindow normalizePage(Integer pageNo, Integer pageSize) {
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int maxPageNo = Math.max(1, MAX_RESULT_WINDOW / safePageSize);
        safePageNo = Math.min(safePageNo, maxPageNo);
        return new PageWindow(safePageNo, safePageSize, (safePageNo - 1) * safePageSize);
    }

    static boolean allowsFuzziness(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        String trimmed = keyword.trim();
        return trimmed.codePointCount(0, trimmed.length()) >= MIN_FUZZY_KEYWORD_LENGTH;
    }

    record PageWindow(int pageNo, int pageSize, int from) {
    }
}
