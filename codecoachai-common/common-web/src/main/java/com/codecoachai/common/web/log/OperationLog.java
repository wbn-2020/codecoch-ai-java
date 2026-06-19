package com.codecoachai.common.web.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。标注在 Controller 方法上，自动记录操作日志到 operation_log 表。
 *
 * <pre>
 * &#64;OperationLog(module = "question", action = "CREATE", description = "新增题目")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 业务模块 */
    String module();

    /** 动作：CREATE / UPDATE / DELETE / APPROVE / EXPORT 等 */
    String action();

    /** 描述（可选） */
    String description() default "";

    /** 是否记录请求参数（默认 false，敏感接口需显式开启并经过脱敏） */
    boolean logArgs() default false;

    /** 是否记录响应（默认 false，大响应体不建议开） */
    boolean logResponse() default false;
}
