package com.codecoachai.file.domain.dto;

import lombok.Data;

@Data
public class AdminFileQueryDTO {

    public static final long DEFAULT_PAGE_NO = 1L;
    public static final long DEFAULT_PAGE_SIZE = 10L;
    public static final long MAX_PAGE_SIZE = 100L;

    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private Long userId;
    private String bizType;
    private String status;
    private String parseStatus;

    public long effectivePageNo() {
        return pageNo == null || pageNo < 1L ? DEFAULT_PAGE_NO : pageNo;
    }

    public long effectivePageSize() {
        if (pageSize == null || pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
