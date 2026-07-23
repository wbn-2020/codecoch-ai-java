package com.codecoachai.interview.streamingasr;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StreamingAsrOpenRequest {

    String sessionId;
    String language;
    Integer sampleRateHz;
    Integer channels;
    String encoding;
    String mockTranscript;
    boolean mockTimestampsAvailable;
}
