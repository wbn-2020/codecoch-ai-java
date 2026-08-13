package com.codecoachai.resume.careeroffer.service;

import com.codecoachai.resume.careeroffer.vo.CareerOfferReminderCandidateVO;
import java.time.LocalDate;
import java.util.List;

public interface CareerOfferReminderProvider {

    List<CareerOfferReminderCandidateVO> deadlineReminderCandidatesForUser(
            Long userId, LocalDate day, int limit);
}
