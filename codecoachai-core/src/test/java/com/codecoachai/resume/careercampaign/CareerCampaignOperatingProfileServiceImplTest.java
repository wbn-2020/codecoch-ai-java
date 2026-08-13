package com.codecoachai.resume.careercampaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerCampaignOperatingProfileServiceImplTest {

    @Mock
    private CareerCampaignMapper campaignMapper;
    @Mock
    private CareerCampaignOperatingProfileMapper profileMapper;

    private CareerCampaignOperatingProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(
                LoginUser.builder().userId(7L).username("owner").build());
        service = new CareerCampaignOperatingProfileServiceImpl(
                campaignMapper, profileMapper, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void historicalCampaignWithoutProfileReturnsConservativeDefaults() {
        CareerCampaign campaign = campaign("COMPLETED");
        when(campaignMapper.selectOne(any())).thenReturn(campaign);
        when(profileMapper.selectActive(7L, 12L)).thenReturn(null);

        var result = service.getForUser(7L, 12L);

        assertFalse(Boolean.TRUE.equals(result.getConfigured()));
        assertEquals(3, result.getWeeklyApplicationTarget());
        assertEquals(180, result.getWeeklyTimeBudgetMinutes());
        assertEquals("UTC", result.getTimezone());
        assertEquals(0, result.getLockVersion());
    }

    @Test
    void invalidIanaTimezoneIsRejected() {
        when(campaignMapper.selectOne(any())).thenReturn(campaign("ACTIVE"));
        CareerCampaignOperatingProfileModels.SaveRequest request =
                new CareerCampaignOperatingProfileModels.SaveRequest();
        request.setTimezone("Asia/Not_A_Zone");

        assertThrows(BusinessException.class, () -> service.save(12L, request));
    }

    @Test
    void existingProfileRequiresExpectedLockVersion() {
        when(campaignMapper.selectOne(any())).thenReturn(campaign("ACTIVE"));
        CareerCampaignOperatingProfile existing = new CareerCampaignOperatingProfile();
        existing.setId(3L);
        existing.setUserId(7L);
        existing.setCampaignId(12L);
        existing.setLockVersion(2);
        when(profileMapper.selectActive(7L, 12L)).thenReturn(existing);
        CareerCampaignOperatingProfileModels.SaveRequest request =
                new CareerCampaignOperatingProfileModels.SaveRequest();
        request.setTimezone("Asia/Shanghai");

        assertThrows(BusinessException.class, () -> service.save(12L, request));
    }

    @Test
    void ownerMismatchIsRejectedBeforeProfileLookup() {
        when(campaignMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getForUser(7L, 99L));
    }

    private CareerCampaign campaign(String status) {
        CareerCampaign campaign = new CareerCampaign();
        campaign.setId(12L);
        campaign.setUserId(7L);
        campaign.setStatus(status);
        return campaign;
    }
}
