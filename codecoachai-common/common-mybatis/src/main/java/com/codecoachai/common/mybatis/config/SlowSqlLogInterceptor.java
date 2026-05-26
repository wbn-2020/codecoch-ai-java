package com.codecoachai.common.mybatis.config;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Captures slow MyBatis statements for admin-side diagnosis. Logging failures
 * are swallowed because SQL observation must never affect business requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class SlowSqlLogInterceptor implements Interceptor {

    private static final String INSERT_SQL =
            "INSERT INTO slow_sql_log (mapper_id, sql_command_type, sql_text, parameter_summary, database_name, " +
                    "cost_ms, threshold_ms, result_size, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong nextWarnAt = new AtomicLong(0);

    @Value("${codecoachai.slow-sql.threshold-ms:800}")
    private long thresholdMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable failure = null;
        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            long costMs = System.currentTimeMillis() - start;
            if (costMs >= thresholdMs) {
                recordSlowSql(invocation, result, failure, costMs);
            }
        }
    }

    private void recordSlowSql(Invocation invocation, Object result, Throwable failure, long costMs) {
        try {
            Object[] args = invocation.getArgs();
            MappedStatement statement = (MappedStatement) args[0];
            Object parameter = args.length > 1 ? args[1] : null;
            BoundSql boundSql = args.length > 5 && args[5] instanceof BoundSql
                    ? (BoundSql) args[5]
                    : statement.getBoundSql(parameter);

            String sql = normalizeSql(boundSql.getSql());
            if (!StringUtils.hasText(sql) || sql.toLowerCase(Locale.ROOT).contains("slow_sql_log")) {
                return;
            }

            jdbcTemplate.update(INSERT_SQL,
                    truncate(statement.getId(), 255),
                    statement.getSqlCommandType().name(),
                    truncate(sql, 8000),
                    truncate(summarizeParameter(parameter), 1000),
                    resolveDatabaseName(statement),
                    costMs,
                    thresholdMs,
                    resultSize(result, failure),
                    LocalDateTime.now());
        } catch (Exception ex) {
            long now = System.currentTimeMillis();
            long warnAt = nextWarnAt.get();
            if (now >= warnAt && nextWarnAt.compareAndSet(warnAt, now + 60_000)) {
                log.warn("慢 SQL 日志写入失败，已跳过本次记录", ex);
            }
        }
    }

    private String resolveDatabaseName(MappedStatement statement) {
        try {
            return statement.getConfiguration().getEnvironment().getId();
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer resultSize(Object result, Throwable failure) {
        if (failure != null) {
            return null;
        }
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        if (result instanceof Number number) {
            return number.intValue();
        }
        return result == null ? 0 : 1;
    }

    private String summarizeParameter(Object parameter) {
        if (parameter == null) {
            return null;
        }
        if (parameter instanceof Number || parameter instanceof CharSequence || parameter instanceof Boolean) {
            return String.valueOf(parameter);
        }
        return parameter.getClass().getSimpleName();
    }

    private String normalizeSql(String sql) {
        return sql == null ? null : sql.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }
}
