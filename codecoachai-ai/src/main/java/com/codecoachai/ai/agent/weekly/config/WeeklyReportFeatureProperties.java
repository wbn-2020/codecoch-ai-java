package com.codecoachai.ai.agent.weekly.config;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.v6.features")
public class WeeklyReportFeatureProperties {

    private boolean weeklyReportEnabled;
    private boolean weeklyReportAiEnabled;
    private boolean weeklyReportPlanDraftEnabled;
    private String weeklyReportDefaultTimezone = "Asia/Shanghai";

    public void requireWeeklyReportEnabled() {
        if (!weeklyReportEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "AI 求职周报生成功能未启用");
        }
    }
}
