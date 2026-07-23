package com.codecoachai.resume.careercalendar;

public interface CareerInterviewPreparationService {

    CareerInterviewPreparationVO get(Long eventId);

    CareerInterviewPreparationVO generate(
            Long eventId,
            CareerInterviewPreparationGenerateDTO request);
}
