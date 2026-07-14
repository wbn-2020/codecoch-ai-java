package com.codecoachai.interview.voicedelivery;

public interface VoiceDeliveryService {

    VoiceDeviceCheckVO createDeviceCheck(Long sessionId, VoiceDeviceCheckCreateDTO dto);

    VoiceDeliveryAnalysisVO createAnalysis(Long sessionId, VoiceDeliveryAnalysisCreateDTO dto);

    VoiceDeliveryAnalysisVO getAnalysis(Long sessionId, Long analysisId);

    VoiceDeliveryAnalysisVO cancelAnalysis(Long sessionId, Long analysisId);
}
