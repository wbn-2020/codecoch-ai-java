package com.codecoachai.common.web.idempotent;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 幂等性 AOP 切面。
 * 依赖 StringRedisTemplate（common-redis 提供）。
 */
@Slf4j
@Aspect
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String TOKEN_HEADER = "X-Idempotent-Token";
    private static final String KEY_PREFIX = "codecoachai:idempotent:";

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String lockKey = buildKey(joinPoint, idempotent);

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(idempotent.expireSeconds()));

        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            // 业务异常时释放锁，允许重试
            redisTemplate.delete(lockKey);
            throw ex;
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        if (idempotent.mode() == IdempotentMode.TOKEN) {
            return buildTokenKey();
        } else {
            return buildAutoKey(joinPoint, idempotent);
        }
    }

    private String buildTokenKey() {
        HttpServletRequest request = currentRequest();
        String token = request != null ? request.getHeader(TOKEN_HEADER) : null;
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少幂等 Token，请先获取");
        }
        return KEY_PREFIX + "token:" + token;
    }

    private String buildAutoKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String methodKey = sig.getDeclaringType().getSimpleName() + "#" + sig.getMethod().getName();
        Long userId = LoginUserContext.getUserId();
        String userPart = userId != null ? String.valueOf(userId) : "anon";

        if (StringUtils.hasText(idempotent.key())) {
            // 简化：直接用 SpEL key 值作为后缀（完整 SpEL 解析可后续增强）
            return KEY_PREFIX + "key:" + methodKey + ":" + userPart + ":" + idempotent.key();
        }
        // 默认用方法名 + userId
        return KEY_PREFIX + "key:" + methodKey + ":" + userPart;
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
