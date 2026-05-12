package com.codecoachai.common.feign.util;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;

public final class FeignResultUtils {

    private FeignResultUtils() {
    }

    public static <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Spring Cloud OpenFeign 调用无响应");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
