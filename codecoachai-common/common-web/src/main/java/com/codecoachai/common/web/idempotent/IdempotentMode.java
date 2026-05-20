package com.codecoachai.common.web.idempotent;

public enum IdempotentMode {
    /** 前端先获取 token，提交时带上 */
    TOKEN,
    /** 根据请求参数自动生成 key */
    KEY
}
