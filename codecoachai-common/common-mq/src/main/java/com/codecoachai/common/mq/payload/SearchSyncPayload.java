package com.codecoachai.common.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用搜索索引同步任务负载。
 * Topic: codecoachai-search
 * Tag: question / resume / interview
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyncPayload {

    /** 索引名 cc_question / cc_resume / cc_interview */
    private String indexName;

    /** 文档 ID（业务主键） */
    private String docId;

    /** UPSERT / DELETE */
    private String op;
}
