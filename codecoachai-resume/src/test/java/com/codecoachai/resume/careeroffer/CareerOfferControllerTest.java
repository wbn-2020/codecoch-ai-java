package com.codecoachai.resume.careeroffer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careeroffer.controller.CareerOfferController;
import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionConfirmDTO;
import com.codecoachai.resume.careeroffer.service.CareerOfferService;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.careeroffer.vo.CareerOfferDecisionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerOfferControllerTest {

    @Mock
    private CareerOfferService service;
    @Mock
    private V7FeatureGate featureGate;

    private CareerOfferController controller;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(10L).username("offer-owner").build());
        controller = new CareerOfferController(service, featureGate);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void confirmPassesCampaignAndDecisionIdentityBeforeMutation() {
        CareerOfferDecisionConfirmDTO request = new CareerOfferDecisionConfirmDTO();
        request.setSelectedOfferId(7L);
        request.setUserConfirmed(true);
        CareerOfferDecisionVO expected = new CareerOfferDecisionVO();
        when(service.confirmDecision(5L, 9L, request, "confirm-9")).thenReturn(expected);

        CareerOfferDecisionVO actual = controller.confirm(5L, 9L, request, "confirm-9").getData();

        assertSame(expected, actual);
        verify(service).confirmDecision(5L, 9L, request, "confirm-9");
    }
}
