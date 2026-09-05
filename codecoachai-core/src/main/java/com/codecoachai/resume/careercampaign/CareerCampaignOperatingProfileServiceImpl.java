package com.codecoachai.resume.careercampaign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels.OperatingProfileView;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels.SaveRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerCampaignOperatingProfileServiceImpl
        implements CareerCampaignOperatingProfileService {

    private static final int MAX_WEEKLY_APPLICATION_TARGET = 100;
    private static final int MAX_WEEKLY_TIME_BUDGET_MINUTES = 10080;
    private static final int MAX_ACTIVE_OPPORTUNITIES = 100;
    private static final int MAX_STALE_AFTER_DAYS = 365;
    private static final int MAX_FOLLOW_UP_DAYS = 90;
    private static final int MAX_LIST_ITEMS = 20;
    private static final int MAX_ITEM_LENGTH = 100;

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final CareerCampaignMapper campaignMapper;
    private final CareerCampaignOperatingProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    @Override
    public OperatingProfileView get(Long campaignId) {
        return getForUser(SecurityAssert.requireLoginUserId(), campaignId);
    }

    @Override
    public OperatingProfileView getForUser(Long userId, Long campaignId) {
        ownedCampaign(userId, campaignId);
        CareerCampaignOperatingProfile profile = profileMapper.selectActive(userId, campaignId);
        return profile == null
                ? CareerCampaignOperatingProfileModels.conservativeDefaults(userId, campaignId)
                : toView(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperatingProfileView save(Long campaignId, SaveRequest request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerCampaign campaign = ownedCampaign(userId, campaignId);
        assertWritable(campaign);
        NormalizedProfile normalized = normalize(request);
        CareerCampaignOperatingProfile existing = profileMapper.selectActive(userId, campaignId);
        if (existing == null) {
            if (normalized.expectedLockVersion != null
                    && normalized.expectedLockVersion != 0) {
                throw concurrent();
            }
            CareerCampaignOperatingProfile profile = new CareerCampaignOperatingProfile();
            profile.setUserId(userId);
            profile.setCampaignId(campaignId);
            profile.setWeeklyApplicationTarget(normalized.weeklyApplicationTarget);
            profile.setWeeklyTimeBudgetMinutes(normalized.weeklyTimeBudgetMinutes);
            profile.setMaxActiveOpportunities(normalized.maxActiveOpportunities);
            profile.setStaleAfterDays(normalized.staleAfterDays);
            profile.setDefaultFollowUpDays(normalized.defaultFollowUpDays);
            profile.setFocusRolesJson(normalized.focusRolesJson);
            profile.setFocusLocationsJson(normalized.focusLocationsJson);
            profile.setFocusChannelsJson(normalized.focusChannelsJson);
            profile.setTimezone(normalized.timezone);
            profile.setLockVersion(1);
            try {
                profileMapper.insert(profile);
            } catch (DuplicateKeyException exception) {
                throw concurrent();
            }
            return toView(profile);
        }

        int expectedLockVersion = requiredExpectedVersion(normalized.expectedLockVersion);
        if (!Integer.valueOf(expectedLockVersion).equals(existing.getLockVersion())) {
            throw concurrent();
        }
        int updatedRows = profileMapper.updateOptimistic(
                existing.getId(), userId, campaignId, expectedLockVersion,
                normalized.weeklyApplicationTarget, normalized.weeklyTimeBudgetMinutes,
                normalized.maxActiveOpportunities, normalized.staleAfterDays,
                normalized.defaultFollowUpDays, normalized.focusRolesJson,
                normalized.focusLocationsJson, normalized.focusChannelsJson,
                normalized.timezone);
        if (updatedRows != 1) {
            throw concurrent();
        }
        CareerCampaignOperatingProfile updated = profileMapper.selectActive(userId, campaignId);
        if (updated == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "经营配置更新后无法读取结果");
        }
        return toView(updated);
    }

    private CareerCampaign ownedCampaign(Long userId, Long campaignId) {
        if (campaignId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "campaignId 不能为空");
        }
        CareerCampaign campaign = campaignMapper.selectOne(new LambdaQueryWrapper<CareerCampaign>()
                .eq(CareerCampaign::getId, campaignId)
                .eq(CareerCampaign::getUserId, userId)
                .eq(CareerCampaign::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "求职周期不存在");
        }
        return campaign;
    }

    private static void assertWritable(CareerCampaign campaign) {
        String status = campaign.getStatus() == null
                ? "" : campaign.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "ACTIVE", "PAUSED").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "已完成或已归档周期不能修改经营配置");
        }
    }

    private NormalizedProfile normalize(SaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "经营配置不能为空");
        }
        int weeklyApplicationTarget = valueOrDefault(request.getWeeklyApplicationTarget(),
                CareerCampaignOperatingProfileModels.DEFAULT_WEEKLY_APPLICATION_TARGET);
        int weeklyTimeBudgetMinutes = valueOrDefault(request.getWeeklyTimeBudgetMinutes(),
                CareerCampaignOperatingProfileModels.DEFAULT_WEEKLY_TIME_BUDGET_MINUTES);
        int maxActiveOpportunities = valueOrDefault(request.getMaxActiveOpportunities(),
                CareerCampaignOperatingProfileModels.DEFAULT_MAX_ACTIVE_OPPORTUNITIES);
        int staleAfterDays = valueOrDefault(request.getStaleAfterDays(),
                CareerCampaignOperatingProfileModels.DEFAULT_STALE_AFTER_DAYS);
        int defaultFollowUpDays = valueOrDefault(request.getDefaultFollowUpDays(),
                CareerCampaignOperatingProfileModels.DEFAULT_FOLLOW_UP_DAYS);
        range("weeklyApplicationTarget", weeklyApplicationTarget, 1,
                MAX_WEEKLY_APPLICATION_TARGET);
        range("weeklyTimeBudgetMinutes", weeklyTimeBudgetMinutes, 1,
                MAX_WEEKLY_TIME_BUDGET_MINUTES);
        range("maxActiveOpportunities", maxActiveOpportunities, 1, MAX_ACTIVE_OPPORTUNITIES);
        range("staleAfterDays", staleAfterDays, 1, MAX_STALE_AFTER_DAYS);
        range("defaultFollowUpDays", defaultFollowUpDays, 1, MAX_FOLLOW_UP_DAYS);
        String timezone = cleanTimezone(request.getTimezone());
        return new NormalizedProfile(
                weeklyApplicationTarget,
                weeklyTimeBudgetMinutes,
                maxActiveOpportunities,
                staleAfterDays,
                defaultFollowUpDays,
                normalizeList(request.getFocusRoles(), "focusRoles"),
                normalizeList(request.getFocusLocations(), "focusLocations"),
                normalizeList(request.getFocusChannels(), "focusChannels"),
                timezone,
                request.getExpectedLockVersion());
    }

    private OperatingProfileView toView(CareerCampaignOperatingProfile profile) {
        OperatingProfileView view = new OperatingProfileView();
        view.setId(profile.getId());
        view.setUserId(profile.getUserId());
        view.setCampaignId(profile.getCampaignId());
        view.setConfigured(true);
        view.setWeeklyApplicationTarget(profile.getWeeklyApplicationTarget());
        view.setWeeklyTimeBudgetMinutes(profile.getWeeklyTimeBudgetMinutes());
        view.setMaxActiveOpportunities(profile.getMaxActiveOpportunities());
        view.setStaleAfterDays(profile.getStaleAfterDays());
        view.setDefaultFollowUpDays(profile.getDefaultFollowUpDays());
        view.setFocusRoles(readList(profile.getFocusRolesJson()));
        view.setFocusLocations(readList(profile.getFocusLocationsJson()));
        view.setFocusChannels(readList(profile.getFocusChannelsJson()));
        view.setTimezone(profile.getTimezone());
        view.setLockVersion(profile.getLockVersion());
        view.setCreatedAt(profile.getCreatedAt());
        view.setUpdatedAt(profile.getUpdatedAt());
        return view;
    }

    private String cleanTimezone(String value) {
        String timezone = StringUtils.hasText(value)
                ? value.trim() : CareerCampaignOperatingProfileModels.DEFAULT_TIMEZONE;
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "timezone 必须是有效的 IANA 时区");
        }
        return timezone;
    }

    private String normalizeList(List<String> values, String fieldName) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > MAX_LIST_ITEMS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    fieldName + " 最多只能包含 " + MAX_LIST_ITEMS + " 项");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : source) {
            if (!StringUtils.hasText(value)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        fieldName + " 不能包含空白项");
            }
            String item = value.trim();
            if (item.length() > MAX_ITEM_LENGTH) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        fieldName + " 单项长度不能超过 " + MAX_ITEM_LENGTH);
            }
            unique.add(item);
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(unique));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "经营配置 JSON 序列化失败");
        }
    }

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "经营配置 JSON 格式异常");
        }
    }

    private static int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static void range(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    name + " 必须在 " + minimum + " 到 " + maximum + " 之间");
        }
    }

    private static int requiredExpectedVersion(Integer expected) {
        if (expected == null || expected < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "已存在经营配置时必须提供有效的 lockVersion");
        }
        return expected;
    }

    private static BusinessException concurrent() {
        return new BusinessException(ErrorCode.PARAM_ERROR,
                "经营配置已被其他请求修改，请刷新后重试");
    }

    private record NormalizedProfile(
            int weeklyApplicationTarget,
            int weeklyTimeBudgetMinutes,
            int maxActiveOpportunities,
            int staleAfterDays,
            int defaultFollowUpDays,
            String focusRolesJson,
            String focusLocationsJson,
            String focusChannelsJson,
            String timezone,
            Integer expectedLockVersion) {
    }
}
