package com.codecoachai.interview.tts;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TtsSynthesisResult {

    String provider;
    String contentType;
    byte[] audio;
    Long estimatedDurationMs;
}
