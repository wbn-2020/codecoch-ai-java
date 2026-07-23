package com.codecoachai.interview.voicedelivery;

import com.codecoachai.common.core.domain.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/interviews/{sessionId}/voice-delivery")
public class VoiceDeliveryController {

    private final VoiceDeliveryService service;

    public VoiceDeliveryController(@Qualifier("voiceDeliveryServiceImpl") VoiceDeliveryService service) {
        this.service = service;
    }

    @PostMapping("/device-checks")
    public Result<VoiceDeviceCheckVO> createDeviceCheck(@PathVariable Long sessionId,
                                                        @Valid @RequestBody VoiceDeviceCheckCreateDTO dto) {
        return Result.success(service.createDeviceCheck(sessionId, dto));
    }

    @PostMapping("/analyses")
    public Result<VoiceDeliveryAnalysisVO> createAnalysis(@PathVariable Long sessionId,
                                                          @Valid @RequestBody VoiceDeliveryAnalysisCreateDTO dto) {
        return Result.success(service.createAnalysis(sessionId, dto));
    }

    @GetMapping("/analyses/{analysisId}")
    public Result<VoiceDeliveryAnalysisVO> getAnalysis(@PathVariable Long sessionId,
                                                       @PathVariable Long analysisId) {
        return Result.success(service.getAnalysis(sessionId, analysisId));
    }

    @DeleteMapping("/analyses/{analysisId}")
    public Result<VoiceDeliveryAnalysisVO> cancelAnalysis(@PathVariable Long sessionId,
                                                          @PathVariable Long analysisId) {
        return Result.success(service.cancelAnalysis(sessionId, analysisId));
    }
}
