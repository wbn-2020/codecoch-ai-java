package com.codecoachai.common.web.sensitive;

/**
 * 脱敏类型枚举。
 */
public enum SensitiveType {
    /** 手机号：138****1234 */
    PHONE,
    /** 邮箱：t***@example.com */
    EMAIL,
    /** 身份证：110***********1234 */
    ID_CARD,
    /** 姓名：张* / 张*三 */
    NAME,
    /** 地址：保留前 6 位 */
    ADDRESS,
    /** 银行卡：6222 **** **** 1234 */
    BANK_CARD,
    /** 自定义：全部替换为 *** */
    CUSTOM
}
