package com.codecoachai.common.web.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等性注解。标注在 Controller 方法上，防止重复提交。
 *
 * 两种模式：
 * 1. TOKEN 模式（默认）：前端先调 GET /idempotent/token 获取 token，提交时带上 X-Idempotent-Token 请求头
 * 2. KEY 模式：根据请求参数自动生成幂等 key（适合后台接口）
 *
 * <pre>
 * &#64;Idempotent(mode = IdempotentMode.TOKEN, message = "请勿重复提交")
 * &#64;PostMapping("/submit")
 * public Result submit(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 幂等模式 */
    IdempotentMode mode() default IdempotentMode.TOKEN;

    /** 重复提交时的提示信息 */
    String message() default "请勿重复提交";

    /** KEY 模式下的 SpEL 表达式（从参数中提取 key） */
    String key() default "";

    /** 幂等窗口（秒），超过此时间允许再次提交 */
    int expireSeconds() default 10;
}
