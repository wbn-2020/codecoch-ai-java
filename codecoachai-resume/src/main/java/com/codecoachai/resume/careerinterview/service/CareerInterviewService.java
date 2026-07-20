package com.codecoachai.resume.careerinterview.service;

import com.codecoachai.resume.careerinterview.dto.CareerInterviewCalendarLinkDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewProcessCreateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRescheduleDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRoundCreateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewRoundUpdateDTO;
import com.codecoachai.resume.careerinterview.dto.CareerInterviewTransitionDTO;
import com.codecoachai.resume.careerinterview.vo.CareerInterviewProcessVO;
import com.codecoachai.resume.careerinterview.vo.CareerInterviewRoundVO;

public interface CareerInterviewService {
    CareerInterviewProcessVO getProcess(Long applicationId);
    CareerInterviewProcessVO createProcess(Long applicationId, CareerInterviewProcessCreateDTO request);
    CareerInterviewRoundVO createRound(Long processId, CareerInterviewRoundCreateDTO request);
    CareerInterviewRoundVO updateRound(Long roundId, CareerInterviewRoundUpdateDTO request);
    CareerInterviewRoundVO transition(Long roundId, CareerInterviewTransitionDTO request);
    CareerInterviewRoundVO reschedule(Long roundId, CareerInterviewRescheduleDTO request);
    CareerInterviewRoundVO linkCalendarEvent(Long roundId, CareerInterviewCalendarLinkDTO request);
}
