package com.codecoachai.resume.careercontact.service;

import com.codecoachai.resume.careercontact.vo.CareerContactReminderCandidateVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CareerContactReminderCandidateService {
    List<CareerContactReminderCandidateVO> listReminderCandidates(Long userId, LocalDate date,
                                                                  LocalDateTime now);
}
