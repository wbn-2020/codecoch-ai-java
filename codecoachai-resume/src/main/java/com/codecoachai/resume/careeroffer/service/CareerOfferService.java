package com.codecoachai.resume.careeroffer.service;

import com.codecoachai.resume.careeroffer.dto.CareerOfferCreateDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionConfirmDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionPreviewDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferTransitionDTO;
import com.codecoachai.resume.careeroffer.dto.CareerOfferVersionCreateDTO;
import com.codecoachai.resume.careeroffer.vo.CareerOfferDecisionVO;
import com.codecoachai.resume.careeroffer.vo.CareerOfferVO;
import com.codecoachai.resume.careeroffer.vo.CareerOfferReminderCandidateVO;
import java.time.LocalDate;
import java.util.List;

public interface CareerOfferService {

    List<CareerOfferVO> listByApplication(Long applicationId);

    CareerOfferVO create(Long applicationId, CareerOfferCreateDTO request, String idempotencyKey);

    CareerOfferVO createVersion(Long offerId, CareerOfferVersionCreateDTO request, String idempotencyKey);

    CareerOfferVO transition(Long offerId, CareerOfferTransitionDTO request, String idempotencyKey);

    CareerOfferDecisionVO previewDecision(Long campaignId, CareerOfferDecisionPreviewDTO request,
                                          String idempotencyKey);

    CareerOfferDecisionVO confirmDecision(Long campaignId, Long decisionId, CareerOfferDecisionConfirmDTO request,
                                          String idempotencyKey);

    List<CareerOfferReminderCandidateVO> deadlineReminderCandidates(LocalDate day, int limit);

    CareerOfferDecisionVO decision(Long decisionId);
}
