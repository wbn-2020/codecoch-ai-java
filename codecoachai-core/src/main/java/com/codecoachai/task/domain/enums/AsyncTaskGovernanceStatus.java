package com.codecoachai.task.domain.enums;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.util.Locale;

/**
 * Human-review lifecycle for failed or stale asynchronous tasks.
 *
 * <p>This lifecycle is independent from the task execution status. Classifying
 * a task never dispatches a message; only the existing retry workflow can do
 * that after its impact preview and confirmation checks.</p>
 */
public enum AsyncTaskGovernanceStatus {
    UNASSESSED,
    RETRY_APPROVED,
    RETRYING,
    RESOLVED,
    WONT_RETRY,
    MANUAL_ACTION_REQUIRED;

    public static AsyncTaskGovernanceStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择任务治理状态");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的任务治理状态");
        }
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? UNASSESSED.name() : parse(value).name();
    }
}
