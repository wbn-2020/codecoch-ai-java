package com.codecoachai.common.core.domain;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private Long total;
    private Long pageNo;
    private Long pageSize;
    private Long pages;

    public static <T> PageResult<T> empty(long pageNo, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNo, pageSize, 0L);
    }

    public static <T> PageResult<T> of(List<T> records, long total, long pageNo, long pageSize) {
        long pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(records, total, pageNo, pageSize, pages);
    }
}
