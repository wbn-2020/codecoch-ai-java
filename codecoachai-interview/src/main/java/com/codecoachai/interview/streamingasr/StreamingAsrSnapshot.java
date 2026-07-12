package com.codecoachai.interview.streamingasr;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StreamingAsrSnapshot {

    String provider;
    String status;
    String partialTranscript;
    String finalTranscript;
    String timestampMode;
    List<StreamingAsrWord> words;
    long acceptedChunks;
    long acceptedBytes;
    String errorCode;
    String errorMessage;
}
