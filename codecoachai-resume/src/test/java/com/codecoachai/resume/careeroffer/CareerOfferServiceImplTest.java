package com.codecoachai.resume.careeroffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careercampaign.CareerCampaignMapper;
import com.codecoachai.resume.careeroffer.dto.CareerOfferTransitionDTO;
import com.codecoachai.resume.careeroffer.entity.CareerOffer;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionItemMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferDecisionSnapshotMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferEventMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferMapper;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferVersionMapper;
import com.codecoachai.resume.careeroffer.service.CareerOfferApplicationLifecyclePort;
import com.codecoachai.resume.careeroffer.service.CareerOfferCampaignClosurePort;
import com.codecoachai.resume.careeroffer.service.impl.CareerOfferServiceImpl;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerOfferServiceImplTest {

    @Mock private CareerOfferMapper offerMapper;
    @Mock private CareerOfferVersionMapper versionMapper;
    @Mock private CareerOfferEventMapper eventMapper;
    @Mock private CareerOfferDecisionMapper decisionMapper;
    @Mock private CareerOfferDecisionSnapshotMapper snapshotMapper;
    @Mock private CareerOfferDecisionItemMapper itemMapper;
    @Mock private JobApplicationMapper applicationMapper;
    @Mock private CareerCampaignMapper campaignMapper;
    @Mock private CareerOfferApplicationLifecyclePort lifecyclePort;
    @Mock private CareerOfferCampaignClosurePort campaignClosurePort;

    private CareerOfferServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("offer-owner").build());
        service = new CareerOfferServiceImpl(offerMapper, versionMapper, eventMapper, decisionMapper,
                snapshotMapper, itemMapper, applicationMapper, campaignMapper, lifecyclePort,
                campaignClosurePort, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void finalTransitionRequiresExplicitConfirmation() {
        CareerOffer offer = offer("RECEIVED", 3);
        when(offerMapper.selectOwned(7L, 10L)).thenReturn(offer);
        CareerOfferTransitionDTO request = transition("ACCEPTED", false, 3, 4);

        assertThrows(BusinessException.class, () -> service.transition(7L, request, "accept-7"));

        verify(offerMapper, never()).transition(any(), any(), any(), any(), any());
        verify(lifecyclePort, never()).synchronizeFinalOutcome(any(), any(), any(), any(), any());
    }

    @Test
    void acceptedTransitionUsesOptimisticLockAndLifecyclePort() {
        CareerOffer before = offer("RECEIVED", 3);
        CareerOffer after = offer("ACCEPTED", 4);
        when(offerMapper.selectOwned(7L, 10L)).thenReturn(before, after);
        when(offerMapper.transition(7L, 10L, "RECEIVED", "ACCEPTED", 3)).thenReturn(1);
        CareerOfferTransitionDTO request = transition("ACCEPTED", true, 3, 4);

        assertEquals("ACCEPTED", service.transition(7L, request, "accept-7").getStatus());

        verify(lifecyclePort).synchronizeFinalOutcome(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq("ACCEPTED"),
                org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.startsWith("offer:"));
        verify(eventMapper).insert(any(
                com.codecoachai.resume.careeroffer.entity.CareerOfferEvent.class));
    }

    @Test
    void optimisticLockFailureDoesNotSynchronizeApplication() {
        when(offerMapper.selectOwned(7L, 10L)).thenReturn(offer("NEGOTIATING", 8));
        when(offerMapper.transition(7L, 10L, "NEGOTIATING", "DECLINED", 7)).thenReturn(0);
        CareerOfferTransitionDTO request = transition("DECLINED", true, 7, 4);

        assertThrows(BusinessException.class, () -> service.transition(7L, request, "decline-7"));

        verify(lifecyclePort, never()).synchronizeFinalOutcome(any(), any(), any(), any(), any());
    }

    private static CareerOffer offer(String status, int lockVersion) {
        CareerOffer offer = new CareerOffer();
        offer.setId(7L);
        offer.setUserId(10L);
        offer.setApplicationId(77L);
        offer.setStatus(status);
        offer.setLockVersion(lockVersion);
        offer.setNextVersionNo(2);
        return offer;
    }

    private static CareerOfferTransitionDTO transition(String status, boolean confirmed,
                                                       int offerLock, int applicationLock) {
        CareerOfferTransitionDTO request = new CareerOfferTransitionDTO();
        request.setTargetStatus(status);
        request.setUserConfirmed(confirmed);
        request.setExpectedLockVersion(offerLock);
        request.setApplicationLockVersion(applicationLock);
        return request;
    }
}
