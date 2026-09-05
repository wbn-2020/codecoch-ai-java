package com.codecoachai.interview.streamingasr;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StreamingAsrAudioChunk {

    long sequence;
    byte[] audio;
    boolean endOfStream;
}
