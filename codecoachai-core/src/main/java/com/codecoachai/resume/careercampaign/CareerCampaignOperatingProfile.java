package com.codecoachai.resume.careercampaign;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_campaign_operating_profile")
public class CareerCampaignOperatingProfile extends BaseEntity {

    private Long userId;
    private Long campaignId;
    private Integer weeklyApplicationTarget;
    private Integer weeklyTimeBudgetMinutes;
    private Integer maxActiveOpportunities;
    private Integer staleAfterDays;
    private Integer defaultFollowUpDays;
    private String focusRolesJson;
    private String focusLocationsJson;
    private String focusChannelsJson;
    private String timezone;
    private Integer lockVersion;
    private Long activeGuard;
}
