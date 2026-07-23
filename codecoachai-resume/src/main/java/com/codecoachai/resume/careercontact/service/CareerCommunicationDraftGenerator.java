package com.codecoachai.resume.careercontact.service;

import com.codecoachai.resume.careercontact.dto.CareerCommunicationDraftDTO;
import com.codecoachai.resume.careercontact.vo.CareerCommunicationDraftVO;

public interface CareerCommunicationDraftGenerator {
    String SCENE = "CAREER_COMMUNICATION_DRAFT";

    CareerCommunicationDraftVO generate(Long userId, Long applicationId,
                                        String displayName, CareerCommunicationDraftDTO request);
}
