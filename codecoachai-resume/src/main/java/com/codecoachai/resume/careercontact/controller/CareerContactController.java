package com.codecoachai.resume.careercontact.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.careercontact.dto.CareerActivityRecordDTO;
import com.codecoachai.resume.careercontact.dto.CareerActivitySaveDTO;
import com.codecoachai.resume.careercontact.dto.CareerCommunicationDraftDTO;
import com.codecoachai.resume.careercontact.dto.CareerContactSaveDTO;
import com.codecoachai.resume.careercontact.dto.CareerInterviewRoundContactSaveDTO;
import com.codecoachai.resume.careercontact.service.CareerContactService;
import com.codecoachai.resume.careercontact.vo.CareerActivityVO;
import com.codecoachai.resume.careercontact.vo.CareerCommunicationDraftVO;
import com.codecoachai.resume.careercontact.vo.CareerContactVO;
import com.codecoachai.resume.careercontact.vo.CareerInterviewRoundContactVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerContactController {
    private final CareerContactService service;
    private final V7FeatureGate featureGate;

    @GetMapping("/applications/{applicationId}/contacts")
    public Result<List<CareerContactVO>> listContacts(@PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.listContacts(applicationId));
    }

    @PostMapping("/applications/{applicationId}/contacts")
    public Result<CareerContactVO> createContact(@PathVariable Long applicationId,
                                                  @Valid @RequestBody CareerContactSaveDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        request.setApplicationId(applicationId);
        return Result.success(service.createContact(request));
    }

    @PutMapping("/career-contacts/{contactId}")
    public Result<CareerContactVO> updateContact(@PathVariable Long contactId,
                                                  @Valid @RequestBody CareerContactSaveDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.updateContact(contactId, request));
    }

    @DeleteMapping("/career-contacts/{contactId}")
    public Result<Void> deleteContact(@PathVariable Long contactId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        service.deleteContact(contactId);
        return Result.success();
    }

    @GetMapping("/applications/{applicationId}/activities")
    public Result<List<CareerActivityVO>> listActivities(@PathVariable Long applicationId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.listActivities(applicationId));
    }

    @PostMapping("/applications/{applicationId}/activities")
    public Result<CareerActivityVO> createActivity(@PathVariable Long applicationId,
                                                    @Valid @RequestBody CareerActivitySaveDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.createActivity(applicationId, request));
    }

    @PostMapping("/career-activities/{activityId}/record")
    public Result<CareerActivityVO> recordActivity(@PathVariable Long activityId,
                                                   @Valid @RequestBody CareerActivityRecordDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.recordActivity(activityId, request));
    }

    @PostMapping("/applications/{applicationId}/communication-drafts")
    public Result<CareerCommunicationDraftVO> communicationDraft(
            @PathVariable Long applicationId,
            @Valid @RequestBody CareerCommunicationDraftDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.createCommunicationDraft(applicationId, request));
    }

    @GetMapping("/interview-rounds/{roundId}/contacts")
    public Result<List<CareerInterviewRoundContactVO>> listRoundContacts(@PathVariable Long roundId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.listRoundContacts(roundId));
    }

    @PostMapping("/interview-rounds/{roundId}/contacts")
    public Result<CareerInterviewRoundContactVO> addRoundContact(
            @PathVariable Long roundId,
            @Valid @RequestBody CareerInterviewRoundContactSaveDTO request) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        return Result.success(service.addRoundContact(roundId, request));
    }

    @DeleteMapping("/interview-round-contacts/{roundContactId}")
    public Result<Void> removeRoundContact(@PathVariable Long roundContactId) {
        SecurityAssert.requireLoginUserId();
        featureGate.requireContactActivity();
        service.removeRoundContact(roundContactId);
        return Result.success();
    }
}
