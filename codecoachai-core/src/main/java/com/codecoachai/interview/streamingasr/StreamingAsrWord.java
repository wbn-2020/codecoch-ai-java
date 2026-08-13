package com.codecoachai.interview.streamingasr;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StreamingAsrWord {

    String text;
    Long startMs;
    Long endMs;
}
