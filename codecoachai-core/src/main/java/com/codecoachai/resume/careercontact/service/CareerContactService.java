package com.codecoachai.resume.careercontact.service;

import com.codecoachai.resume.careercontact.dto.CareerActivityRecordDTO;
import com.codecoachai.resume.careercontact.dto.CareerActivitySaveDTO;
import com.codecoachai.resume.careercontact.dto.CareerCommunicationDraftDTO;
import com.codecoachai.resume.careercontact.dto.CareerContactSaveDTO;
import com.codecoachai.resume.careercontact.dto.CareerInterviewRoundContactSaveDTO;
import com.codecoachai.resume.careercontact.vo.CareerActivityVO;
import com.codecoachai.resume.careercontact.vo.CareerCommunicationDraftVO;
import com.codecoachai.resume.careercontact.vo.CareerContactVO;
import com.codecoachai.resume.careercontact.vo.CareerInterviewRoundContactVO;
import java.util.List;

public interface CareerContactService {
    List<CareerContactVO> listContacts(Long applicationId);
    CareerContactVO createContact(CareerContactSaveDTO request);
    CareerContactVO updateContact(Long contactId, CareerContactSaveDTO request);
    void deleteContact(Long contactId);
    List<CareerActivityVO> listActivities(Long applicationId);
    CareerActivityVO createActivity(Long applicationId, CareerActivitySaveDTO request);
    CareerActivityVO recordActivity(Long activityId, CareerActivityRecordDTO request);
    CareerCommunicationDraftVO createCommunicationDraft(Long applicationId,
                                                        CareerCommunicationDraftDTO request);
    List<CareerInterviewRoundContactVO> listRoundContacts(Long roundId);
    CareerInterviewRoundContactVO addRoundContact(Long roundId, CareerInterviewRoundContactSaveDTO request);
    void removeRoundContact(Long roundContactId);
}
